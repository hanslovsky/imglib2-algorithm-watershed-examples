package de.hanslovsky.examples;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.function.Function;

import org.apache.commons.io.FileUtils;
import org.mastodon.collection.ref.RefArrayList;

import bdv.img.h5.H5Utils;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.DisplayMode;
import bdv.viewer.ViewerPanel;
import bdv.viewer.VisibilityAndGrouping;
import gnu.trove.iterator.TLongDoubleIterator;
import gnu.trove.map.hash.TLongDoubleHashMap;
import gnu.trove.map.hash.TLongIntHashMap;
import gnu.trove.set.hash.TLongHashSet;
import net.imglib2.RealRandomAccess;
import net.imglib2.algorithm.morphology.watershed.AffinityWatershed2;
import net.imglib2.algorithm.morphology.watershed.AffinityWatershed2.WeightedEdge;
import net.imglib2.algorithm.morphology.watershed.CompareBetter;
import net.imglib2.algorithm.morphology.watershed.DisjointSets;
import net.imglib2.algorithm.morphology.watershed.affinity.AffinityView;
import net.imglib2.algorithm.morphology.watershed.affinity.CompositeFactory;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.cell.CellImg;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.ui.OverlayRenderer;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import net.imglib2.view.composite.CompositeIntervalView;
import net.imglib2.view.composite.RealComposite;

public class AffinityViewWatershedTEM2
{
	public static void main( final String[] args ) throws InterruptedException, ExecutionException, IOException
	{

		final CompositeFactory< DoubleType, RealComposite< DoubleType > > compFac = size -> Views.collapseReal( ArrayImgs.doubles( 1, size ) ).randomAccess().get();


		final int[] dimsInt = new int[] { 200, 200, 20, 3 }; // dropbox
//		final int[] dimsInt = new int[] { 1554, 1670, 153, 3 }; // A
		final long[] dims = new long[] { dimsInt[0], dimsInt[1], dimsInt[2], dimsInt[3] };
		final long[] dimsWithEdges = new long[] { dimsInt[0], dimsInt[1], dimsInt[2], 2*dimsInt[3] };
		final long[] labelsDims = new long[] { dimsInt[ 0 ], dimsInt[ 1 ], dimsInt[ 2 ] };
		final int[] perm = new int[] { 2, 1, 0 };
//		final String path = "/groups/saalfeld/home/funkej/workspace/projects/caffe/run/cremi/03_process_training/processed/setup26/100000/sample_A.augmented.0.hdf";
		final String path = Util.HOME_DIR + String.format(
				"/Dropbox/misc/excerpt-%dx%dx%d.h5", dims[ 2 ], dims[ 1 ], dims[ 0 ] );
		final CellImg< FloatType, ?, ? > data = H5Utils.loadFloat( path, "main", dimsInt );
		System.out.println( Arrays.toString( Intervals.dimensionsAsLongArray( data ) ) );
		final long size = dimsWithEdges[ 0 ] * dimsWithEdges[ 1 ] * dimsWithEdges[ 2 ] * dimsWithEdges[ 3 ];
		final boolean requiresCell = size > Integer.MAX_VALUE;
		final Img< DoubleType > affs = requiresCell ? new CellImgFactory< DoubleType >( new int[] { 128, 128, 64, 3 } ).create( dims, new DoubleType() ) : ArrayImgs.doubles( dims );
		for ( final Pair< FloatType, DoubleType > p : Views.interval( Views.pair( Views.permuteCoordinates( data, perm, 3 ), affs ), affs ) )
			p.getB().set( p.getA().getRealDouble() );

		for ( final DoubleType a : affs )
		{
			final double ad = a.get();
			if ( ad < 0.00 )
				a.set( 0.0 );
			else if ( ad > 1.0 )
				a.set( 1.0 );
		}

		final CompositeIntervalView< DoubleType, RealComposite< DoubleType > > affsCollapsed = Views.collapseReal( affs );

		for ( int d = 0; d < affsCollapsed.numDimensions(); ++d )
		{
			final IntervalView< RealComposite< DoubleType > > hs = Views.hyperSlice( affsCollapsed, d, affsCollapsed.max( d ) );
			for ( final RealComposite< DoubleType > v : hs )
				v.get( d ).set( Double.NaN );
		}

		final RealComposite< DoubleType > extension = Views.collapseReal( ArrayImgs.doubles( 1, 3 ) ).randomAccess().get();
		for ( int i = 0; i < 3; ++i )
			extension.get( i ).set( Double.NaN );

		final CompareBetter< DoubleType > comp = ( t1, t2 ) -> t1.get() > t2.get();

		final Img< LongType > labels = requiresCell ? new CellImgFactory< LongType >( Integer.MAX_VALUE ).create( labelsDims, new LongType() ) : ArrayImgs.longs( labelsDims );

		final AffinityView< DoubleType, RealComposite< DoubleType > > affsView = new AffinityView<>( Views.extendValue( affsCollapsed, extension ), compFac );



		final Img< DoubleType > affsCopy = affs.factory().create( dimsWithEdges, new DoubleType() );

		System.out.println( "Materializing view..." );
		for ( final Pair< RealComposite< DoubleType >, RealComposite< DoubleType > > p : Views.flatIterable( Views.interval( Views.pair( affsView, Views.collapseReal( affsCopy ) ), labels ) ) )
			p.getB().set( p.getA() );

		final int nThreads = 1;
		final int nWarmup = 0;

		System.out.println( "Warming up..." );
		for ( int i = 0; i < nWarmup; ++i )
			AffinityWatershed2.letItRain(
					Views.collapseReal( affsCopy ),
					requiresCell ? new CellImgFactory< LongType >( Integer.MAX_VALUE ).create( labelsDims, new LongType() ) : ArrayImgs.longs( labelsDims ),
							comp,
							new DoubleType( -Double.MAX_VALUE ),
							Executors.newFixedThreadPool( nThreads ),
							nThreads,
							() -> {} );

		System.out.println();
		System.out.println( "Let it rain!" );
		final long t0 = System.nanoTime();
		final long[] counts = AffinityWatershed2.letItRain(
				Views.collapseReal( affsCopy ),
				labels,
				comp,
				new DoubleType( -Double.MAX_VALUE ),
				Executors.newFixedThreadPool( nThreads ),
				nThreads,
				() -> {} );
		final long t1 = System.nanoTime();
		System.out.println( "Runtime: " + ( t1 - t0 ) / 1e6 + "ms" );
		long countSum = 0;
		for ( final long count : counts )
			countSum += count;
		System.out.println( "COUNT SUM " + countSum );

//		counts[ 269511 ] = 848;
//		counts[ 276235 ] = 328;
//		counts[ 283176 ] = 116;
//		counts[ 433426 ] = 81;

		final long[] strides = AffinityWatershed2.generateStride( labels );
		final long[] steps = AffinityWatershed2.generateSteps( strides );

		final long highBit = 1l << 63;
		final long secondHighBit = 1l << 62;

		FileUtils.writeStringToFile(
				new File( System.getProperty( "user.home" ) + "/local/tmp/watershed-regular-counts" ),
				Arrays.toString( counts ) );

		final TLongDoubleHashMap rgMap = AffinityWatershed2.generateRegionGraph(
				Views.collapseReal( affsCopy ),
				labels,
				steps,
				comp,
				new DoubleType( -Double.MAX_VALUE ),
				highBit,
				secondHighBit,
				counts.length );

		final TLongHashSet indices = new TLongHashSet();
		for ( final long k : rgMap.keys() )
		{
			if ( indices.contains( k ) )
			{
				System.out.println( k + " exists!" );
				System.exit( 231 );
			}
			indices.add( k );
		}



		final BdvStackSource< ARGBType > bdv = BdvFunctions.show( new ConvertedRandomAccessibleInterval<>( Views.collapseReal( affs ), ( s, t ) -> {
			t.set( ARGBType.rgba( 255 * tf( s.get( 0 ).get() ), 255 * tf( s.get( 1 ).get() ), 255 * tf( s.get( 2 ).get() ), 255.0 ) );
		}, new ARGBType() ), "affs" );

		final VisibilityAndGrouping vag = bdv.getBdvHandle().getViewerPanel().getVisibilityAndGrouping();
		vag.setDisplayMode( DisplayMode.GROUP );

		final Path p = Paths.get( System.getProperty( "user.home" ) + "/local/tmp/watershed-regular-rg" );
		Files.deleteIfExists( p );
		Files.createFile( p );
		for ( final TLongDoubleIterator it = rgMap.iterator(); it.hasNext(); )
		{
			it.advance();
			Files.write( p, ( it.key() + "," + it.value() + "\n" ).getBytes(), StandardOpenOption.APPEND );
		}


		final RefArrayList< WeightedEdge > rg = AffinityWatershed2.graphToList( rgMap, counts.length );
		System.out.println( rgMap.size() + " edges" + " (" + rg.size() + ")" );
		rg.get( 0 );
		Collections.sort( rg, Collections.reverseOrder() );

		FileUtils.writeLines( new File( System.getProperty( "user.home" ) + "/local/tmp/watershed-regular-edges" ), rg );

		System.out.println( "Merging graph!" );

		final double[] thresholds = new double[] { -1.0, 100, 1000, 10000, 10000, 50000, 150000, 1000000 };
//		final double[] thresholds = new double[] { 1e4 };
		final DisjointSets[] djs = AffinityWatershed2.mergeRegionGraph(
				rg,
				counts,
				( v1, v2 ) -> v1 < v2,
				thresholds );

		for ( int d = 0; d < djs.length; ++d )
		{

			final DisjointSets dj = djs[ d ];

			System.out.println( "Got roots! " + dj.setCount() + " " + counts.length + " " + thresholds[ d ] );

			final TLongIntHashMap colors = new TLongIntHashMap();
			final Random rng = new Random( 100 );
			{
				colors.put( 0, 0 );
				for ( int i = 1; i < counts.length; ++i )
				{
					final long root = dj.findRoot( i );
					if ( !colors.contains( root ) )
						colors.put( root, rng.nextInt() );
				}
			}



			final ConvertedRandomAccessibleInterval< LongType, ARGBType > colored = new ConvertedRandomAccessibleInterval<>(
					labels, ( s, t ) -> {
						t.set( colors.get( dj.findRoot( s.getInteger() ) ) );
					}, new ARGBType() );


			BdvFunctions.show( colored, "colored labels " + thresholds[ d ], BdvOptions.options().addTo( bdv ) );

			vag.addSourceToGroup( 0, d );
			vag.addSourceToGroup( d + 1, d );

			System.out.println( "cnt: " + dj.setCount() + " " + vag.numGroups() + " " + vag.numSources() );
		}

		final RealRandomAccess< RealComposite< DoubleType > > rra = Views.interpolate( Views.extendBorder( Views.collapseReal( affs ) ), new NearestNeighborInterpolatorFactory<>() ).realRandomAccess();

		final ValueDisplayListener< RealComposite< DoubleType > > vdl = new ValueDisplayListener<>(
				rra,
				bdv.getBdvHandle().getViewerPanel(),
				c -> String.format( "%.4f , %.4f , %.4f", c.get( 0 ).getRealDouble(), c.get( 1 ).getRealDouble(), c.get( 2 ).getRealDouble() ) );
		bdv.getBdvHandle().getViewerPanel().getDisplay().addOverlayRenderer( vdl );
		bdv.getBdvHandle().getViewerPanel().getDisplay().addMouseMotionListener( vdl );

		final ConvertedRandomAccessibleInterval< LongType, LongType > rooted = new ConvertedRandomAccessibleInterval<>(
				labels, ( s, t ) -> {
					t.set( djs[ 0 ].findRoot( s.getInteger() ) );
				}, new LongType() );

		final RealRandomAccess< LongType > rootedRra = Views.interpolate( Views.extendValue( rooted, new LongType( -1 ) ), new NearestNeighborInterpolatorFactory<>() ).realRandomAccess();

		final ValueDisplayListener< LongType > rootedVdl = new ValueDisplayListener<>( rootedRra, bdv.getBdvHandle().getViewerPanel() );
		bdv.getBdvHandle().getViewerPanel().getDisplay().addOverlayRenderer( rootedVdl );
		bdv.getBdvHandle().getViewerPanel().getDisplay().addMouseMotionListener( rootedVdl );

	}

	public static double tf( final double e )
	{
		return 1 - e;
	}

	public static < T > T getProbs( final int x, final int y, final RealRandomAccess< T > access, final ViewerPanel viewer )
	{
		access.setPosition( x, 0 );
		access.setPosition( y, 1 );
		access.setPosition( 0, 2 );

		viewer.displayToGlobalCoordinates( access );

		return access.get();
	}

	public static class ValueDisplayListener< T > implements MouseMotionListener, OverlayRenderer
	{

		private final RealRandomAccess< T > access;

		private final ViewerPanel viewer;

		private int width = 0;

		private int height = 0;

		private T val = null;

		private final Function< T, String > stringConverter;

		private static final ArrayList< WeakReference< ValueDisplayListener< ? > > > references = new ArrayList<>();

		private int offset;

		public ValueDisplayListener( final RealRandomAccess< T > access, final ViewerPanel viewer, final Function< T, String > stringConverter )
		{
			super();
			this.access = access;
			this.viewer = viewer;
			this.stringConverter = stringConverter;
			synchronized ( references )
			{
				for ( int i = references.size() - 1; i >= 0; --i )
					if ( references.get( i ).get() == null )
						references.remove( i );
				references.add( new WeakReference<>( this ) );

				for ( int i = 0; i < references.size(); ++i )
					references.get( i ).get().offset = i;
			}
		}

		public ValueDisplayListener( final RealRandomAccess< T > access, final ViewerPanel viewer )
		{
			this( access, viewer, t -> t.toString() );
		}

		@Override
		public void mouseDragged( final MouseEvent e )
		{}

		@Override
		public void mouseMoved( final MouseEvent e )
		{
			final int x = e.getX();
			final int y = e.getY();

			this.val = getProbs( x, y, access, viewer );
			viewer.getDisplay().repaint();
		}

		@Override
		public void drawOverlays( final Graphics g )
		{
			if ( val != null )
			{
				final Graphics2D g2d = ( Graphics2D )g;

				g2d.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
				g2d.setComposite( AlphaComposite.SrcOver );


				final int w = 176;
				final int h = 11;

				final int top = height - h * ( offset + 1 );
				final int left = width - w;

				g2d.setColor( Color.white );
				g2d.fillRect( left, top, w, h );
				g2d.setColor( Color.BLACK );
				final String string = stringConverter.apply( this.val );

				g2d.drawString( string, left + 1, top + h - 1 );
//				drawBox( "selection", g2d, top, left, fid );
			}
		}


		@Override
		public void setCanvasSize( final int width, final int height )
		{
			this.width = width;
			this.height = height;
		}

	}
}

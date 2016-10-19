package de.hanslovsky.examples;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import bdv.img.h5.H5Utils;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.ViewerPanel;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.hash.TLongIntHashMap;
import net.imglib2.RealRandomAccess;
import net.imglib2.algorithm.morphology.watershed.AffinityWatershed2;
import net.imglib2.algorithm.morphology.watershed.AffinityWatershed2.CompareBetter;
import net.imglib2.algorithm.morphology.watershed.AffinityWatershed2.WeightedEdge;
import net.imglib2.algorithm.morphology.watershed.DisjointSets;
import net.imglib2.algorithm.morphology.watershed.affinity.AffinityView;
import net.imglib2.algorithm.morphology.watershed.affinity.CompositeFactory;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.img.cell.CellImg;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.ui.OverlayRenderer;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import net.imglib2.view.composite.CompositeIntervalView;
import net.imglib2.view.composite.RealComposite;

public class AffinityViewWatershedTEM2
{
	public static void main( final String[] args ) throws InterruptedException, ExecutionException
	{

		final CompositeFactory< DoubleType, RealComposite< DoubleType > > compFac = size -> Views.collapseReal( ArrayImgs.doubles( 1, size ) ).randomAccess().get();


		final int[] dimsInt = new int[] { 300, 300, 100, 3 };
		final long[] dims = new long[] { dimsInt[0], dimsInt[1], dimsInt[2], dimsInt[3] };
		final long[] dimsWithEdges = new long[] { dimsInt[0], dimsInt[1], dimsInt[2], 2*dimsInt[3] };
		final long[] labelsDims = new long[] { dimsInt[ 0 ], dimsInt[ 1 ], dimsInt[ 2 ] };
		final int[] perm = new int[] { 2, 1, 0 };
		final CellImg< FloatType, ?, ? > data = H5Utils.loadFloat( Util.HOME_DIR + String.format( "/Dropbox/misc/excerpt-%dx%dx%d.h5", dims[ 2 ], dims[ 1 ], dims[ 0 ] ), "main", dimsInt );
		System.out.println( Arrays.toString( Intervals.dimensionsAsLongArray( data ) ) );
		final ArrayImg< DoubleType, DoubleArray > affs = ArrayImgs.doubles( dims );
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

		final AffinityView< DoubleType, RealComposite< DoubleType > > affsView = new AffinityView<>( Views.extendValue( affsCollapsed, extension ), compFac );

		final ArrayImg< LongType, LongArray > labels = ArrayImgs.longs( labelsDims );

//		final RandomAccess< RealComposite< DoubleType > > affsAcc = affsView.randomAccess();
//		System.out.println( Arrays.toString( Intervals.dimensionsAsLongArray( affsCollapsed ) ) );
//		affsAcc.setPosition( new long[] { 39, 1, 99 } );
//		for ( int d = 0; d < 6; ++d )
//			System.out.println( "aff = " + affsAcc.get().get( d ) );
//
//		final OutOfBounds< RealComposite< DoubleType > > acc2 = Views.extendValue( affsCollapsed, extension ).randomAccess();
//		acc2.setPosition( new long[] { 39, 1, 100 } );
//		for ( int i = 0; i < 3; ++i )
//			System.out.println( "gff = " + acc2.get().get( i ) + " , ");
//
//		System.exit( 0 );

		final ValuePair< TLongArrayList, long[] > rootsAndCounts = AffinityWatershed2.letItRain(
				affsView,
				labels,
				comp,
				new DoubleType( -Double.MAX_VALUE ),
				Executors.newFixedThreadPool( 1 ),
				1,
				() -> {} );

		final long[] strides = AffinityWatershed2.generateStride( labels );
		final long[] steps = AffinityWatershed2.generateSteps( strides );

		final long[] counts = rootsAndCounts.getB();

		final long highBit = 1l << 63;
		final long secondHighBit = 1l << 62;

		final ArrayList< WeightedEdge > rg = AffinityWatershed2.generateRegionGraph(
				affsView,
				labels,
				steps,
				comp,
				new DoubleType( -Double.MAX_VALUE ),
				highBit,
				secondHighBit,
				counts.length );

		final long sizeThreshold = 50000;


		final DisjointSets dj = new DisjointSets( counts.length );





		System.out.println( "Merging graph!" );
		for ( final WeightedEdge edge : rg )
		{

			final double val = edge.getV();

			final double valSquared = val * val * sizeThreshold;

			if ( val < 1e-4 )
				break;

			final int id1 = dj.findRoot( ( int ) edge.getFirst() );
			final int id2 = dj.findRoot( ( int ) edge.getSecond() );
//			if ( val > 0.5 )

			if ( id1 != id2 && id1 > 0 && id2 > 0 )
				if ( Math.min( counts[id1], counts[id2] ) < valSquared ) {
					final int newId = dj.join( id1, id2 );
					final int otherId = newId == id1 ? id2 : id1;
					counts[ newId ] = counts[ newId ] + counts[ otherId ];
					counts[ otherId ] = 0;
//					System.out.println( id1 + " " + id2 + " " + newId + " : " + counts[ id1 ] + " " + counts[ id2 ] + " " + counts[ newId ] );
//					break;

//					counts[ id1 ] += counts[ id2 ];
//					counts[ id2 ] = 0;
//					final long tmpCount = counts[ id1 ];
//					counts[ id1 ] = counts[ newId ];
//					counts[ newId ] = tmpCount;

//					if ( id2 < id1 )
//						System.out.println( id1 + " " + id2 + " " + newId + " " + counts[id1] + " " + counts[id2] + " " + counts[newId] + " " + edge);
//
//					if ( id1 != newId )
//					{
//						System.out.println( "MIZZZGE!" );
//						break;
//					}
				}

		}

//		final TLongArrayList roots = AffinityWatershed.watershed( affsView, labels, compare, new DoubleType( Double.MAX_VALUE ), new LongType( -1 ) );

		System.out.println( "Got roots! " + dj.setCount() + " " + counts.length );

//		BdvFunctions.show( labels, "labels" );

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
					t.set( colors.get( dj.findRoot( ( int ) s.getIntegerLong() ) ) );
				}, new ARGBType() );


		final BdvStackSource< ARGBType > bdv = BdvFunctions.show( new ConvertedRandomAccessibleInterval<>( Views.collapseReal( affs ), ( s, t ) -> {
			t.set( ARGBType.rgba( 255 * tf( s.get( 0 ).get() ), 255 * tf( s.get( 1 ).get() ), 255 * tf( s.get( 2 ).get() ), 255.0 ) );
		}, new ARGBType() ), "affs" );

		BdvFunctions.show( colored, "colored labels", BdvOptions.options().addTo( bdv ) );

		System.out.println( "cnt: " + dj.setCount() );

		final RealRandomAccess< RealComposite< DoubleType > > rra = Views.interpolate( Views.extendBorder( Views.collapseReal( affs ) ), new NearestNeighborInterpolatorFactory<>() ).realRandomAccess();

		final ValueDisplayListener< DoubleType > vdl = new ValueDisplayListener<>( rra, bdv.getBdvHandle().getViewerPanel() );
		bdv.getBdvHandle().getViewerPanel().getDisplay().addOverlayRenderer( vdl );
		bdv.getBdvHandle().getViewerPanel().getDisplay().addMouseMotionListener( vdl );

	}

	public static double tf( final double e )
	{
		return 1 - e;
	}

	public static < T extends RealType< T > > RealComposite< T > getProbs( final int x, final int y, final RealRandomAccess< RealComposite< T > > access, final ViewerPanel viewer )
	{
		access.setPosition( x, 0 );
		access.setPosition( y, 1 );
		access.setPosition( 0, 2 );

		viewer.displayToGlobalCoordinates( access );

		return access.get();
	}

	public static class ValueDisplayListener< T extends RealType< T > > implements MouseMotionListener, OverlayRenderer
	{

		private final RealRandomAccess< RealComposite< T > > access;

		private final ViewerPanel viewer;

		private int width = 0;

		private int height = 0;

		private RealComposite< T > probs = null;

		public ValueDisplayListener( final RealRandomAccess< RealComposite< T > > access, final ViewerPanel viewer )
		{
			super();
			this.access = access;
			this.viewer = viewer;
		}

		@Override
		public void mouseDragged( final MouseEvent e )
		{}

		@Override
		public void mouseMoved( final MouseEvent e )
		{
			final int x = e.getX();
			final int y = e.getY();

			this.probs = getProbs( x, y, access, viewer );
			viewer.getDisplay().repaint();
		}

		@Override
		public void drawOverlays( final Graphics g )
		{
			if ( probs != null )
			{
				final Graphics2D g2d = ( Graphics2D )g;

				g2d.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
				g2d.setComposite( AlphaComposite.SrcOver );


				final int w = 176;
				final int h = 11;

				final int top = height - h;
				final int left = width - w;

				g2d.setColor( Color.white );
				g2d.fillRect( left, top, w, h );
				g2d.setColor( Color.BLACK );
				final String string = String.format( "%.4f , %.4f , %.4f", this.probs.get( 0 ).getRealDouble(), this.probs.get( 1 ).getRealDouble(), this.probs.get( 2 ).getRealDouble() );
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

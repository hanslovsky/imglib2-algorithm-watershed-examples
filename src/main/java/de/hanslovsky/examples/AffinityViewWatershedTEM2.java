package de.hanslovsky.examples;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import bdv.img.h5.H5Utils;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.ViewerPanel;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.hash.TLongLongHashMap;
import net.imglib2.Point;
import net.imglib2.RealRandomAccess;
import net.imglib2.algorithm.gradient.PartialDerivative;
import net.imglib2.algorithm.morphology.watershed.AffinityWatershed2;
import net.imglib2.algorithm.morphology.watershed.affinity.AffinityView;
import net.imglib2.algorithm.morphology.watershed.affinity.CompositeFactory;
import net.imglib2.converter.RealDoubleConverter;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.img.array.ArrayCursor;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.img.cell.CellImg;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.ui.OverlayRenderer;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;
import net.imglib2.view.composite.CompositeIntervalView;
import net.imglib2.view.composite.RealComposite;

public class AffinityViewWatershedTEM2
{
	public static void main( final String[] args ) throws InterruptedException, ExecutionException
	{

		final CompositeFactory< DoubleType, RealComposite< DoubleType > > compFac = size -> Views.collapseReal( ArrayImgs.doubles( 1, size ) ).randomAccess().get();

		final long[] dims = new long[] { 300, 300, 100, 3 };
		final long[] dimsWithEdges = new long[] { 300, 300, 100, 6 };
		final long[] labelsDims = new long[] { 300, 300, 100 };
		final int[] perm = new int[] { 2, 1, 0 };
		final CellImg< FloatType, ?, ? > data = H5Utils.loadFloat( Util.HOME_DIR + "/Dropbox/misc/excerpt.h5", "main", new int[] { 300, 300, 100, 3 } );
		System.out.println( Arrays.toString( Intervals.dimensionsAsLongArray( data ) ) );
		final ArrayImg< DoubleType, DoubleArray > affs = ArrayImgs.doubles( dims );
		for ( final Pair< FloatType, DoubleType > p : Views.interval( Views.pair( Views.permuteCoordinates( data, perm, 3 ), affs ), affs ) )
			p.getB().set( 1 - p.getA().getRealDouble() );

		for ( final DoubleType a : affs )
		{
			final double ad = a.get();
			if ( ad < 0.02 )
				a.set( 0.0 );
			else if ( ad > 0.8 )
				a.set( 1.0 );
		}

		final CompositeIntervalView< DoubleType, RealComposite< DoubleType > > affsCollapsed = Views.collapseReal( affs );

		final RealComposite< DoubleType > extension = Views.collapseReal( ArrayImgs.doubles( 1, 3 ) ).randomAccess().get();
		for ( int i = 0; i < 3; ++i )
			extension.get( i ).set( Double.NaN );

		final AffinityView< DoubleType, RealComposite< DoubleType > > affsView = new AffinityView<>( Views.extendValue( affsCollapsed, extension ), compFac );

		final ArrayImg< LongType, LongArray > labels = ArrayImgs.longs( labelsDims );

		final ArrayImg< BitType, LongArray > edges = ArrayImgs.bits( dimsWithEdges );

		final TLongArrayList roots = AffinityWatershed2.letItRain(
				affsView,
				Views.collapseReal( edges ),
				labels,
				( t1, t2 ) -> t1.get() < t2.get(),
				new LongType( -1 ),
				new LongType( -2 ),
				new DoubleType( Double.MAX_VALUE ),
				Executors.newFixedThreadPool( 1 ),
				1 );

//		final TLongArrayList roots = AffinityWatershed.watershed( affsView, labels, compare, new DoubleType( Double.MAX_VALUE ), new LongType( -1 ) );

		System.out.println( "Got roots!" );

//		BdvFunctions.show( labels, "labels" );

		@SuppressWarnings( "unchecked" )
		final ArrayImg< DoubleType, DoubleArray >[] gradients = new ArrayImg[] {
				ArrayImgs.doubles( labelsDims ),
				ArrayImgs.doubles( labelsDims ),
				ArrayImgs.doubles( labelsDims ),
				ArrayImgs.doubles( labelsDims )
		};

		for ( int d = 0; d < gradients.length - 1; ++d )
			PartialDerivative.gradientCentralDifference( Views.extendBorder( new ConvertedRandomAccessibleInterval<>( labels, new RealDoubleConverter<>(), new DoubleType() ) ), gradients[ d ], d );

		@SuppressWarnings( { "unchecked" } )
		final ArrayCursor< DoubleType >[] cursors = new ArrayCursor[] {
				gradients[ 0 ].cursor(),
				gradients[ 1 ].cursor(),
				gradients[ 2 ].cursor(),
				gradients[ 3 ].cursor(),
		};

		while ( cursors[ 0 ].hasNext() )
		{
			double val = 0.0;
			for ( int d = 0; d < gradients.length - 1; ++d )
			{
				final double v = cursors[ d ].next().get();
				val += v;
			}
			cursors[ gradients.length - 1 ].next().set( val );
		}

		final ConvertedRandomAccessibleInterval< DoubleType, LongType > view =
				new ConvertedRandomAccessibleInterval<>( gradients[ gradients.length - 1 ], ( s, t ) -> {
					t.set( s.get() > 0.0 ? 1 << 16 : 0 );
				}, new LongType() );

		final TLongLongHashMap hs = new TLongLongHashMap();
		final int colors[] = new int[ roots.size() ];
		final Random rng = new Random( 100 );
		{
			int i = 0;
			for ( final TLongIterator r = roots.iterator(); r.hasNext(); ++i )
			{
				hs.put( r.next(), i );
				colors[ i ] = rng.nextInt() << 8;
			}
		}


//		BdvFunctions.show( view, "labels grad" );
		final ConvertedRandomAccessibleInterval< LongType, LongType > oneBased = new ConvertedRandomAccessibleInterval<>( labels, ( s, t ) -> {
			t.set( hs.get( s.get() ) );
		}, new LongType() );
		BdvFunctions.show( oneBased, "labels" );

		final ConvertedRandomAccessibleInterval< LongType, ARGBType > colored = new ConvertedRandomAccessibleInterval<>(
				oneBased, ( s, t ) -> {
					t.set( colors[ s.getInteger() ] );
				}, new ARGBType() );



		final BdvStackSource< ARGBType > bdv = BdvFunctions.show( new ConvertedRandomAccessibleInterval<>( Views.collapseReal( affs ), ( s, t ) -> {
			t.set( ARGBType.rgba( 255 * s.get( 0 ).get(), 255 * s.get( 1 ).get(), 255 * s.get( 2 ).get(), 1.0 ) );
		}, new ARGBType() ), "affs" );

		final ArrayImg< DoubleType, DoubleArray > rootImg = ArrayImgs.doubles( labelsDims );

		for ( final TLongIterator r = roots.iterator(); r.hasNext(); ) {
			final ArrayCursor< DoubleType > c = rootImg.cursor();
			c.jumpFwd( r.next() );
			c.next().set( 1 << 16 );
		}

		BdvFunctions.show( colored, "colored labels", BdvOptions.options().addTo( bdv ) );
		BdvFunctions.show( rootImg, "roots", BdvOptions.options().addTo( bdv ) );

		final RealRandomAccess< RealComposite< DoubleType > > rra = Views.interpolate( Views.extendBorder( Views.collapseReal( affs ) ), new NearestNeighborInterpolatorFactory<>() ).realRandomAccess();

		final ValueDisplayListener< DoubleType > vdl = new ValueDisplayListener<>( rra, bdv.getBdvHandle().getViewerPanel() );
		bdv.getBdvHandle().getViewerPanel().getDisplay().addOverlayRenderer( vdl );
		bdv.getBdvHandle().getViewerPanel().getDisplay().addMouseMotionListener( vdl );

		for ( int i = 1, k = 0; i < roots.size(); ++i, ++k )
		{
			final long r1 = roots.get( i ), r2 = roots.get( k );
			if ( Math.abs( r1 - r2 ) == 1 )
			{
				final Point p1 = new Point( 3 ), p2 = new Point( 3 );
				IntervalIndexer.indexToPosition( r1, labels, p1 );
				IntervalIndexer.indexToPosition( r2, labels, p2 );
				System.out.println( p1 + " " + p2 );
				break;
			}
		}

//		AffinityWatershed.watershed( affView, labels, compare, new DoubleType( Double.MAX_VALUE ) );

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

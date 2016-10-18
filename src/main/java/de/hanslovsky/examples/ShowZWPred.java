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

import bdv.img.h5.H5Utils;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.ViewerPanel;
import gnu.trove.map.hash.TLongIntHashMap;
import net.imglib2.RealRandomAccess;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
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
import net.imglib2.view.Views;
import net.imglib2.view.composite.RealComposite;

public class ShowZWPred
{

	public static void main( final String[] args )
	{
		final String probPath = Util.HOME_DIR + "/Dropbox/misc/excerpt.h5";
		final String predPath = Util.HOME_DIR + "/Dropbox/misc/excerpt-pred.h5";
//		final String predPath = Util.HOME_DIR + "/local/tmp/zw.h5";

		final long[] dims = new long[] { 300, 300, 100, 3 };
		final long[] labelsDims = new long[] { 300, 300, 100 };
		final int[] perm = new int[] { 2, 1, 0 };
		final CellImg< FloatType, ?, ? > data = H5Utils.loadFloat( probPath, "main", new int[] { 300, 300, 100, 3 } );
		System.out.println( Arrays.toString( Intervals.dimensionsAsLongArray( data ) ) );
		final ArrayImg< DoubleType, DoubleArray > affs = ArrayImgs.doubles( dims );
		for ( final Pair< FloatType, DoubleType > p : Views.interval( Views.pair( Views.permuteCoordinates( data, perm, 3 ), affs ), affs ) )
			p.getB().set( 1 - p.getA().getRealDouble() );

		final BdvStackSource< ARGBType > bdv = BdvFunctions.show( new ConvertedRandomAccessibleInterval<>( Views.collapseReal( affs ), ( s, t ) -> {
			t.set( ARGBType.rgba( 255 * s.get( 0 ).get(), 255 * s.get( 1 ).get(), 255 * s.get( 2 ).get(), 1.0 ) );
		}, new ARGBType() ), "affs" );

		final RealRandomAccess< RealComposite< DoubleType > > rra = Views.interpolate( Views.extendBorder( Views.collapseReal( affs ) ), new NearestNeighborInterpolatorFactory<>() ).realRandomAccess();

		final ValueDisplayListener< DoubleType > vdl = new ValueDisplayListener<>( rra, bdv.getBdvHandle().getViewerPanel() );
		bdv.getBdvHandle().getViewerPanel().getDisplay().addOverlayRenderer( vdl );
		bdv.getBdvHandle().getViewerPanel().getDisplay().addMouseMotionListener( vdl );

		final CellImg< LongType, ?, ? > pred0 = H5Utils.loadUnsignedLong( predPath, "pred1", new int[] { 300, 300, 100 } );

//		BdvFunctions.show( pred0, "pred0" );

		final Random rng = new Random( 100 );
		final TLongIntHashMap colors = new TLongIntHashMap();

		final ConvertedRandomAccessibleInterval< LongType, ARGBType > colored = new ConvertedRandomAccessibleInterval<>( pred0, ( s, t ) -> {
			final long v = s.get();
			synchronized ( colors )
			{
				if ( colors.contains( v ) )
					t.set( colors.get( v ) );
				else
				{
					final int r = rng.nextInt();
					t.set( r );
					colors.put( v, r );
				}
			}
		}, new ARGBType() );

		BdvFunctions.show( colored, "colored labels", BdvOptions.options().addTo( bdv ) );

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
				final Graphics2D g2d = ( Graphics2D ) g;

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

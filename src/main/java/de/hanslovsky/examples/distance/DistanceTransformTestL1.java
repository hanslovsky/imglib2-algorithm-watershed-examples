package de.hanslovsky.examples.distance;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import ij.ImageJ;
import ij.ImagePlus;
import net.imglib2.Cursor;
import net.imglib2.algorithm.morphology.distance.DistanceTransform;
import net.imglib2.algorithm.morphology.distance.DistanceTransform.DISTANCE_TYPE;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.img.array.ArrayCursor;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

public class DistanceTransformTestL1
{

	public static void main( final String[] args ) throws InterruptedException, ExecutionException
	{
		final String homeDir = System.getProperty( "user.home" );
		new ImageJ();
//		final String url = "/home/hanslovskyp/Downloads/dt/input.png";
		final String url = homeDir + "/Downloads/dt/input-100x100+50+50.png";
		final ImagePlus imp = new ImagePlus( url );
		final ArrayImg< FloatType, FloatArray > img = ArrayImgs.floats( ( float[] ) imp.getProcessor().convertToFloatProcessor().getPixels(), imp.getWidth(), imp.getHeight() );

		final ArrayImg< FloatType, FloatArray > target = ArrayImgs.floats( imp.getWidth(), imp.getHeight() );
		final ArrayImg< FloatType, FloatArray > target2 = ArrayImgs.floats( imp.getWidth(), imp.getHeight() );

		final ConvertedRandomAccessibleInterval< FloatType, FloatType > conv = new ConvertedRandomAccessibleInterval<>( img, ( s, t ) -> {
			t.set( s.get() > 0.0 ? 1e8f : 0.0f );
		}, new FloatType() );

		ImageJFunctions.show( conv, "conv" );

		final double[] w = { 1.0, 1.0 };

		DistanceTransform.transform( conv, target, DISTANCE_TYPE.L1_ANISOTROPIC, Runtime.getRuntime().availableProcessors(), w );
		DistanceTransform.transformL1( conv, target2, Executors.newFixedThreadPool( Runtime.getRuntime().availableProcessors() ), Runtime.getRuntime().availableProcessors(), w );

		final ArrayImg< DoubleType, DoubleArray > ref = ArrayImgs.doubles( imp.getWidth(), imp.getHeight() );
		for ( final DoubleType r : ref )
			r.set( Double.MAX_VALUE );

		for ( final ArrayCursor< DoubleType > p = ref.cursor(); p.hasNext(); )
		{
			final DoubleType pType = p.next();
			final double pX = p.getDoublePosition( 0 );
			final double pY = p.getDoublePosition( 1 );
			for ( final Cursor< FloatType > q = Views.flatIterable( conv ).cursor(); q.hasNext(); )
			{
				final FloatType qType = q.next();
				final double diffX = q.getDoublePosition( 0 ) - pX;
				final double diffY = q.getDoublePosition( 1 ) - pY;
				pType.set( Math.min( qType.getRealDouble() + ( w[ 0 ] * Math.abs( diffX ) + w[ 1 ] * Math.abs( diffY ) ), pType.get() ) );
			}
		}

		ImageJFunctions.show( ref, "ref" );
		ImageJFunctions.show( target, "dt" );
		ImageJFunctions.show( target2, "dt" );

	}

}

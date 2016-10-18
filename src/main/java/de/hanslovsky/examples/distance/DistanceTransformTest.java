package de.hanslovsky.examples.distance;

import java.util.concurrent.ExecutionException;

import ij.ImageJ;
import ij.ImagePlus;
import net.imglib2.algorithm.morphology.distance.DistanceTransform;
import net.imglib2.algorithm.morphology.distance.DistanceTransform.DISTANCE_TYPE;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

public class DistanceTransformTest
{

	public static void main( final String[] args ) throws InterruptedException, ExecutionException
	{

		final String homeDir = System.getProperty( "user.home" );

		new ImageJ();
		final String url = homeDir + "/Downloads/dt/input.png";
		System.out.println( url + " " + homeDir );
//		final String url = "/home/hanslovskyp/Downloads/dt/input-100x100+50+50.png";
		final ImagePlus imp = new ImagePlus( url );
		imp.show();
		final ArrayImg< FloatType, FloatArray > img = ArrayImgs.floats( ( float[] ) imp.getProcessor().convertToFloatProcessor().getPixels(), imp.getWidth(), imp.getHeight() );

		final ArrayImg< FloatType, FloatArray > target = ArrayImgs.floats( imp.getWidth(), imp.getHeight() );

		final ConvertedRandomAccessibleInterval< FloatType, FloatType > conv = new ConvertedRandomAccessibleInterval<>( img, ( s, t ) -> {
			t.set( s.get() > 0.0 ? 1e8f : 0.0f );
		}, new FloatType() );

		ImageJFunctions.show( conv, "conv" );

		final int nThreads = 1;

		DistanceTransform.transform( conv, target, DISTANCE_TYPE.L1, nThreads, 1.0e5 );
//		DistanceTransform.transform( conv, target, DISTANCE_TYPE.EUCLIDIAN, nThreads, 5.0e5 );
//		DistanceTransform.transform( conv, target, new L1DistanceIsotropic( 1e8f / 10 ) );
//		DistanceTransform.transform( conv, target, new L1DistanceAnisotropic( 1.0, 0.2 ) );
//		DistanceTransform.transform( conv, target, new EuclidianDistanceAnisotropic( 0.1, 3.0 ) );
//		DistanceTransform.transformL1( conv, target, 1.0, 0.2 );

		ImageJFunctions.show( target, "dt" );

		float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;

		for ( final FloatType v : Views.iterable( new ConvertedRandomAccessibleInterval<>( target, ( s, t ) -> {
			t.set( ( float ) Math.sqrt( s.get() ) );
		}, new FloatType() ) ) )
		{
			min = Math.min( v.get(), min );
			max = Math.max( v.get(), max );
		}

		final float fMin = min;
		final float range = max - min;

		ImageJFunctions.show( new ConvertedRandomAccessibleInterval<>( target, ( s, t ) -> {
			t.set( ( ( float ) Math.sqrt( s.get() ) - fMin ) / range * 255.0f );
		}, new FloatType() ) );


		new ImagePlus( homeDir + "/Downloads/dt/output.pgm" ).show();

	}

}

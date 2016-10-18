package de.hanslovsky.examples.distance;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ij.ImageJ;
import ij.ImagePlus;
import net.imglib2.algorithm.morphology.distance.DistanceTransform;
import net.imglib2.algorithm.morphology.distance.EuclidianDistanceIsotropic;
import net.imglib2.converter.Converter;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;

public class DistanceTransformTest3 {

	public static void main( final String[] args ) throws IncompatibleTypeException, InterruptedException, ExecutionException
	{

		new ImageJ();

		final String homeDir = System.getProperty( "user.home" );

//		final String url = "http://img.autobytel.com/car-reviews/autobytel/11694-good-looking-sports-cars/2016-Ford-Mustang-GT-burnout-red-tire-smoke.jpg";
		final String url = homeDir + "/Dropbox/misc/butterfly.jpg";
//		final String url = "http://www.tomgibara.com/images/canny-example-edges.png";
		final ImagePlus imp = new ImagePlus( url );
//		imp.show();

		final ArrayImg< FloatType, FloatArray > img = ArrayImgs.floats( ( float[] ) imp.getProcessor().convertToFloatProcessor().getPixels(), imp.getWidth(), imp.getHeight() );
		ImageJFunctions.show( img );

		final Converter< FloatType, DoubleType > conv = ( input, output ) -> {
			output.set( input.getRealDouble() );
			output.mul( -1.0 );
		};

		final int N = 5;
		for ( final int nThreads : new int[] { 1, 2, 3, 4, 5, 6, Runtime.getRuntime().availableProcessors() } )
		{
			final ExecutorService es = Executors.newFixedThreadPool( nThreads );
			System.out.println( nThreads + " threads" );
			for ( int i = 0; i < N; ++i )
			{
				final ArrayImg< DoubleType, DoubleArray > dt = ArrayImgs.doubles( imp.getWidth(), imp.getHeight() );

				final long t0 = System.currentTimeMillis();
				if ( false )
					DistanceTransform.transform(
							new ConvertedRandomAccessibleInterval<>( img, conv, new DoubleType() ),
							dt,
							new EuclidianDistanceIsotropic( 5e-2 ),
							es,
							nThreads );
				else
					DistanceTransform.transformL1(
							new ConvertedRandomAccessibleInterval<>( img, conv, new DoubleType() ),
							dt,
							es,
							nThreads,
							5e0 );
				final long t1 = System.currentTimeMillis();
				System.out.println( t1 - t0 + "ms" );
				if ( i == 0 )
					ImageJFunctions.show( dt, "dt" + nThreads );
			}
			System.out.println();
		}
	}

}

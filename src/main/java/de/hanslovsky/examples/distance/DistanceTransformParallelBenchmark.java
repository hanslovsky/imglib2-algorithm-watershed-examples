package de.hanslovsky.examples.distance;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.io.FileUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import ij.ImagePlus;
import ij.io.FileSaver;
import ij.process.ImageConverter;
import net.imglib2.algorithm.morphology.distance.DistanceTransform;
import net.imglib2.algorithm.morphology.distance.DistanceTransform.DISTANCE_TYPE;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;

public class DistanceTransformParallelBenchmark
{

	public static class Parameters
	{
		public final String source;

		public final int nWarmup;

		public final int nTest;

		public final int nDim;

		public final long[] dimensions;

		public final long nPixels;

		public final int nThreads;

		public final long[] runtimesNanos;

		public final DISTANCE_TYPE dType;

		public final double[] weights;

		public Parameters(
				final String source,
				final int nWarmup,
				final int nTest,
				final int nDim,
				final long[] dimensions,
				final long nPixels,
				final int nThreads,
				final long[] runtimesNanos,
				final DISTANCE_TYPE dType,
				final double[] weights )
		{
			super();
			this.source = source;
			this.nWarmup = nWarmup;
			this.nTest = nTest;
			this.nDim = nDim;
			this.dimensions = dimensions;
			this.nPixels = nPixels;
			this.nThreads = nThreads;
			this.runtimesNanos = runtimesNanos;
			this.dType = dType;
			this.weights = weights;
		}
	}

	public static void main( final String... args ) throws InterruptedException, ExecutionException, IOException
	{

		final String homeDir = System.getProperty( "user.home" );
		final String source = homeDir + "/Dropbox/misc/distance-transform-benchmark/butterfly/source.jpg";
		final ImagePlus imp = new ImagePlus( source );
		final int nWarmup = 10;
		final int nTest = 20;
		final int nDim = 2;
		final long[] dimensions = new long[] { imp.getWidth(), imp.getHeight() };
		final long nPixels = dimensions[ 0 ] * dimensions[ 1 ];
//		final DISTANCE_TYPE dType = DISTANCE_TYPE.EUCLIDIAN;
		final double[] weights = { 0.1, 0.1 };




		new ImageConverter( imp ).convertToGray32();
		final Img< FloatType > wrapped = ImagePlusImgs.from( imp );
		final ArrayImg< DoubleType, DoubleArray > img = ArrayImgs.doubles( Intervals.dimensionsAsLongArray( wrapped ) );
		for ( final Pair< FloatType, DoubleType > p : Views.interval( Views.pair( wrapped, img ), img ) )
			p.getB().set( -p.getA().get() );

//		imp.show();

		for ( final DISTANCE_TYPE dType : new DISTANCE_TYPE[] { DISTANCE_TYPE.L1, DISTANCE_TYPE.EUCLIDIAN } )
		{
			final String targetDir = homeDir + "/Dropbox/misc/distance-transform-benchmark/butterfly/" + dType.toString();

			new File( targetDir ).mkdirs();

			{
				new FileSaver( imp ).saveAsTiff( targetDir + "/source.tif" );
				final ArrayImg< DoubleType, DoubleArray > dt = ArrayImgs.doubles( Intervals.dimensionsAsLongArray( wrapped ) );
				DistanceTransform.transform( img, dt, dType, Runtime.getRuntime().availableProcessors(), weights );
				new FileSaver( ImageJFunctions.wrap( dt, "" ) ).saveAsTiff( targetDir + "/dt.tif" );
			}

			for ( int nThreads = 1; nThreads <= Runtime.getRuntime().availableProcessors(); ++nThreads )
			{
				final ExecutorService es = Executors.newFixedThreadPool( nThreads );

				for ( int k = 0; k < nWarmup; ++k )
				{
					final ArrayImg< DoubleType, DoubleArray > dt = ArrayImgs.doubles( Intervals.dimensionsAsLongArray( wrapped ) );
					DistanceTransform.transform( img, dt, dType, es, nThreads, weights );
				}

				final long[] runtimesNanos = new long[ nTest ];
				for ( int k = 0; k < nTest; ++k )
				{
					final ArrayImg< DoubleType, DoubleArray > dt = ArrayImgs.doubles( Intervals.dimensionsAsLongArray( wrapped ) );
					final long t0 = System.nanoTime();
					DistanceTransform.transform( img, dt, dType, es, nThreads, weights );
					final long t1 = System.nanoTime();
					runtimesNanos[ k ] = t1 - t0;
				}

				final Parameters p = new Parameters( source, nWarmup, nTest, nDim, dimensions, nPixels, nThreads, runtimesNanos, dType, weights );

				final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
				final String json = gson.toJson( p );

				FileUtils.writeStringToFile( new File( targetDir + "/" + nThreads + ".json" ), json );

				System.out.println( "" + nThreads );

				es.shutdown();
			}
		}



	}


}

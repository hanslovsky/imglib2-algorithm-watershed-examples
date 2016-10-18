package de.hanslovsky.examples.distance;

import java.util.concurrent.ExecutionException;

import ij.ImageJ;
import ij.ImagePlus;
import ij.process.ImageConverter;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.morphology.distance.DistanceTransform;
import net.imglib2.algorithm.morphology.distance.DistanceTransform.DISTANCE_TYPE;
import net.imglib2.converter.Converters;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;

public class DistanceTransformTestL2
{

	public static void main( final String[] args ) throws IncompatibleTypeException, InterruptedException, ExecutionException
	{
		new ImageJ();
		final String homeDir = System.getProperty( "user.home" );
		final String dataDir = "/data/" + System.getProperty( "user.name" );
		final String url = homeDir + "/Downloads/dt/input.png";
//		final String url = "/home/hanslovskyp/Downloads/dt/input-100x100+50+50.png";

//		final String url = "http://img.autobytel.com/car-reviews/autobytel/11694-good-looking-sports-cars/2016-Ford-Mustang-GT-burnout-red-tire-smoke.jpg";
//		final String url = dataDir + "/davi_toy_set/data/intensity_1-contrast.tif";

		final ImagePlus imp = new ImagePlus( url );
		new ImageConverter( imp ).convertToGray32();
		imp.show();
//		final ArrayImg< FloatType, FloatArray > img = ArrayImgs.floats( ( float[] ) imp.getProcessor().convertToFloatProcessor().getPixels(), imp.getWidth(), imp.getHeight() );
		final Img< FloatType > img = ImagePlusImgs.from( imp );

		final ArrayImg< DoubleType, DoubleArray > target = ArrayImgs.doubles( Intervals.dimensionsAsLongArray( img ) );

		final RandomAccessibleInterval< FloatType > converted = new ConvertedRandomAccessibleInterval<>( img, ( s, t ) -> {
			t.set( s.get() );
		}, new FloatType() );

//		final ArrayImg< DoubleType, DoubleArray > g = WatershedsExample2DGeneric.gradientsAndMagnitude( img, 1.0 )[ 2 ];

//		ImageJFunctions.show( g );
//		final ConvertedRandomAccessibleInterval< DoubleType, DoubleType > conv =
//				new ConvertedRandomAccessibleInterval<>( g, ( s, t ) -> {
//					t.set( -s.get() );
//				}, new DoubleType() );
		DistanceTransform.transform( converted, target, DISTANCE_TYPE.EUCLIDIAN_ANISOTROPIC, Runtime.getRuntime().availableProcessors(), 0.1e-2, 0.1e-2, 25e-1 );

		final RandomAccessibleInterval< DoubleType > inv = Converters.convert( ( RandomAccessibleInterval< DoubleType > ) target, ( s, t ) -> {
			t.set( -s.get() );
		}, new DoubleType() );


		ImageJFunctions.show( target );
		ImageJFunctions.show( inv );




	}

}

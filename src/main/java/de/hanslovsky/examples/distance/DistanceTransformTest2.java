package de.hanslovsky.examples.distance;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import net.imglib2.algorithm.morphology.distance.DistanceTransform;
import net.imglib2.algorithm.morphology.distance.DistanceTransform.DISTANCE_TYPE;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

public class DistanceTransformTest2
{

	public static void main( final String[] args ) throws InterruptedException, ExecutionException
	{
		final float f[] = { 1.0f, 2.0f, 3.0f, 4.0f, 1.0f, 10.0f, 5.0f };
		final ArrayImg< FloatType, FloatArray > img = ArrayImgs.floats( f, f.length );
		final ArrayImg< FloatType, FloatArray > target = ArrayImgs.floats( f.length );

		System.out.println( Arrays.toString( Intervals.dimensionsAsIntArray( Views.collapseReal( img ) ) ) );

		DistanceTransform.transform( img, target, DISTANCE_TYPE.L1, 1, 1.0 );

//		DistanceTransform.transformL1_1D(
//				Views.collapseReal( img ).randomAccess().get(),
//				Views.collapseReal( target ).randomAccess().get(), 1.0, f.length );

		for ( final FloatType i : img )
			System.out.print( i + ", " );
		System.out.println();

		for ( final FloatType i : target )
			System.out.print( i + ", " );
		System.out.println();

	}

}

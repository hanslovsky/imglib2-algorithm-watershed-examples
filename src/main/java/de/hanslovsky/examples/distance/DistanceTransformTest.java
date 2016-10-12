package de.hanslovsky.examples.distance;

import ij.ImageJ;
import ij.ImagePlus;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

public class DistanceTransformTest
{

	public static void main( final String[] args )
	{
		new ImageJ();
		final String url = "/home/phil/input.png";
		final ImagePlus imp = new ImagePlus( url );
		imp.show();
		final ArrayImg< FloatType, FloatArray > img = ArrayImgs.floats( ( float[] ) imp.getProcessor().convertToFloatProcessor().getPixels(), imp.getWidth(), imp.getHeight() );

		final ArrayImg< FloatType, FloatArray > target = ArrayImgs.floats( imp.getWidth(), imp.getHeight() );

		final ConvertedRandomAccessibleInterval< FloatType, FloatType > conv = new ConvertedRandomAccessibleInterval<>( img, ( s, t ) -> {
			t.set( s.get() > 0.0 ? 1e8f : 0.0f );
		}, new FloatType() );

		ImageJFunctions.show( conv, "conv" );

		DistanceTransform.transformSquared( conv, target );

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

		new ImagePlus( "/home/phil/output.pgm" ).show();

	}

}

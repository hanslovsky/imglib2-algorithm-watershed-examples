package de.hanslovsky.examples;

import ij.ImageJ;
import ij.ImagePlus;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.algorithm.gradient.PartialDerivative;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.RectangleShape;
import net.imglib2.algorithm.neighborhood.RectangleShape.NeighborhoodsAccessible;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.array.ArrayCursor;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;

public class DilateDistance
{

	public static < T extends RealType< T > > ArrayImg< DoubleType, DoubleArray >[] gradientsAndMagnitude(
			final RandomAccessibleInterval< T > img,
			final double sigma ) throws IncompatibleTypeException
	{
		final ArrayImg< DoubleType, DoubleArray > gauss = ArrayImgs.doubles( img.dimension( 0 ), img.dimension( 1 ) );
		Gauss3.gauss( sigma, Views.extendBorder( img ), gauss );
		@SuppressWarnings( "unchecked" )
		final ArrayImg< DoubleType, DoubleArray >[] gradients = new ArrayImg[] {
				ArrayImgs.doubles( img.dimension( 0 ), img.dimension( 1 ) ),
				ArrayImgs.doubles( img.dimension( 0 ), img.dimension( 1 ) ),
				ArrayImgs.doubles( img.dimension( 0 ), img.dimension( 1 ) )
		};

		PartialDerivative.gradientCentralDifference( Views.extendBorder( gauss ), gradients[ 0 ], 0 );
		PartialDerivative.gradientCentralDifference( Views.extendBorder( gauss ), gradients[ 1 ], 1 );

		for ( ArrayCursor< DoubleType > g1 = gradients[ 0 ].cursor(), g2 = gradients[ 1 ].cursor(), g = gradients[ 2 ].cursor(); g.hasNext(); )
			g.next().setReal( Math.sqrt( Math.pow( g1.next().get(), 2 ) + Math.pow( g2.next().get(), 2 ) ) );

		return gradients;
	}

	public static void main( final String[] args ) throws IncompatibleTypeException
	{

		new ImageJ();
		final String url = "http://img.autobytel.com/car-reviews/autobytel/11694-good-looking-sports-cars/2016-Ford-Mustang-GT-burnout-red-tire-smoke.jpg";

		final ImagePlus imp = new ImagePlus( url );

		final ArrayImg< FloatType, FloatArray > impWrapped = ArrayImgs.floats( ( float[] ) imp.getProcessor().convertToFloatProcessor().getPixels(), imp.getWidth(), imp.getHeight() );

		final ArrayImg< DoubleType, DoubleArray >[] grads = gradientsAndMagnitude( impWrapped, 3.0 );

		final RandomAccessibleInterval< DoubleType > img = grads[ 2 ];
		final ArrayImg< DoubleType, DoubleArray > imgCopy = ArrayImgs.doubles( Intervals.dimensionsAsLongArray( img ) );

		for ( final Pair< DoubleType, DoubleType > c : Views.interval( Views.pair( img, imgCopy ), img ) )
			c.getB().set( c.getA() );

		final ArrayImg< LongType, LongArray > distances = ArrayImgs.longs( Intervals.dimensionsAsLongArray( img ) );

		final RectangleShape shape = new RectangleShape( 1, true );

		final ExtendedRandomAccessibleInterval< DoubleType, RandomAccessibleInterval< DoubleType > > imgExtended = Views.extendBorder( imgCopy );
		final ExtendedRandomAccessibleInterval< LongType, ArrayImg< LongType, LongArray > > distancesExtended = Views.extendBorder( distances );

		final RandomAccessible< Pair< DoubleType, LongType > > paired = Views.pair( imgExtended, distancesExtended );

		final NeighborhoodsAccessible< DoubleType > neighborhoods = shape.neighborhoodsRandomAccessible( Views.extendBorder( img ) );



		long count = 1;
		boolean changed = false;

		do
		{
			final Cursor< Pair< DoubleType, LongType > > pairCursor = Views.flatIterable( Views.interval( paired, img ) ).cursor();
			final Cursor< Neighborhood< DoubleType > > imgCursor = Views.flatIterable( Views.interval( neighborhoods, img ) ).cursor();
			changed = false;
			while ( pairCursor.hasNext() )
			{

				final Pair< DoubleType, LongType > p = pairCursor.next();
				final DoubleType output = p.getA();
				final LongType dist = p.getB();

				for ( final DoubleType n : imgCursor.next() )
					if ( output.compareTo( n ) < 0 )
					{
						changed = true;
						output.set( n );
						dist.set( count );
					}

			}
			System.out.println( count );
			++count;
//			ImageJFunctions.show( img, "img at " + count );
//			ImageJFunctions.show( imgCopy, "imgCopy at " + count );
			for ( final Pair< DoubleType, DoubleType > c : Views.interval( Views.pair( img, imgCopy ), img ) )
				c.getA().set( c.getB() );
		}
		while ( changed );

		ImageJFunctions.show( grads[ 2 ], "grad magnitude" );
		ImageJFunctions.show( distances, "distances" );

	}

}

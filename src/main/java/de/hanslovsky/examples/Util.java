package de.hanslovsky.examples;

import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;

import ij.ImagePlus;
import ij.process.ColorProcessor;
import net.imglib2.Cursor;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.algorithm.gradient.PartialDerivative;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.RectangleShape;
import net.imglib2.algorithm.neighborhood.RectangleShape.NeighborhoodsAccessible;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayCursor;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import net.imglib2.view.composite.Composite;
import net.imglib2.view.composite.GenericComposite;

public class Util
{

	public static < T extends RealType< T > & NativeType< T > > RandomAccessibleInterval< GenericComposite< T > > toAffinities(
			final RandomAccessibleInterval< T > input,
			final Shape shape )
	{
		final T extension = net.imglib2.util.Util.getTypeFromInterval( input ).createVariable();
		extension.setReal( Double.POSITIVE_INFINITY );
		return toAffinities( input, shape, new ArrayImgFactory<>(), extension );
	}

	public static < T extends Type< T > > RandomAccessibleInterval< GenericComposite< T > > toAffinities( final RandomAccessibleInterval< T > input, final Shape shape, final ImgFactory< T > factory, final T extension )
	{

		int numChannels = 1;
		for ( final Cursor< T > cursor = shape.neighborhoods( input ).cursor().next().cursor(); cursor.hasNext(); ++numChannels )
			cursor.fwd();

		final long[] dims = LongStream.concat( Arrays.stream( Intervals.dimensionsAsLongArray( input ) ), LongStream.of( numChannels ) ).toArray();
		System.out.println( "NUM CHANNELS " + numChannels );
		final Img< T > img = factory.create( dims, net.imglib2.util.Util.getTypeFromInterval( input ).copy() );
		final IntervalView< GenericComposite< T > > collapsedAndTranslated = Views.translate( Views.collapse( img ), Intervals.minAsLongArray( input ) );

		toAffinities( Views.extendValue( input, extension ), collapsedAndTranslated, shape );

		return collapsedAndTranslated;

	}

	public static < T extends Type< T > > void toAffinities( final RandomAccessible< T > input, final RandomAccessibleInterval< ? extends Composite< T > > output, final Shape shape )
	{
		final Cursor< Neighborhood< T > > source = Views.flatIterable( Views.interval( shape.neighborhoodsRandomAccessible( input ), output ) ).cursor();
		final Cursor< ? extends Composite< T > > target = Views.flatIterable( output ).cursor();
		while ( source.hasNext() )
		{
			final Cursor< T > s = source.next().cursor();
			final Composite< T > t = target.next();
			for ( int i = 0; s.hasNext(); ++i )
				t.get( i ).set( s.next() );
		}
	}

	public static < T extends RealType< T > > ArrayImg< DoubleType, DoubleArray >[] gradientsAndMagnitudeNoExcept(
			final RandomAccessibleInterval< T > img,
			final double sigma )
	{
		try
		{
			return gradientsAndMagnitude( img, sigma );
		}
		catch ( final IncompatibleTypeException e )
		{
			throw new RuntimeException( e );
		}
	}

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

	public static < T extends IntegerType< T > > long seedsGrid( final RandomAccessibleInterval< T > seeds, final long stride, final int startSeed, final List< Localizable > seedList )
	{
		long seed = startSeed;

		final long start = stride / 2;

		final RandomAccess< T > seedsAccess = seeds.randomAccess();

		for ( long y = start; y < seeds.dimension( 1 ); y += stride )
		{
			seedsAccess.setPosition( new long[] { start, y } );
			for ( long x = start; x < seeds.dimension( 0 ); x += stride, seedsAccess.move( stride, 0 ) )
			{
				seedList.add( new Point( seedsAccess ) );
				seedsAccess.get().setInteger( seed++ );
			}
		}
		return seed;
	}

	public static < T extends IntegerType< T > & NativeType< T > > ArrayImg< T, ? > makeSeedsGradient(
			final RandomAccessibleInterval< T > img,
			final T backGround )
	{
		final ArrayImgFactory< T > fac = new ArrayImgFactory<>();

		final T dummy = backGround.createVariable();
		final NeighborhoodsAccessible< T > nh = new RectangleShape( 1, true ).neighborhoodsRandomAccessible( Views.extendValue( img, backGround ) );
		final ArrayImg< T, ? > result = fac.create( new long[] { img.dimension( 0 ), img.dimension( 1 ) }, backGround );
		final ArrayCursor< T > ic = result.cursor();
		for ( final Cursor< Neighborhood< T > > n = Views.interval( nh, img ).cursor(); n.hasNext(); )
		{
			final Cursor< T > c = n.next().cursor();
			ic.fwd();
			ic.get().set( backGround );
			dummy.set( c.next() );
			while ( c.hasNext() )
			{
				c.fwd();
				if ( !dummy.valueEquals( backGround ) && !dummy.valueEquals( c.get() ) )
				{
					ic.get().setInteger( 255 );
					break;
				}
			}
		}

		System.out.println( "returning result" );
		return result;

	}

	public static < T extends IntegerType< T > > void overlay( final ImagePlus imp, final RandomAccessibleInterval< T > watersheds, final T backGround )
	{
		final ColorProcessor ip = imp.getProcessor().convertToColorProcessor();
		imp.setProcessor( ip );
		final int[] px = ( int[] ) ip.getPixels();
		final Cursor< T > w = Views.iterable( watersheds ).cursor();
		for ( int i = 0; i < px.length; ++i )
			if ( !w.next().valueEquals( backGround ) )
				px[ i ] = 255 << 16;
	}

	public static < T extends IntegerType< T >, U extends RealType< U > >
	void seedsGradientMinima( final RandomAccessibleInterval< T > seeds, final RandomAccessible< Pair< U, Neighborhood< U > > > gWithNeighborhood, final long startSeed )
	{

		long seed = startSeed;

		final RandomAccess< T > seedsAccess = seeds.randomAccess();

		for ( final Cursor< Pair< U, Neighborhood< U > > > pair = Views.interval( gWithNeighborhood, seeds ).cursor(); pair.hasNext(); )
		{
			pair.fwd();
			seedsAccess.setPosition( pair );
			final double comp = pair.get().getA().getRealDouble();
			if ( Double.isNaN( comp ) || comp == 0.0 )
				// seedsAccess.get().set( -1 );
				continue;
			else
			{
				boolean isMinimum = true;
				for ( final U n : pair.get().getB() )
				{
					final double nVal = n.getRealDouble();
					if ( !Double.isNaN( nVal ) && nVal <= comp )
					{
						isMinimum = false;
						break;
					}
				}
				if ( isMinimum )
				{
					seedsAccess.get().setInteger( seed );
					++seed;
				}
			}
		}
	}

	public static < T extends IntegerType< T > > long seedsGrid( final RandomAccessibleInterval< T > seeds, final long stride, final long startSeed )
	{
		long seed = startSeed;

		final long start = stride / 2;

		final RandomAccess< T > seedsAccess = seeds.randomAccess();

		for ( long y = start; y < seeds.dimension( 1 ); y += stride )
		{
			seedsAccess.setPosition( new long[] { start, y } );
			for ( long x = start; x < seeds.dimension( 0 ); x += stride, seedsAccess.move( stride, 0 ) )
				seedsAccess.get().setInteger( seed++ );
		}
		return seed;
	}

}

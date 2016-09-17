package de.hanslovsky.examples;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;

import net.imglib2.RandomAccess;
import net.imglib2.algorithm.morphology.watershed.flat.FlatViews;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.type.numeric.integer.LongType;
import sun.misc.Unsafe;

@SuppressWarnings( "restriction" )
public class Dummy
{
	private static final Unsafe UNSAFE;

	static
	{
		try
		{
			final PrivilegedExceptionAction< Unsafe > action = () -> {
				final Field field = Unsafe.class.getDeclaredField( "theUnsafe" );
				field.setAccessible( true );
				return ( Unsafe ) field.get( null );
			};

			UNSAFE = AccessController.doPrivileged( action );
		}
		catch ( final Exception ex )
		{
			throw new RuntimeException( ex );
		}
	}

	public static void putLong( final long value, final long[] array, final long position )
	{
		UNSAFE.putLong( array, LONG_ARRAY_OFFSET + position, value );
	}

	public static void putDouble( final double value, final long[] array, final long position )
	{
		UNSAFE.putDouble( array, LONG_ARRAY_OFFSET + position, value );
	}

	public static long getLong( final long[] array, final long position )
	{
		return UNSAFE.getLong( array, LONG_ARRAY_OFFSET + position );
	}

	public static double getDouble( final long[] array, final long position )
	{
		return UNSAFE.getDouble( array, LONG_ARRAY_OFFSET + position );
	}

	private static final long LONG_ARRAY_OFFSET = UNSAFE.arrayBaseOffset( long[].class );

	public static void main( final String[] args )
	{
		final int N1 = 100;
		final int N2 = 100000000;

		final long[] arr = new long[ 1 ];
		final double[] drr = new double[ 1 ];

		long tUnsafe = 0;
		for ( int i = 0; i < N1; ++i )
		{
			final long t0 = System.currentTimeMillis();
			for ( int k = 0; k < N2; ++k )
			{
				drr[ 0 ] = getDouble( arr, 0 );
			}
			final long t1 = System.currentTimeMillis();
			if ( i > 0 )
			{
				tUnsafe += t1 - t0;
			}
		}

		long tUnsafeW = 0;
		for ( int i = 0; i < N1; ++i )
		{
			final long t0 = System.currentTimeMillis();
			for ( int k = 0; k < N2; ++k )
			{
				putDouble( 1.0d, arr, 0 );
			}
			final long t1 = System.currentTimeMillis();
			if ( i > 0 )
			{
				tUnsafeW += t1 - t0;
			}
		}

		long tDouble = 0;
		for ( int i = 0; i < N1; ++i )
		{
			final long t0 = System.currentTimeMillis();
			for ( int k = 0; k < N2; ++k )
			{
				arr[ 0 ] = Double.doubleToLongBits( 1.0d );
			}
			final long t1 = System.currentTimeMillis();
			if ( i > 0 )
			{
				tDouble += t1 - t0;
			}
		}

		long tDoubleR = 0;
		for ( int i = 0; i < N1; ++i )
		{
			final long t0 = System.currentTimeMillis();
			for ( int k = 0; k < N2; ++k )
			{
				arr[ 0 ] = Double.doubleToRawLongBits( 1.0d );
			}
			final long t1 = System.currentTimeMillis();
			if ( i > 0 )
			{
				tDoubleR += t1 - t0;
			}
		}

		long tDoubleInv = 0;
		for ( int i = 0; i < N1; ++i )
		{
			final long t0 = System.currentTimeMillis();
			for ( int k = 0; k < N2; ++k )
			{
				drr[ 0 ] = Double.longBitsToDouble( arr[ 0 ] );
			}
			final long t1 = System.currentTimeMillis();
			if ( i > 0 )
			{
				tDoubleInv += t1 - t0;
			}
		}

		System.out.println(
				Arrays.toString( arr ) + " " + Arrays.toString( drr ) + " " +
						tUnsafe * 1.0 / N1 + " " + tUnsafeW * 1.0 / N1 + " " +
						tDouble * 1.0 / N1 + " " + tDoubleR * 1.0 / N1 + " " +
						tDoubleInv * 1.0 / N1 );

		final ArrayImg< LongType, LongArray > img = ArrayImgs.longs( 20, 30 );
		long l = 1;
		for ( final LongType i : img )
		{
			i.set( l++ );
		}
		final RandomAccess< LongType > fa = FlatViews.flatten( img ).randomAccess();
		l = 0;
		for ( final LongType i : img )
		{
			fa.setPosition( l, 0 );
			if ( !i.valueEquals( fa.get() ) )
			{
				System.out.println( i + " " + fa.get() );
			}
			++l;
		}

	}
}

package de.hanslovsky.examples;

import org.apache.commons.lang.StringUtils;

import bdv.util.ConstantRandomAccessible;
import net.imglib2.Cursor;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.type.numeric.integer.LongType;

public class Dummy
{
	public static void main( final String[] args )
	{
		final int nDim = 2;
		final RandomAccess< Neighborhood< LongType > > access = new DiamondShape( 1 ).neighborhoodsRandomAccessible( new ConstantRandomAccessible<>( new LongType(), nDim ) ).randomAccess();
		for ( final Cursor< LongType > n = access.get().cursor(); n.hasNext(); )
		{
			n.fwd();
			System.out.println( new Point( n) );
		}

		System.out.println( StringUtils.leftPad( Long.toBinaryString( 1l << 63 ), 64, "0" ) + " " + ( ( 1l << 63 ) - 1 ) );
	}
}

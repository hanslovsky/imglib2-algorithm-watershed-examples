package de.hanslovsky.examples;

import java.util.Random;

import org.apache.commons.lang.StringUtils;
import org.mastodon.collection.ref.RefArrayList;

import bdv.util.ConstantRandomAccessible;
import net.imglib2.Cursor;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.morphology.watershed.AffinityWatershed2;
import net.imglib2.algorithm.morphology.watershed.AffinityWatershed2.WeightedEdge;
import net.imglib2.algorithm.morphology.watershed.AffinityWatershed2.WeightedEdgePool;
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

		final WeightedEdgePool pool = new AffinityWatershed2.WeightedEdgePool( 2 );
		final RefArrayList< WeightedEdge > list = new RefArrayList<>( pool );
		final Random rng = new Random( 100 );
		for ( int i = 0; i < 3; ++i )
		{
			final WeightedEdge ref = pool.create().set( i, i + 1, rng.nextDouble() );
			list.add( ref );
			System.out.println( "Adding " + ref );
//			pool.releaseRef( ref );
		}

		for ( int i = 0; i < list.size(); ++i )
			System.out.println( "Has " + list.get( i ) );

	}
}

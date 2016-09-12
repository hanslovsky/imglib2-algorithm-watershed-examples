package de.hanslovsky.examples;

import java.util.Random;

import ij.ImageJ;
import net.imglib2.algorithm.morphology.watershed.DistanceDeprecated;
import net.imglib2.algorithm.morphology.watershed.WatershedBigList;
import net.imglib2.algorithm.morphology.watershed.WatershedBigList.LabelSeedLocationMapFactory;
import net.imglib2.algorithm.morphology.watershed.WatershedBigList.MarkersFactory;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.converter.RealFloatConverter;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.img.array.ArrayCursor;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.array.ArrayRandomAccess;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;

public class WatershedsExampleBigList
{
	public static void main( final String[] args ) throws InterruptedException
	{

		final ArrayImg< DoubleType, DoubleArray > img = ArrayImgs.doubles( 3, 3 );
		final Random rng = new Random( 100 );

		for ( final ArrayCursor< DoubleType > c = img.cursor(); c.hasNext(); )
		{
			c.fwd();
			c.get().set( 0. * ( rng.nextDouble() - 0.5 ) + ( c.getLongPosition( 0 ) < c.getLongPosition( 1 ) ? 2.0 : 1.0 ) );
		}

		final ArrayImg< LongType, LongArray > seeds = ArrayImgs.longs( img.dimension( 0 ), img.dimension( 1 ) );
		final ArrayRandomAccess< LongType > seedsAccess = seeds.randomAccess();


		seedsAccess.setPosition( new long[] { 1, 0 } );
		seedsAccess.get().set( 1 );
		seedsAccess.setPosition( new long[] { 0, 1 } );
		seedsAccess.get().set( 2 );

//		for ( int i = 0; i < 10; ++i )
//		{
//			seedsAccess.setPosition( new long[] { i, i } );
//			seedsAccess.get().set( -1 );
//		}

		new ImageJ();
//		Thread.sleep( 30000 );
		ImageJFunctions.show( img, "img" );
		ImageJFunctions.show( new ConvertedRandomAccessibleInterval<>( seeds, new RealFloatConverter<>(), new FloatType() ) );

		final DistanceDeprecated< DoubleType > distance = ( neighborVal, currentVal, neighborPosition, seedPosition ) -> {
//			System.out.println( neighborVal.get() - currentVal.get() );
			return Math.abs( neighborVal.get() - currentVal.get() );
		};

		final MarkersFactory< LongType > factory = ( u, initialCapacity ) -> new WatershedBigList.IntegerTypeMarkers<>( u, initialCapacity );
		final LabelSeedLocationMapFactory< LongType > factory2 = () -> new WatershedBigList.LabelSeedlocationMapIntegerType<>();

//				Watershed.flood( img, seeds, new DiamondShape( 1 ), distance, new LongType( 0 ), new LongType( -1 ) );
//				Watershed.watershedsOneQueuePerSeed( img, seeds, new DiamondShape( 1 ), distance, new LongType( 0 ), new LongType( -1 ) );
//		System.out.println( "Starting watersheds" );
		WatershedBigList.watersheds( img, seeds, factory, factory2, new DiamondShape( 1 ), distance, new LongType( 0 ), new LongType( -1 ) );

		ImageJFunctions.show( new ConvertedRandomAccessibleInterval<>( seeds, new RealFloatConverter<>(), new FloatType() ) );
	}
}

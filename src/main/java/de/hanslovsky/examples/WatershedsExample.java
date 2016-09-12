package de.hanslovsky.examples;

import ij.ImageJ;
import ij.ImagePlus;
import ij.plugin.FolderOpener;
import ij.process.ImageConverter;
import net.imglib2.algorithm.morphology.watershed.DistanceDeprecated;
import net.imglib2.algorithm.morphology.watershed.Watershed;
import net.imglib2.algorithm.morphology.watershed.Watershed.MarkersFactory;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.converter.RealFloatConverter;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.array.ArrayRandomAccess;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.FloatType;

public class WatershedsExample
{
	public static void main( final String[] args ) throws InterruptedException
	{
		final long w = 10;
		final String dir = "/data/hanslovskyp/davi_toy_set/substacks/scale=0.3/data";
		final ImagePlus imp = new FolderOpener().openFolder( dir );
		new ImageConverter( imp ).convertToGray32();

		final Img< FloatType > img = ImageJFunctions.wrapFloat( imp );


		final ArrayImg< LongType, LongArray > seeds = ArrayImgs.longs( img.dimension( 0 ), img.dimension( 1 ), img.dimension( 2 ) );
		final ArrayRandomAccess< LongType > seedsAccess = seeds.randomAccess();

		long seed = 1;

		for ( int z = 0; z < img.dimension( 2 ); z += 10 )
		{
			for ( int y = 0; y < img.dimension( 1 ); y += 10 )
			{
				for ( int x = 0; x < img.dimension( 0 ); x += 10 )
				{
					seedsAccess.setPosition( new long[] { x, y, z } );
					seedsAccess.get().set( ++seed );
				}
			}
		}

//		final ArrayRandomAccess< LongType > seedsAccess = seeds.randomAccess();
//		seedsAccess.setPosition( new long[] { 1, 0 } );
//		seedsAccess.get().set( 1 );
//		seedsAccess.setPosition( new long[] { 0, 1 } );
//		seedsAccess.get().set( 2 );

//		for ( int i = 0; i < 10; ++i )
//		{
//			seedsAccess.setPosition( new long[] { i, i } );
//			seedsAccess.get().set( -1 );
//		}

		new ImageJ();
//		Thread.sleep( 30000 );
		ImageJFunctions.show( img, "img" );
		ImageJFunctions.show( new ConvertedRandomAccessibleInterval<>( seeds, new RealFloatConverter<>(), new FloatType() ) );

		final DistanceDeprecated< FloatType > distance = ( neighborVal, currentVal, neighborPosition, seedPosition ) -> {
//			System.out.println( neighborVal.get() - currentVal.get() );
			return Math.abs( neighborVal.get() - currentVal.get() );
		};

		final MarkersFactory< LongType > factory = ( u, initialCapacity ) -> new Watershed.IntegerTypeMarkers<>( u, initialCapacity );

//				Watershed.flood( img, seeds, new DiamondShape( 1 ), distance, new LongType( 0 ), new LongType( -1 ) );
//				Watershed.watershedsOneQueuePerSeed( img, seeds, new DiamondShape( 1 ), distance, new LongType( 0 ), new LongType( -1 ) );
		System.out.println( "Starting watersheds" );
		Watershed.watersheds( img, seeds, factory, new DiamondShape( 1 ), distance, new LongType( 0 ), new LongType( -1 ) );

		ImageJFunctions.show( new ConvertedRandomAccessibleInterval<>( seeds, new RealFloatConverter<>(), new FloatType() ) );
	}
}

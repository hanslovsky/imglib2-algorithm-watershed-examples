package de.hanslovsky.examples;

import java.util.ArrayList;
import java.util.Random;

import ij.ImageJ;
import net.imglib2.Cursor;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.algorithm.labeling.Watershed;
import net.imglib2.algorithm.morphology.watershed.DistanceDeprecated;
import net.imglib2.algorithm.morphology.watershed.WatershedInterval;
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
import net.imglib2.labeling.LabelingType;
import net.imglib2.labeling.NativeImgLabeling;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;

public class WatershedsExampleKamentsky
{
	@SuppressWarnings( "deprecation" )
	public static void main( final String[] args ) throws InterruptedException
	{

		final long width = 900;
		final long height = 600;
		final ArrayImg< DoubleType, DoubleArray > img = ArrayImgs.doubles( width, height );
		final Random rng = new Random( 100 );

		final boolean useKamentsky = false;

		for ( final ArrayCursor< DoubleType > c = img.cursor(); c.hasNext(); )
		{
			final double rnd = rng.nextDouble() * 1e-8;
			c.fwd();
			if ( useKamentsky )
			{

				c.get().set( c.getLongPosition( 0 ) == c.getLongPosition( 1 ) ? 1.0 : rnd );
			}
			else
			{
				c.get().set( c.getLongPosition( 0 ) == c.getLongPosition( 1 ) ? 1.0 : rnd );
//				c.get().set( 0. * ( rng.nextDouble() - 0.5 ) + ( c.getLongPosition( 0 ) < c.getLongPosition( 1 ) ? 2.0 : 1.0 ) );
			}
		}

		final ArrayImg< LongType, LongArray > seeds = ArrayImgs.longs( img.dimension( 0 ), img.dimension( 1 ) );
		final ArrayRandomAccess< LongType > seedsAccess = seeds.randomAccess();

		final ArrayImg< DoubleType, DoubleArray > weights = ArrayImgs.doubles( width, height );
		for ( final DoubleType w : weights )
		{
			w.set( Double.NaN );
		}

		final ArrayList< Localizable > seedsList = new ArrayList<>();

		seedsAccess.setPosition( new long[] { 1, 0 } );
		seedsList.add( new Point( seedsAccess ) );
		seedsAccess.get().set( 1 );
		seedsAccess.setPosition( new long[] { 0, 1 } );
		seedsList.add( new Point( seedsAccess ) );
		seedsAccess.get().set( 2 );

//		for ( int i = 0; i < 10; ++i )
//		{
//			seedsAccess.setPosition( new long[] { i, i } );
//			seedsAccess.get().set( -1 );
//		}

		new ImageJ();
//		Thread.sleep( 30000 );
		ImageJFunctions.show( img, "img" );
		ImageJFunctions.show( new ConvertedRandomAccessibleInterval<>( seeds, new RealFloatConverter<>(), new FloatType() ), "seeds1" );

		final DistanceDeprecated< DoubleType > distance = ( neighborVal, currentVal, neighborPosition, seedPosition ) -> {
//			System.out.println( neighborVal.get() - currentVal.get() );
			return neighborVal.get();
//			return Math.abs( neighborVal.get() - currentVal.get() );
		};

//		Watershed2.flood( img, seeds, weights, new DiamondShape( 1 ), distance, new LongType( 0 ), new LongType( -1 ) );
		final Watershed< DoubleType, Long > ws = new Watershed<>();
		final NativeImgLabeling< Long, LongType > seedsLabeling = new NativeImgLabeling<>( seeds );

//		final RandomAccess< LabelingType< LongType > > abc = new NativeImgLabeling< LongType, LongType >( seeds ).randomAccess();

		final int i = 0;
		final ArrayCursor< LongType > s = seeds.cursor();
		for ( final Cursor< LabelingType< Long > > c = seedsLabeling.cursor(); c.hasNext(); )
		{
			c.fwd();
			final long sn = s.next().get();
			if ( sn == 0 )
			{
				continue;
			}
			else
			{
				c.get().setLabel( sn );
				System.out.println( new Point( c ) + " " + s.get() + " " + c.get().getLabeling().get( 0 ) );
			}
//			System.out.println( i++ );
//
//			for ( final LongType l : c.next().getLabeling() )
//			{
//				System.out.println( l );
//			}
		}
		ImageJFunctions.show( new ConvertedRandomAccessibleInterval<>( seeds, new RealFloatConverter<>(), new FloatType() ), "seeds2" );
		ws.setSeeds( seedsLabeling );
		final ArrayImg< LongType, LongArray > labelingImg = ArrayImgs.longs( width, height );
		final NativeImgLabeling< Long, LongType > labeling = new NativeImgLabeling<>( labelingImg );
		ws.setOutputLabeling( labeling );
		ws.setIntensityImage( img );
//		ws.setStructuringElement( null );
		ws.setStructuringElement( new long[][] {
			{ 0, 1 },
			{ 0, -1 },
			{ 1, 0 },
			{ -1, 0 }
		} );



		ImageJFunctions.show( new ConvertedRandomAccessibleInterval<>( labelingImg, new RealFloatConverter<>(), new FloatType() ) );

		final long t0 = System.currentTimeMillis();
//		Watershed.flood( img, seeds, new DiamondShape( 1 ), distance, new LongType( 0 ), new LongType( -1 ) );
		final boolean success;

		if ( useKamentsky )
		{
			success = ws.process();
		}
		else
		{
			success = true;
			WatershedInterval.flood( img, seeds, new DiamondShape( 1 ), distance, new LongType( 0l ), new LongType( -1l ) );
		}
		final long t1 = System.currentTimeMillis();
		System.out.println( success );
		System.out.println( "runtime: " + ( t1 - t0 ) );
		System.out.println( ws.getErrorMessage() );

//		final ArrayCursor< LongType > lc = labelingImg.cursor();
//		for ( final LabelingType< Long > l : labeling )
//		{
//			lc.next().set( l.getLabeling().get( 0 ) );
//		}

		if ( useKamentsky )
		{
			ImageJFunctions.show( new ConvertedRandomAccessibleInterval<>( labelingImg, new RealFloatConverter<>(), new FloatType() ), "labelingImg" );
		}
		else
		{
			ImageJFunctions.show( new ConvertedRandomAccessibleInterval<>( seeds, new RealFloatConverter<>(), new FloatType() ), "labelingImg" );
		}
//		ImageJFunctions.show( new ConvertedRandomAccessibleInterval<>( weights, new RealFloatConverter<>(), new FloatType() ), "weights" );
	}
}

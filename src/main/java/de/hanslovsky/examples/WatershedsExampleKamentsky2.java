package de.hanslovsky.examples;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import ij.ImageJ;
import ij.ImagePlus;
import ij.process.ImageConverter;
import net.imglib2.Cursor;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.algorithm.gradient.PartialDerivative;
import net.imglib2.algorithm.labeling.Watershed;
import net.imglib2.algorithm.morphology.watershed.DistanceDeprecated;
import net.imglib2.algorithm.morphology.watershed.WatershedInterval;
import net.imglib2.algorithm.morphology.watershed.WatershedIntervalWithLists;
import net.imglib2.algorithm.morphology.watershed.WatershedIntervalWithLists.MarkersFactory;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayCursor;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.array.ArrayRandomAccess;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.labeling.LabelingType;
import net.imglib2.labeling.NativeImgLabeling;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

public class WatershedsExampleKamentsky2
{
	@SuppressWarnings( "deprecation" )
	public static void main( final String[] args ) throws InterruptedException, IncompatibleTypeException
	{
		final boolean useKamentsky = false;

		final String url = "http://img.autobytel.com/car-reviews/autobytel/11694-good-looking-sports-cars/2016-Ford-Mustang-GT-burnout-red-tire-smoke.jpg";
//		final String url = "http://mediad.publicbroadcasting.net/p/wuwm/files/styles/medium/public/201402/LeAnn_Crowe.jpg";
		final ImagePlus imp = new ImagePlus( url );
		new ImageConverter( imp ).convertToRGB();
		final ImagePlus imp2 = imp.duplicate();
		new ImageConverter( imp2 ).convertToGray32();
		imp2.show();

		final Img< FloatType > img = ImageJFunctions.wrapFloat( imp2 );
		final ArrayImg< FloatType, FloatArray > gauss = ArrayImgs.floats( img.dimension( 0 ), img.dimension( 1 ) );
		Gauss3.gauss( 3.0, Views.extendBorder( img ), gauss );
		@SuppressWarnings( "unchecked" )
		final ArrayImg< FloatType, FloatArray >[] gradients = new ArrayImg[] {
				ArrayImgs.floats( img.dimension( 0 ), img.dimension( 1 ) ),
				ArrayImgs.floats( img.dimension( 0 ), img.dimension( 1 ) ),
				ArrayImgs.floats( img.dimension( 0 ), img.dimension( 1 ) )
		};

		PartialDerivative.gradientCentralDifference( Views.extendBorder( gauss ), gradients[ 0 ], 0 );
		PartialDerivative.gradientCentralDifference( Views.extendBorder( gauss ), gradients[ 1 ], 1 );

		for ( ArrayCursor< FloatType > g1 = gradients[ 0 ].cursor(), g2 = gradients[ 1 ].cursor(), g = gradients[ 2 ].cursor(); g.hasNext(); )
		{
			g.next().setReal( Math.sqrt( Math.pow( g1.next().get(), 2 ) + Math.pow( g2.next().get(), 2 ) ) );
		}


		final long[] dataArray = new long[ ( int ) ( img.dimension( 0 ) * img.dimension( 1 ) ) ];
		final ArrayImg< LongType, LongArray > seeds = ArrayImgs.longs( dataArray, img.dimension( 0 ), img.dimension( 1 ) );

		final long seed = 1;
		final long stride = 10;
		final ArrayList< Localizable > seedsList = new ArrayList<>();
		seedsGrid( seeds, stride, seed, seedsList );

		final Random rng = new Random( 100 );

		final ArrayRandomAccess< LongType > seedsAccess = seeds.randomAccess();

		final int width = ( int ) img.dimension( 0 );
		final int height = ( int ) img.dimension( 1 );

		new ImageJ();
//		ImageJFunctions.show( img, "img" );
//		ImageJFunctions.show( new ConvertedRandomAccessibleInterval<>( seeds, new RealFloatConverter<>(), new FloatType() ), "seeds1" );

		final DistanceDeprecated< FloatType > distance = ( neighborVal, currentVal, neighborPosition, seedPosition ) -> {
			return neighborVal.get();
//			double euclideanDistance = 0.0;
//			for ( int d = 0; d < 2; ++d )
//			{
//				final double diff = neighborPosition.getDoublePosition( d ) - seedPosition.getDoublePosition( d );
//				euclideanDistance += diff * diff;
//			}
//			final double v = neighborVal.get();
//			return v + 0.1 * euclideanDistance;
//			return Math.abs( neighborVal.get() - currentVal.get() );
		};

		final Watershed< FloatType, Long > ws = new Watershed<>();
		final NativeImgLabeling< Long, LongType > seedsLabeling = new NativeImgLabeling<>( seeds );

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
//				System.out.println( new Point( c ) + " " + s.get() + " " + c.get().getLabeling().get( 0 ) );
			}
		}
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



//		ImageJFunctions.show( new ConvertedRandomAccessibleInterval<>( labelingImg, new RealFloatConverter<>(), new FloatType() ) );

//		Watershed.flood( img, seeds, new DiamondShape( 1 ), distance, new LongType( 0 ), new LongType( -1 ) );

		final MarkersFactory< LongType > mf = ( u, initialCapacity ) -> new WatershedIntervalWithLists.IntegerTypeMarkers< LongType >( u, initialCapacity );

		long rtAccu = 0;
		final int N = 20;

		final Runtime rt = Runtime.getRuntime();
		final long currentMemoryUseBefore = rt.totalMemory() - rt.freeMemory();

		for ( int count = 0; count < N; ++count )
		{
			final ArrayImg< LongType, LongArray > seedsLocal = ArrayImgs.longs( width, height );
			for ( ArrayCursor< LongType > sc = seeds.cursor(), t = seedsLocal.cursor(); sc.hasNext(); )
			{
				t.next().set( sc.next() );
			}
			final boolean success;
			final long t0 = System.currentTimeMillis();
			if ( useKamentsky )
			{
				success = ws.process();
			}
			else
			{
				success = true;
				WatershedInterval.flood( img, seedsLocal, seedsList, new DiamondShape( 1 ), distance, new LongType( 0l ), new LongType( -1l ) );
//			WatershedIntervalWithLists.flood( img, seeds, seedsList, mf, new DiamondShape( 1 ), distance, new LongType( 0l ), new LongType( -1l ) );
			}
			final long t1 = System.currentTimeMillis();

			final long currentMemoryUseAfter = rt.totalMemory() - rt.freeMemory();
			System.out.println( success );
			final long time = t1 - t0;
			rtAccu += time;
			System.out.println( "runtime: " + time );
			System.out.println( "Memory use (bytes): " + ( currentMemoryUseAfter - currentMemoryUseBefore ) );
		}
		System.out.println( rtAccu * 1.0 / N );
		WatershedInterval.flood( img, seeds, seedsList, new DiamondShape( 1 ), distance, new LongType( 0l ), new LongType( -1l ) );
//		System.out.println( ws.getErrorMessage() );

//		final ArrayCursor< LongType > lc = labelingImg.cursor();
//		for ( final LabelingType< Long > l : labeling )
//		{
//			lc.next().set( l.getLabeling().get( 0 ) );
//		}

//		if ( useKamentsky )
//		{
//			ImageJFunctions.show( new ConvertedRandomAccessibleInterval<>( labelingImg, new RealFloatConverter<>(), new FloatType() ), "labelingImg" );
//		}
//		else
//		{
//			ImageJFunctions.show( new ConvertedRandomAccessibleInterval<>( seeds, new RealFloatConverter<>(), new FloatType() ), "labelingImg" );
//		}
//		ImageJFunctions.show( new ConvertedRandomAccessibleInterval<>( weights, new RealFloatConverter<>(), new FloatType() ), "weights" );

		final ArrayImg< LongType, ? > seedGradient = makeSeedsGradient( useKamentsky ? labelingImg : seeds, new LongType() );
		ImageJFunctions.show( seedGradient, "seedGradient" );
	}

	public static < T extends IntegerType< T > > void seedsGrid( final RandomAccessibleInterval< T > seeds, final long stride, final long startSeed, final List< Localizable > seedList )
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
	}

	public static < T extends IntegerType< T > & NativeType< T > > ArrayImg< T, ? > makeSeedsGradient( final RandomAccessibleInterval< T > img, final T t )
	{
		final ArrayImgFactory< T > fac = new ArrayImgFactory<>();

		@SuppressWarnings( "unchecked" )
		final ArrayImg< T, ? >[] seedGradients = new ArrayImg[] {
				fac.create( new long[] { img.dimension( 0 ), img.dimension( 1 ) }, t ),
				fac.create( new long[] { img.dimension( 0 ), img.dimension( 1 ) }, t ),
				fac.create( new long[] { img.dimension( 0 ), img.dimension( 1 ) }, t )
		};

		PartialDerivative.gradientCentralDifference( Views.extendBorder( img ), seedGradients[ 0 ], 0 );
		PartialDerivative.gradientCentralDifference( Views.extendBorder( img ), seedGradients[ 1 ], 1 );
		for ( ArrayCursor< T > g0 = seedGradients[ 0 ].cursor(), g1 = seedGradients[ 1 ].cursor(), g = seedGradients[ 2 ].cursor(); g.hasNext(); )
		{
			final long g0Val = g0.next().getIntegerLong();
			final long g1Val = g1.next().getIntegerLong();
			g.next().setInteger( Math.sqrt( g0Val * g0Val + g1Val + g1Val ) > 0 ? 255 : 0 );
		}
		return seedGradients[ 2 ];
//		ImageJFunctions.show( seedGradients[ 2 ] );
	}
}

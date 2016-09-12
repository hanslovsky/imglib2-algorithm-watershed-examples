package de.hanslovsky.examples;

import ij.ImageJ;
import ij.ImagePlus;
import ij.process.ImageConverter;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.algorithm.gradient.PartialDerivative;
import net.imglib2.algorithm.morphology.watershed.DistanceDeprecated;
import net.imglib2.algorithm.morphology.watershed.Watershed;
import net.imglib2.algorithm.morphology.watershed.Watershed.MarkersFactory;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.RectangleShape;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayCursor;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;

public class WatershedsExample5
{
	public static void main( final String[] args ) throws IncompatibleTypeException
	{
		new ImageJ();
//		final String url = "http://img.autobytel.com/car-reviews/autobytel/11694-good-looking-sports-cars/2016-Ford-Mustang-GT-burnout-red-tire-smoke.jpg";
//		final String url = "http://mediad.publicbroadcasting.net/p/wuwm/files/styles/medium/public/201402/LeAnn_Crowe.jpg";
//		final String url = "http://emdata.janelia.org/api/node/bf1/grayscale/raw/xy/438_321/2900_3450_2000";
		final String url = "/home/hanslovskyp/workspace/imglib2-watersheds/vis/lena.bmp";
		final ImagePlus imp = new ImagePlus( url );
		new ImageConverter( imp ).convertToGray32();

		final Img< FloatType > img = ImageJFunctions.wrapFloat( imp );
		ImageJFunctions.show( img );
		final ArrayImg< FloatType, FloatArray > gauss = ArrayImgs.floats( img.dimension( 0 ), img.dimension( 1 ) );
		Gauss3.gauss( 3, Views.extendBorder( img ), gauss );
		@SuppressWarnings( "unchecked" )
		final ArrayImg< FloatType, FloatArray >[] gradients = new ArrayImg[] {
				ArrayImgs.floats( img.dimension( 0 ), img.dimension( 1 ) ),
				ArrayImgs.floats( img.dimension( 0 ), img.dimension( 1 ) ),
				ArrayImgs.floats( img.dimension( 0 ), img.dimension( 1 ) )
		};
		final long[] dataArray = new long[ ( int ) ( img.dimension( 0 ) * img.dimension( 1 ) ) ];
		ArrayImg< LongType, LongArray > seeds = ArrayImgs.longs( dataArray, img.dimension( 0 ), img.dimension( 1 ) );

		PartialDerivative.gradientCentralDifference( Views.extendBorder( gauss ), gradients[ 0 ], 0 );
		PartialDerivative.gradientCentralDifference( Views.extendBorder( gauss ), gradients[ 1 ], 1 );

		for ( ArrayCursor< FloatType > g1 = gradients[ 0 ].cursor(), g2 = gradients[ 1 ].cursor(), g = gradients[ 2 ].cursor(); g.hasNext(); )
		{
			g.next().setReal( Math.sqrt( Math.pow( g1.next().get(), 2 ) + Math.pow( g2.next().get(), 2 ) ) );
		}

		final RectangleShape shape = new RectangleShape( 10, true );

		final RandomAccessible< Pair< FloatType, Neighborhood< FloatType > > > gWithNeighborhood = Views.pair(
				Views.extendValue( gradients[ 2 ], new FloatType( Float.NaN ) ),
				shape.neighborhoodsRandomAccessible( Views.extendValue( gradients[ 2 ], new FloatType( Float.NaN ) ) ) );

		final long seed = 1;
		final long stride = 10;
		seedsGrid( seeds, stride, seed );
//		seedsGradientMinima( seeds, gWithNeighborhood, seed );

		ImageJFunctions.show( gauss );
		ImageJFunctions.show( gradients[ 2 ], "g" );
		ImageJFunctions.show( seeds );

		final double lambda = 2e-1;

		final DistanceDeprecated< FloatType > distance =
				( neighborVal, currentVal, neighborPosition, seedPosition ) -> {
					double dist = 0.0;
					for ( int i = 0; i < seedPosition.numDimensions(); ++i )
					{
						final double d = seedPosition.getDoublePosition( i ) - neighborPosition.getDoublePosition( i );
						dist += d * d;
					}
					final double d = neighborVal.get() - currentVal.get();
					final double result = d * d + lambda * dist;
//					System.out.println( result + " " + rgbaC + " " + rgbaN );
					return result;
				};
				final MarkersFactory< LongType > factory = ( u, initialCapacity ) -> new Watershed.IntegerTypeMarkers<>( u, initialCapacity );
		for ( int i = 0; i < 1; ++i )
				{
					seeds = ArrayImgs.longs( dataArray.clone(), img.dimension( 0 ), img.dimension( 1 ) );
					final long t0 = System.currentTimeMillis();
//			Watershed.flood( gauss, seeds, new DiamondShape( 1 ), distance, new LongType( 0 ), new LongType( -1 ) );
					Watershed.watershedsOneQueuePerSeed( gauss, seeds, new DiamondShape( 1 ), distance, new LongType( 0 ), new LongType( -1 ) );
//					Watershed.watersheds( gauss, seeds, factory, new DiamondShape( 1 ), distance, new LongType( 0 ), new LongType( -1 ) );
					final long t1 = System.currentTimeMillis();
					System.out.println( "runtime " + ( t1 - t0 ) + "ms" );
				}

				ImageJFunctions.show( seeds );

				@SuppressWarnings( "unchecked" )
				final ArrayImg< LongType, LongArray >[] seedGradients = new ArrayImg[] {
						ArrayImgs.longs( img.dimension( 0 ), img.dimension( 1 ) ),
						ArrayImgs.longs( img.dimension( 0 ), img.dimension( 1 ) ),
						ArrayImgs.longs( img.dimension( 0 ), img.dimension( 1 ) )
				};

				PartialDerivative.gradientCentralDifference( Views.extendBorder( seeds ), seedGradients[ 0 ], 0 );
				PartialDerivative.gradientCentralDifference( Views.extendBorder( seeds ), seedGradients[ 1 ], 1 );
				for ( ArrayCursor< LongType > g0 = seedGradients[ 0 ].cursor(), g1 = seedGradients[ 1 ].cursor(), g = seedGradients[ 2 ].cursor(); g.hasNext(); )
				{
					final long g0Val = g0.next().get();
					final long g1Val = g1.next().get();
					g.next().set( Math.sqrt( g0Val * g0Val + g1Val + g1Val ) > 0 ? 255 : 0 );
				}
				ImageJFunctions.show( seedGradients[ 2 ] );

	}

	public static < T extends IntegerType< T > > void seedsGrid( final RandomAccessibleInterval< T > seeds, final long stride, final long startSeed )
	{
		long seed = startSeed;

		final long start = stride / 2;

		final RandomAccess< T > seedsAccess = seeds.randomAccess();

		for ( long y = start; y < seeds.dimension( 1 ); y += stride )
		{
			seedsAccess.setPosition( new long[] { start, y } );
			for ( long x = start; x < seeds.dimension( 0 ); x += stride, seedsAccess.move( stride, 0 ) )
			{
				seedsAccess.get().setInteger( seed++ );
			}
		}
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
			{
//				seedsAccess.get().set( -1 );
				continue;
			}
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
}

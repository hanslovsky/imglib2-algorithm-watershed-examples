package de.hanslovsky.examples;

import java.util.List;
import java.util.concurrent.ExecutionException;

import gnu.trove.list.array.TLongArrayList;
import ij.ImageJ;
import ij.ImagePlus;
import net.imglib2.Cursor;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.algorithm.gradient.PartialDerivative;
import net.imglib2.algorithm.morphology.watershed.Watershed;
import net.imglib2.algorithm.morphology.watershed.Watershed.Compare;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.RectangleShape;
import net.imglib2.algorithm.neighborhood.RectangleShape.NeighborhoodsAccessible;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.array.ArrayCursor;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;

public class WatershedsExample2DParallelPaper
{

	public static double euclidianSquared( final long pos1, final long pos2, final long[] dim )
	{
		double dist = 0;
		for ( int d = 0; d < dim.length; ++d )
		{
			final double diff = IntervalIndexer.indexToPosition( pos1, dim, d ) - IntervalIndexer.indexToPosition( pos2, dim, d );
			dist += diff * diff;
		}
		return dist;
	}

	public static double square( final double val )
	{
		return val * val;
	}

	public static void main( final String[] args ) throws IncompatibleTypeException, InterruptedException, ExecutionException
	{

		new ImageJ();
//		final String url = "https://cdn1.partner.hp.com/hpi-cpp-default-theme/images/common/Icon_Refresh.png";
		final String url = "http://img.autobytel.com/car-reviews/autobytel/11694-good-looking-sports-cars/2016-Ford-Mustang-GT-burnout-red-tire-smoke.jpg";
//		final String url = "http://mediad.publicbroadcasting.net/p/wuwm/files/styles/medium/public/201402/LeAnn_Crowe.jpg";
//		final String url = "http://www.mathworks.com/cmsimages/65309_wl_watershed_fig6_wl.gif";
		final ImagePlus imp = new ImagePlus( url );

		final ArrayImg< FloatType, FloatArray > source = ArrayImgs.floats( ( float[] ) imp.getProcessor().convertToFloatProcessor().getPixels(), imp.getWidth(), imp.getHeight() );
		final ArrayImg< FloatType, FloatArray > img = ArrayImgs.floats( source.dimension( 0 ), source.dimension( 1 ) );

//		Gauss3.gauss( 3.0, source, img );

		final ArrayImg< DoubleType, DoubleArray >[] gradients = gradientsAndMagnitude( source, 8.5 );

		ImageJFunctions.show( gradients[ 2 ], "grad mag" );

		final long[] markers = new long[ ( int ) source.size() ];

		final ArrayImg< LongType, LongArray > markersWrapped = ArrayImgs.longs( markers, imp.getWidth(), imp.getHeight() );

		final DiamondShape shape = new DiamondShape( 1 );
//		final RectangleShape shape = new RectangleShape( 1, false );

		final Compare< DoubleType > comp = ( first, second ) -> first.get() < second.get();

		// 2D image (900x600), no speed improvent after 8 threads, big
		// improvement until then.
		final int nThreads = 12;// Runtime.getRuntime().availableProcessors();

		final int N = 20;
		final int n = 5;
		long acc = 0;
		for ( int i = 0; i < N; ++i )
		{
			final long[] markersCl = markers.clone();
			final ArrayImg< LongType, LongArray > markersWr = ArrayImgs.longs( markersCl, imp.getWidth(), imp.getHeight() );
			final long t0 = System.currentTimeMillis();
			Watershed.findBasinsAndFill( gradients[ 2 ], markersWr, shape, new LongType( -1 ), new LongType( -2 ), comp, nThreads, () -> {} );
			final long t1 = System.currentTimeMillis();
			final long diff = t1 - t0;
			System.out.println( "Runtime: " + diff );
			if ( i > n )
				acc += diff;
		}

		if ( N > n )
			System.out.println( "avg rt " + acc * 1.0 / ( N - n - 1 ) );

		final TLongArrayList sp = Watershed.findBasinsAndFill( gradients[ 2 ], markersWrapped, shape, new LongType( -1 ), new LongType( -2 ), comp, nThreads, () -> {} );


		ImageJFunctions.show( ArrayImgs.longs( markers, imp.getWidth(), imp.getHeight() ) );
		final LongType bg = markersWrapped.firstElement().createVariable();
		bg.set( -1l );
		final ArrayImg< LongType, ? > seedsGradient =
				makeSeedsGradient( ArrayImgs.longs( markers, imp.getWidth(), imp.getHeight() ), bg );
		ImageJFunctions.show( seedsGradient, "labels" );
		overlay( imp, seedsGradient, bg );

		imp.show();

//		for ( int i = 0; i < sp.size(); ++i )
//			System.out.println( sp.get( i ) );

//		System.out.println( "\n" + sp.size() );
//
//		final ArrayImg< LongType, LongArray > spImage = ArrayImgs.longs( img.dimension( 0 ), img.dimension( 1 ) );
//		final RandomAccess< LongType > access = FlatViews.flatten( spImage ).randomAccess();
//		for ( int i = 0; i < sp.size(); ++i )
//		{
//			access.setPosition( sp.get( i ), 0 );
//			access.get().set( i );
//		}
//
//		ImageJFunctions.show( spImage, "spImage" );

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
				seedsAccess.get().setInteger( seed++ );
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
				//				seedsAccess.get().set( -1 );
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

	public static < T extends IntegerType< T > > void seedsGrid( final RandomAccessibleInterval< T > seeds, final long stride, final int startSeed, final List< Localizable > seedList )
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

//		@SuppressWarnings( "unchecked" )
//		final ArrayImg< T, ? >[] seedGradients = new ArrayImg[] {
//				fac.create( new long[] { img.dimension( 0 ), img.dimension( 1 ) }, backGround ),
//				fac.create( new long[] { img.dimension( 0 ), img.dimension( 1 ) }, backGround ),
//				fac.create( new long[] { img.dimension( 0 ), img.dimension( 1 ) }, backGround )
//		};
//
//		PartialDerivative.gradientCentralDifference( Views.extendBorder( img ), seedGradients[ 0 ], 0 );
//		PartialDerivative.gradientCentralDifference( Views.extendBorder( img ), seedGradients[ 1 ], 1 );
//		for ( ArrayCursor< T > g0 = seedGradients[ 0 ].cursor(), g1 = seedGradients[ 1 ].cursor(), g = seedGradients[ 2 ].cursor(); g.hasNext(); )
//		{
//			final long g0Val = g0.next().getIntegerLong();
//			final long g1Val = g1.next().getIntegerLong();
//			g.next().setInteger( Math.sqrt( g0Val * g0Val + g1Val + g1Val ) > 0 ? 255 : 0 );
//		}
//		return seedGradients[ 2 ];
//		ImageJFunctions.show( seedGradients[ 2 ] );
	}

	public static < T extends IntegerType< T > > void overlay( final ImagePlus imp, final RandomAccessibleInterval< T > watersheds, final T backGround )
	{
		final int[] px = ( int[] ) imp.getProcessor().getPixels();
		final Cursor< T > w = Views.iterable( watersheds ).cursor();
		for ( int i = 0; i < px.length; ++i )
			if ( !w.next().valueEquals( backGround ) )
				px[ i ] = 255 << 16;
	}

}

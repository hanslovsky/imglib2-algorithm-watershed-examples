package de.hanslovsky.examples;

import java.util.List;
import java.util.concurrent.ExecutionException;

import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.ui.view.Viewer;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

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
import net.imglib2.algorithm.morphology.watershed.Watershed.Visitor;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.RectangleShape;
import net.imglib2.algorithm.neighborhood.RectangleShape.NeighborhoodsAccessible;
import net.imglib2.converter.Converter;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.array.ArrayCursor;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;

public class WatershedsExample2DParallel
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
		final int w = 10;
		final int h = 12;

		final int[] imgArray = new int[] {
				3, 5, 5, 2, 8, 8, 8, 11, 10, 10,
				5, 5, 11, 11, 8, 11, 11, 8, 10, 10,
				11, 5, 11, 11, 9, 9, 9, 9, 8, 10,
				11, 11, 11, 7, 7, 7, 7, 9, 9, 8,
				11, 11, 11, 11, 11, 9, 7, 10, 8, 10,
				11, 10, 11, 9, 7, 7, 9, 9, 10, 8,
				11, 10, 11, 9, 11, 9, 10, 10, 8, 10,
				11, 11, 11, 8, 8, 8, 8, 8, 10, 10,
				11, 11, 11, 11, 10, 10, 10, 10, 10, 10,
				10, 10, 10, 10, 10, 10, 10, 10, 10, 10,
				11, 11, 11, 11, 10, 10, 10, 10, 10, 10,
				10, 10, 10, 10, 10, 10, 10, 10, 10, 10
		};

		final ArrayImg< IntType, IntArray > img = ArrayImgs.ints( imgArray, w, h );

		ImageJFunctions.show( img, "img" );

		final long[] markers = new long[ imgArray.length ];

		final ArrayImg< LongType, LongArray > markersWrapped = ArrayImgs.longs( markers, w, h );

//		final DiamondShape shape = new DiamondShape( 1 );
		final RectangleShape shape = new RectangleShape( 1, false );

		final Compare< IntType > comp = ( first, second ) -> first.get() < second.get();


		final Converter< LongType, LongType > conv = ( input, output ) -> {
			final long i = input.getIntegerLong();
			output.set( i < 0 ? 255 + i : i );
		};

		final DefaultDirectedGraph< Long, DefaultEdge > g = new DefaultDirectedGraph<>( DefaultEdge.class );
		final MultiGraph gs = new MultiGraph( "Connectivity" );

		final Viewer viewer = gs.display();
		viewer.disableAutoLayout();
		gs.addAttribute( "ui.stylesheet", "node { fill-color: red; } node.marked {fill-color: black;} node.meh {fill-color:white;}" );

		final Visitor vis = () -> {
			ImageJFunctions.show( new ConvertedRandomAccessibleInterval<>( markersWrapped, conv, new LongType() ), "BLUB" );
			for ( int y = 0, i = 0; y < h; ++y )
				for ( int x = 0; x < w; ++x, ++i )
				{
					final Node n = gs.addNode( "" + i );
					n.setAttribute( "xy", x, -y );
					n.addAttribute( "ui.label", "" + imgArray[ i ] );
//					System.out.println( 123 );
//				g.addVertex( ( long ) i );
				}

			long i = 0;
			for ( final LongType m : markersWrapped )
			{
				if ( m.get() != i )
				{
					gs.getNode( "" + i ).setAttribute( "ui.class", "marked" );
					gs.addEdge( i + "-" + m.get(), "" + i, "" + m.get(), true );
				}
				else if ( m.get() == -2 )
					gs.getNode( "" + i ).setAttribute( "ui.class", "meh" );
				++i;
			}

		};


		final int nThreads = 12;// Runtime.getRuntime().availableProcessors();

		final TLongArrayList sp = Watershed.findBasinsAndFill( img, markersWrapped, shape, new LongType( -1 ), new LongType( -2 ), comp, nThreads, vis );

		ImageJFunctions.show( new ConvertedRandomAccessibleInterval<>( ArrayImgs.longs( markers, w, h ), conv, new LongType() ) );

//		final LongType bg = markersWrapped.firstElement().createVariable();
//		bg.set( -1l );
//		final ArrayImg< LongType, ? > seedsGradient =
//				makeSeedsGradient( ArrayImgs.longs( markers, w, h ), bg );
//		ImageJFunctions.show( seedsGradient, "labels" );

		System.out.println( "\n" + sp.size() );

//		final ArrayImg< LongType, LongArray > spImage = ArrayImgs.longs( img.dimension( 0 ), img.dimension( 1 ) );
//		for ( final LongType s : spImage )
//			s.set( 255 );
//		final RandomAccess< LongType > access = FlatViews.flatten( spImage ).randomAccess();
//		for ( int i = 0; i < sp.size(); ++i )
//		{
//			access.setPosition( sp.get( i ), 0 );
//			access.get().set( i );
////			System.out.println( sp.get( i ) );
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

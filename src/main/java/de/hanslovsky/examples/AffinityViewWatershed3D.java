package de.hanslovsky.examples;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.MultiGraph;

import gnu.trove.list.array.TLongArrayList;
import ij.ImageJ;
import net.imglib2.Cursor;
import net.imglib2.Dimensions;
import net.imglib2.Point;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.morphology.watershed.AffinityWatershed2;
import net.imglib2.algorithm.morphology.watershed.AffinityWatershed2.WeightedEdge;
import net.imglib2.algorithm.morphology.watershed.affinity.AffinityView;
import net.imglib2.converter.Converters;
import net.imglib2.img.array.ArrayCursor;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import net.imglib2.view.composite.CompositeView;
import net.imglib2.view.composite.RealComposite;

public class AffinityViewWatershed3D
{
	public static void main( final String[] args ) throws InterruptedException, ExecutionException
	{

		final long[] shape = new long[] { 5, 4 };
		final long[] shapeWithChanneles = new long[] { shape[ 0 ], shape[ 1 ], 2 };
		final long[] shapeWithEdges = new long[] { shape[ 0 ], shape[ 1 ], 4 };
		// https://arxiv.org/abs/1505.00249
		final ArrayImg< DoubleType, DoubleArray > disaffinities = ArrayImgs.doubles( shapeWithChanneles );
		final CompositeView< DoubleType, RealComposite< DoubleType > >.CompositeRandomAccess access = Views.collapseReal( disaffinities ).randomAccess();

		access.setPosition( new int[] { 0, 0 } );
		access.get().get( 0 ).set( 0.5 );
		access.get().get( 1 ).set( 0.6 );

		access.setPosition( new int[] { 1, 0 } );
		access.get().get( 0 ).set( 0.3 );
		access.get().get( 1 ).set( 0.7 );

		access.setPosition( new int[] { 2, 0 } );
		access.get().get( 0 ).set( 0.4 );
		access.get().get( 1 ).set( 0.8 );

		access.setPosition( new int[] { 3, 0 } );
		access.get().get( 0 ).set( 0.8 );
		access.get().get( 1 ).set( 0.5 );

		access.setPosition( new int[] { 4, 0 } );
		access.get().get( 0 ).set( Double.NaN );
		access.get().get( 1 ).set( 0.1 );

		access.setPosition( new int[] { 0, 1 } );
		access.get().get( 0 ).set( 0.6 );
		access.get().get( 1 ).set( 0.9 );

		access.setPosition( new int[] { 1, 1 } );
		access.get().get( 0 ).set( 0.6 );
		access.get().get( 1 ).set( 0.6 );

		access.setPosition( new int[] { 2, 1 } );
		access.get().get( 0 ).set( 0.6 );
		access.get().get( 1 ).set( 0.6 );

		access.setPosition( new int[] { 3, 1 } );
		access.get().get( 0 ).set( 0.5 );
		access.get().get( 1 ).set( 0.9 );

		access.setPosition( new int[] { 4, 1 } );
		access.get().get( 0 ).set( Double.NaN );
		access.get().get( 1 ).set( 0.4 );

		access.setPosition( new int[] { 0, 2 } );
		access.get().get( 0 ).set( 0.6 );
		access.get().get( 1 ).set( 0.6 );

		access.setPosition( new int[] { 1, 2 } );
		access.get().get( 0 ).set( 0.6 );
		access.get().get( 1 ).set( 0.7 );

		access.setPosition( new int[] { 2, 2 } );
		access.get().get( 0 ).set( 0.9 );
		access.get().get( 1 ).set( 0.7 );

		access.setPosition( new int[] { 3, 2 } );
		access.get().get( 0 ).set( 0.4 );
		access.get().get( 1 ).set( 0.4 );

		access.setPosition( new int[] { 4, 2 } );
		access.get().get( 0 ).set( Double.NaN );
		access.get().get( 1 ).set( 0.4 );

		access.setPosition( new int[] { 0, 3 } );
		access.get().get( 0 ).set( 0.6 );
		access.get().get( 1 ).set( Double.NaN );

		access.setPosition( new int[] { 1, 3 } );
		access.get().get( 0 ).set( 0.6 );
		access.get().get( 1 ).set( Double.NaN );

		access.setPosition( new int[] { 2, 3 } );
		access.get().get( 0 ).set( 0.6 );
		access.get().get( 1 ).set( Double.NaN );

		access.setPosition( new int[] { 3, 3 } );
		access.get().get( 0 ).set( 0.4 );
		access.get().get( 1 ).set( Double.NaN );

		access.setPosition( new int[] { 4, 3 } );
		access.get().get( 0 ).set( Double.NaN );
		access.get().get( 1 ).set( Double.NaN );



		final ArrayImg< LongType, LongArray > labels = ArrayImgs.longs( shape );

//		final MultiGraph initialGraph = createGraph( 20, "Disaffinities", labels );
//
//		{
//			final Cursor< RealComposite< DoubleType > > c = Views.flatIterable( Views.collapseReal( disaffinities ) ).cursor();
//			for ( int i = 0; i < 20; ++i )
//			{
//				final RealComposite< DoubleType > e = c.next();
//				if ( !Double.isNaN( e.get( 0 ).get() ) )
//				{
//					final Edge edge = initialGraph.addEdge( "" + i + "-" + ( i + 1 ), "" + i, "" + ( i + 1 ), false );
//					edge.addAttribute( "ui.label", e.get( 0 ).get() );
//				}
//
//				if ( !Double.isNaN( e.get( 1 ).get() ) )
//				{
//					final Edge edge = initialGraph.addEdge( "" + i + "-" + ( i + labels.dimension( 0 ) ), "" + i, "" + ( i + labels.dimension( 0 ) ), false );
//					edge.addAttribute( "ui.label", e.get( 1 ).get() );
//				}
//			}
//		}
//
//		initialGraph.display( false );
		final ArrayImg< BitType, LongArray > edges = ArrayImgs.bits( shapeWithEdges );

		final RealComposite< DoubleType > extension = Views.collapseReal( ArrayImgs.doubles( new double[] { Double.NaN, Double.NaN }, 1, 2 ) ).randomAccess().get();
		final AffinityView< DoubleType, RealComposite< DoubleType > > input =
				new AffinityView< DoubleType, RealComposite< DoubleType > >(
						Views.extendValue( Views.collapseReal( disaffinities ), extension ),
						( size ) -> Views.collapseReal( ArrayImgs.doubles( 1, size ) ).randomAccess().get() );

		for ( final Cursor< RealComposite< DoubleType > > c = Views.flatIterable( Views.interval( input, labels ) ).cursor(); c.hasNext(); )
		{
			c.fwd();
			System.out.println( new Point( c ) + " " + c.get().get( 0 ) + " " + c.get().get( 1 ) + " " + c.get().get( 2 ) + " " + c.get().get( 3 ) );
		}

		new ImageJ();

		final Runnable visitor = new Runnable()
		{

			final String[] names = new String[] { "parents", "corners", "no plateaus" };

			int index = 0;

			final ArrayImg< LongType, LongArray > img = ArrayImgs.longs( shape );

			@Override
			public void run()
			{
				for ( final Pair< LongType, LongType > p : Views.interval( Views.pair( labels, img ), img ) )
					p.getB().set( p.getA() );
				ImageJFunctions.show( img, names[ index ] );
				final MultiGraph g = createGraph( shape[ 0 ] * shape[ 1 ], names[ index ], img );
				final ArrayCursor< LongType > c = labels.cursor();
				for ( int i = 0; c.hasNext(); ++i )
				{
					final long l = c.next().get();
					if ( ( l & 1l << 2 ) != 0 )
						g.addEdge( i + "-" + ( i + 1 ), "" + i, "" + ( i + 1 ), true );
					if ( ( l & 1l << 3 ) != 0 )
						g.addEdge( i + "-" + ( i + labels.dimension( 0 ) ), "" + i, "" + ( i + labels.dimension( 0 ) ), true );

					if ( ( l & 1l << 1 ) != 0 )
						g.addEdge( i + "-" + ( i - 1 ), "" + i, "" + ( i - 1 ), true );

					if ( ( l & 1l << 0 ) != 0 )
						g.addEdge( i + "-" + ( i - labels.dimension( 0 ) ), "" + i, "" + ( i - labels.dimension( 0 ) ), true );
				}
				g.display( false );
				++index;
			}
		};

		final ValuePair< TLongArrayList, long[] > rootsAndCounts = AffinityWatershed2.letItRain(
				input,
				labels,
				( f, s ) -> f.getRealDouble() < s.getRealDouble(),
				new DoubleType( Double.MAX_VALUE ),
				Executors.newFixedThreadPool( 1 ),
				1,
				visitor );


		ImageJFunctions.show( Converters.convert( ( RandomAccessibleInterval< LongType > ) labels, ( s, t ) -> {
			System.out.println( Long.toBinaryString( s.get() ) );
			t.set( s.get() & ~( 1l << 63 | 1l << 62 ) );
		}, new LongType() ) );
//		ImageJFunctions.show( labels );

		System.out.println( Arrays.toString( rootsAndCounts.getB() ) );


		final long highBit = 1l << 63;
		final long secondHighBit = 1l << 62;


		final long[] steps = AffinityWatershed2.generateSteps( AffinityWatershed2.generateStride( labels ) );

		final ArrayList< WeightedEdge > rg =
				AffinityWatershed2.generateRegionGraph(
						input,
						labels,
						steps,
						( f, s ) -> f.getRealDouble() < s.getRealDouble(),
						new DoubleType( Double.MAX_VALUE ),
						highBit,
						secondHighBit,
						rootsAndCounts.getB().length );

		for ( final WeightedEdge w : rg )
			System.out.println(w);

//		final short val = ( short ) 255;
//		ImageJFunctions.show( Converters.convert( ( RandomAccessibleInterval< LongType > ) labels, ( s, t ) -> {
//			t.set( s.get() < 0 ? val : 0 );
//		}, new ShortType() ) );

//		final int[] lookUp = new int[] { 1, 0, 2, 3 };
//
//		final CompareBetter< DoubleType > compare = ( first, second ) -> first.getRealDouble() > second.getRealDouble();
//
//		final RealComposite< DoubleType > extension = Views.collapseReal( ArrayImgs.doubles( 1, 2 ) ).randomAccess().get();
//		for ( int i = 0; i < 2; ++i )
//			extension.get( i ).set( Double.NaN );
//
//		final RandomAccessibleInterval< RealComposite< DoubleType > > aff = Views.collapseReal( affinities );
//
//		final AffinityView< DoubleType, RealComposite< DoubleType > > affView = new AffinityView<>( Views.extendValue( aff, extension ), compFac );
//
//
//		AffinityWatershed.watershed( affView, labels, compare, new DoubleType( Double.MAX_VALUE ), new LongType( -1 ) );
//
//		final MultiGraph g = new MultiGraph( "Roots" );
//		for ( int i = 0; i < 20; ++i ) {
//			final Node n = g.addNode( i + "" );
//			n.addAttribute( "ui.label", "" + i );
//			n.addAttribute( "xy", IntervalIndexer.indexToPosition( i, Intervals.dimensionsAsLongArray( labels ), 0 ), -IntervalIndexer.indexToPosition( i, Intervals.dimensionsAsLongArray( labels ), 1 ) );
//		}
//
//		{
//			int i = 0;
//			for ( final AbstractArrayCursor< LongType > c = labels.cursor(); c.hasNext(); )
//			{
//				final long l = c.next().get();
//				g.addEdge( i + "-" + l, i + "", l + "", true );
//				++i;
//			}
//
//		}
//
//		final Viewer viewer = g.display( false );
	}

	public static MultiGraph createGraph( final long size, final String name, final Dimensions dims )
	{
		final MultiGraph g = new MultiGraph( name );
		for ( int i = 0; i < size; ++i )
		{
			final Node n = g.addNode( i + "" );
			n.addAttribute( "ui.label", "" + i );
			n.addAttribute( "xy", IntervalIndexer.indexToPosition( i, Intervals.dimensionsAsLongArray( dims ), 0 ), -IntervalIndexer.indexToPosition( i, Intervals.dimensionsAsLongArray( dims ), 1 ) );
		}

		return g;
	}
}

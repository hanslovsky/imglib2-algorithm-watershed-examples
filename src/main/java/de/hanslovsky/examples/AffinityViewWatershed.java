package de.hanslovsky.examples;

import java.util.Random;

import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.ui.view.Viewer;

import gnu.trove.iterator.TLongIterator;
import gnu.trove.list.array.TLongArrayList;
import net.imglib2.Cursor;
import net.imglib2.Point;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.morphology.watershed.AffinityWatershed;
import net.imglib2.algorithm.morphology.watershed.CompareBetter;
import net.imglib2.algorithm.morphology.watershed.affinity.AffinityView;
import net.imglib2.algorithm.morphology.watershed.affinity.CompositeFactory;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.img.array.AbstractArrayCursor;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import net.imglib2.view.composite.CompositeView;
import net.imglib2.view.composite.RealComposite;

public class AffinityViewWatershed
{
	public static void main( final String[] args )
	{
		// https://arxiv.org/abs/1505.00249
		final ArrayImg< DoubleType, DoubleArray > disaffinities = ArrayImgs.doubles( 3, 2, 2, 3 );
		final CompositeView< DoubleType, RealComposite< DoubleType > >.CompositeRandomAccess access = Views.collapseReal( disaffinities ).randomAccess();

		final Random rng = new Random( 100 );

		for ( final Cursor< RealComposite< DoubleType > > c = Views.flatIterable( Views.collapseReal( disaffinities ) ).cursor(); c.hasNext(); ) {
			final RealComposite< DoubleType > comp = c.next();
			final long x = c.getLongPosition( 0 );
			comp.get( 0 ).set( x == 0 ? rng.nextDouble() * 0.03 + 0.7 : x == 1 ? rng.nextDouble() * 0.03 + 0.3 : Double.NaN );
			for ( int d = 1; d < 3; ++d )
				comp.get( d ).set( c.getLongPosition( d ) == 1 ? Double.NaN : rng.nextDouble() * 0.03 + 0.7 );
			System.out.println( new Point( c ) + " " + comp.get( 0 ) + " " + comp.get( 1 ) + " " + comp.get( 2 ) );
		}

		for ( final DoubleType d : disaffinities )
			d.set( 1 - d.get() );

		final ArrayImg< LongType, LongArray > labels = ArrayImgs.longs( 3, 2, 2 );

		final CompositeFactory< DoubleType, RealComposite< DoubleType > > compFac = size -> Views.collapseReal( ArrayImgs.doubles( 1, size ) ).randomAccess().get();

		final ConvertedRandomAccessibleInterval< DoubleType, DoubleType > affinities = new ConvertedRandomAccessibleInterval<>( disaffinities, ( s, t ) -> {
//			t.set( 1.0 );
//			t.sub( s );
			t.set( s );
		}, new DoubleType() );

		final CompareBetter< DoubleType > compare = ( first, second ) -> first.getRealDouble() > second.getRealDouble();

		final RealComposite< DoubleType > extension = Views.collapseReal( ArrayImgs.doubles( 1, 3 ) ).randomAccess().get();
		for ( int i = 0; i < 3; ++i )
			extension.get( i ).set( Double.NaN );

		final RandomAccessibleInterval< RealComposite< DoubleType > > aff = Views.collapseReal( affinities );

		final AffinityView< DoubleType, RealComposite< DoubleType > > affView = new AffinityView<>( Views.extendValue( aff, extension ), compFac );


		final TLongArrayList roots = AffinityWatershed.watershed( affView, labels, compare, new DoubleType( Double.MAX_VALUE ), new LongType( -1 ) );
		for ( final TLongIterator r = roots.iterator(); r.hasNext(); )
			System.out.println( r.next() );

		final MultiGraph g = new MultiGraph( "Roots" );
		for ( int i = 0; i < 12; ++i )
		{
			final Node n = g.addNode( i + "" );
			n.addAttribute( "ui.label", "" + i );
			n.addAttribute( "xyz",
					+IntervalIndexer.indexToPosition( i, Intervals.dimensionsAsLongArray( labels ), 0 ),
					-IntervalIndexer.indexToPosition( i, Intervals.dimensionsAsLongArray( labels ), 1 ),
					+IntervalIndexer.indexToPosition( i, Intervals.dimensionsAsLongArray( labels ), 2 ) );
		}

		{
			int i = 0;
			for ( final AbstractArrayCursor< LongType > c = labels.cursor(); c.hasNext(); )
			{
				final long l = c.next().get();
				if ( l == -1 )
					continue;
				g.addEdge( i + "-" + l, i + "", l + "", true );
				++i;
			}

		}

		final Viewer viewer = g.display( false );

		for ( final AbstractArrayCursor< LongType > c = labels.cursor(); c.hasNext(); )
		{
			final LongType l = c.next();
			System.out.println( new Point( c ) + " " + l );
		}

		for ( final Cursor< RealComposite< DoubleType > > c = Views.flatIterable( Views.interval( affView, labels ) ).cursor(); c.hasNext(); )
		{
			final RealComposite< DoubleType > comp = c.next();
			System.out.print( new Point( c ) );
			for ( int d = 0; d < 6; ++d )
				System.out.print( ", " + comp.get( d ) );

			System.out.println();

		}
	}
}

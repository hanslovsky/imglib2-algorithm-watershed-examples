package de.hanslovsky.examples;

import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.ui.view.Viewer;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.morphology.watershed.AffinityWatershed;
import net.imglib2.algorithm.morphology.watershed.AffinityWatershed.CompareBetter;
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

public class AffinityViewWatershed2D
{
	public static void main( final String[] args )
	{
		// https://arxiv.org/abs/1505.00249
		final ArrayImg< DoubleType, DoubleArray > disaffinities = ArrayImgs.doubles( 5, 4, 2 );
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

		final ArrayImg< LongType, LongArray > labels = ArrayImgs.longs( 5, 4 );

		final CompositeFactory< DoubleType, RealComposite< DoubleType > > compFac = size -> Views.collapseReal( ArrayImgs.doubles( 1, size ) ).randomAccess().get();

		final ConvertedRandomAccessibleInterval< DoubleType, DoubleType > affinities = new ConvertedRandomAccessibleInterval<>( disaffinities, ( s, t ) -> {
//			t.set( 1.0 );
//			t.sub( s );
			t.set( s );
		}, new DoubleType() );

		final int[] lookUp = new int[] { 1, 0, 2, 3 };

		final CompareBetter< DoubleType > compare = ( first, second ) -> first.getRealDouble() > second.getRealDouble();

		final RealComposite< DoubleType > extension = Views.collapseReal( ArrayImgs.doubles( 1, 2 ) ).randomAccess().get();
		for ( int i = 0; i < 2; ++i )
			extension.get( i ).set( Double.NaN );

		final RandomAccessibleInterval< RealComposite< DoubleType > > aff = Views.collapseReal( affinities );

		final AffinityView< DoubleType, RealComposite< DoubleType > > affView = new AffinityView<>( Views.extendValue( aff, extension ), compFac );


		AffinityWatershed.watershed( affView, labels, compare, new DoubleType( Double.MAX_VALUE ), new LongType( -1 ) );

		final MultiGraph g = new MultiGraph( "Roots" );
		for ( int i = 0; i < 20; ++i ) {
			final Node n = g.addNode( i + "" );
			n.addAttribute( "ui.label", "" + i );
			n.addAttribute( "xy", IntervalIndexer.indexToPosition( i, Intervals.dimensionsAsLongArray( labels ), 0 ), -IntervalIndexer.indexToPosition( i, Intervals.dimensionsAsLongArray( labels ), 1 ) );
		}

		{
			int i = 0;
			for ( final AbstractArrayCursor< LongType > c = labels.cursor(); c.hasNext(); )
			{
				final long l = c.next().get();
				g.addEdge( i + "-" + l, i + "", l + "", true );
				++i;
			}

		}

		final Viewer viewer = g.display( false );
	}
}

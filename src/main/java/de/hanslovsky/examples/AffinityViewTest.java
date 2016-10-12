package de.hanslovsky.examples;

import net.imglib2.Cursor;
import net.imglib2.Point;
import net.imglib2.algorithm.morphology.watershed.affinity.AffinityView;
import net.imglib2.algorithm.morphology.watershed.affinity.CompositeFactory;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.outofbounds.OutOfBounds;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;
import net.imglib2.view.composite.CompositeIntervalView;
import net.imglib2.view.composite.RealComposite;

public class AffinityViewTest
{

	public static void main( final String[] args )
	{
		final CompositeIntervalView< DoubleType, RealComposite< DoubleType > > affinities = Views.collapseReal( ArrayImgs.doubles( 3, 4, 2 ) );

		for ( final Cursor< RealComposite< DoubleType > > c = Views.flatIterable( affinities ).cursor(); c.hasNext(); )
		{
			final RealComposite< DoubleType > t = c.next();
			for ( int d = 0; d < 2; ++d )
				t.get( d ).set( c.getDoublePosition( d ) );
		}

		final CompositeFactory< DoubleType, RealComposite< DoubleType > > factory = size -> {
			return Views.collapseReal( ArrayImgs.doubles( 1, size ) ).randomAccess().get();
		};

		final RealComposite< DoubleType > extension = factory.create( 2 );
		for ( int d = 0; d < 2; ++d )
			extension.get( d ).set( Double.NaN );

		final AffinityView< DoubleType, RealComposite< DoubleType > > view = new AffinityView<>( Views.extendValue( affinities, extension ), factory );

		final OutOfBounds< RealComposite< DoubleType > > access = Views.extendValue( affinities, extension ).randomAccess();
		for ( final Cursor< RealComposite< DoubleType > > p = Views.interval( view, affinities ).cursor(); p.hasNext(); )
		{
			final RealComposite< DoubleType > c1 = p.next();
			access.setPosition( p );
			final RealComposite< DoubleType > c2 = access.get();
			for ( int i = 0; i < 2; ++i )
				if ( !c1.get( i ).valueEquals( c2.get( i ) ) )
					System.out.println( new Point( p ) + " " + c1.get( i ) + " " + c2.get( i ) );

			for ( int i = 0; i < 2; ++i )
			{
				access.bck( i );
				if ( !c1.get( i + 2 ).valueEquals( access.get().get( i ) ) )
					System.out.println( new Point( p ) + " " + c1.get( i + 2 ) + " " + access.get().get( i ) );
				access.fwd( i );
			}

		}

	}

}

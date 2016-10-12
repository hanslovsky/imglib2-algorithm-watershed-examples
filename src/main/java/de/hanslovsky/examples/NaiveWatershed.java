package de.hanslovsky.examples;

import gnu.trove.map.hash.TLongLongHashMap;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.morphology.watershed.Watershed.Compare;
import net.imglib2.algorithm.morphology.watershed.flat.FlatViews;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.view.Views;
import net.imglib2.view.composite.Composite;

public class NaiveWatershed
{

	public static < T extends Type< T >, C extends Composite< T >, U extends IntegerType< U > > void watershed(
			final RandomAccessible< C > img,
			final RandomAccessibleInterval< U > labels,
			final U ignoreval,
			final U notVisitedVal,
			final T maxVal,
			final Compare< T > compare )
	{
		for ( final U l : Views.flatIterable( labels ) )
			l.set( notVisitedVal );

		for ( int d = 0; d < labels.numDimensions(); ++d )
		{
			for ( final U l : Views.hyperSlice( labels, d, 0 ) )
				l.set( ignoreval );

			for ( final U l : Views.hyperSlice( labels, d, labels.max( d ) ) )
				l.set( ignoreval );
		}
		long size = 1;
		for ( int d = 0; d < labels.numDimensions(); ++d )
			size *= labels.dimension( d );

		final long[] strides = new long[ labels.numDimensions() ];
		strides[ 0 ] = 1;
		for ( int d = 1; d < labels.numDimensions(); ++d )
			strides[ d ] = strides[ d - 1 ] * labels.dimension( d - 1 );

		final RandomAccess< C > imgFlatAccess = FlatViews.flatten( Views.interval( img, labels ) ).randomAccess();
		final RandomAccess< U > labelsFlatAcess = FlatViews.flatten( labels ).randomAccess();
		final RandomAccess< U > labelsFlatAccess2 = FlatViews.flatten( labels ).randomAccess();

		final TLongLongHashMap map = new TLongLongHashMap();

		final T min = img.randomAccess().get().get( 0 ).createVariable();

		final int nDim = img.numDimensions();

		final long[] steps = new long[ strides.length * 2 ];
		{
			int idx = 0;
			for ( final int i : new int[] { -1, 1 } )
				for ( int d = 0; d < strides.length; ++d, ++idx )
					steps[ idx ] = strides[ d ] * i;
		}

		for ( long index = 0; index < size; ++index )
		{
			final U currentLabel = get( labelsFlatAcess, index );
			if ( !currentLabel.valueEquals( notVisitedVal ) || currentLabel.equals( ignoreval ))
				continue;

			currentLabel.setInteger( index );

			for ( final long currentIndex = index; currentIndex != -1; )
			{

				final long argMin = 0;
				min.set( maxVal );
				final C currentImg = get( imgFlatAccess, index );

				final boolean connect = false;

				for ( int d = 0; d < steps.length; ++d )
				{
					final long step = steps[ d ];
					final long neighborIndex = index + step;
					final U neighbor = get( labelsFlatAccess2, neighborIndex );
					if ( neighbor.valueEquals( ignoreval ) )
						continue;

					final T val = currentImg.get( d );

					if ( connect )
					{

					}
					else if ( neighbor.valueEquals( notVisitedVal ) && compare.isSmaller( val, min ) )
						min.set( val );

				}
			}

		}


	}

	public static < T > T get( final RandomAccess< T > access, final long index ) {
		access.setPosition( index, 0 );
		return access.get();
	}


}

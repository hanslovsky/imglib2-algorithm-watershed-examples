package de.hanslovsky.examples.distance;

import ij.ImageJ;
import ij.ImagePlus;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.algorithm.gradient.PartialDerivative;
import net.imglib2.converter.Converter;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.array.ArrayCursor;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import net.imglib2.view.composite.RealComposite;

public class DistanceTransform
{

	public static < T > T get( final RandomAccess< T > access, final long index )
	{
		access.setPosition( index, 0 );
		return access.get();
	}

	// source and target may not be the same?
	public static < T extends RealType< T >, U extends RealType< U > > void transformeSquared1D(
			final RealComposite< T > source,
			final RealComposite< U > target,
			final long size )
	{
		final RealComposite< LongType > envelopeIndex = Views.collapseReal( ArrayImgs.longs( size ) ).randomAccess().get();
		final RealComposite< DoubleType > envelopeValue = Views.collapseReal( ArrayImgs.doubles( size + 1 ) ).randomAccess().get();

//		final RandomAccess< LongType > envelopeIndexAccess = envelopeIndex.randomAccess();
//		final RandomAccess< DoubleType > envelopeValueAccess = envelopeValues.randomAccess();
//		final RandomAccess< T > sourceAccess = FlatViews.flatten( source ).randomAccess();

		long k = 0;

		envelopeIndex.get( 0 ).set( 0 );
		envelopeValue.get( 0 ).set( -1e20 );
		envelopeValue.get( 1 ).set( +1e20 );
		for ( long position = 1; position < size; ++position )
		{
			long envelopeIndexAtK = envelopeIndex.get( k ).get();
			final double sourceAtPosition = source.get( position ).getRealDouble();
			final double positionSquared = position * position;
			double s = 0.5 * ( sourceAtPosition + positionSquared - ( source.get( envelopeIndexAtK ).getRealDouble() +
					envelopeIndexAtK * envelopeIndexAtK ) ) / ( position - envelopeIndexAtK );

			for ( double envelopeValueAtK = envelopeValue.get( k ).get(); s <= envelopeValueAtK; envelopeValueAtK = envelopeValue.get( k ).get() )
			{
				--k;
				envelopeIndexAtK = envelopeIndex.get( k ).get();
				s = 0.5 * ( sourceAtPosition + positionSquared - ( source.get( envelopeIndexAtK ).getRealDouble() + envelopeIndexAtK * envelopeIndexAtK ) ) / ( position - envelopeIndexAtK );
			}
			++k;
			envelopeIndex.get( k ).set( position );
			envelopeValue.get( k ).set( s );
			envelopeValue.get( k + 1 ).set( 1e20 );
		}

		k = 0;

//		final RandomAccess< U > targetAccess = FlatViews.flatten( target ).randomAccess();

		for ( long position = 0; position < size; ++position )
		{
			while ( envelopeValue.get( k + 1 ).get() < position )
				++k;
			final long envelopeIndexAtK = envelopeIndex.get( k ).get();
			final long diff = position - envelopeIndexAtK;
			// copy necessary because of the following line, access to source
			// after write to source -> source and target cannot be the same
			target.get( position ).setReal( diff * diff + source.get( envelopeIndexAtK ).getRealDouble() );
		}

	}

	public static < T extends RealType< T >, U extends RealType< U > > void transformSquared2D(
			final RandomAccessibleInterval< T > source,
			final RandomAccessibleInterval< U > target )
	{

		// transform along columns
		{
			for ( long x = 0; x < target.dimension( 0 ); ++x )
				transformeSquared1D(
						Views.collapseReal( Views.hyperSlice( source, 0, x ) ).randomAccess().get(),
						Views.collapseReal( Views.hyperSlice( target, 0, x ) ).randomAccess().get(),
						target.dimension( 1 ) );
		}

		// transform along rows
		for ( long y = 0; y < target.dimension( 1 ); ++y )
		{
			// would like to avoid copy to row but seems unavoidable
			final ArrayImg< DoubleType, DoubleArray > row = ArrayImgs.doubles( 1, source.dimension( 0 ) );
			final IntervalView< DoubleType > rowView = Views.hyperSlice( row, 0, 0l );
			for ( final Pair< U, DoubleType > pair : Views.interval( Views.pair( Views.hyperSlice( target, 1, y ), rowView ), rowView ) )
				pair.getB().set( pair.getA().getRealDouble() );
			transformeSquared1D(
					Views.collapseReal( row ).randomAccess().get(),
					Views.collapseReal( Views.hyperSlice( target, 1, y ) ).randomAccess().get(),
					target.dimension( 0 ) );
//			for ( final Pair< DoubleType, U > pair : Views.interval(
//					Views.pair( Views.hyperSlice( row, 0, 0l ), Views.hyperSlice( target, 1, y ) ), Views.hyperSlice( row, 0, 0l ) ) )
//				pair.getB().setReal( pair.getA().getRealDouble() );
//			transformeSquared1D( Views.hyperSlice( target, 1, y ), Views.hyperSlice( target, 1, y ) );
		}

	}

	// TODO fix this -> arbitrary collapse in views?
	public static < T extends RealType< T >, U extends RealType< U > > void transformSquared(
			final RandomAccessibleInterval< T > source,
			final RandomAccessibleInterval< U > target )
	{

		assert source.numDimensions() == target.numDimensions(): "Dimension mismatch";
		final int nDim = source.numDimensions();
		final int lastDim = nDim - 1;

		// transform along first dimension
		{
			final long size = target.dimension( 0 );
			final Cursor< RealComposite< T > > s = Views.flatIterable( Views.collapseReal( Views.permute( source, 0, lastDim ) ) ).cursor();
			final Cursor< RealComposite< U > > t = Views.flatIterable( Views.collapseReal( Views.permute( target, 0, lastDim ) ) ).cursor();
			while ( s.hasNext() )
				transformeSquared1D( s.next(), t.next(), size );
		}

		// transform along subsequent dimensions
		for ( int d = 1; d < nDim; ++d )
		{

			// would like to avoid copy to tmp but seems unavoidable
			final long size = target.dimension( d );

			final ArrayImg< DoubleType, DoubleArray > tmp = ArrayImgs.doubles( 1, size );
			final Cursor< RealComposite< U > > t = Views.flatIterable( Views.collapseReal( Views.permute( target, d, lastDim ) ) ).cursor();

			while ( t.hasNext() )
			{
				final RealComposite< U > composite = t.next();
				final ArrayCursor< DoubleType > tmpCursor = tmp.cursor();
				for ( long i = 0; i < size; ++i )
					tmpCursor.next().set( composite.get( i ).getRealDouble() );

				transformeSquared1D( Views.collapseReal( tmp ).randomAccess().get(), composite, size );

//				for ( final Pair< U, DoubleType > p : Views.interval( Views.pair( Views.hyperSlice( target, d, pos ), tmp ), tmp ) )
//					p.getA().setReal( p.getB().get() );
			}
		}

	}

	public static void main( final String[] args ) throws IncompatibleTypeException
	{

		new ImageJ();

		final String url = "http://img.autobytel.com/car-reviews/autobytel/11694-good-looking-sports-cars/2016-Ford-Mustang-GT-burnout-red-tire-smoke.jpg";
		final ImagePlus imp = new ImagePlus( url );

		final ArrayImg< FloatType, FloatArray > img = ArrayImgs.floats( ( float[] ) imp.getProcessor().convertToFloatProcessor().getPixels(), imp.getWidth(), imp.getHeight() );
		final ArrayImg< DoubleType, DoubleArray > gauss = ArrayImgs.doubles( imp.getWidth(), imp.getHeight() );
		Gauss3.gauss( 0.1, Views.extendBorder( img ), gauss );

		@SuppressWarnings( "unchecked" )
		final ArrayImg< DoubleType, DoubleArray >[] gradients = new ArrayImg[] {

				ArrayImgs.doubles( imp.getWidth(), imp.getHeight() ),
				ArrayImgs.doubles( imp.getWidth(), imp.getHeight() ),
				ArrayImgs.doubles( imp.getWidth(), imp.getHeight() )

		};

		PartialDerivative.gradientCentralDifference( Views.extendBorder( gauss ), gradients[ 0 ], 0 );
		PartialDerivative.gradientCentralDifference( Views.extendBorder( gauss ), gradients[ 1 ], 1 );

		for ( ArrayCursor< DoubleType > g0 = gradients[ 0 ].cursor(), g1 = gradients[ 1 ].cursor(), g = gradients[ 2 ].cursor(); g.hasNext(); )
		{
			final double v0 = g0.next().get();
			final double v1 = g1.next().get();
			g.next().set( v0 * v0 + v1 * v1 );
			g.get().mul( 10.0 );
		}

		ImageJFunctions.show( gradients[ 2 ], "gradient" );
		final ArrayImg< DoubleType, DoubleArray > dt = ArrayImgs.doubles( imp.getWidth(), imp.getHeight() );

		transformSquared( gradients[ 2 ], dt );

//		final ArrayImg< DoubleType, DoubleArray > test = ArrayImgs.doubles( 10, 20 );
//
//		final double r1 = 5;
//		final double r2 = 3;
//
//		final double cX = 5;
//		final double cY = 8;
//
//		for ( final ArrayCursor< DoubleType > t = test.cursor(); t.hasNext(); )
//		{
//			final DoubleType v = t.next();
//			final double dX = t.getDoublePosition( 0 ) - cX;
//			final double dY = t.getDoublePosition( 1 ) - cY;
//			final double d = dX * dX + dY * dY;
//			v.set( d < r1 * r1 && d > r2 * r2 ? -d : 0.0 );
//		}
//
//		final ArrayImg< DoubleType, DoubleArray > dt = transformSquared2D( test );
//
//		ImageJFunctions.show( test, "test" );

		final Converter< DoubleType, DoubleType > conv = ( input, output ) -> {
			output.set( input );
			output.mul( -1.0 );
		};

		ImageJFunctions.show( new ConvertedRandomAccessibleInterval<>( dt, conv, new DoubleType() ), "dt" );

//		final double[] f = new double[] {
//				1.0, 0.0, 1.0, 2.0, 3.0, 1.5, 10.0, 8.0, 9.0
//		};
//		System.out.println( Arrays.toString( f ) );
//		for ( final DoubleType d : transformeSquared1D( ArrayImgs.doubles( f, f.length ) ) )
//			System.out.println( d );
	}
}

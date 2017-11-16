package de.hanslovsky.examples;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import ij.ImageJ;
import ij.ImagePlus;
import it.unimi.dsi.fastutil.longs.LongBigArrayBigList;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Point;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.morphology.watershed.AffinityWatersheds;
import net.imglib2.algorithm.morphology.watershed.HierarchicalPriorityQueueIntHeaps;
import net.imglib2.algorithm.morphology.watershed.PriorityQueueFactory;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.converter.Converters;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.array.ArrayCursor;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;
import net.imglib2.view.composite.GenericComposite;

public class AffinityWatershedsExample2DGeneric
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

	@SuppressWarnings( "unchecked" )
	public static void main( final String[] args ) throws IncompatibleTypeException
	{

		new ImageJ();
//		final String url = "http://img.autobytel.com/car-reviews/autobytel/11694-good-looking-sports-cars/2016-Ford-Mustang-GT-burnout-red-tire-smoke.jpg";
//		final String url = "http://mediad.publicbroadcasting.net/p/wuwm/files/styles/medium/public/201402/LeAnn_Crowe.jpg";
		final String url = "https://www.dropbox.com/s/g4i5ey9yc281dif/bfly_crop.jpeg?raw=1";
//		final String url = "https://i.imgur.com/2rmtqab.jpg";
//		final String url = "/home/hanslovskyp/local/tmp/butterfly.jpg";
//		final String url = "https://i.imgur.com/Yk5M99Uh.jpg";
//		final String url = "https://i.imgur.com/ql8NRRWh.jpg";
		final ImagePlus imp = new ImagePlus( url );

		final int[] shifts = { 16, 8, 0 };
		final double[] weights = { 1.0, 1.0, 1.0 };
		final double weightSum = Arrays.stream( weights ).sum();
		final double sigma = 0.5;
		final RandomAccessibleInterval< ARGBType > source = ImageJFunctions.wrapRGBA( imp );
		final ArrayImg< DoubleType, DoubleArray >[][] gradients = IntStream
				.range( 0, shifts.length )
				.mapToObj( i -> Util.gradientsAndMagnitudeNoExcept( Converters.convert( source, ( s, t ) -> t.set( weights[ i ] * ( s.get() >>> shifts[ i ] & 0xff ) ), new DoubleType() ), sigma ) )
				.toArray( ArrayImg[][]::new );

		final double[] img = new double[ imp.getWidth() * imp.getHeight() ];

		final ArrayCursor< DoubleType >[] c = Arrays.stream( gradients ).map( g -> g[ 2 ] ).map( ArrayImg::cursor ).toArray( n -> new ArrayCursor[ n ] );
		for ( int i = 0; i < img.length; ++i )
			img[ i ] = Arrays.stream( c ).map( Cursor::next ).mapToDouble( DoubleType::getRealDouble ).sum() / weightSum;

		final long[] markers = new long[ img.length ];

		final ArrayImg< LongType, LongArray > markersWrapped = ArrayImgs.longs( markers, imp.getWidth(), imp.getHeight() );

		final long seedPointSpacing = 20l;
		final long nextSeed = Util.seedsGrid( markersWrapped, seedPointSpacing, 1, new ArrayList<>() );
		final List< Point > locations = new ArrayList<>();
		for ( final Cursor< LongType > m = markersWrapped.cursor(); m.hasNext(); )
			if ( m.next().get() != 0 )
				locations.add( new Point( m ) );

		final DiamondShape shape = new DiamondShape( 1 );
//		final RectangleShape shape = new RectangleShape( 1, true );

		final PriorityQueueFactory fac2 = new HierarchicalPriorityQueueIntHeaps.Factory( 1024 * 4 );

		ImageJFunctions.show( gradients[ 0 ][ 2 ], "grad 1" );
		ImageJFunctions.show( gradients[ 1 ][ 2 ], "grad 2" );
		ImageJFunctions.show( gradients[ 2 ][ 2 ], "grad 3" );

		final RandomAccessibleInterval< GenericComposite< DoubleType > > affinities = Util.toAffinities( ArrayImgs.doubles( img, imp.getWidth(), imp.getHeight() ), shape );

		AffinityWatersheds.flood(
				affinities,
				( RandomAccessible< LongType > ) Views.extendValue( ArrayImgs.longs( markers, imp.getWidth(), imp.getHeight() ), new LongType( 0l ) ),
				new FinalInterval( imp.getWidth(), imp.getHeight() ),
				locations,
				shape,
				fac2 );
		ImageJFunctions.show( ArrayImgs.longs( markers, imp.getWidth(), imp.getHeight() ) );
		final LongType bg = markersWrapped.firstElement().createVariable();
		bg.set( -1l );
		final ArrayImg< LongType, ? > seedsGradient =
				Util.makeSeedsGradient( ArrayImgs.longs( markers, imp.getWidth(), imp.getHeight() ), bg );
		ImageJFunctions.show( seedsGradient, "labels" );
		Util.overlay( imp, seedsGradient, bg );

		imp.show();

		final LongBigArrayBigList rs = new LongBigArrayBigList( nextSeed + 1 );
		final LongBigArrayBigList gs = new LongBigArrayBigList( nextSeed + 1 );
		final LongBigArrayBigList bs = new LongBigArrayBigList( nextSeed + 1 );
		final LongBigArrayBigList counts = new LongBigArrayBigList( nextSeed + 1 );

		for ( long s = 1; s < nextSeed + 1; ++s )
		{
			counts.add( 0 );
			rs.add( 0 );
			gs.add( 0 );
			bs.add( 0 );
		}

		final ImagePlus imp2 = new ImagePlus( url );
		for ( final Pair< ARGBType, LongType > pair : Views.flatIterable( Views.interval( Views.pair( ImageJFunctions.wrapRGBA( imp2 ), markersWrapped ), markersWrapped ) ) )
		{
			final long index = pair.getB().get();
			if ( index < 0 )
				continue;
			final int color = pair.getA().get();

			counts.set( index, counts.getLong( index ) + 1 );
			rs.set( index, rs.getLong( index ) + ARGBType.red( color ) );
			gs.set( index, gs.getLong( index ) + ARGBType.green( color ) );
			bs.set( index, bs.getLong( index ) + ARGBType.blue( color ) );
		}

		for ( final Pair< ARGBType, LongType > pair : Views.flatIterable( Views.interval( Views.pair( ImageJFunctions.wrapRGBA( imp2 ), markersWrapped ), markersWrapped ) ) )
		{
			final long index = pair.getB().get();
			if ( index < 0 )
				continue;
			final long count = counts.getLong( index );
			pair.getA().set( ARGBType.rgba(
					( int ) rs.getLong( index ) / count,
					( int ) gs.getLong( index ) / count,
					( int ) bs.getLong( index ) / count,
					255 ) );
		}

		imp2.show();

	}



}

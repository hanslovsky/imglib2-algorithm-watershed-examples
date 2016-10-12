package de.hanslovsky.examples.distance;

import java.util.Arrays;

public class AnisotropicEuclidianDistance implements Distance
{

	private final double[] weights;

	public AnisotropicEuclidianDistance( final double[] weights )
	{
		super();
		this.weights = weights;
	}

	public AnisotropicEuclidianDistance( final double weight, final int nDim )
	{
		super();
		this.weights = new double[ nDim ];
		Arrays.fill( this.weights, weight );
	}

	public AnisotropicEuclidianDistance( final int nDim )
	{
		this( 1.0, nDim );
	}

	@Override
	public double evaluate( final int x, final int xShift, final double yShift, final int dimension )
	{
		final double diff = x - xShift;
		return weights[ dimension ] * diff * diff + yShift;
	}

	@Override
	public double intersect( final int xShift1, final double yShift1, final int xShift2, final double yShift2, final int dimension )
	{
		// TODO Auto-generated method stub
		return 0;
	}

}

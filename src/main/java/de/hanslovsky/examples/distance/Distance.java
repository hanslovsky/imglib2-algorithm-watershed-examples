package de.hanslovsky.examples.distance;

/**
 *
 * One-dimensional, strictly convex function that can be shifted along the x-
 * and y-axes. Must provide unique intersect between two
 *
 * @author Philipp Hanslovsky
 *
 */
public interface Distance
{

	double evaluate( int x, int xShift, double yShift, int dimension );

	double intersect( int xShift1, double yShift1, int xShift2, double yShift2, int dimension );

}

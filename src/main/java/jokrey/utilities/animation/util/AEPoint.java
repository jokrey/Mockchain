package jokrey.utilities.animation.util;

public class AEPoint {
	public double x;
	public double y;
	public AEPoint() {
		this(0,0);
	}
	public AEPoint(double x, double y) {
		this.x=x;
		this.y=y;
	}
	public double getX() {return x;}
	public double getY() {return y;}
	public double distance(AEPoint p2) {
		double x_dist = p2.x-x;
		double y_dist = p2.y-y;
		return Math.sqrt(x_dist*x_dist + y_dist*y_dist);
	}
	public void setLocation(double x, double y) {
		this.x=x;
		this.y=y;
	}

	@Override public String toString() {
		return x+", "+y;
	}
	@Override public boolean equals(Object obj) {
		return obj instanceof AEPoint && x == ((AEPoint)obj).x && y == ((AEPoint)obj).y;
	}
}
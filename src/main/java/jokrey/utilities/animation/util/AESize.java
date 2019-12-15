package jokrey.utilities.animation.util;

public class AESize {
	public double w;
	public double h;
	public AESize(double w, double h) {
		this.w=w;
		this.h=h;
	}
	public double getWidth() {return w;}
	public double getHeight() {return h;}

	@Override public String toString() {
		return w+", "+w;
	}
	@Override public boolean equals(Object obj) {
		return obj instanceof AESize && w == ((AESize)obj).w && h == ((AESize)obj).h;
	}
}
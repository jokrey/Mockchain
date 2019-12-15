package jokrey.utilities.animation.util;

public class AERect {
	public double x;
	public double y;
	public double w;
	public double h;
	public AERect() {
		this(0,0,0,0);
	}
	public AERect(double w, double h) {
		this(0,0,w,h);
	}
	public AERect(double x, double y, double w, double h) {
		this.x=x;
		this.y=y;
		this.w=w;
		this.h=h;
	}
	public AERect(AERect r) {
		this(r.x, r.y, r.w, r.h);
	}
	public AERect(AEPoint p, AEPoint p1) {
		this(p.x,p.y,Math.abs(p.x-p1.x),Math.abs(p.y-p1.y));
	}
	public AERect(AESize s) {
		this(0,0,s.w,s.h);
	}
	public double getX() {return x;}
	public double getY() {return y;}
	public double getWidth() {return w;}
	public double getHeight() {return h;}
	public void setLocation(double x, double y) {
		this.x=x;
		this.y=y;
	}

	@Override public String toString() {
		return x+", "+y+", "+w+", "+h;
	}
	@Override public boolean equals(Object obj) {
		return obj instanceof AERect && x == ((AERect)obj).x && y == ((AERect)obj).y && w == ((AERect)obj).w && h == ((AERect)obj).h;
	}






	public boolean intersects(AERect o_r) {
		//dependenciesFrom java.awt.rectangle source code
		double tw = this.w;
		double th = this.h;
		double rw = o_r.w;
		double rh = o_r.h;
		if (rw <= 0 || rh <= 0 || tw <= 0 || th <= 0) {
			return false;
		}
		double tx = this.x;
		double ty = this.y;
		double rx = o_r.x;
		double ry = o_r.y;
		rw += rx;
		rh += ry;
		tw += tx;
		th += ty;
		//      overflow || intersect
		return ((rw < rx || rw > tx) &&
				(rh < ry || rh > ty) &&
				(tw < tx || tw > rx) &&
				(th < ty || th > ry));
	}
	public AERect intersection(AERect r) {
		double tx1 = this.x;
		double ty1 = this.y;
		double rx1 = r.x;
		double ry1 = r.y;
		long tx2 = (long) tx1; tx2 += this.w;
		long ty2 = (long) ty1; ty2 += this.h;
		long rx2 = (long) rx1; rx2 += r.w;
		long ry2 = (long) ry1; ry2 += r.h;
		if (tx1 < rx1) tx1 = rx1;
		if (ty1 < ry1) ty1 = ry1;
		if (tx2 > rx2) tx2 = rx2;
		if (ty2 > ry2) ty2 = ry2;
		tx2 -= tx1;
		ty2 -= ty1;
		// tx2,ty2 will never overflow (they will never be
		// larger than the smallest of the two source w,h)
		// they might underflow, though...
		if (tx2 < Integer.MIN_VALUE) tx2 = Integer.MIN_VALUE;
		if (ty2 < Integer.MIN_VALUE) ty2 = Integer.MIN_VALUE;
		return new AERect(tx1, ty1, (int) tx2, (int) ty2);
	}
	public boolean isEmpty() {
		return (w <= 0) || (h <= 0);
	}
	public boolean contains(AEPoint p) {
		return contains(p.x, p.y);
	}
	public boolean contains(double ox, double oy) {
		double x0 = getX();
		double y0 = getY();
		return (ox >= x0 &&
				oy >= y0 &&
				ox < x0 + getWidth() &&
				oy < y0 + getHeight());
	}
	public boolean contains(AERect o) {
		int wcopy = (int) this.w;
		int hcopy = (int) this.h;
		if ((wcopy | hcopy | (int)o.w | (int)o.h) < 0) {
			// At least one of the dimensions is negative...
			return false;
		}
		// Note: if any dimension is zero, tests below must return false...
		int xcopy = (int) this.x;
		int ycopy = (int) this.y;
		if (o.x < xcopy || o.y < ycopy) {
			return false;
		}
		wcopy += xcopy;
		o.w += o.x;
		if (o.w <= o.x) {
			// o.x+o.w overflowed or o.w was zero, return false if...
			// either original w or o.w was zero or
			// x+w did not overflow or
			// the overflowed x+w is smaller than the overflowed o.x+o.w
			if (wcopy >= xcopy || o.w > wcopy) return false;
		} else {
			// o.x+o.w did not overflow and o.w was not zero, return false if...
			// original w was zero or
			// x+w did not overflow and x+w is smaller than o.x+o.w
			if (wcopy >= xcopy && o.w > wcopy) return false;
		}
		hcopy += ycopy;
		o.h += o.y;
		if (o.h <= o.y) {
			return hcopy < ycopy && !(o.h > hcopy);
		} else {
			return hcopy < ycopy || !(o.h > hcopy);
		}
	}
}

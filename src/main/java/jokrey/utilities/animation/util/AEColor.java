package jokrey.utilities.animation.util;

public class AEColor {
    public final int argb;

    public AEColor(int argb) {
		this.argb=argb;
	}
	public AEColor(int a, int r, int g, int b) {
		argb = (a << 24) | (r << 16 ) | (g<<8) | b;
	}
	public AEColor(int r, int g, int b) {
		this(255,r,g,b);
	}
	public int getAlpha() {
		return 0xFF & (argb >> 24);
	}
	public int getRed() {
		return 0xFF & (argb >> 16);
	}
	public int getGreen() {
		return 0xFF & (argb >> 8);
	}
	public int getBlue() {
		return 0xFF & argb;  //  0xFF & (argb >> 0)
	}
	public static AEColor getRandomColor() {
		return new AEColor(AE_UTIL.getRandomNr(Integer.MIN_VALUE, Integer.MAX_VALUE));
	}
	public static AEColor getRandomColor(int small, int high) {
		return new AEColor(255, AE_UTIL.getRandomNr(small, high), AE_UTIL.getRandomNr(small, high), AE_UTIL.getRandomNr(small, high));
	}
	
	@Override
	public String toString() {
		return "AEColor["+getAlpha()+", "+getRed()+", "+getGreen()+", "+getBlue()+"]";
	}
	@Override public boolean equals(Object obj) {
		return obj instanceof AEColor && argb==((AEColor)obj).argb;
	}
	public AEColor darker() {
		return new AEColor(getAlpha(), (int)(getRed()*0.9), (int)(getGreen()*0.9), (int) (getBlue()*0.9));
	}
	public AEColor brighter() {
		return new AEColor(getAlpha(), (int)(Math.min(255, getRed()*1.1)), (int)(Math.min(255, getGreen()*1.1)), (int) (Math.min(255, getBlue()*1.1)));
	}

	@Override public int hashCode() {
		return Integer.hashCode(argb);
	}

	public static final AEColor WHITE = new AEColor(255, 255, 255, 255);
	public static final AEColor RED = new AEColor(255, 255, 0, 0);
	public static final AEColor GRAY = new AEColor(255, 128,128,128);
	public static final AEColor LIGHT_GRAY = new AEColor(255, 192,192,192);
	public static final AEColor DARK_GRAY = new AEColor(255, 169,169,169);
	public static final AEColor BLACK = new AEColor(255, 0, 0, 0);
	public static final AEColor CYAN = new AEColor(255, 0, 255, 255);
	public static final AEColor ORANGE = new AEColor(255, 255,140,0);
	public static final AEColor LIGHT_BLUE = new AEColor(255, 173, 216, 230);
    public static final AEColor BLUE = new AEColor(255, 0, 0, 255);
	public static final AEColor YELLOW = new AEColor(255, 255,255,0);
	public static final AEColor MAROON = new AEColor(255, 128,0,0);
	public static final AEColor CRIMSON = new AEColor(255, 220,20,60);
}
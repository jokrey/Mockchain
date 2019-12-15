package jokrey.utilities.animation.pipeline;

import jokrey.utilities.animation.util.AEColor;
import jokrey.utilities.animation.util.AEImage;
import jokrey.utilities.animation.util.AEPoint;
import jokrey.utilities.animation.util.AERect;

public abstract class AnimationDrawer {
    protected abstract AERect getPanelBoundsOnScreen();

    public void drawLine(AEColor colorToDraw, AEPoint p1, AEPoint p2) {
        drawLine(colorToDraw, p1, p2, 1);
    }
    public abstract void drawLine(AEColor colorToDraw, AEPoint p1, AEPoint p2, float size);
    public abstract void fillRect(AEColor colorToDraw, AERect drawB);
    public abstract void drawRect(AEColor colorToDraw, AERect aeRect);
    public abstract void fillOval(AEColor colorToDraw, AERect drawB);
	public abstract void drawOval(AEColor colorToDraw, AERect aeRect);
	//openDirection==0,up ; openDirection==1,down ; openDirection==2,left ; openDirection==3,right
    public abstract void drawHalfOval(AEColor colorToDraw, AERect aeRect, int openDirection);
    public abstract void fillHalfOval(AEColor colorToDraw, AERect aeRect, int openDirection);
    public abstract void fillTriangle(AEColor colorToDraw, AERect aeRect);
    // returns height of drawn string..
    public abstract double drawString(AEColor clr, double font_size, String str, double mid_x, double mid_y);
    public abstract void drawString(AEColor clr, String str, AERect rect);
    public abstract void drawImage(AEImage toDrawer, AERect drawB);
}
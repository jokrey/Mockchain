package jokrey.utilities.animation.pipeline;

import jokrey.utilities.animation.engine.AnimationEngine;
import jokrey.utilities.animation.util.AEColor;
import jokrey.utilities.animation.util.AEPoint;
import jokrey.utilities.animation.util.AERect;
import jokrey.utilities.animation.util.AESize;

import java.util.List;

public class AnimationPipeline {
    private final AnimationDrawer drawer;
    public AnimationDrawer getDrawer() {
        return drawer;
    }
    public AnimationPipeline(AnimationDrawer drawer) {
        this.drawer=drawer;
    }

    
    //OVERRIDEABLE
    protected void drawBackground(AERect drawBounds, AnimationEngine engine) {
        drawer.fillRect(AEColor.DARK_GRAY, new AERect(0,0,getPixelSize().getWidth(), getPixelSize().getHeight()));
        drawer.fillRect(AEColor.BLACK, new AERect(drawBounds.getX(), drawBounds.getY(),drawBounds.getWidth(), drawBounds.getHeight()));
    }
    protected void drawForeground(AERect drawBounds, AnimationEngine engine) {  }


    private void doActualDrawing(List<AnimationObject> list, AnimationEngine engine) {
        setPanelBoundsOnScreen(drawer.getPanelBoundsOnScreen());

        AERect drawBounds = getDrawBounds(engine);
        AERect partOfDrawBoundsVisible = getVisibleDrawArea(drawBounds);

        drawBackground(drawBounds, engine);

        for(AnimationObject ip:list) {
            if(ip!=null) {
                AERect drawB = getDrawBoundsFor(ip);
                if((//ip.getX()<0||ip.getY()<0||ip.getX()+ip.getW()>100||ip.getY()+ip.getH()>100 || //if the particle is not within the 100 x 100 map field -> NOT ONLY REDUNDANT, BUT UNNECESSARY (its a joke)
                        drawB.intersects(partOfDrawBoundsVisible))//if the particle would be visible if drawn - if not discard
                        || ip.isLine()) {//lines would have to be handled differently, which is why we just draw them always
                    drawAnimationObject(ip, ip.drawParam);
                }
            }
        }

        drawForeground(drawBounds, engine);
    }
    private void drawAnimationObject(AnimationObject ip, Object drawParam) {
        AnimationObjectDrawer aod = getDrawingUnitForParam(ip, ip.drawParam);
        aod.draw(ip, this, ip.drawParam);
    }

    public double squareEqualsPixels = 1;

    private AERect pixelSize = new AERect();
    public void setPanelBoundsOnScreen(AERect pixelSize) {
        this.pixelSize=pixelSize;
    }
    public void resetDrawBounds(AnimationEngine engine) {
        resetDrawBounds(engine.getVirtualBoundaries());
    }
    public void resetDrawBounds(AESize virtSize) {
        squareEqualsPixels=1;
        while(virtSize.getWidth()*squareEqualsPixels<pixelSize.getWidth() && virtSize.getHeight()*squareEqualsPixels<pixelSize.getHeight())
            squareEqualsPixels+=0.001;
        while(! (virtSize.getWidth()*squareEqualsPixels<=pixelSize.getWidth() && virtSize.getHeight()*squareEqualsPixels<=pixelSize.getHeight())) {
            squareEqualsPixels-=0.001;
        }
        userDrawBoundsMidOverride = null;
    }
    public AERect getPixelSize() {return pixelSize;}

    public int fpsCaps = 50;
    private double lastFPS = 0;
    public double getFPS() {return lastFPS;}
    private long lastNanoTime=-1;
    public void resetDelta() {lastNanoTime=System.nanoTime();}
    public boolean canDraw() {
        long delta = System.nanoTime()-lastNanoTime;
        if(delta<1)delta=1;
        long fps=((long)1e9)/delta;//this might not be the correct
        return fpsCaps <= 0 || fps <= fpsCaps;
    }

    public void draw(List<AnimationObject> list) {
        draw(list, null, false);
    }
    public final void draw(List<AnimationObject> list, AnimationEngine engine, boolean forceRedraw) {
        if(lastNanoTime==-1)resetDelta();
        if(!canDraw() && !forceRedraw) {
            return;
        }
        long delta = System.nanoTime()-lastNanoTime;
        if(delta<1)delta=1;
        long fps=((long)1e9)/delta;//this might not be the correct
        lastNanoTime=System.nanoTime();
        lastFPS=fps;
        if(squareEqualsPixels<0&&pixelSize!=null) {//means fit to screen
            resetDrawBounds(engine);
        }

        currentDrawBounds=getDrawBounds(engine);
        doActualDrawing(list, engine);
//		System.out.println("drawn");
    }

    protected AnimationObjectDrawer[] getSupportedDrawUnits() {
        return new AnimationObjectDrawer[]{new StandardAODrawer(), new ImageAODrawer()};
    }

    //OVERRIDEABLE IF NEEDED - For example a ResManObjectDrawer that would take a string(or int for BufImg)
    public AnimationObjectDrawer[] possibleDUnits = getSupportedDrawUnits();
    public AnimationObjectDrawer getDrawingUnitForParam(AnimationObject o, Object param) {
        if(param == null) return possibleDUnits[0];
        if(param instanceof AnimationObjectDrawer)return (AnimationObjectDrawer) param;
        for(AnimationObjectDrawer aod:possibleDUnits)
            if(aod.canDraw(o, param))
                return aod;
        return possibleDUnits[0];
    }

    public AEPoint userDrawBoundsMidOverride = null;
    public boolean useUserMidOV() {
        return userDrawBoundsMidOverride!=null;
    }
    public AERect getDrawBounds(AnimationEngine engine) {
        return getDrawBounds(engine.getDrawerMidOverride(), engine.getVirtualBoundaries());
    }
    public AERect getDrawBounds(AEPoint drawBoundsMidOverride, AESize virtualSize) {
        if(useUserMidOV())drawBoundsMidOverride=userDrawBoundsMidOverride;
        AERect drawBounds = new AERect(0,0,(int)(virtualSize.getWidth()*squareEqualsPixels),(int) (virtualSize.getHeight()*squareEqualsPixels));
        currentDrawBounds=drawBounds;
        if((drawBoundsMidOverride!=null && (drawBounds.getWidth()>pixelSize.getWidth() || drawBounds.getHeight()>pixelSize.getHeight())) || useUserMidOV()) {
            AEPoint supposedMid = new AEPoint(pixelSize.getWidth()/2 - drawBounds.getWidth()/2, pixelSize.getHeight()/2 - drawBounds.getHeight()/2);
            AEPoint p = convertToPixelPoint(drawBoundsMidOverride);
            drawBounds.setLocation((supposedMid.x+drawBounds.getWidth()/2)-(p.x), (supposedMid.y+drawBounds.getHeight()/2)-(p.y));

            if(!useUserMidOV()) {
                //Now a check that does not allow the drawBounds to be outside of pixelSize, which is technically not known to be wanted by the method caller but since that is me anyways I should be fine:
                double o_x=drawBounds.x;
                double o_y=drawBounds.y;
                if(drawBounds.getWidth()>pixelSize.getWidth()) {
                    if(o_x>0 && o_x+drawBounds.getWidth()<pixelSize.getWidth()) {
                    } else if(o_x>0) {
                        drawBounds.x=0;
                    } else if(o_x+drawBounds.getWidth()<pixelSize.getWidth()) {
                        drawBounds.x=pixelSize.getWidth()-drawBounds.getWidth();
                    }
                } else {
                    if(o_x<0 && o_x+drawBounds.getWidth()>pixelSize.getWidth()) {
                    } else if(o_x<0) {
                        drawBounds.x=0;
                    } else if(o_x+drawBounds.getWidth()>pixelSize.getWidth()) {
                        drawBounds.x=pixelSize.getWidth()-drawBounds.getWidth();
                    }
                }
                if(drawBounds.getHeight()>pixelSize.getHeight()) {
                    if(o_y>0 && o_y+drawBounds.getHeight()<pixelSize.getHeight()) {
                    } else if(o_y>0) {
                        drawBounds.y=0;
                    } else if(o_y+drawBounds.getHeight()<pixelSize.getHeight()) {
                        drawBounds.y=pixelSize.getHeight()-drawBounds.getHeight();
                    }
                } else {
                    if(o_y<0 && o_y+drawBounds.getHeight()>pixelSize.getHeight()) {
                    } else if(o_y<0) {
                        drawBounds.y=0;
                    } else if(o_y+drawBounds.getHeight()>pixelSize.getHeight()) {
                        drawBounds.y=pixelSize.getHeight()-drawBounds.getHeight();
                    }
                }
            }
        } else {
            AEPoint supposedMid = new AEPoint(pixelSize.getWidth()/2 - drawBounds.getWidth()/2, pixelSize.getHeight()/2 - drawBounds.getHeight()/2);
            drawBounds.setLocation((supposedMid.x), ((supposedMid.y)));
        }
        return drawBounds;
    }

    AERect currentDrawBounds=null;
    public AERect getDrawBoundsFor(AnimationObject ib) {
        double drawX = ib.getX()*squareEqualsPixels;
        double drawY = ib.getY()*squareEqualsPixels;
        if(currentDrawBounds!=null) {
            drawX += currentDrawBounds.x;
            drawY += currentDrawBounds.y;
        }
        double drawW = ib.getW()*squareEqualsPixels;
        double drawH = ib.getH()*squareEqualsPixels;
        return new AERect(drawX,drawY,drawW,drawH);
    }

    protected AERect getVisibleDrawArea(AERect drawBounds) {
        double x = drawBounds.x;
        double y = drawBounds.y;
        double width = drawBounds.getWidth();
        double height = drawBounds.getHeight();
        if(x<0) {
            x=0;
            width=drawBounds.getWidth()+drawBounds.x;
        } else if(x>pixelSize.getWidth()) {
            return new AERect();//nothing visible
        }
        if(y<0) {
            y=0;
            height=drawBounds.getHeight()+drawBounds.y;
        } else if(y>pixelSize.getHeight()) {
            return new AERect();//nothing visible
        }

        if(x+width>pixelSize.getWidth()) {
            width=pixelSize.getWidth()-x;
        }
        if(y+height>pixelSize.getHeight()) {
            height=pixelSize.getHeight()-y;
        }
        if(width<0||height<0)
            return new AERect();//nothing visible
        return new AERect(x, y, width, height);
    }
    public AEPoint convertToPixelPoint(AEPoint p) {
//		AERect drawBounds = getDrawBounds(pixelSize);
        double drawX = (p.getX()*squareEqualsPixels) + currentDrawBounds.x;
        double drawY = (p.getY()*squareEqualsPixels) + currentDrawBounds.y;
        return new AEPoint(drawX, drawY);
    }
    public AEPoint convertFromPixelPoint(AEPoint p) {
        return convertFromPixelPoint(p, currentDrawBounds);
    }
    public AEPoint convertFromPixelPoint(AEPoint p, AERect drawBounds) {
        return new AEPoint(((p.getX()-(drawBounds==null?0:drawBounds.x))/squareEqualsPixels), ((p.getY()-(drawBounds==null?0:drawBounds.y))/squareEqualsPixels));
    }
    public AEPoint convertFromScreenPoint(AEPoint p) {
        return convertFromPixelPoint(new AEPoint(p.x-pixelSize.x, p.y-pixelSize.y));
    }
    public AEPoint convertFromScreenPoint(AEPoint p, AERect drawBounds) {
        return convertFromPixelPoint(new AEPoint(p.x-pixelSize.x, p.y-pixelSize.y), drawBounds);
    }
}

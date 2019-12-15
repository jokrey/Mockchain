package jokrey.utilities.animation.pipeline;

import jokrey.utilities.animation.util.AEColor;
import jokrey.utilities.animation.util.AEPoint;
import jokrey.utilities.animation.util.AERect;

public class StandardAODrawer extends AnimationObjectDrawer {
	@Override public boolean canDraw(AnimationObject o, Object param) {
		return param instanceof AEColor;
	}
	@Override public void draw(AnimationObject o, AnimationPipeline pipeline, Object param) {
		//Color colorToDraw = param==null||!canDraw(o, param)? Color.AQUA:Color.rgb(((AEColor)param).getRed(), ((AEColor)param).getGreen(), ((AEColor)param).getBlue());
        if (o.shape_type==AnimationObject.OVAL) {
        	AERect at = pipeline.getDrawBoundsFor(o);
			pipeline.getDrawer().fillOval((AEColor)param, at);
        } else if (o.shape_type==AnimationObject.RECT) {
        	AERect at = pipeline.getDrawBoundsFor(o);
        	pipeline.getDrawer().fillRect((AEColor)param, at);
        } else if (o.shape_type==AnimationObject.LINE) {
        	AEPoint p1 = pipeline.convertToPixelPoint(new AEPoint(o.getX(), o.getY()));
        	AEPoint p2 = pipeline.convertToPixelPoint(new AEPoint(o.getW(), o.getH()));
			pipeline.getDrawer().drawLine((AEColor)param, p1, p2);
        }
	}
}
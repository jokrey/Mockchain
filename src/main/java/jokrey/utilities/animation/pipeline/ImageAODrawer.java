package jokrey.utilities.animation.pipeline;

import jokrey.utilities.animation.util.AEColor;
import jokrey.utilities.animation.util.AEImage;
import jokrey.utilities.animation.util.AERect;

/**
 * TODO: This entire class is useless as is. But may be interesting in the future.
 */
public class ImageAODrawer extends AnimationObjectDrawer {
	@Override public boolean canDraw(AnimationObject o, Object param) {
		return param instanceof AEImage;
	}
	@Override public void draw(AnimationObject o, AnimationPipeline pipeline, Object param) {
		AERect drawB = pipeline.getDrawBoundsFor(o);
		if(param!=null) {
			pipeline.getDrawer().drawImage((AEImage) param, drawB);
		} else {
			pipeline.getDrawer().fillRect(new AEColor(255,0,0,0), drawB);
		}
	}
}
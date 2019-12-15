package jokrey.utilities.animation.pipeline;

public abstract class AnimationObjectDrawer {
	public abstract boolean canDraw(AnimationObject o, Object param);
	public abstract void draw(AnimationObject o, AnimationPipeline pipeline, Object param);
}
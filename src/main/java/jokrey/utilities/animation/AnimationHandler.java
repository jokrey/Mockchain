package jokrey.utilities.animation;

import jokrey.utilities.animation.engine.AnimationEngine;
import jokrey.utilities.animation.pipeline.AnimationPipeline;
import jokrey.utilities.animation.util.AEPoint;
import jokrey.utilities.animation.util.AERect;

import java.util.ConcurrentModificationException;

/**
 * Main Controller interface for the drawer implementations..
 *
 * TODO:
 * - SOUND
 * - DROP TICKS IF TOO FAR BEHIND
 * - Documentation!!!!!
 */
public abstract class AnimationHandler implements Runnable {
	private AnimationEngine engine;
	private AnimationPipeline pipeline;
	public AnimationPipeline getPipeline() {
		return pipeline;
	}
	public AnimationEngine getEngine() {
		return engine;
	}

	public abstract void draw();
	public void start() {
		engine.initiate();
		engine.start();
	}
	public void on() {
		engine.start();
	}
	public void pause() {
		engine.pause();
	}
	public void zoomIn(AEPoint on) {
		zoomTo(on, getPipeline().squareEqualsPixels+getPipeline().squareEqualsPixels*0.02);
	}

	public void zoomOut(AEPoint on) {
		zoomTo(on, getPipeline().squareEqualsPixels-getPipeline().squareEqualsPixels*0.02);
	}

	private void zoomTo(AEPoint on, double squareEqualsPixels) {
		AERect previousDrawBounds = getPipeline().getDrawBounds(engine);
		AEPoint onDrawArea = new AEPoint(on.x-previousDrawBounds.x, on.y-previousDrawBounds.y);

		if(getPipeline().userDrawBoundsMidOverride==null) {
			if (engine.getDrawerMidOverride() == null)
				getPipeline().userDrawBoundsMidOverride = getPipeline().convertFromPixelPoint(new AEPoint(previousDrawBounds.x + previousDrawBounds.getWidth() / 2, previousDrawBounds.y + previousDrawBounds.getHeight() / 2));
			else
				getPipeline().userDrawBoundsMidOverride = engine.getDrawerMidOverride();
		}
		AEPoint midOvBeforeInPixelCoords = getPipeline().convertToPixelPoint(getPipeline().userDrawBoundsMidOverride);

		getPipeline().squareEqualsPixels= squareEqualsPixels;

		AERect afterDrawBounds = getPipeline().getDrawBounds(engine);
		double xDiff = previousDrawBounds.x - afterDrawBounds.x;
		double yDiff = previousDrawBounds.y - afterDrawBounds.y;
		midOvBeforeInPixelCoords = new AEPoint(midOvBeforeInPixelCoords.x - xDiff, midOvBeforeInPixelCoords.y - yDiff);
		getPipeline().userDrawBoundsMidOverride = getPipeline().convertFromPixelPoint(midOvBeforeInPixelCoords);


		int oldWidth = (int) previousDrawBounds.w;
		int oldHeight = (int) previousDrawBounds.h;
		int newWidth = (int) afterDrawBounds.w;
		int newHeight = (int) afterDrawBounds.h;

		double oldRelativeLoc_x = (onDrawArea.getX()/((double)oldWidth/100000000.0));
		double oldRelativeLoc_y = (onDrawArea.getY()/((double)oldHeight/100000000.0));
		double newMouse_x =  (oldRelativeLoc_x*(newWidth/100000000.0));
		double newMouse_y =  (oldRelativeLoc_y*(newHeight/100000000.0));

		double newLocationX = xDiff + (onDrawArea.getX() - newMouse_x );
		double newLocationY = yDiff + (onDrawArea.getY() - newMouse_y );

		getPipeline().userDrawBoundsMidOverride = getPipeline().convertFromPixelPoint(new AEPoint(
				getPipeline().getPixelSize().getWidth()/2 - newLocationX,
				getPipeline().getPixelSize().getHeight()/2 - newLocationY));
	}


	public AnimationHandler(AnimationEngine engineToRun, AnimationPipeline pipeline) {
		engine = engineToRun;
		this.pipeline = pipeline;

		Thread engine_p = new Thread(new Runnable() {
			@Override public void run() {
				getPipeline().squareEqualsPixels=engine.getInitialPixelsPerBox();
				while(engine!=null) {
					try {
						engine.calculate();
						sleep(1);
					} catch(ConcurrentModificationException ex) {
						System.err.println("concs are boring");
					} catch(Exception ex) {
						ex.printStackTrace();
					}
				}
			}
		});
		engine_p.start();
	}

	@Override public void run() {
		while(pipeline !=null) {
			if(getPipeline().canDraw()) {
				draw();
			}
			sleep(1);
		}
	}

	static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void kill() {
		engine=null;
		pipeline =null;
	}
}
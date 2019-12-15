package jokrey.utilities.animation.engine;

import jokrey.utilities.animation.pipeline.AnimationObject;
import jokrey.utilities.animation.util.AEColor;
import jokrey.utilities.animation.util.AEPoint;
import jokrey.utilities.animation.util.AESize;
import jokrey.utilities.animation.util.AE_UTIL;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class LimitRangeMovingAnimationObject extends MovingAnimationObject {
	public AnimationObject explBounds;
	//First Color is the most likely to be picked-50% next 25% next 12.5% etc....
	public LimitRangeMovingAnimationObject(AEPoint p, AnimationObject explBounds, AEColor... c_s) {
		super(p.x, p.y, 0, 0, 0, 0, 0, 0, AnimationObject.OVAL, c_s[0]);
		this.explBounds = explBounds;
		Random rand = new Random();
        double[] v_s = AE_UTIL.angleVelocityToXYVelocity(rand.nextInt(360)-180, (rand.nextInt(400)));
		setV_X(v_s[0]);
		setV_Y(v_s[1]);
		setF_X((v_s[0]+30)*11);
		setF_Y((v_s[1]+30)*11);
//		setF_X(rand.nextInt(100)-50);
//		setF_Y(rand.nextInt(100)-50);
        setW(rand.nextInt(5)+3);
		setH(rand.nextInt(5)+3);
        for (int i = c_s.length-1; i!= 0; i--)if (rand.nextBoolean())drawParam=c_s[i];
	}
	//First Color is the most likely to be picked-50% next 25% next 12.5% etc....
	public LimitRangeMovingAnimationObject(AEPoint p, AnimationObject explBounds, int angle, AEColor... c_s) {
		super(p.x, p.y, 0, 0, 0, 0, 0, 0, AnimationObject.OVAL, c_s[0]);
		this.explBounds = explBounds;
		Random rand = new Random();
        double[] v_s = AE_UTIL.angleVelocityToXYVelocity(angle, (rand.nextInt(400)));
		setV_X(v_s[0]);
		setV_Y(v_s[1]);
		setF_X(rand.nextInt(100)-50);
		setF_Y(rand.nextInt(100)-50);
        setW(rand.nextInt(5)+3);
		setH(rand.nextInt(5)+3);
        for (int i = c_s.length-1; i!= 0; i--)if (rand.nextBoolean())drawParam=c_s[i];
	}
	//First Color is the most likely to be picked-50% next 25% next 12.5% etc....
	public LimitRangeMovingAnimationObject(AEPoint p, AESize size, AnimationObject explBounds, boolean hasAcceleration, int highSpeed, AEColor... c_s) {
		super(p.x, p.y, 0, 0, 0, 0, size.w, size.h, AnimationObject.OVAL, c_s[0]);
		this.explBounds = explBounds;
		Random rand = new Random();
        double[] v_s = AE_UTIL.angleVelocityToXYVelocity(rand.nextInt(360)-180, (rand.nextInt(highSpeed)));
		setV_X(v_s[0]);
		setV_Y(v_s[1]);
		if (hasAcceleration) {
			setF_X(rand.nextInt(100)-50);
			setF_Y(rand.nextInt(100)-50);
		} else {
			setF_X(0);setF_Y(0);
		}
        for (int i = c_s.length-1; i!= 0; i--)if (rand.nextBoolean())drawParam=c_s[i];
	}
    public LimitRangeMovingAnimationObject(AnimationObject explBounds, double x,double y,double vX,double vY,double fX,double fY,int width,int height,int shape_type,AEColor... c_s) {
    	super(x,y,vX,vY,fX,fY,width,height,shape_type,c_s[0]);
    	this.explBounds=explBounds;
    	Random rand = new Random();
        for (int i = c_s.length-1; i!= 0; i--)if (rand.nextBoolean())drawParam=c_s[i];
    }
	public LimitRangeMovingAnimationObject(AEPoint p, AnimationObject explBounds, int angle, double v, double f, AEColor... c_s) {
		super(p.x, p.y, 0, 0, 0, 0, 0, 0, AnimationObject.OVAL, null);
		this.explBounds = explBounds;
		Random rand = new Random();
		double[] v_s = AE_UTIL.angleVelocityToXYVelocity(angle, v);
		setV_X(v_s[0]);
		setV_Y(v_s[1]);
		double[] f_s = AE_UTIL.angleVelocityToXYVelocity(angle, f);
		setF_X(f_s[0]);
		setF_Y(f_s[1]);
		setW(rand.nextInt(5)+3);
		setH(rand.nextInt(5)+3);
		for (int i = c_s.length-1; i!= 0; i--)if (rand.nextBoolean())drawParam=c_s[i];
	}
	public LimitRangeMovingAnimationObject(AEPoint p, AnimationObject explBounds, double v_x, double v_y, AEColor c) {
		super(p.x, p.y, v_x, v_y, 0, 0, 4, 4, AnimationObject.OVAL, c);
		this.explBounds = explBounds;
	}

	public static List<LimitRangeMovingAnimationObject> getExplosion(AEPoint expl_loc, AnimationObject explBounds, int expl_size) {
		List<LimitRangeMovingAnimationObject> explosionAnimationObjects = new ArrayList<>();
	    for (int counter=0;counter!=expl_size;counter++)
	    	explosionAnimationObjects.add(new LimitRangeMovingAnimationObject(expl_loc,new AESize(new Random().nextInt(14)+8, new Random().nextInt(14)+8),explBounds,true,400,AEColor.getRandomColor()));
	    return explosionAnimationObjects;
	}
	public static void startExplosion(List<LimitRangeMovingAnimationObject> explosionAnimationObjects, AEPoint expl_loc, AnimationObject explBounds, int expl_size) {
		explosionAnimationObjects.addAll(getExplosion(expl_loc, explBounds, expl_size));
	}
	public static List<LimitRangeMovingAnimationObject> getExplosion(AEPoint expl_loc, AESize size, AnimationObject explBounds, int expl_size) {
		List<LimitRangeMovingAnimationObject> explosionAnimationObjects = new ArrayList<>();
	    for (int counter=0;counter!=expl_size;counter++)
	    	explosionAnimationObjects.add(new LimitRangeMovingAnimationObject(expl_loc,size,explBounds,true,400,AEColor.getRandomColor()));
	    return explosionAnimationObjects;
	}
	public static void startExplosion(List<LimitRangeMovingAnimationObject> explosionAnimationObjects, int expl_size, AEPoint expl_loc, AESize size, AnimationObject explBounds, AEColor... c_s) {
		explosionAnimationObjects.addAll(getExplosion(expl_size,expl_loc,size,explBounds,true,400,c_s));
	}
	public static void startExplosion(List<LimitRangeMovingAnimationObject> explosionAnimationObjects, int expl_size, AEPoint expl_loc, AESize size, AnimationObject explBounds, int highSpeed, AEColor... c_s) {
		explosionAnimationObjects.addAll(getExplosion(expl_size,expl_loc,size,explBounds,false,highSpeed,c_s));
	}
	public static List<LimitRangeMovingAnimationObject> getExplosion(int expl_size, AEPoint expl_loc, AESize size, AnimationObject explBounds, boolean hasAcceleration, int highSpeed, AEColor... c_s) {
		List<LimitRangeMovingAnimationObject> explosionAnimationObjects = new ArrayList<>();
		for (int counter=0;counter!=expl_size;counter++) explosionAnimationObjects.add(new LimitRangeMovingAnimationObject(expl_loc,size,explBounds,hasAcceleration,highSpeed,c_s));
	    return explosionAnimationObjects;
	}
	public static void startExplosion(List<LimitRangeMovingAnimationObject> explosionAnimationObjects, int expl_size, AEPoint expl_loc, AnimationObject explBounds, AEColor... c_s) {
		explosionAnimationObjects.addAll(getExplosion(expl_size, expl_loc, explBounds, c_s));
	}
	public static List<LimitRangeMovingAnimationObject> getExplosion(int expl_size, AEPoint expl_loc, AnimationObject explBounds, AEColor... c_s) {
		List<LimitRangeMovingAnimationObject> explosionAnimationObjects = new ArrayList<>();
		for (int counter=0;counter!=expl_size;counter++) explosionAnimationObjects.add(new LimitRangeMovingAnimationObject(expl_loc,explBounds,c_s));
	    return explosionAnimationObjects;
	}
	public static void startExplosion(List<LimitRangeMovingAnimationObject> explosionAnimationObjects, int expl_size, AEPoint expl_loc, AnimationObject explBounds, int angle, AEColor... c_s) {
		explosionAnimationObjects.addAll(getExplosion(expl_size, expl_loc, explBounds, angle, c_s));
	}
	public static List<LimitRangeMovingAnimationObject> getExplosion(int expl_size, AEPoint expl_loc, AnimationObject explBounds, int angle, AEColor... c_s) {
		List<LimitRangeMovingAnimationObject> explosionAnimationObjects = new ArrayList<>();
		for (int counter=0;counter!=expl_size;counter++) explosionAnimationObjects.add(new LimitRangeMovingAnimationObject(expl_loc,explBounds,angle,c_s));
	    return explosionAnimationObjects;
	}
	public static void startExplosion(List<LimitRangeMovingAnimationObject> particles, AEPoint expl_loc, AnimationObject explBounds, int expl_size, AEColor clr) {
		particles.addAll(getExplosion(expl_size, expl_loc, explBounds, clr));
	}

	public static void moveExplosionAnimationObjects(Iterator<LimitRangeMovingAnimationObject> explosionAnimationObjects_iter, boolean useGravity, int ticksPerSecond) {
	    while (explosionAnimationObjects_iter.hasNext()) {
	    	LimitRangeMovingAnimationObject explP = explosionAnimationObjects_iter.next();
	    	if(useGravity)explP.applyGravityToVy();
	        explP.move(ticksPerSecond);
	        if (explP.explBounds.isRect() && !AnimationObject.intersect(explP, explP.explBounds) || !AnimationObject.collision(explP, explP.explBounds))
	            explosionAnimationObjects_iter.remove();
	    }
	}
	public static void moveExplosionAnimationObjects(Iterator<LimitRangeMovingAnimationObject> explosionAnimationObjects_iter, double gravitation, int ticksPerSecond) {
	    while (explosionAnimationObjects_iter.hasNext()) {
	    	LimitRangeMovingAnimationObject explP = explosionAnimationObjects_iter.next();
	    	explP.setF_Y(gravitation);
	        explP.move(ticksPerSecond);
	        if (explP.explBounds.isRect() && !AnimationObject.intersect(explP, explP.explBounds) || !AnimationObject.collision(explP, explP.explBounds))
	            explosionAnimationObjects_iter.remove();
	    }
	}

	public static AnimationObject getExplosionBnds(AEPoint explLoc, int explRadius, int shape_type) {
		return new AnimationObject(explLoc.x-explRadius, explLoc.y-explRadius, explRadius*2, explRadius*2, shape_type);
	}
	public static AnimationObject getExplosionBnds(AEPoint explLoc, int w, int h, int shape_type) {
		return new AnimationObject(explLoc.x-w/2, explLoc.y-h/2, w, h, shape_type);
	}
}
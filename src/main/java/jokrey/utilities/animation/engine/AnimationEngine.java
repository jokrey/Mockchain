package jokrey.utilities.animation.engine;

import jokrey.utilities.animation.pipeline.AnimationObject;
import jokrey.utilities.animation.util.AEPoint;
import jokrey.utilities.animation.util.AESize;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class AnimationEngine extends HandelableEngine {
//	private CopyOnWriteArrayList<AnimationObject> objects = Collections.synchronizedList(new ArrayList<AnimationObject>());
	private CopyOnWriteArrayList<AnimationObject> objects = new CopyOnWriteArrayList<>();
	public final AnimationObject getObjectAt(int index) {
		if(index >= 0 && index < objects.size())
			return objects.get(index);
		return null;
	}
	public final void initiateObjectAt(int index, AnimationObject ao) {
		while(objects.size()<=index)
			objects.add(null);
		objects.set(index, ao);
	}
	public final void initiateNewObject(AnimationObject ao) {
		objects.add(ao);
	}
	public final void clearObjects() {
		objects.clear();
	}
	public final List<AnimationObject> getObjectsInRange(int i1, int i2) {
		return objects.subList(i1, i2);
	}
	public final void removeObjectAt(int index) {
		if(index >= 0 || index < objects.size())
			objects.remove(index);
	}
	public final int getObjectsCount() {
		return objects.size();
	}
	public final List<AnimationObject> getAllObjects() {
		return objects;
	}

	//to be overridden if felt neccs
	public List<AnimationObject> getAllObjectsToDraw() {
		return getAllObjects();
	}

	public abstract AESize getVirtualBoundaries();

	public final int getVirtualLimit_width() {return (int) getVirtualBoundaries().getWidth();}
	public final int getVirtualLimit_height() {return (int) getVirtualBoundaries().getHeight();}
	public final int getLimitPerc_W(double percentage) {return (int) (getVirtualLimit_width()*(percentage/100));}
	public final int getLimitPerc_H(double percentage) {return (int) (getVirtualLimit_height()*(percentage/100));}
    public double getInitialPixelsPerBox() {return -1;}
    public AEPoint getDrawerMidOverride(){return null;}
    public void mouseClicked(int mouseCode) {}
	public void locationInputChanged(AEPoint p, boolean mousePressed) {}

	ArrayList<Character> pressedKeys = new ArrayList<>();
	ArrayList<Integer> pressedKeyCodes = new ArrayList<>();
    public void keyPressed(char keyChar, int key_code) {
		if(!pressedKeys.contains(keyChar))	pressedKeys.add(keyChar);
		if(!pressedKeyCodes.contains(key_code))	pressedKeyCodes.add(key_code);
    }
    public void keyReleased(char keyChar, int key_code) {
		pressedKeys.remove(keyChar);
		pressedKeyCodes.remove(key_code);
    }
	public boolean isKeyPressed(char keyChar) {
		return pressedKeys.contains(keyChar);
	}
	public boolean isKeyPressed(int key_code) {
		return pressedKeyCodes.contains(key_code);
	}

//	private ArrayList<Integer> pressedKeys = new ArrayList<>();
//    public final void setKeyPressed(int key) {
//    	pressedKeys.remove((Integer)key);
//    	pressedKeys.add(key);
//    }
//    public final void setKeyNotPressed(int key) {
//    	pressedKeys.remove((Integer)key);
//    }
//	public final boolean isKeyPressed(int keyCode) {
//		return pressedKeys.contains(keyCode);
//	}

//    private Rectangle frameBounds=new Rectangle();
//    public final void setFrameBounds(Rectangle frameBounds) {
//    	if(frameBounds!=null)
//    		this.frameBounds=frameBounds;
//    }
//    public final Rectangle getFrameBounds() {return frameBounds;}
//    public final Dimension getDrawSize() {return frameBounds.getSize();}

//    public final Point convertFromPixelPoint(MouseInfo.getPointerInfo().getLocation()) {
//        Point mouseLoc = MouseInfo.getPointerInfo().getLocation();
//        return new Point(mouseLoc.x-frameBounds.x, mouseLoc.y-frameBounds.y);
//    }
//    public final boolean isMouseOnAnimation() {
//    	return frameBounds.contains(MouseInfo.getPointerInfo().getLocation());
//    }

//    public static AnimationEngine getAnimationByName_or_Type(String name) {
//        AnimationEngine[] animations = getAnimations();
//        if(UTIL.getInt(name, -1)!=-1)
//        	return animations[UTIL.getInt(name)];
//        else
//            for(int i=0;i<animations.length;i++)
//            	if(name.equalsIgnoreCase(animations[i].getName()))
//            		return animations[i];
//        return null;
//    }
//    public static String[] getAnimationNames() {
//        AnimationEngine[] animations = getAnimations();
//        String[] animationNames = new String[animations.length];
//        for(int i=0;i<animations.length;i++)
//        	animationNames[i]=animations[i].getName();
//    	return animationNames;
//    }
//    public static ArrayList<String> getAnimationsList() {
//        ArrayList<String> background_names = new ArrayList<>();
//        background_names.add("::::Animations::::");
//        background_names.add(":nr_shortcut:(:animationName:) + :mandatoryParam: [+ :optionalParam:]: :animationDescription:");
//        background_names.add("");
//        AnimationEngine[] animations = getAnimations();
//        for(int i=0;i<animations.length;i++)
//            background_names.add(i+" - "+animations[i].getName());
//        return background_names;
//    }
//    public static AnimationEngine[] getAnimations() {
//    	return new AnimationEngine[] {
//    	};
//    }

//    public static AnimationEngine getAnimation_loadingScreen() {
//        return new AnimationEngine() {
//	        @Override public String getName() {return "loadingScreen";}
//            @Override public AERect getVirtualBoundaries() {return new AERect(drawer.getVisibleS()); }
//
//			@Override protected void calculateTick() {
//		    	if(UTIL.isEven(Calendar.getInstance().get(Calendar.SECOND)) && getObjectsCount()<2500)
//		    		initiateNewObject(new MovingAnimationObject(getVirtualLimit_width()/4, getVirtualLimit_height()/4, getVirtualLimit_width()/4, 0, 0, 0, 2, 2, AnimationObject.OVAL, Color.GREEN));
//		    	AEPoint midP = new AEPoint(getVirtualLimit_width()/2, getVirtualLimit_height()/2);
//		        for(AnimationObject p:getAllObjects()) {
//		            double[] a_s = AE_UTIL.angleVelocityToXYVelocity(AE_UTIL.getAngle(p.getMidAsPoint(), midP), getVirtualLimit_width());
//		            ((MovingAnimationObject) p).setF_X(a_s[0]);
//		            ((MovingAnimationObject) p).setF_Y(a_s[1]);
//		            ((MovingAnimationObject) p).move(getTicksPerSecond());
//		        }
//			}
//			@Override public void initiate() {
//				clearObjects();
//			}
//        };
//	}
}
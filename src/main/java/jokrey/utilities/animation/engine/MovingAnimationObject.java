package jokrey.utilities.animation.engine;

import jokrey.utilities.animation.pipeline.AnimationObject;
import jokrey.utilities.animation.util.AERect;
import jokrey.utilities.animation.util.AEVector;
import jokrey.utilities.animation.util.AE_UTIL;

import java.util.List;

public class MovingAnimationObject extends AnimationObject {
    public static final double g = -9.80665;

    public AEVector v = new AEVector(2);
		public void setV_X(double vx) {v.set(AEVector.X, vx);}
		public double getV_X() {return v.get(AEVector.X);}
		public void setV_Y(double vy) {v.set(AEVector.Y, vy);}
		public double getV_Y() {return v.get(AEVector.Y);}
    public AEVector f = new AEVector(2);
		public void setF_X(double fx) {f.set(AEVector.X, fx);}
		public double getF_X() {return f.get(AEVector.X);}
		public void setF_Y(double fy) {f.set(AEVector.Y, fy);}
		public double getF_Y() {return f.get(AEVector.Y);}

	public double getA_X() {return AEVector.divideScalar_getVector(f, virtualMass).get(AEVector.X);}
	public double getA_Y() {return AEVector.divideScalar_getVector(f, virtualMass).get(AEVector.Y);}
    public double virtualMass = 1;
    @Override public boolean equals(Object o) {
        if (o instanceof MovingAnimationObject) {
            MovingAnimationObject op = (MovingAnimationObject)o;
            return
            getX() == op.getX() &&
            getY() == op.getY() &&
            getW() == op.getW() &&
            getH() == op.getH() &&
            v.equals(op.v)&&
            f.equals(op.f)&&
            shape_type==op.shape_type&&
            drawParam.equals(op.drawParam);
        } else
            return false;
    }

    public MovingAnimationObject(double x,double y,double vX,double vY,double fX,double fY,double width,double height,int shape_type,Object drawParam) {
        super(x, y, width, height, shape_type);
        v.set(AEVector.X,vX);
        v.set(AEVector.Y,vY);
        f.set(AEVector.X,fX);
        f.set(AEVector.Y,fY);
        this.drawParam=drawParam;
    }

//MOVING MOVING MOVING MOVING MOVING MOVING MOVING MOVING MOVING MOVING MOVING
    public void applyGravityToVy() {
        setV_Y(getV_Y() - g);
    }
    //F = FG - FL = mÂ·g âˆ’ Â½Â·cwÂ·AÂ·Ï�Â·v2
    //cw=0.45 <<StrÃ¶mungswiderstandskoeffizient beim kreis
    //p=1.2041 <<Luftdichte
    //A= <<QuerschnittsflÃ¤che (FlÃ¤cheninhalt der 2D entsprechung des Objects)
    //AndAcceleration
//    public void applyAirFrictionAndAccelerationToV(long delta) {
//    	double v_angle = AE_UTIL.getAngle(getV_X(), getV_Y());
//
//    	double cW = 0;
//    	double A = 0;
//    	double p = 1.2041;
//    	if (isOval()) {
//    		cW = 0.45;//standard StrÃ¶mungswiderstandskoeffizient beim kreis
//    		A = Math.PI * ((getW()/2)*(getH()/2));
//    	} else if (isRect()) {
//    		cW = 0.7;//geraten
//    		A = getW()*getH();
//    	} else {
//    		cW = 1;//geraten
//    		A = getW()*getH();
//    	}
//    	Vector fL_vctr = new Vector(2);
//    	double fL_sclr = 0.5 * cW * A * p * Vector.timesVector_getScalar(v, v);
//    	System.out.println("fL_sclr: "+fL_sclr);
//    	System.out.println("v^2: "+Vector.timesVector_getScalar(v, v));
//    	System.out.println("pos: "+getX()+", "+getY());
//    	fL_vctr.set(AE_UTIL.angleVelocityToXYVelocity(v_angle, -(fL_sclr/1000)));
//
////    	Vector aL = Vector.divideScalar_getVector(fL_vctr, virtuallMass);
////        v = Vector.plusVector_getVector(v, Vector.timesScalar_getVector(aL, delta / 1e9));
//
////    	Vector a = Vector.divideScalar_getVector(f, virtuallMass);
////        v = Vector.plusVector_getVector(v, Vector.timesScalar_getVector(a, delta / 1e9));
//
//        Vector f_ges = Vector.plusVector_getVector(f, fL_vctr);
//    	Vector a = Vector.divideScalar_getVector(f_ges, virtualMass);
//        v = Vector.plusVector_getVector(v, Vector.timesScalar_getVector(a, delta / 1e9));
//
////    	System.out.println("fw: "+fw);
//////    	fw/=1000;
//////    	double fw_setY(0.5*cW*A*p*(vY*vY);
//////    	AE_UTIL.angleVelocityToXYVelocity(AE_UTIL.getAngle(vX, vY), fw);
////    	double fw_setX(Math.cos(Math.toRadians(v_angle))*fw;
////    	double fw_setY(Math.sin(Math.toRadians(v_angle))*fw;
////    	double f_total_setX(vX<0?fX+fw_x:
////								fX-fw_x;
////    	double f_total_setY(vY<0?fX+fw_y:
////    							fX-fw_y;
//////    	if ((fX>0&&fw_x>fX) || (fX<0&&fw_x<fX))
//////    		f_total_setX(0;
//////    	if ((fY>0&&fw_y>fY) || (fY<0&&fw_y<fY))
//////    		f_total_setY(0;
////
////    	System.out.println("fw_x: "+fw_x);
//////    	System.out.println("fX: "+fX);
////    	System.out.println("f_total_x: "+f_total_x);
//////    	if ((vX > 0 && f_total_x<0) || (vX < 0 && f_total_x>0))f_total_x=0;
//////    	if ((vY > 0 && f_total_y<0) || (vY < 0 && f_total_y>0))f_total_y=0;
////    	double a_setX(f_total_x/virtuallMass;
////    	double a_setY(f_total_y/virtuallMass;
////    	System.out.println("a_x: "+a_x);
////    	System.out.println("vX old: "+vX);
//////    	System.out.println("-(0.5*cW*A*p*(vX*vX)): "+(-(0.5*cW*A*p*(vX*vX))));
////    	setV_X(getV_X() + a_x;
////    	setV_Y(getV_Y() + a_y;
////    	System.out.println("vX new: "+vX);
//////    	if ((a_x > 0 && vX > 0) || (a_x < 0 && vX < 0)) setV_X(0;
//////    	if ((a_y > 0 && vY > 0) || (a_y < 0 && vY < 0)) setV_Y(0;
////    	System.out.println("vX new new: "+vX);
////    	System.out.println("");
//    }
//    public void applyAccelerationToV(long delta) {
//    	Vector a = Vector.divideScalar_getVector(f, virtualMass);
//        v = Vector.plusVector_getVector(v, Vector.timesScalar_getVector(a, delta / 1e9));
//    }
//
//    public void applySpeedToXY(long delta) {
//        pos = Vector.plusVector_getVector(pos, Vector.timesScalar_getVector(v, delta / 1e9));
//    }

    public void move(int ticksPerSecond) {
    	AEVector a = AEVector.divideScalar_getVector(f, virtualMass);
        v = AEVector.plusVector_getVector(v, AEVector.timesScalar_getVector(a, 1.0/ticksPerSecond));

        AEVector pos = AEVector.plusVector_getVector(new AEVector(getX(), getY()), AEVector.timesScalar_getVector(v, 1.0/ticksPerSecond));
        setX(pos.get(AEVector.X));
        setY(pos.get(AEVector.Y));
    }
//    public void move(int angle) {
//        setV_X(getV_X() + (getF_X()/virtualMass));
//
//        setX(getX()+getV_X()* Math.cos(Math.toRadians(angle)));
//        setY(getY()+getV_X()* Math.sin(Math.toRadians(angle)));
//    }
//    public void move(int angle, double v, double a) {
//        setX(getX()+v* Math.cos(Math.toRadians(angle)));
//        setY(getY()+v* Math.sin(Math.toRadians(angle)));
//    }
    
//BOUNCING BOUNCING BOUNCING BOUNCING BOUNCING BOUNCING BOUNCING BOUNCING
    public boolean bounceOfRect(MovingAnimationObject p, double restitution) {
        return bounceOfRect(p, restitution, 0, false);
    }

    public boolean bounceOfRect(MovingAnimationObject rect, double restitution, int angleChange, boolean randomAngleChange) {
        if (!rect.isRect()) return false;
        AERect i = getBounds().intersection(rect.getBounds());
        if (!i.isEmpty()) {
            double leftOverlap=getX()+getW()-rect.getX();//find out how much rect1 overlaps rect2 on each side
            double rightOverlap=rect.getX()+rect.getW()-getX();
            double topOverlap=getY()+getH()-rect.getY();
            double botOverlap=rect.getY()+rect.getH()-getY();

            double smallestOverlap=Double.MAX_VALUE;//we want to know which side has the smallest overlap.  
            double shiftX=0;//we'll scootch (shift) rect1 over or up/down/left/right enough to eliminate the smallest overlap.
            double shiftY=0;
            boolean bouncend = false;

            if(leftOverlap<smallestOverlap) {
                smallestOverlap=leftOverlap;
                shiftX=-leftOverlap;//scoot to the left enough that it's no longer overlapping
                shiftY=0;
                setV_X(getV_X()+rect.getV_X()/2);
                setV_X(getV_X()*restitution);
                setV_X(getV_X()>0?-getV_X():getV_X());
                bouncend=true;
            }
            if(rightOverlap<smallestOverlap) {
                smallestOverlap=rightOverlap;
                shiftX=rightOverlap;//scoot to the right enough that it's no longer overlapping
                shiftY=0;
                setV_X(getV_X()-rect.getV_X()/2);
                setV_X(getV_X()*restitution);
                setV_X(getV_X()<0?-getV_X():getV_X());
                bouncend=true;
            }
            if(topOverlap<smallestOverlap) {
                smallestOverlap=topOverlap;
                shiftX=0;
                shiftY=-topOverlap;//scoot up enough that it's no longer overlapping
                setV_Y(getV_Y()+rect.getV_Y()/2);
                setV_Y(getV_Y()*restitution);
                setV_Y(getV_Y()>0?-getV_Y():getV_Y());
                bouncend=true;
            }
            if(botOverlap<smallestOverlap) {
                smallestOverlap=botOverlap;
                shiftX=0;
                shiftY=botOverlap;//scoot down enough that it's no longer overlapping
                setV_Y(getV_Y()-rect.getV_Y()/2);
                setV_Y(getV_Y()*restitution);
                setV_Y(getV_Y()<0?-getV_Y():getV_Y());
                bouncend=true;
            }
            if (bouncend) {
                if(randomAngleChange) {
                    double old_angle = AE_UTIL.getAngle(getV_X(), getV_Y());
                    old_angle+=AE_UTIL.getRandomNr(-angleChange, angleChange);
                    double[] v_s = AE_UTIL.angleVelocityToXYVelocity(old_angle, Math.sqrt(getV_X()*getV_X()+getV_Y()*getV_Y()));
                    setV_X(v_s[0]);
                    setV_Y(v_s[1]);
                } else {
                    double old_angle = AE_UTIL.getAngle(getV_X(), getV_Y());
                    old_angle+=angleChange;
                    double[] v_s = AE_UTIL.angleVelocityToXYVelocity(old_angle, Math.sqrt(getV_X()*getV_X()+getV_Y()*getV_Y()));
                    setV_X(v_s[0]);
                    setV_Y(v_s[1]);
                }
            }

            setX(getX()+shiftX);//scoot rect1 so it's no longer overlapping rect2
            setY(getY()+shiftY);
            return true;
        }
        return false;
    }

    public boolean bounceCircleOfCircle(MovingAnimationObject o, double restitution) {
        if(collision(this, o)) {
            System.out.println();
            System.out.println("----OLD----");
            System.out.println("vX: "+getV_X());
            System.out.println("vY: "+getV_Y());
            double m1 = 1; //mass object 1
            double m2 = 1; //mass object 2
            double mAngle1 = AE_UTIL.getAngle(getV_X(), getV_Y()); //movement Angle object 1
            double mAngle2 = AE_UTIL.getAngle(o.getV_X(), o.getV_Y()); //movement Angle object 2
            double contactAngle = AE_UTIL.getAngle(getMid(), o.getMid()); //contact Angle ???????
           // double v1 = Math.sqrt(vX*vX + vY*vY);//scalar size of vX and vY dependenciesFrom object 1
            //double v2 = Math.sqrt(o.vX*o.vX + o.vY*o.vY);//scalar size of vX and vY dependenciesFrom object 2
                          double v1 = getV_X()*getV_Y()*Math.cos(mAngle1);//scalar size of vX and vY dependenciesFrom object 1 ???????
                          double v2 = o.getV_X()*o.getV_Y()*Math.cos(mAngle2);//scalar size of vX and vY dependenciesFrom object 2 ???????
                   //       double v = Math.sqrt
            System.out.println("----Test----");
            System.out.println("v1: "+v1);
            System.out.println("v2: "+v2);

            setV_X(((v1*Math.cos(mAngle1-contactAngle)*(m1-m2)+2*m2*v2*Math.cos(mAngle2-contactAngle))
                /m1+m2)                                                     *Math.cos(contactAngle)
            +v1*Math.sin(mAngle1-contactAngle)*Math.cos(contactAngle+(Math.PI/2)));
            setV_X(getV_X()*restitution);

            setV_Y(((v1*Math.cos(mAngle1-contactAngle)*(m1-m2)+2*m2*v2*Math.cos(mAngle2-contactAngle))
                /m1+m2)                                                     *Math.sin(contactAngle)
            +v1*Math.sin(mAngle1-contactAngle)*Math.sin(contactAngle+(Math.PI/2)));
            setV_Y(getV_Y()*restitution);
            System.out.println("----NEW----");
            System.out.println("vX: "+getV_X());
            System.out.println("vY: "+getV_Y());
            System.out.println();
            return true;
        }
        return false;
    }
    //https://github.com/I82Much/BouncingBall/blob/master/src/boundingball/Main.java#L33
    public boolean computeBoxBounce(AERect bnds, double restitution, double friction) {
        return computeBoxBounce(bnds, restitution, friction, 0, false);
    }

    public boolean computeBoxBounce(AERect bnds, double restitution, double friction, int angleChange, boolean randomAngleChange) {
        int maxY = (int) (bnds.getHeight() - (int)getH());
        int maxX = (int) (bnds.getWidth() - (int)getW());
        // Ball is out of bounds in Y dimension
        if (overlapingBoundsBottom(bnds.getHeight())) {
            setY(maxY);
            setV_Y(-restitution * getV_Y());
            if(randomAngleChange) {
                double old_angle = AE_UTIL.getAngle(getV_X(), getV_Y());
                old_angle+=AE_UTIL.getRandomNr(-angleChange, angleChange);
                double[] v_s = AE_UTIL.angleVelocityToXYVelocity(old_angle, Math.sqrt(getV_X()*getV_X()+getV_Y()*getV_Y()));
                setV_X(v_s[0]);
                setV_Y(v_s[1]);
            } else {
                double old_angle = AE_UTIL.getAngle(getV_X(), getV_Y());
                old_angle+=angleChange;
                double[] v_s = AE_UTIL.angleVelocityToXYVelocity(old_angle, Math.sqrt(getV_X()*getV_X()+getV_Y()*getV_Y()));
                setV_X(v_s[0]);
                setV_Y(v_s[1]);
            }
            return true;
        } else if (overlapingBoundsTop()) {
            setY(0);
            setV_Y(-restitution * getV_Y());
            if(randomAngleChange) {
                double old_angle = AE_UTIL.getAngle(getV_X(), getV_Y());
                old_angle+=AE_UTIL.getRandomNr(-angleChange, angleChange);
                double[] v_s = AE_UTIL.angleVelocityToXYVelocity(old_angle, Math.sqrt(getV_X()*getV_X()+getV_Y()*getV_Y()));
                setV_X(v_s[0]);
                setV_Y(v_s[1]);
            } else {
                double old_angle = AE_UTIL.getAngle(getV_X(), getV_Y());
                old_angle+=angleChange;
                double[] v_s = AE_UTIL.angleVelocityToXYVelocity(old_angle, Math.sqrt(getV_X()*getV_X()+getV_Y()*getV_Y()));
                setV_X(v_s[0]);
                setV_Y(v_s[1]);
            }
            return true;
        }
        // Ball is out of bounds in X dimension
        if (overlapingBoundsRight(bnds.getWidth())) {
            setX(maxX);
            setV_X(-restitution * getV_X());
            if(randomAngleChange) {
                double old_angle = AE_UTIL.getAngle(getV_X(), getV_Y());
                old_angle+=AE_UTIL.getRandomNr(-angleChange, angleChange);
                double[] v_s = AE_UTIL.angleVelocityToXYVelocity(old_angle, Math.sqrt(getV_X()*getV_X()+getV_Y()*getV_Y()));
                setV_X(v_s[0]);
                setV_Y(v_s[1]);
            } else {
                double old_angle = AE_UTIL.getAngle(getV_X(), getV_Y());
                old_angle+=angleChange;
                double[] v_s = AE_UTIL.angleVelocityToXYVelocity(old_angle, Math.sqrt(getV_X()*getV_X()+getV_Y()*getV_Y()));
                setV_X(v_s[0]);
                setV_Y(v_s[1]);
            }
            return true;
        } else if (overlapingBoundsLeft()) {
            setX(0);
            setV_X(-restitution * getV_X());
            if(randomAngleChange) {
                double old_angle = AE_UTIL.getAngle(getV_X(), getV_Y());
                old_angle+=AE_UTIL.getRandomNr(-angleChange, angleChange);
                double[] v_s = AE_UTIL.angleVelocityToXYVelocity(old_angle, Math.sqrt(getV_X()*getV_X()+getV_Y()*getV_Y()));
                setV_X(v_s[0]);
                setV_Y(v_s[1]);
            } else {
                double old_angle = AE_UTIL.getAngle(getV_X(), getV_Y());
                old_angle+=angleChange;
                double[] v_s = AE_UTIL.angleVelocityToXYVelocity(old_angle, Math.sqrt(getV_X()*getV_X()+getV_Y()*getV_Y()));
                setV_X(v_s[0]);
                setV_Y(v_s[1]);
            }
            return true;
        }
        // ball is rolling along the bottom
        if (getY() == maxY || getY() == 0) setV_X(friction * getV_X());
        if (getX() == maxX || getX() == 0) setV_Y(friction * getV_Y());
        return false;
    }

    public boolean computeInsideBoxStop(AERect box) {
		boolean didSomeStopping = false;
        int maxY = (int) (box.getHeight() - (int)getH());
        int maxX = (int) (box.getWidth() - (int)getW());
        // Ball is out of bounds in Y dimension
        if (getY() > maxY) {
            setY(maxY);
            setV_Y(0);
            didSomeStopping=true;
        } else if (getY() < 0) {
        	setY(0);
            setV_Y(0);
            didSomeStopping=true;
        }
        // Ball is out of bounds in X dimension
        if (getX() > maxX) {
            setX(maxX);
            setV_X(0);
            didSomeStopping=true;
        } else if (getX() < 0) {
            setX(0);
            setV_X(0);
            didSomeStopping=true;
        }
        return didSomeStopping;
    }

	public boolean computeStops(List<? extends MovingAnimationObject> loadables) {
		boolean didSomeStoping = false;
        for(MovingAnimationObject mr:loadables) {
    		if(getBounds().intersects(mr.getBounds())) {
    			double leftOverlap=getX()+getW()-mr.getX();
    			double rightOverlap=mr.getX()+mr.getW()-getX();
    			double topOverlap=getY()+getH()-mr.getY();
    			double botOverlap=mr.getY()+mr.getH()-getY();

    			double smallestOverlap=Double.MAX_VALUE;
    			double shiftX=0;
    			double shiftY=0;

    			int l_r_t_b = -1;
    			if(leftOverlap<smallestOverlap) {
    				smallestOverlap=leftOverlap;
    				shiftX=-leftOverlap;
    				shiftY=0;
    				l_r_t_b=0;
    			}if(rightOverlap<smallestOverlap) {
    				smallestOverlap=rightOverlap;
    				shiftX=rightOverlap;
    				shiftY=0;
    				l_r_t_b=1;
    			}if(topOverlap<smallestOverlap) {
    				smallestOverlap=topOverlap;
    				shiftX=0;
    				shiftY=-topOverlap;
    				l_r_t_b=2;
    			}if(botOverlap<smallestOverlap) {
    				smallestOverlap=botOverlap;
    				shiftX=0;
    				shiftY=botOverlap;
    				l_r_t_b=3;
    			}

    			if(l_r_t_b==0 || l_r_t_b==1) {
    				setV_X(0);
//        				double oldply_vx=player_1.getV_X();
//        				player_1.setV_X(mr.getV_X());
//    			    	mr.setV_X(oldply_vx);
//        				if(Math.abs(mr.getV_X())<=22)
//            				mr.setV_X(mr.getV_X()<0?-22:22);
    			} else if(l_r_t_b==2 || l_r_t_b==3) {
    				setV_Y(0);
    				if(getV_X()<0)
    					setF_X(66);
    				if(getV_X()>0)
    					setF_X(-66);
    			} else
	                setF_X(0);

    			if(l_r_t_b!=-1)didSomeStoping = true;

    			setX(getX()+shiftX);
    			setY(getY()+shiftY);
    		}
        }
        return didSomeStoping;
	}
	
	public MovingAnimationObject getClone() {
		return new MovingAnimationObject(getX(), getY(), getV_X(), getV_Y(), getF_X(), getF_Y(), getW(), getH(), shape_type, drawParam);
	}
}
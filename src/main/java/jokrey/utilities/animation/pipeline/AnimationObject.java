package jokrey.utilities.animation.pipeline;

import jokrey.utilities.animation.engine.MovingAnimationObject;
import jokrey.utilities.animation.util.AEPoint;
import jokrey.utilities.animation.util.AERect;
import jokrey.utilities.animation.util.AEVector;
import jokrey.utilities.animation.util.AE_UTIL;

import java.util.ArrayList;
import java.util.List;

public class AnimationObject {
    private AEVector pos = new AEVector(2);
    	public void setX(double x) {pos.set(AEVector.X, x);}
		public double getX() {return pos.get(AEVector.X);}
    	public void setY(double y) {pos.set(AEVector.Y, y);}
		public double getY() {return pos.get(AEVector.Y);}
	private AEVector dim = new AEVector(2);
		public void setW(double w) {dim.set(AEVector.X, w);}
		public double getW() {return dim.get(AEVector.X);}
		public void setH(double h) {dim.set(AEVector.Y, h);}
		public double getH() {return dim.get(AEVector.Y);}

    public AEPoint getLoc() {
        return new AEPoint((int)getX(), (int)getY());
    }
    public AEPoint getMid() {
        return new AEPoint(getX()+getW()/2, getY()+getH()/2);
    }
    public void setMid(AEPoint p) {
    	setX(p.x - getW()/2);
    	setY(p.y - getH()/2);
    }
    public AERect getBounds() {
    	if (isLine()) {
    		AEPoint point1 = new AEPoint((int)getX(), (int)getY());
    		AEPoint point2 = new AEPoint((int)getW(), (int)getH());
    		AERect rect= new AERect(point1, point2);
    		return rect;
    	} else
    		return new AERect((int)getX(), (int)getY(), (int)getW(), (int)getH());
    }

    public Object drawParam=null;

    public int shape_type = OVAL;
	    public static final int OVAL = 0;
		    public boolean isOval() {
		        return shape_type == OVAL;
		    }
		    public boolean isCircle() {
		        return shape_type == OVAL && getW() == getH();
		    }
	    public static final int RECT = 1;
		    public boolean isRect() {
		        return shape_type == RECT;
		    }
	    public static final int LINE = 2;
		    public boolean isLine() {
		        return shape_type == LINE;
		    }
//	        public static final int triangle = 2;
//	      public boolean isTriangle() {
//	          return shape_type == triangle;
//	      }

	public AnimationObject(int shape_type) {
		this.shape_type = shape_type;
	}
	public AnimationObject(double x, double y, double w, double h, int shape_type) {
		pos.set(x,y);
		dim.set(w,h);
		this.shape_type = shape_type;
	}
	public AnimationObject(double x, double y, double w, double h, int shape_type, Object drawParam) {
		pos.set(x,y);
		dim.set(w,h);
		this.shape_type = shape_type;
		this.drawParam=drawParam;
	}
	public AnimationObject(AERect r, int shape_type) {
		pos.set(r.x,r.y);
		dim.set(r.getWidth(),r.getHeight());
		this.shape_type = shape_type;
	}
	public AnimationObject(AERect r, int shape_type, Object drawParam) {
		if(r!=null) {
			pos.set(r.x,r.y);
			dim.set(r.getWidth(),r.getHeight());
		}
		this.shape_type = shape_type;
		this.drawParam=drawParam;
	}

//BOUNDS RESETING (make sure no collision is occuring) (partially dependenciesFrom https://www.getY()outube.com/watch?v=JIXhCvXgjsQ))
    public static boolean resetBoundsOutOf(AnimationObject p, AnimationObject o) {
//		if (p.isCircle()) {
//			if (o.isCircle()) 	 return resetOvalOutOfOval(p, o);
//		} 
		if (p.isOval()) {
			if (o.isOval()) 	 return resetOvalOutOfOval(p, o);  //NOT SUPPORTING ANGLE
			else
				if (o.isRect()) return resetOvalOutOfRect(p, o);  //NOT SUPPORTING ANGLE
		} 
		if (p.isRect()) {
			if (o.isOval())  return resetRectOutOfOval(p, o);  				  //NOT SUPPORTING ANGLE
			else if (o.isRect()) return resetRectOutOfRect(p, o);  //NOT SUPPORTING ANGLE
		}
		return false;
    }
    public static boolean resetRectOutOfRect(AnimationObject rect1, AnimationObject rect2) {
		if(rect1.getBounds().intersects(rect2.getBounds())) {
			double leftOverlap=rect1.getX()+rect1.getW()-rect2.getX();
			double rightOverlap=rect2.getX()+rect2.getW()-rect1.getX();
			double topOverlap=rect1.getY()+rect1.getH()-rect2.getY();
			double botOverlap=rect2.getY()+rect2.getH()-rect1.getY();
			
			double smallestOverlap=Double.MAX_VALUE;
			double shiftX=0;
			double shiftY=0;
			
			 if(leftOverlap<smallestOverlap) {
				smallestOverlap=leftOverlap;
				shiftX=-leftOverlap;
				shiftY=0;
			}if(rightOverlap<smallestOverlap) {
				smallestOverlap=rightOverlap;
				shiftX=rightOverlap;
				shiftY=0;
			}if(topOverlap<smallestOverlap) {
				smallestOverlap=topOverlap;
				shiftX=0;
				shiftY=-topOverlap;
			}if(botOverlap<smallestOverlap) {
				smallestOverlap=botOverlap;
				shiftX=0;
				shiftY=botOverlap;
			}

			rect1.setX(rect1.getX()+shiftX);
			rect1.setY(rect1.getY()+shiftY);
			return true;
		}
		return false;
    }
    private static boolean resetOvalOutOfOval(AnimationObject p, AnimationObject o) {
		double distance;
		if((distance=getOvalOvalDistance(p, o))>0) {
			double normX=((o.getX()+o.getW()/2)-(p.getX()+p.getW()/2))/distance;
			double normY=((o.getY()+o.getH()/2)-(p.getY()+p.getH()/2))/distance;
			p.setX((o.getX()+o.getW()/2)-(normX*(p.getW()/2+o.getW()/2))-(p.getW()/2));
			p.setY((o.getY()+o.getH()/2)-(normY*(p.getH()/2+o.getH()/2))-(p.getH()/2));
			return true;
		}
		return false;
    }
  	private static boolean resetOvalOutOfRect(AnimationObject circ, AnimationObject rect) {
		if(ovalRectSidesCollide(circ, rect))
			return resetRectOutOfRect(circ, rect);
		else {
			int corner=ovalRectCornersCollide(circ, rect);
			if(corner>0) {
				AnimationObject tempCirc = new AnimationObject(0,0,1,1,OVAL);
				switch(corner) {
					case 1:
						tempCirc.setX(rect.getX());//top-left corner.
						tempCirc.setY(rect.getY());
						break;
					case 2:
						tempCirc.setX(rect.getX()+rect.getW());//top-right corner.
						tempCirc.setY(rect.getY());
						break;
					case 3:
						tempCirc.setX(rect.getX());//bottom-left corner.
						tempCirc.setY(rect.getY()+rect.getH());
						break;
					case 4:
						tempCirc.setX(rect.getX()+rect.getW());//bottom-right corner.
						tempCirc.setY(rect.getY()+rect.getH());
						break;
				}
				
				return resetOvalOutOfOval(circ, tempCirc);
			}
		}
		return false;
  	}
  	private static boolean resetRectOutOfOval(AnimationObject rect, AnimationObject circ) {
		if(ovalRectSidesCollide(circ, rect))
			return resetRectOutOfRect(rect, circ);
		else {
			int corner=ovalRectCornersCollide(circ, rect);
			if(corner>0) {
				AnimationObject tempCirc = new AnimationObject(0,0,1,1,OVAL);
				double xOffset=0,yOffset=0;
				switch(corner) {
                    case 1:
						xOffset=0;//top-left corner.
						yOffset=0;
						break;
					case 2:
						xOffset=rect.getW();//top-right corner.
						yOffset=0;
						break;
					case 3:
						xOffset=0;//bottom-left corner.
						yOffset=rect.getH();
						break;
					case 4:
						xOffset=rect.getW();//bottom-right corner.
						yOffset=rect.getH();
						break;
				}
				tempCirc.setX(rect.getX()+xOffset);
				tempCirc.setY(rect.getY()+yOffset);

				resetOvalOutOfOval(tempCirc, circ);

				rect.setX(tempCirc.getX()-xOffset);
				rect.setY(tempCirc.getY()-yOffset);
				return true;
			}
		}
		return false;
  	}



	public static double distance(AEPoint p1, AEPoint p2) {
		return Math.sqrt(((p1.x - p2.x) * (p1.x - p2.x)) + ((p1.y - p2.y) * (p1.y - p2.y)));
	}

//COLLISION DETECTION
	public static boolean intersect(AnimationObject p, AnimationObject o) {
		return p!=null && o!=null && p.getBounds().intersects(o.getBounds());
	}
	public static boolean intersect(AnimationObject p, List<? extends AnimationObject> list) {
		for(AnimationObject o:list)
			if(intersect(p,o))
				return true;
		return false;
	}
	public static AnimationObject intersectsWhich(AnimationObject p, List<? extends AnimationObject> list) {
		for(AnimationObject o:list)
			if(intersect(p,o))
				return o;
		return null;
	}
	public static ArrayList<AnimationObject> collidesWith(AnimationObject checkP, List<? extends AnimationObject> particles) {
		ArrayList<AnimationObject> collidingAnimationObjects = new ArrayList<>();
		for(AnimationObject o:particles)
			if(collision(checkP,o))
				collidingAnimationObjects.add(o);
		return collidingAnimationObjects;
	}
	public static AnimationObject collidesWithOne(AnimationObject checkP, ArrayList<? extends AnimationObject> particles) {
		for(AnimationObject o:particles)
			if(collision(checkP,o))
				return o;
		return null;
	}
	public static boolean collision(AnimationObject p, ArrayList<? extends AnimationObject> list) {
		for(AnimationObject o:list)
			if(collision(p,o))
				return true;
		return false;
	}
	public static boolean collision(AnimationObject p, /*int p_angle,*/ AnimationObject o/*, int o_angle*/) {
//		System.out.println("collision");
		if (!intersect(p, o)) return false;
//		System.out.println("bnds intersected");
		if (p.isCircle()) {
			if (o.isCircle()) 	 return isCircleCollidingCircle(p, o);
			else if (o.isLine()) return isCircleCollidingLine(p, o);
		} 
		if (p.isOval()) {
//			System.out.println("p is Oval");
			if (o.isOval()) 	 return isOvalCollidingOval(p, o);  //NOT SUPPORTING ANGLE
			else if (o.isRect()) return isRectCollidingOval(o, p);  //NOT SUPPORTING ANGLE
			else if (o.isLine()) return isOvalCollidingLine(p, o);  //NOT SUPPORTING ANGLE
		}
		if (p.isRect()) {
			if (o.isOval())  	 return isRectCollidingOval(p, o);  				  //NOT SUPPORTING ANGLE
			else if (o.isRect()) return p.getBounds().intersects(o.getBounds());  //NOT SUPPORTING ANGLE
			else if (o.isLine()) return isRectCollidingLine(p, o);  //NOT SUPPORTING ANGLE
		}
		if (p.isLine()) {
			if (o.isCircle())    return isCircleCollidingLine(o, p);  				  //NOT SUPPORTING ANGLE
			else if (o.isOval()) return isOvalCollidingLine(o,p);  				  //NOT SUPPORTING ANGLE
			else if (o.isRect()) return isRectCollidingLine(o, p);  //NOT SUPPORTING ANGLE
			else if (o.isLine()) return isLineCollidingLine(p, o);
		}
		return false;
	}

	private static boolean isOvalCollidingOval(AnimationObject p, AnimationObject o) {
        if (!p.isOval()||!o.isOval() || !p.getBounds().intersects(o.getBounds())) {
            return false;
        } else {
        	ArrayList<AEPoint> p_ellPs = AE_UTIL.getPointsOnEllipse(p.getMid(), (int)p.getW(), (int) p.getH(), 60);
        	for (AEPoint p_loc:p_ellPs)
        		if (isPointInOval(p_loc, o)) return true;
        	return isPointInOval(o.getMid(), p);
        }
	}
//https://stackoverflow.com/questions/13285007/how-to-determine-if-a-point-is-within-an-ellipse
	static boolean isPointInOval(AEPoint p, AnimationObject oval) {
        double r_x = oval.getW() /2;
        double r_y = oval.getH()/2;
        if (r_x<=0.0||r_y<=0.0) return false;
        AEPoint normalized = new AEPoint(p.x - oval.getMid().x,p.y - oval.getMid().y);
        return ((normalized.getX()*normalized.getX())/(r_x*r_x)) + 
        	   ((normalized.getY()*normalized.getY())/(r_y*r_y))   <= 1.0;
	}

    private static boolean isCircleCollidingCircle(AnimationObject p, AnimationObject o) {
        return getOvalOvalDistance(p, o)>0;
    }
    private static double getOvalOvalDistance(AnimationObject p, AnimationObject o) {
        if (!p.isOval()||!o.isOval()) {
            return -1;
        } else {
            if(p.getBounds().intersects(o.getBounds())){
                double distance=new AEPoint(p.getX()+p.getW()/2, p.getY()+p.getH()/2).distance(new AEPoint(o.getX()+o.getW()/2, o.getY()+o.getH()/2));
                if (distance<p.getW()/2+o.getW()/2)
                	return distance;
            }
            return -1;
        }
    }
	public static double midtomidDistance(AnimationObject p, MovingAnimationObject o) {
		return new AEPoint(p.getX()+p.getW()/2, p.getY()+p.getH()/2).distance(new AEPoint(o.getX()+o.getW()/2, o.getY()+o.getH()/2));
	}

    private static boolean isRectCollidingOval(AnimationObject rect, AnimationObject oval) {
      if (!rect.isRect()||!oval.isOval()||!oval.getBounds().intersects(rect.getBounds())) return false;
      else return(
				ovalRectSidesCollide(oval, rect) ||
				ovalRectCornersCollide(oval, rect)>0
			);
	}
    private static boolean ovalRectSidesCollide(AnimationObject oval, AnimationObject rect)	{
		return ((oval.getX()+oval.getW()/2)>rect.getX() && (oval.getX()+oval.getW()/2)<(rect.getX()+rect.getW())) || ((oval.getY()+oval.getH()/2)>rect.getY() && (oval.getY()+oval.getH()/2)<(rect.getY()+rect.getH()));
	}
    private static int ovalRectCornersCollide(AnimationObject oval, AnimationObject rect) {
    	AEPoint tempP = new AEPoint();
		tempP.x=(int) rect.getX();//top-left corner.
		tempP.y=(int) rect.getY();
		if(isPointInOval(tempP, oval)){return 1;}
		tempP.x=(int) (rect.getX()+rect.getW());//top-right corner.
		tempP.y=(int) rect.getY();
		if(isPointInOval(tempP, oval)){return 2;}
		tempP.x=(int) rect.getX();//bottom-left corner.
		tempP.y=(int) (rect.getY()+rect.getH());
		if(isPointInOval(tempP, oval)){return 3;}
		tempP.x=(int) (rect.getX()+rect.getW());//bottom-right corner.
		tempP.y=(int) (rect.getY()+rect.getH());
		if(isPointInOval(tempP, oval)){return 4;}
		return -1;
	}
    //        public boolean isTriangleCollidingCircle(AnimationObject triangle, AnimationObject circ) {
    //          if (!triangle.isTriangle()||!circ.isCircle())
    //              return false;
    //          else {
    //              Polygon p = new Polygon(new int[] {(int)triangle.getX()+triangle.getW()/2, (int) triangle.getX(), (int) (triangle.getX()+triangle.getW())},
    //                                      new int[] {(int) triangle.getY(), (int) (triangle.getY()+triangle.getH()), (int) (triangle.getY()+triangle.getH())},
    //                                      3);
    //              
    //              return p.contains(circ.getMid()) ||
    //                     isCircleCollidingLine(new Point(p.getX()points[0], p.getY()points[0]), new Point(p.getX()points[1], p.getY()points[1]), circ) ||
    //                     isCircleCollidingLine(new Point(p.getX()points[1], p.getY()points[1]), new Point(p.getX()points[2], p.getY()points[2]), circ) ||
    //                     isCircleCollidingLine(new Point(p.getX()points[2], p.getY()points[2]), new Point(p.getX()points[0], p.getY()points[0]), circ);
    //          }
    //        }
    public static boolean isRectCollidingLine(AnimationObject rect, AnimationObject line) {
//        if (!rect.isRect() || !line.isLine()) return false;
		double topIntersection;
		double bottomIntersection;
		double topPoint;
		double bottomPoint;

		// Calculate m and c for the equation for the line (y = mx+c)
		double m = (line.getH()-line.getY()) / (line.getW()-line.getX());
		double c = line.getY() -(m*line.getX());

		// If the line is going up dependenciesFrom right to left then the top intersect point is on the left
		if(m > 0) {
			topIntersection = (m*rect.getX()  + c);
			bottomIntersection = (m*(rect.getX()+rect.getW())  + c);
		} else {// Otherect.getW()ise it's on the right
			topIntersection = (m*(rect.getX()+rect.getW())  + c);
			bottomIntersection = (m*rect.getX()  + c);
		}

		// Work out the top and bottom extents for the triangle
		if(line.getY() < line.getH()) {
			topPoint = line.getY();
			bottomPoint = line.getH();
		} else {
			topPoint = line.getH();
			bottomPoint = line.getY();
		}

		double topOverlap;
		double botOverlap;

		// Calculate the overlap between those two bounds
		topOverlap = topIntersection > topPoint ? topIntersection : topPoint;
		botOverlap = bottomIntersection < bottomPoint ? bottomIntersection : bottomPoint;
		
		return (topOverlap<botOverlap) && (!((botOverlap<rect.getY()) || (topOverlap>rect.getY()+rect.getH())));
    }
//FROM: https://stackoverflow.com/questions/15514906/how-to-check-intersection-between-a-line-and-a-rectangle
    private static boolean isLineCollidingLine(AnimationObject line1, AnimationObject line2) {
        if (!line1.isLine() || !line2.isLine()) return false;
	    double
	        s1_x = line1.getW() - line1.getX(),
	        s1_y = line1.getH() - line1.getY(),

	        s2_x = line2.getW() - line2.getX(),
	        s2_y = line2.getH() - line2.getY(),

	        s = (-s1_y * (line1.getX() - line2.getX()) + s1_x * (line1.getY() - line2.getY())) / (-s2_x * s1_y + s1_x * s2_y),
	        t = ( s2_x * (line1.getY() - line2.getY()) - s2_y * (line1.getX() - line2.getX())) / (-s2_x * s1_y + s1_x * s2_y);

        return s >= 0 && s <= 1 && t >= 0 && t <= 1;
    }
    private static boolean isOvalCollidingLine(AnimationObject oval, AnimationObject line) {
        if (!oval.isOval() || !line.isLine()) return false;
        List<AEPoint> linePs = AE_UTIL.getPointsOnLine((int)line.getX(), (int)line.getY(), (int)line.getW(), (int)line.getH());
        for (AEPoint p:linePs)
        	if (isPointInOval(p, oval)) return true;
        return false;
    }
    private static boolean isCircleCollidingLine(AnimationObject circ, AnimationObject line) {
        if (!circ.isCircle() || !line.isLine()) return false;
        AEPoint center = circ.getMid();
        double radius = circ.getW()/2;
        double baX = line.getW()   - line.getX();
        double baY = line.getH()   - line.getY();
        double caX = center.getX() - line.getX();
        double caY = center.getY() - line.getY();

        double a = baX * baX + baY * baY;
        double bBy2 = baX * caX + baY * caY;
        double c = caX * caX + caY * caY - radius * radius;

        double pBy2 = bBy2 / a;
        double q = c / a;

        double disc = pBy2 * pBy2 - q;
        return !(disc < 0);
    }

    public boolean overlapingBoundsLeft() {
        return getX() < 0;
    }
    public boolean overlapingBoundsRight(double width) {
        return getX() > width - getW();
    }
    public boolean overlapingBoundsTop() {
        return getY() < 0;
    }
    public boolean overlapingBoundsBottom(double height) {
        return getY() > height - getH();
    }

//	public static double distance(AEPoint point, AnimationObject line) {
//		// A - the standalone point (x, y)
//		// B - start point of the line segment (x1, y1)
//		// C - end point of the line segment (x2, y2)
//		// D - the crossing point between line from A to BC
//		AEPoint lineP1 = line.getLoc();
//		AEPoint lineP2 = new AEPoint(line.getW(), line.getH());
//
//		double AB = AnimationObject.distance(point, lineP1);
//		double BC = AnimationObject.distance(lineP1, lineP2);
//		double AC = AnimationObject.distance(point, lineP2);
//
//		// Heron's formula
//		double s = (AB + BC + AC) / 2;
//		double area = (float) Math.sqrt(s * (s - AB) * (s - BC) * (s - AC));
//
//		// but also area == (BC * AD) / 2
//		// BC * AD == 2 * area
//		// AD == (2 * area) / BC
//		// TODO: check if BC == 0
//		double AD = (2 * area) / BC;
//		return AD;
//	}


    //FROM:: https://stackoverflow.com/a/6853926
    public static double distance(AEPoint point, AnimationObject line) {
        double x = point.x;
        double y = point.y;
        double x1 = line.getX();
        double y1 = line.getY();
        double x2 = line.getW();
        double y2 = line.getH();

        double A = x - x1;
        double B = y - y1;
        double C = x2 - x1;
        double D = y2 - y1;

        double dot = A * C + B * D;
        double len_sq = C * C + D * D;
        double param = -1;
        if (len_sq != 0) //in case of 0 length line
            param = dot / len_sq;

        double xx, yy;

        if (param < 0) {
            xx = x1;
            yy = y1;
        } else if (param > 1) {
            xx = x2;
            yy = y2;
        } else {
            xx = x1 + param * C;
            yy = y1 + param * D;
        }

        double dx = x - xx;
        double dy = y - yy;
        return Math.sqrt(dx * dx + dy * dy);
    }

}
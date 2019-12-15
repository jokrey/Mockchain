package jokrey.utilities.animation.util;

import java.util.ArrayList;
import java.util.List;

public class AE_UTIL {
    public static int getRandomNr(long min, long max) {
	   long range = (max - min) + 1;     
	   return (int) ((Math.random() * range) + min);
    }
    public static int getRandomNr(int min, int max) {
	   int range = (max - min) + 1;     
	   return (int)(Math.random() * range) + min;
    }
    public static double getRandomNr(double min, double max) {
    	double range = (max - min) + 1;     
	   return (Math.random() * range) + min;
    }
    
    //FROM: http://www.java-forums.org/new-java/81793-bresenhams-line-algorithm-get-points-along-line-segment.html
    public static List<AEPoint> getPointsOnLine(int x1, int y1, int x2, int y2) {
        final List<AEPoint> coords = new ArrayList<>();
        final int dx = Math.abs(x2 - x1);
        final int dy = Math.abs(y2 - y1);
        final int sx = x1 < x2 ? 1 : -1;
        final int sy = y1 < y2 ? 1 : -1;
        // Verticle Line
        if(dx == 0) {
            while(true) {
                final AEPoint c = new AEPoint(x1, y1);
                if(!coords.contains(c))
                    coords.add(c);
                if(y1 == y2)
                    break;
                y1 += sy;
            }
        }
        // Horizontal Line
        else if(dy == 0) {
            while(true) {
                final AEPoint c = new AEPoint(x1, y1);
                if(!coords.contains(c))
                    coords.add(c);
                if(x1 == x2)
                    break;
                x1 += sx;
            }
        }
        // Other Line
        else {
            int err = dx - dy;
            while(true) {
                final AEPoint c = new AEPoint(x1, y1);
                if(!coords.contains(c))
                    coords.add(c);
                if(x1 == x2 || y1 == y2)
                    break;
                int e2 = 2 * err;
                if(e2 > -dy) {
                    err -= dy;
                    x1 += sx;
                }
                if(e2 < dx) {
                    err += dx;
                    y1 += sy;
                }
            }
        }
        return coords;
    }
    /**
     * 
     * @param center
     * @param width
     * @param height
     * @param accuracy 100 is enough 50 probably too
     * 			Determines how "dashed" the line is
     * @return
     */
    public static ArrayList<AEPoint> getPointsOnEllipse(AEPoint center, int width, int height, int accuracy) {
    	width/=2;
    	height/=2;
    	ArrayList<AEPoint> ellAEPoints = new ArrayList<>();
    	for (int p_i = 0; p_i!=accuracy;p_i++) {
			double pEX = (width*height) /
					Math.sqrt((height*height)+(width*width) * ((Math.tan(p_i))*(Math.tan(p_i))));
			double pEY = (width*height) /
					Math.sqrt((width*width)+(height*height) / ((Math.tan(p_i))*(Math.tan(p_i))));
			ellAEPoints.add(new AEPoint((int)pEX+center.x, (int)pEY+center.y));
    	}
    	for (int p_i = 0; p_i!=accuracy;p_i++) {
			double pEX = (width*height) /
					Math.sqrt((height*height)+(width*width) * ((Math.tan(p_i))*(Math.tan(p_i))));
			double pEY = (width*height) /
					Math.sqrt((width*width)+(height*height) / ((Math.tan(p_i))*(Math.tan(p_i))));
			ellAEPoints.add(new AEPoint((int)-pEX+center.x, (int)pEY+center.y));
    	}
    	for (int p_i = 0; p_i!=accuracy;p_i++) {
			double pEX = (width*height) /
					Math.sqrt((height*height)+(width*width) * ((Math.tan(p_i))*(Math.tan(p_i))));
			double pEY = (width*height) /
					Math.sqrt((width*width)+(height*height) / ((Math.tan(p_i))*(Math.tan(p_i))));
			ellAEPoints.add(new AEPoint((int)pEX+center.x, (int)-pEY+center.y));
    	}
    	for (int p_i = 0; p_i!=accuracy;p_i++) {
			double pEX = (width*height) /
					Math.sqrt((height*height)+(width*width) * ((Math.tan(p_i))*(Math.tan(p_i))));
			double pEY = (width*height) /
					Math.sqrt((width*width)+(height*height) / ((Math.tan(p_i))*(Math.tan(p_i))));
			ellAEPoints.add(new AEPoint((int)-pEX+center.x, (int)-pEY+center.y));
    	}
    	return ellAEPoints;
    }

  //https://stackoverflow.com/questions/9970281/java-calculating-the-angle-between-two-points-in-degrees
          public static int getAngle(AEPoint start, AEPoint target) {
              int angle = (int) Math.toDegrees(Math.atan2(target.y - start.y, target.x - start.x));
              if(angle < 0) angle += 360;
              return angle;
          }
  //https://stackoverflow.com/revisions/9760950/1
          public static double getAngle(double vx, double vy) {
              return Math.toDegrees(Math.atan2(vy, vx));
          }
          public static double getVelocityWithAngle(double vx, double vy) {
              return Math.sqrt(Math.pow(vx, 2) + Math.pow(vy, 2));
          }
          //altered
          public static double[] angleVelocityToXYVelocity(double angle, double velocity) {
//              double vx = Math.cos(Math.toRadians(angle)) * velocity;
////            double vy = Math.sqrt(Math.pow(velocity, 2) - Math.pow(vx, 2));
//              double vy = Math.sin(Math.toRadians(angle)) * velocity;

              double[] vX_vY = {angleXVelocityToXVelocity(angle, velocity),angleYVelocityToYVelocity(angle, velocity)};
              return vX_vY;
//              System.out.println("vx: " + vx + " vy: " + vy);
          }
          public static double angleXVelocityToXVelocity(double angle, double x_velocity) {
              return Math.cos(Math.toRadians(angle)) * x_velocity;
          }
          public static double angleYVelocityToYVelocity(double angle, double y_velocity) {
              return Math.sin(Math.toRadians(angle)) * y_velocity;
          }
          public static double getForce(AEPoint p1, AEPoint p2, double m1, double m2) {
        	  double distance = p1.distance(p2);//r
        	  return (/*getGravitationalConstant()*/m1*m2)/(distance*distance);
          }
          public static double getGravitationalConstant() {
        	  return 6.674*Math.pow(10, -11);
          }

          public static AEPoint getPointAtDistanceFrom(AEPoint from, int angle, double distance) {
                angle = angle % 360;
                if(angle < 0)
                    angle += 360;
              return new AEPoint(from.x + distance*Math.cos(Math.toRadians(angle)),
                      from.y + distance*Math.sin(Math.toRadians(angle)));
          }
}
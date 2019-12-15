package jokrey.utilities.animation.implementations.android.pipeline;

class AnimationDrawerAndroid{} //remove, just so that no compile errors are shown, when compiling for desktop
//import android.graphics.Color;
//import android.graphics.Paint;
//import android.graphics.RectF;
//
//import util.animation.implementations.android.display.AnimationView;
//import util.animation.pipeline.AnimationDrawer;
//import util.animation.util.AEColor;
//import util.animation.util.AEImage;
//import util.animation.util.AEPoint;
//import util.animation.util.AERect;
//
//public class AnimationDrawerAndroid extends AnimationDrawer {
//	public AnimationView p=null;
//
//    @Override protected AERect getPanelBoundsOnScreen() {
//        return new AERect(0, 0, p.getWidth(), p.getHeight());
//    }
//
//
//    @Override public void drawImage(AEImage param, AERect drawB) {
////        Bitmap ip_img = (Bitmap) param;
////        p.getDrawCanvas().drawBitmap(ip_img, null, new RectF((float)drawB.x, (float)drawB.y, (float)(drawB.x + drawB.w), (float)(drawB.y + drawB.h)), p.paint);
//    }
//
//    @Override public void drawLine(AEColor param, AEPoint p1, AEPoint p2) {
//        p.paint.setColor(Color.rgb(param.getRed(), param.getGreen(), param.getBlue()));
//        p.getDrawCanvas().drawLine((float)p1.x, (float)p1.y, (float)p2.x, (float)p2.y, p.paint);
//    }
//
//    @Override public void fillOval(AEColor param, AERect drawB) {
//        p.paint.setColor(Color.rgb(param.getRed(), param.getGreen(), param.getBlue()));
//        p.paint.setStyle(Paint.Style.FILL);
//        p.getDrawCanvas().drawOval(new RectF((float)drawB.x, (float)drawB.y, (float)(drawB.x + drawB.w), (float)(drawB.y + drawB.h)), p.paint);
//    }
//
//    @Override public void fillRect(AEColor param, AERect drawB) {
//        p.paint.setColor(param.argb);
//        p.paint.setStyle(Paint.Style.FILL);
//        p.getDrawCanvas().drawRect(new RectF((float)drawB.x, (float)drawB.y, (float)(drawB.x + drawB.w), (float)(drawB.y + drawB.h)), p.paint);
//    }
//
//    @Override public void drawRect(AEColor param, AERect drawB) {
//        p.paint.setColor(Color.rgb(param.getRed(), param.getGreen(), param.getBlue()));
//        p.paint.setStyle(Paint.Style.STROKE);
//        p.getDrawCanvas().drawRect(new RectF((float)drawB.x, (float)drawB.y, (float)(drawB.x + drawB.w), (float)(drawB.y + drawB.h)), p.paint);
//    }
//
//    @Override public double drawString(AEColor param, double font_size, String stateStr, double mid_x, double mid_y) {
//        p.paint.setTextAlign(Paint.Align.CENTER);
//        p.paint.setColor(Color.rgb(param.getRed(), param.getGreen(), param.getBlue()));
//        p.paint.setStyle(Paint.Style.STROKE);
//        p.paint.setTextSize((float) font_size);
//        p.getDrawCanvas().drawText(stateStr, (float)mid_x, (float) mid_y  - ((p.paint.descent() + p.paint.ascent()) / 2.0f), p.paint);
//
//        return font_size;//TODO this is technically bullshit
//    }
//
//    @Override public void drawString(AEColor param, String stateStr, AERect drawB) {
//        p.paint.setTextAlign(Paint.Align.CENTER);
//        p.paint.setColor(Color.rgb(param.getRed(), param.getGreen(), param.getBlue()));
//        p.paint.setStyle(Paint.Style.STROKE);
//        p.paint.setTextSize((float) drawB.getHeight());
//        p.getDrawCanvas().drawText(stateStr, (float)(drawB.x+drawB.w/2), (float)(drawB.y+drawB.h/2) - ((p.paint.descent() + p.paint.ascent()) / 2.0f), p.paint);    }
//
//    @Override public void drawOval(AEColor param, AERect drawB) {
//        p.paint.setColor(Color.rgb(param.getRed(), param.getGreen(), param.getBlue()));
//        p.paint.setStyle(Paint.Style.STROKE);
//        p.getDrawCanvas().drawOval(new RectF((float)drawB.x, (float)drawB.y, (float)(drawB.x + drawB.w), (float)(drawB.y + drawB.h)), p.paint);
//    }
//
//
//    @Override public void drawHalfOval(AEColor param, AERect aeRect, int openDirection) {
////        GraphicsContext gc = p.getGraphicsContext2D();
////        Color colorToDraw = param==null? Color.AQUA:Color.rgb(param.getRed(), param.getGreen(), param.getBlue());
////        gc.setStroke(colorToDraw);
////        if(openDirection==0) {
////            gc.strokeArc(aeRect.x, aeRect.y, aeRect.w, aeRect.h, 180, 180, ArcType.OPEN);
////        } else if(openDirection==1) {
////            gc.strokeArc(aeRect.x, aeRect.y, aeRect.w, aeRect.h, 0, 180, ArcType.OPEN);
////        } else if(openDirection==2) {
////            gc.strokeArc(aeRect.x, aeRect.y, aeRect.w, aeRect.h, 90, 180, ArcType.OPEN);
////        } else if(openDirection==3) {
////            gc.strokeArc(aeRect.x, aeRect.y, aeRect.w, aeRect.h, 90, -180, ArcType.OPEN);
////        }
//    }
//
//    @Override public void fillHalfOval(AEColor param, AERect aeRect, int openDirection) {
////        GraphicsContext gc = p.getGraphicsContext2D();
////        Color colorToDraw = param==null? Color.AQUA:Color.rgb(param.getRed(), param.getGreen(), param.getBlue());
////        gc.setFill(colorToDraw);
////        if(openDirection==0) {
////            gc.fillArc(aeRect.x, aeRect.y, aeRect.w, aeRect.h, 180, 180, ArcType.OPEN);
////        } else if(openDirection==1) {
////            gc.fillArc(aeRect.x, aeRect.y, aeRect.w, aeRect.h, 0, 180, ArcType.OPEN);
////        } else if(openDirection==2) {
////            gc.fillArc(aeRect.x, aeRect.y, aeRect.w, aeRect.h, 90, 180, ArcType.OPEN);
////        } else if(openDirection==3) {
////            gc.fillArc(aeRect.x, aeRect.y, aeRect.w, aeRect.h, 90, -180, ArcType.OPEN);
////        }
//    }
//
//    @Override public void fillTriangle(AEColor param, AERect aeRect) {
////        GraphicsContext gc = p.getGraphicsContext2D();
////        Color colorToDraw = param==null? Color.AQUA:Color.rgb(param.getRed(), param.getGreen(), param.getBlue());
////        gc.setFill(colorToDraw);
////
////        double[] xp = { aeRect.x, aeRect.x + aeRect.w,  aeRect.x + aeRect.w/2};
////        double[] yp = { aeRect.y + aeRect.h/4, aeRect.y + aeRect.h/4,  aeRect.y + aeRect.h};
////        gc.fillPolygon(xp, yp, xp.length);
//    }
//}
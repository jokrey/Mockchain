package jokrey.utilities.animation.implementations.android.display;

class AnimationView{}//remove just to disable error display outside of android sdk knowing env
//import android.content.Context;
//import android.graphics.Canvas;
//import android.graphics.Paint;
//import android.view.MotionEvent;
//import android.view.View;
//
//public class AnimationView extends View {
//	public AnimationHandler handler;
//
//    public void start() {
//		handler.start();
//	}
//	public AnimationView(Context cntx, AnimationEngine engineToRun, AnimationPipeline pipeline) {
//        super(cntx);
//        this.handler = new AnimationHandler(engineToRun, pipeline) {
//            @Override public void draw() {
//                postInvalidate();
//            }
//        };
//        ((AnimationDrawerAndroid) handler.getPipeline().getDrawer()).p = this;
//        new Thread(handler).start();
//
//        setOnTouchListener(new OnTouchListener() {
//            @Override
//            public boolean onTouch(View v, MotionEvent event) {
//                AEPoint mouseP = handler.getPipeline().convertFromScreenPoint(new AEPoint(event.getX(), event.getY()));
//                if (event.getAction() == MotionEvent.ACTION_UP) {
//                    handler.getEngine().mouseClicked(0);
//                    handler.getEngine().locationInputChanged(null, true);
//                } else if (event.getAction() == MotionEvent.ACTION_DOWN) {
//                    handler.getEngine().locationInputChanged(mouseP, true);
//                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
//                    handler.getEngine().locationInputChanged(mouseP, true);
//                } else {
//                    return false;
//                }
//                return true;
//            }
//        });
//    }
//
//
//    private Canvas canvas = null;
//    public Paint paint = new Paint();
//    public Canvas getDrawCanvas() {
//        return canvas;
//    }
//    protected void onDraw(Canvas canvas) {
////        if(handler.getPipeline()==null || (!handler.getPipeline().canDraw() && !handler.getEngine().isPaused()))return;
//        super.onDraw(canvas);
//
//        paint = new Paint();
//        this.canvas=canvas;
//		handler.getPipeline().draw(handler.getEngine().getAllObjectsToDraw(), handler.getEngine(), true);
//        this.canvas=null;
//        paint = null;
//	}
//}
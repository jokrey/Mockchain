package jokrey.utilities.animation.engine;

public abstract class TickEngine extends AnimationEngine {
    private long last = System.nanoTime();
    private long tick_counter = 0;
    public final void calculate() {
        if(isPaused()) {
            last = System.nanoTime();
            return;
        }
        long newLast = System.nanoTime();
        if(calculate((newLast - last)/1e9))
            last = newLast;
    }
    private final boolean calculate(double delta) {
        double tickEvery = 1.0/getTicksPerSecond();
        if(delta > tickEvery) {
            do {
                calculateTick();
                delta-=tickEvery;
            } while(delta > tickEvery && redoDelayedTicks());
            return true;
        }
        return false;
    }
    public final void calculateTick() {
        calculateTickImpl();
        tick_counter++;
    }
    protected abstract void calculateTickImpl();

    public final long getCurrentTick() {
        return tick_counter;
    }

    //allow override
    public int getTicksPerSecond() {return 100;}
    public boolean redoDelayedTicks() {return true;}

}

package jokrey.utilities.animation.engine;

public abstract class HandelableEngine {
    public abstract void calculate();

    public abstract void initiate();

    private boolean isPaused = true;
    public final boolean isPaused() {
        return isPaused;
    }
    public final void start() {
        isPaused = false;
    }
    public final void pause() {
        isPaused = true;
    }
}

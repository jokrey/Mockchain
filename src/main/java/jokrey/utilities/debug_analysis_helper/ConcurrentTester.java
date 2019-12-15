package jokrey.utilities.debug_analysis_helper;

//FROM: https://stackoverflow.com/a/2596530
//Though heavily extended
class ConcurrentTester implements Runnable {
    private Thread thread;
    private AssertionError exc;

    public ConcurrentTester(final Runnable runnable){
        thread = new Thread(() -> {
            try{
                runnable.run();
            }catch(AssertionError e){
                exc = e;
            }
        });
    }

    public void run() {
        thread.start();
        try {
            thread.join(); //wait for thread to finish.
            if (exc != null)
                throw exc;
        } catch (InterruptedException e) {
            throw new RuntimeException();
        }
    }
}
package jokrey.utilities.debug_analysis_helper;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

//TODO::: rework to use own threads.. To make sure there really is one and to expose access to that thread...
   // shouldn't make a difference if thread pool executor works like i think
//also maybe pre initialize them
public class ConcurrentPoolTester extends ThreadPoolExecutor {
    private Throwable t;

    //same as Executor.newFixedThreadPool(nThreads);
    public ConcurrentPoolTester(int nThreads){
        super(nThreads, nThreads,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>());
    }

    @Override protected void afterExecute(Runnable r, Throwable t) {
        if(t != null)
            this.t = t;
    }

    public void throwLatestException() throws Throwable {
        if(t != null)
            throw t;
    }

    public void waitForShutdownOrException() throws Throwable {
        shutdown();
        awaitTermination(Long.MAX_VALUE, TimeUnit.HOURS);
        throwLatestException();
    }
}
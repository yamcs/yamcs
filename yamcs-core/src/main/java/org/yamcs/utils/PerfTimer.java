package org.yamcs.utils;

/**
 * A simple timer that can be inserted in the code to measure the performance of certain operations.
 * <p>
 * Relies on System.nanoTime() called before and after the operation.
 * <p>
 * Prints on the standard output from time to time the number of nanoseconds per operation
 */
public class PerfTimer {
    final int numOps;
    final String name;
    long duration;
    int count;
    long before;
    
    /**
     * name will be printed in the output
     * <p>
     * numOps is how many operations it should time and print the result for
     */
    public PerfTimer(String name, int numOps) {
        this.name = name;
        this.numOps = numOps;
    }

    public void before() {
        this.before = System.nanoTime();
    }

    public void after() {
        duration += (System.nanoTime() - before);
        count+=1;
        if (count == numOps) {
            System.out.println(name + ": " + duration / numOps + " nanosec/op");
            count = 0;
            duration = 0;
        }
    }
}

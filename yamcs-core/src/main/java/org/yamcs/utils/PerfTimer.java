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
    int count;
    long before;
    long min = Long.MAX_VALUE;;
    long max = Long.MIN_VALUE;;
    long sum;
    double sum2;
    
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
        long elapsed = System.nanoTime() - before;
        sum += elapsed;
        sum2 += elapsed * elapsed;

        if (elapsed < min) {
            min = elapsed;
        }
        if (elapsed > max) {
            max = elapsed;
        }
        count += 1;
        if (count == numOps) {
            computeAndPrintStats();
            resetStats();
        }
    }

    private void computeAndPrintStats() {
        long avg = sum / numOps;
        double variance = (sum2 / numOps) - (avg * avg);
        long mdev = (long) Math.sqrt(variance);

        System.out.println(String.format("%-30s: min/avg/max/mdev: %d/%d/%d/%d ns/op",
                name, min, avg, max, mdev));
    }

    private void resetStats() {
        min = Long.MAX_VALUE;
        max = Long.MIN_VALUE;
        sum = 0;
        sum2 = 0;
        count = 0;
    }
}

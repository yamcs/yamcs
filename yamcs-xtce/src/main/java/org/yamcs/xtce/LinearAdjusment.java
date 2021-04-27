package org.yamcs.xtce;

public class LinearAdjusment {
    double intercept = 0;
    double slope = 1;

    public LinearAdjusment(double intercept, double slope) {
        this.intercept = intercept;
        this.slope = slope;
    }

    public double getIntercept() {
        return intercept;
    }

    public double getSlope() {
        return slope;
    }
}

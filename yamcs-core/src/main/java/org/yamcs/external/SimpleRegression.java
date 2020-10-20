package org.yamcs.external;
/*
 * This class has been copied from apache math in order not to include the whole package into Yamcs.
 *  Methods we do not use have been removed.
 */

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


/**
 * Estimates an ordinary least squares regression model
 * with one independent variable.
 * <p>
 * <code> y = intercept + slope * x  </code></p>
 * <p>
 * Standard errors for <code>intercept</code> and <code>slope</code> are
 * available as well as ANOVA, r-square and Pearson's r statistics.</p>
 * <p>
 * Observations (x,y pairs) can be added to the model one at a time or they
 * can be provided in a 2-dimensional array.  The observations are not stored
 * in memory, so there is no limit to the number of observations that can be
 * added to the model.</p>
 * <p>
 * <strong>Usage Notes</strong>: <ul>
 * <li> When there are fewer than two observations in the model, or when
 * there is no variation in the x values (i.e. all x values are the same)
 * all statistics return <code>NaN</code>. At least two observations with
 * different x coordinates are required to estimate a bivariate regression
 * model.
 * </li>
 * <li> Getters for the statistics always compute values based on the current
 * set of observations -- i.e., you can get statistics, then add more data
 * and get updated statistics without using a new instance.  There is no
 * "compute" method that updates all statistics.  Each of the getters performs
 * the necessary computations to return the requested statistic.
 * </li>
 * <li> The intercept term may be suppressed by passing {@code false} to
 * the {@link #SimpleRegression(boolean)} constructor.  When the
 * {@code hasIntercept} property is false, the model is estimated without a
 * constant term and {@link #getIntercept()} returns {@code 0}.</li>
 * </ul>
 *
 */
public class SimpleRegression {

    /** sum of x values */
    private double sumX = 0d;

    /** total variation in x (sum of squared deviations from xbar) */
    private double sumXX = 0d;

    /** sum of y values */
    private double sumY = 0d;

    /** total variation in y (sum of squared deviations from ybar) */
    private double sumYY = 0d;

    /** sum of products */
    private double sumXY = 0d;

    /** number of observations */
    private long n = 0;

    /** mean of accumulated x values, used in updating formulas */
    private double xbar = 0;

    /** mean of accumulated y values, used in updating formulas */
    private double ybar = 0;

    /** include an intercept or not */
    private final boolean hasIntercept;
    // ---------------------Public methods--------------------------------------

    /**
     * Create an empty SimpleRegression instance
     */
    public SimpleRegression() {
        this(true);
    }
    /**
    * Create a SimpleRegression instance, specifying whether or not to estimate
    * an intercept.
    *
    * <p>Use {@code false} to estimate a model with no intercept.  When the
    * {@code hasIntercept} property is false, the model is estimated without a
    * constant term and {@link #getIntercept()} returns {@code 0}.</p>
    *
    * @param includeIntercept whether or not to include an intercept term in
    * the regression model
    */
    public SimpleRegression(boolean includeIntercept) {
        super();
        hasIntercept = includeIntercept;
    }

    /**
     * Adds the observation (x,y) to the regression data set.
     * <p>
     * Uses updating formulas for means and sums of squares defined in
     * "Algorithms for Computing the Sample Variance: Analysis and
     * Recommendations", Chan, T.F., Golub, G.H., and LeVeque, R.J.
     * 1983, American Statistician, vol. 37, pp. 242-247, referenced in
     * Weisberg, S. "Applied Linear Regression". 2nd Ed. 1985.</p>
     *
     *
     * @param x independent variable value
     * @param y dependent variable value
     */
    public void addData(final double x,final double y) {
        if (n == 0) {
            xbar = x;
            ybar = y;
        } else {
            if( hasIntercept ){
                final double fact1 = 1.0 + n;
                final double fact2 = n / (1.0 + n);
                final double dx = x - xbar;
                final double dy = y - ybar;
                sumXX += dx * dx * fact2;
                sumYY += dy * dy * fact2;
                sumXY += dx * dy * fact2;
                xbar += dx / fact1;
                ybar += dy / fact1;
            }
         }
        if( !hasIntercept ){
            sumXX += x * x ;
            sumYY += y * y ;
            sumXY += x * y ;
        }
        sumX += x;
        sumY += y;
        n++;
    }

    /**
     * Appends data from another regression calculation to this one.
     *
     * <p>The mean update formulae are based on a paper written by Philippe
     * P&eacute;bay:
     * <a
     * href="http://prod.sandia.gov/techlib/access-control.cgi/2008/086212.pdf">
     * Formulas for Robust, One-Pass Parallel Computation of Covariances and
     * Arbitrary-Order Statistical Moments</a>, 2008, Technical Report
     * SAND2008-6212, Sandia National Laboratories.</p>
     *
     * @param reg model to append data from
     * @since 3.3
     */
    public void append(SimpleRegression reg) {
        if (n == 0) {
            xbar = reg.xbar;
            ybar = reg.ybar;
            sumXX = reg.sumXX;
            sumYY = reg.sumYY;
            sumXY = reg.sumXY;
        } else {
            if (hasIntercept) {
                final double fact1 = reg.n / (double) (reg.n + n);
                final double fact2 = n * reg.n / (double) (reg.n + n);
                final double dx = reg.xbar - xbar;
                final double dy = reg.ybar - ybar;
                sumXX += reg.sumXX + dx * dx * fact2;
                sumYY += reg.sumYY + dy * dy * fact2;
                sumXY += reg.sumXY + dx * dy * fact2;
                xbar += dx * fact1;
                ybar += dy * fact1;
            }else{
                sumXX += reg.sumXX;
                sumYY += reg.sumYY;
                sumXY += reg.sumXY;
            }
        }
        sumX += reg.sumX;
        sumY += reg.sumY;
        n += reg.n;
    }

    /**
     * Removes the observation (x,y) from the regression data set.
     * <p>
     * Mirrors the addData method.  This method permits the use of
     * SimpleRegression instances in streaming mode where the regression
     * is applied to a sliding "window" of observations, however the caller is
     * responsible for maintaining the set of observations in the window.</p>
     *
     * The method has no effect if there are no points of data (i.e. n=0)
     *
     * @param x independent variable value
     * @param y dependent variable value
     */
    public void removeData(final double x,final double y) {
        if (n > 0) {
            if (hasIntercept) {
                final double fact1 = n - 1.0;
                final double fact2 = n / (n - 1.0);
                final double dx = x - xbar;
                final double dy = y - ybar;
                sumXX -= dx * dx * fact2;
                sumYY -= dy * dy * fact2;
                sumXY -= dx * dy * fact2;
                xbar -= dx / fact1;
                ybar -= dy / fact1;
            } else {
                final double fact1 = n - 1.0;
                sumXX -= x * x;
                sumYY -= y * y;
                sumXY -= x * y;
                xbar -= x / fact1;
                ybar -= y / fact1;
            }
             sumX -= x;
             sumY -= y;
             n--;
        }
    }


    /**
     * Removes observations represented by the elements in <code>data</code>.
      * <p>
     * If the array is larger than the current n, only the first n elements are
     * processed.  This method permits the use of SimpleRegression instances in
     * streaming mode where the regression is applied to a sliding "window" of
     * observations, however the caller is responsible for maintaining the set
     * of observations in the window.</p>
     * <p>
     * To remove all data, use <code>clear()</code>.</p>
     *
     * @param data array of observations to be removed
     */
    public void removeData(double[][] data) {
        for (int i = 0; i < data.length && n > 0; i++) {
            removeData(data[i][0], data[i][1]);
        }
    }

    /**
     * Clears all data from the model.
     */
    public void clear() {
        sumX = 0d;
        sumXX = 0d;
        sumY = 0d;
        sumYY = 0d;
        sumXY = 0d;
        n = 0;
    }

    /**
     * Returns the number of observations that have been added to the model.
     *
     * @return n number of observations that have been added.
     */
    public long getN() {
        return n;
    }

    /**
     * Returns the "predicted" <code>y</code> value associated with the
     * supplied <code>x</code> value,  based on the data that has been
     * added to the model when this method is activated.
     * <p>
     * <code> predict(x) = intercept + slope * x </code></p>
     * <p>
     * <strong>Preconditions</strong>: <ul>
     * <li>At least two observations (with at least two different x values)
     * must have been added before invoking this method. If this method is
     * invoked before a model can be estimated, <code>Double,NaN</code> is
     * returned.
     * </li></ul>
     *
     * @param x input <code>x</code> value
     * @return predicted <code>y</code> value
     */
    public double predict(final double x) {
        final double b1 = getSlope();
        if (hasIntercept) {
            return getIntercept(b1) + b1 * x;
        }
        return b1 * x;
    }

    /**
     * Returns the intercept of the estimated regression line, if
     * {@link #hasIntercept()} is true; otherwise 0.
     * <p>
     * The least squares estimate of the intercept is computed using the
     * <a href="http://www.xycoon.com/estimation4.htm">normal equations</a>.
     * The intercept is sometimes denoted b0.</p>
     * <p>
     * <strong>Preconditions</strong>: <ul>
     * <li>At least two observations (with at least two different x values)
     * must have been added before invoking this method. If this method is
     * invoked before a model can be estimated, <code>Double,NaN</code> is
     * returned.
     * </li></ul>
     *
     * @return the intercept of the regression line if the model includes an
     * intercept; 0 otherwise
     * @see #SimpleRegression(boolean)
     */
    public double getIntercept() {
        return hasIntercept ? getIntercept(getSlope()) : 0.0;
    }

    /**
     * Returns true if the model includes an intercept term.
     *
     * @return true if the regression includes an intercept; false otherwise
     * @see #SimpleRegression(boolean)
     */
    public boolean hasIntercept() {
        return hasIntercept;
    }

    /**
    * Returns the slope of the estimated regression line.
    * <p>
    * The least squares estimate of the slope is computed using the
    * <a href="http://www.xycoon.com/estimation4.htm">normal equations</a>.
    * The slope is sometimes denoted b1.</p>
    * <p>
    * <strong>Preconditions</strong>: <ul>
    * <li>At least two observations (with at least two different x values)
    * must have been added before invoking this method. If this method is
    * invoked before a model can be estimated, <code>Double.NaN</code> is
    * returned.
    * </li></ul>
    *
    * @return the slope of the regression line
    */
    public double getSlope() {
        if (n < 2) {
            return Double.NaN; //not enough data
        }
        if (Math.abs(sumXX) < 10 * Double.MIN_VALUE) {
            return Double.NaN; //not enough variation in x
        }
        return sumXY / sumXX;
    }

    /**
     * Returns the <a href="http://www.xycoon.com/SumOfSquares.htm">
     * sum of squared errors</a> (SSE) associated with the regression
     * model.
     * <p>
     * The sum is computed using the computational formula</p>
     * <p>
     * <code>SSE = SYY - (SXY * SXY / SXX)</code></p>
     * <p>
     * where <code>SYY</code> is the sum of the squared deviations of the y
     * values about their mean, <code>SXX</code> is similarly defined and
     * <code>SXY</code> is the sum of the products of x and y mean deviations.
     * </p><p>
     * The sums are accumulated using the updating algorithm referenced in
     * {@link #addData}.</p>
     * <p>
     * The return value is constrained to be non-negative - i.e., if due to
     * rounding errors the computational formula returns a negative result,
     * 0 is returned.</p>
     * <p>
     * <strong>Preconditions</strong>: <ul>
     * <li>At least two observations (with at least two different x values)
     * must have been added before invoking this method. If this method is
     * invoked before a model can be estimated, <code>Double,NaN</code> is
     * returned.
     * </li></ul>
     *
     * @return sum of squared errors associated with the regression model
     */
    public double getSumSquaredErrors() {
        return Math.max(0d, sumYY - sumXY * sumXY / sumXX);
    }

    /**
     * Returns the sum of squared deviations of the y values about their mean.
     * <p>
     * This is defined as SSTO
     * <a href="http://www.xycoon.com/SumOfSquares.htm">here</a>.</p>
     * <p>
     * If {@code n < 2}, this returns <code>Double.NaN</code>.</p>
     *
     * @return sum of squared deviations of y values
     */
    public double getTotalSumSquares() {
        if (n < 2) {
            return Double.NaN;
        }
        return sumYY;
    }

    /**
     * Returns the sum of squared deviations of the x values about their mean.
     *
     * If <code>n &lt; 2</code>, this returns <code>Double.NaN</code>.
     *
     * @return sum of squared deviations of x values
     */
    public double getXSumSquares() {
        if (n < 2) {
            return Double.NaN;
        }
        return sumXX;
    }

    /**
     * Returns the sum of crossproducts, x<sub>i</sub>*y<sub>i</sub>.
     *
     * @return sum of cross products
     */
    public double getSumOfCrossProducts() {
        return sumXY;
    }

    /**
     * Returns the sum of squared deviations of the predicted y values about
     * their mean (which equals the mean of y).
     * <p>
     * This is usually abbreviated SSR or SSM.  It is defined as SSM
     * <a href="http://www.xycoon.com/SumOfSquares.htm">here</a></p>
     * <p>
     * <strong>Preconditions</strong>: <ul>
     * <li>At least two observations (with at least two different x values)
     * must have been added before invoking this method. If this method is
     * invoked before a model can be estimated, <code>Double.NaN</code> is
     * returned.
     * </li></ul>
     *
     * @return sum of squared deviations of predicted y values
     */
    public double getRegressionSumSquares() {
        return getRegressionSumSquares(getSlope());
    }

    /**
     * Returns the sum of squared errors divided by the degrees of freedom,
     * usually abbreviated MSE.
     * <p>
     * If there are fewer than <strong>three</strong> data pairs in the model,
     * or if there is no variation in <code>x</code>, this returns
     * <code>Double.NaN</code>.</p>
     *
     * @return sum of squared deviations of y values
     */
    public double getMeanSquareError() {
        if (n < 3) {
            return Double.NaN;
        }
        return hasIntercept ? (getSumSquaredErrors() / (n - 2)) : (getSumSquaredErrors() / (n - 1));
    }

    /**
     * Returns <a href="http://mathworld.wolfram.com/CorrelationCoefficient.html">
     * Pearson's product moment correlation coefficient</a>,
     * usually denoted r.
     * <p>
     * <strong>Preconditions</strong>: <ul>
     * <li>At least two observations (with at least two different x values)
     * must have been added before invoking this method. If this method is
     * invoked before a model can be estimated, <code>Double,NaN</code> is
     * returned.
     * </li></ul>
     *
     * @return Pearson's r
     */
    public double getR() {
        double b1 = getSlope();
        double result = Math.sqrt(getRSquare());
        if (b1 < 0) {
            result = -result;
        }
        return result;
    }

    /**
     * Returns the <a href="http://www.xycoon.com/coefficient1.htm">
     * coefficient of determination</a>,
     * usually denoted r-square.
     * <p>
     * <strong>Preconditions</strong>: <ul>
     * <li>At least two observations (with at least two different x values)
     * must have been added before invoking this method. If this method is
     * invoked before a model can be estimated, <code>Double,NaN</code> is
     * returned.
     * </li></ul>
     *
     * @return r-square
     */
    public double getRSquare() {
        double ssto = getTotalSumSquares();
        return (ssto - getSumSquaredErrors()) / ssto;
    }

    /**
     * Returns the <a href="http://www.xycoon.com/standarderrorb0.htm">
     * standard error of the intercept estimate</a>,
     * usually denoted s(b0).
     * <p>
     * If there are fewer that <strong>three</strong> observations in the
     * model, or if there is no variation in x, this returns
     * <code>Double.NaN</code>.</p> Additionally, a <code>Double.NaN</code> is
     * returned when the intercept is constrained to be zero
     *
     * @return standard error associated with intercept estimate
     */
    public double getInterceptStdErr() {
        if( !hasIntercept ){
            return Double.NaN;
        }
        return Math.sqrt(
            getMeanSquareError() * ((1d / n) + (xbar * xbar) / sumXX));
    }

    /**
     * Returns the <a href="http://www.xycoon.com/standerrorb(1).htm">standard
     * error of the slope estimate</a>,
     * usually denoted s(b1).
     * <p>
     * If there are fewer that <strong>three</strong> data pairs in the model,
     * or if there is no variation in x, this returns <code>Double.NaN</code>.
     * </p>
     *
     * @return standard error associated with slope estimate
     */
    public double getSlopeStdErr() {
        return Math.sqrt(getMeanSquareError() / sumXX);
    }

   


    // ---------------------Private methods-----------------------------------

    /**
    * Returns the intercept of the estimated regression line, given the slope.
    * <p>
    * Will return <code>NaN</code> if slope is <code>NaN</code>.</p>
    *
    * @param slope current slope
    * @return the intercept of the regression line
    */
    private double getIntercept(final double slope) {
      if( hasIntercept){
        return (sumY - slope * sumX) / n;
      }
      return 0.0;
    }

    /**
     * Computes SSR from b1.
     *
     * @param slope regression slope estimate
     * @return sum of squared deviations of predicted y values
     */
    private double getRegressionSumSquares(final double slope) {
        return slope * slope * sumXX;
    }

}
package org.yamcs.algorithms;

/**
 * Interface used by the algorithms to evaluate math operations.
 * 
 *  Should be unified with what is used for the calibrations, 
 *  once the calibrators will support using other parameters values as inputs
 * 
 * @author nm
 *
 */
public interface MathOperationEvaluator {
    double evaluate(double[] input);
}
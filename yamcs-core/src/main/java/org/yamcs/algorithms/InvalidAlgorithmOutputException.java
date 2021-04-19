package org.yamcs.algorithms;

import org.yamcs.xtce.Parameter;

/**
 * The output of the algorithm does not match the parameter it is supposed to be assigned to
 *
 */
public class InvalidAlgorithmOutputException extends Exception {
    final Parameter parameter;
    final OutputValueBinding output;

    public InvalidAlgorithmOutputException(Parameter parameter, OutputValueBinding output, String msg) {
        super(msg);
        this.parameter = parameter;
        this.output = output;
    }
}

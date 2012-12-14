package org.yamcs.xtce.xml;

public class XtceTerm {

    private int    exponent;
    private double coefficient;

    public int getExponent() {
        return exponent;
    }

    public double getCoefficient() {
        return coefficient;
    }

    public XtceTerm(int exponent, double coefficient) {
        this.exponent = exponent;
        this.coefficient = coefficient;
    }

}

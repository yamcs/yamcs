package org.yamcs.xtce;

public class MathAlgorithm extends Algorithm {
    private static final long serialVersionUID = 1L;
    TriggeredMathOperation operation;
    
    public MathAlgorithm(String name) {
        super(name);
    }

    public void setMathOperation(TriggeredMathOperation mo) {
        this.operation = mo;
    }

    public MathOperation getOperation() {
        return operation;
    }
}

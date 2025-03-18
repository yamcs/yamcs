package org.yamcs.xtce;

/**
 * A general calibrator - references a java class that can convert any raw value to any engineer value or the reverse.
 * <p>
 * Until Yamcs 5.12 only numerical calibrators were supported (as specified in XTCE). Starting with Yamcs 5.12 this
 * restriction is eliminated and this class can be used to reference an algorithm that performs this transformation.
 *
 */
public class AlgorithmCalibrator implements Calibrator {
    private static final long serialVersionUID = 1L;
    private final Algorithm algorithm;

    public AlgorithmCalibrator(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

    public Algorithm getAlgorithm() {
        return algorithm;
    }

    @Override
    public String toString() {
        return "AlgortihmCalibrator [" + algorithm + "]";
    }
}

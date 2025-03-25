package org.yamcs.mdb;

import org.yamcs.YConfiguration;
import org.yamcs.algorithms.AlgorithmExecutionContext;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.CustomAlgorithm;

/**
 * Interface to be implemented by all the java calibrators. These have been introduced in Yamcs 5.12 as a generalization
 * of the numerical calibrators existing in Yamcs before that version.
 */
public interface AlgorithmCalibratorProc extends CalibratorProc {

    /**
     * The init method is called once when the algorithm is loaded - that usually happens first time a parameter of this
     * type has to be processed in a processor.
     * <p>
     * The calibrator may be expected to work in both directions (raw to engineering and engineering to row).
     * <p>
     * The calibrator is supposed to generate values corresponding to the data type they are attached to. The type is
     * passed below in the init method.
     * 
     * @param alg
     *            - the algorithm that defines the calibrator. The text of the algorithm is used by the
     *            {@link CalibratorFactory} to find the implementing java class but other properties may be used by the
     *            init method.
     * @param ctx
     *            - the context of the algorithm, can be used to lookup the Yamcs instance and the processor where the
     *            algorithm is running
     * @param conf
     *            - the configuration is read from the algorithm text
     * @param dtype
     *            - the type for which the calibrator applies. Can be used by the implementing code to know which type
     *            the raw or the engineering value it should generate.
     */
    public void init(CustomAlgorithm alg, AlgorithmExecutionContext ctx, YConfiguration conf,
            BaseDataType dtype);
}

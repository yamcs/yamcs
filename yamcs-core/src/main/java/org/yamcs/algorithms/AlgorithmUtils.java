package org.yamcs.algorithms;

import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.InputParameter;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterInstanceRef;

public class AlgorithmUtils {

    public static int getLookbackSize(Algorithm algorithmDef, Parameter parameter) {
        // e.g. [ -3, -2, -1, 0 ]
        int min = 0;
        for (InputParameter p : algorithmDef.getInputSet()) {
            ParameterInstanceRef pInstance = p.getParameterInstance();
            if (pInstance != null && pInstance.getParameter().equals(parameter) && pInstance.getInstance() < min) {
                min = p.getParameterInstance().getInstance();
            }
        }
        return -min;
    }
}

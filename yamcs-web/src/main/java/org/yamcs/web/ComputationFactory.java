package org.yamcs.web;

import java.io.IOException;

import org.yamcs.ConfigurationException;
import org.yamcs.protobuf.Comp.ComputationDef;
import org.yamcs.utils.YObjectLoader;

/**
 * this is a placeholder to remove dependency on jformula in the open-source yamcs 
 * (the JFormulaCompuation is available as part of yamcs-cdmcs).
 * 
 * Anyway, we should probably convince the USS guys to switch to javascript instead of jformula
 * 
 * @author nm
 *
 */
public class ComputationFactory {
    public static Computation getComputation(ComputationDef cdef) throws ConfigurationException, IOException {
        YObjectLoader<Computation> objLoader = new YObjectLoader<Computation>();
        if("jformula".equals(cdef.getLanguage())) {
            return objLoader.loadObject("org.yamcs.web.JFormulaComputation", cdef);
        }
        throw new ConfigurationException("Unknown language '"+cdef.getLanguage()+"'");
    }
}

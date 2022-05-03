package org.yamcs.mdb;

import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.CustomAlgorithm;

public class DataEncoderFactory {
    public static DataEncoder get(Algorithm a) {
        if(!(a instanceof CustomAlgorithm)) {
            throw new XtceProcessingException("Unsupported algorithm: '"+a+"'. Only Java custom algorithms supported");
        }
        CustomAlgorithm ca = (CustomAlgorithm)a;

        if(!"java".equals(ca.getLanguage().toLowerCase())) {
            throw new XtceProcessingException("Unsupported language for Data Encoder: '"+ca.getLanguage()+"'. Only Java supported");
        }

        return DataDecoderFactory.loadJavaAlgo(ca, null);
    }

}

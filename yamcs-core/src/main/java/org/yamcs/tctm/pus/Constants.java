package org.yamcs.tctm.pus;

public class Constants {
    static final int DEFAULT_IMPLICIT_PFIELD = 46;
    static final int DEFAULT_PKT_TIME_OFFSET = 10;
    static final String CONFIG_KEY_ERROR_DETECTION = "errorDetection";
    static final String CONFIG_KEY_TIME_ENCODING = "timeEncoding";
    
    
    enum TimeEncodingType {
        CUC, CDS, NONE;
    }
}

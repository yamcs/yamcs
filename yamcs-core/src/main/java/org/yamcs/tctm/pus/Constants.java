package org.yamcs.tctm.pus;

public class Constants {
    //the offset of the time inside the regular PUS packets
    public static final int DEFAULT_PKT_TIME_OFFSET = 13;
   //the offset of the time inside the PUS time packets
    public static final int DEFAULT_TIME_PACKET_TIME_OFFSET = 7;
    
    final static int PFIELD_TCID_TAI = 1;// 001 1-Jan-1958 epoch
    final static int PFIELD_TCID_AGENCY_EPOCH = 2; // 010 agency defined epoch
    final static int PFIELD_TCID_CDS = 4; // 100 CCSDS DAY SEGMENTED TIME CODE
    final static int PFIELD_TCID_CCS = 5; // 101 CCSDS CALENDAR SEGMENTED TIME CODE
    final static int PFIELD_TCID_LEVEL34 = 6; // 110 Level 3 or 4 Agency-defined code (i.e. not defined in the standard)
 
    enum TimeEncodingType {
        CUC, CDS, NONE;
    }
}

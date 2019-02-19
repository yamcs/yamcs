package org.yamcs.cfdp;

import com.beust.jcommander.Parameter;

public class CfdpServerArgs {

    @Parameter(names = "--tctm-port")
    public Integer tctmPort;

    @Parameter(names = "--log-timestamp")
    public boolean logTimestamp = true;

}

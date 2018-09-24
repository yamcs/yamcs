package org.yamcs.tse;

import com.beust.jcommander.Parameter;

public class TseCommanderArgs {

    @Parameter(names = "--telnet-port")
    public int telnetPort = 8023;

    @Parameter(names = "--tctm-port")
    public Integer tctmPort;

    @Parameter(names = "--log-timestamp")
    public boolean logTimestamp = true;
}

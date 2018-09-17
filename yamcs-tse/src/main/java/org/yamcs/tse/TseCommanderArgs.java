package org.yamcs.tse;

import com.beust.jcommander.Parameter;

public class TseCommanderArgs {

    @Parameter(names = "--telnet-port")
    public int telnetPort = 8023;

    @Parameter(names = "--tc-port")
    public Integer tcPort;

    @Parameter(names = "--tm-host")
    public String tmHost;

    @Parameter(names = "--tm-port")
    public Integer tmPort;

    @Parameter(names = "--log-timestamp")
    public boolean logTimestamp = true;
}

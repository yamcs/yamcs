package org.yamcs.simulation.simulator;

import com.beust.jcommander.Parameter;

public class SimulatorArgs {

    @Parameter(names = "--telnet-port")
    public int telnetPort = 10023;

    @Parameter(names = "--tc-port")
    public Integer tcPort = 10025;
    
    @Parameter(names = "--tm-port")
    public Integer tmPort = 10015;
   
    @Parameter(names = "--tm2-port")
    public Integer tm2Port = 10016;
   
    @Parameter(names = "--los-port")
    public int losPort = 10115;

    
    @Parameter(names = "--perf-np", description = "performance test: number of packets")
    public int perfNp = 0;
    
    @Parameter(names = "--perf-ps", description = "performance test: packet size")
    public int perfPs = 1400;
    
    @Parameter(names = "--perf-ms", description = "performance test: interval in between packets in milliseconds")
    public long perfMs = 100l;

}

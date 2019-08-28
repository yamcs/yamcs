package org.yamcs.tse;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.converters.PathConverter;

public class TseCommanderArgs {

    @Parameter(names = "--telnet-port")
    public int telnetPort = 8023;

    @Parameter(names = "--tctm-port")
    public Integer tctmPort;

    @Parameter(names = "--log-timestamp")
    public boolean logTimestamp = true;

    @Parameter(names = { "--etc-dir" }, converter = PathConverter.class, description = "Path to config directory")
    public Path configDirectory = Paths.get("etc").toAbsolutePath();
}

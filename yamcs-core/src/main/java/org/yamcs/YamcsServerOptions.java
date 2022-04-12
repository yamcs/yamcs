package org.yamcs;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.converters.PathConverter;

import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetector.Level;

public class YamcsServerOptions {

    @Parameter(names = { "-v", "--version" }, description = "Print version information and quit")
    boolean version;

    @Parameter(names = "--check", description = "Run syntax tests on configuration files and quit")
    boolean check;

    @Parameter(names = "--log", description = "Level of verbosity")
    int verbose = 2;

    @Parameter(names = "--log-config", description = "File with log configuration", converter = PathConverter.class)
    Path logConfig;

    @Parameter(names = "--etc-dir", description = "Path to config directory", converter = PathConverter.class)
    Path configDirectory = Paths.get("etc").toAbsolutePath();

    @Parameter(names = "--data-dir", description = "Path to data directory", converter = PathConverter.class)
    Path dataDir;

    @Parameter(names = "--no-stream-redirect", description = "Do not redirect stdout/stderr to the log system")
    boolean noStreamRedirect;

    @Parameter(names = "--no-color", description = "Turn off console log colorization")
    boolean noColor;

    @Parameter(names = "--netty-leak-detection", description = "Enable leak detection (incurs overhead)", converter = LeakLevelConverter.class)
    ResourceLeakDetector.Level nettyLeakDetection = ResourceLeakDetector.Level.DISABLED;

    @Parameter(names = { "-h", "--help" }, help = true, hidden = true)
    boolean help;

    // Keep public, required by JCommander
    public static class LeakLevelConverter implements IStringConverter<ResourceLeakDetector.Level> {

        @Override
        public Level convert(String value) {
            try {
                return ResourceLeakDetector.Level.valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ParameterException(
                        "Unknown value for --netty-leak-detection. Possible values: "
                                + Arrays.asList(ResourceLeakDetector.Level.values()));
            }
        }
    }
}

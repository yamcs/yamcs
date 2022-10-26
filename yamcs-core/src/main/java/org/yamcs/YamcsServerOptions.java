package org.yamcs;

import java.nio.file.Path;
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
    Path configDirectory;

    @Parameter(names = "--data-dir", description = "Path to data directory", converter = PathConverter.class)
    Path dataDir;

    @Parameter(names = "--cache-dir", description = "Path to cache directory", converter = PathConverter.class)
    Path cacheDir;

    @Parameter(names = "--no-stream-redirect", description = "Do not redirect stdout/stderr to the log system")
    boolean noStreamRedirect;

    @Parameter(names = "--no-color", description = "Turn off console log colorization")
    boolean noColor;

    @Parameter(names = "--netty-leak-detection", description = "Enable leak detection (incurs overhead)", converter = LeakLevelConverter.class)
    ResourceLeakDetector.Level nettyLeakDetection = ResourceLeakDetector.Level.DISABLED;

    @Parameter(names = { "-h", "--help" }, help = true, hidden = true)
    boolean help;

    public YamcsServerOptions() {
        String envNoColor = System.getenv("YAMCS_NO_COLOR");
        if (envNoColor == null) { // envvar used by many other programs too
            envNoColor = System.getenv("NO_COLOR");
        }
        noColor = (envNoColor != null) ? !envNoColor.isEmpty() : false;

        String envEtcDir = System.getenv("YAMCS_ETC_DIR");
        configDirectory = Path.of(envEtcDir != null ? envEtcDir : "etc").toAbsolutePath();

        String envDataDir = System.getenv("YAMCS_DATA_DIR");
        dataDir = (envDataDir != null) ? Path.of(envDataDir).toAbsolutePath() : null;

        String envCacheDir = System.getenv("YAMCS_CACHE_DIR");
        cacheDir = (envCacheDir != null) ? Path.of(envCacheDir).toAbsolutePath() : null;
    }

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

    /**
     * Convert a string into an absolute, normalized path.
     */
    public class AbsolutePathConverter implements IStringConverter<Path> {

        @Override
        public Path convert(String value) {
            return Path.of(value).toAbsolutePath().normalize();
        }
    }
}

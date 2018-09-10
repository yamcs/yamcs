package org.yamcs.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsService;

import com.google.common.util.concurrent.AbstractService;

/**
 * Global service that launches and supervises a configured program or script. The primary purpose is to run non-java
 * code, or to decouple java code that uses a fragile or untested JNI layer.
 */
public class ProcessRunner extends AbstractService implements YamcsService {

    private static final Logger log = LoggerFactory.getLogger(ProcessRunner.class);

    private ProcessBuilder pb;
    private String logLevel;

    private Process process;
    private ScheduledExecutorService watchdog;

    public ProcessRunner(Map<String, Object> args) {
        pb = new ProcessBuilder(YConfiguration.getString(args, "command"));
        pb.redirectErrorStream(true);
        pb.environment().put("YAMCS", "1");

        if (args.containsKey("directory")) {
            pb.directory(new File(YConfiguration.getString(args, "directory")));
        }

        logLevel = YConfiguration.getString(args, "logLevel", "INFO");
    }

    @Override
    protected void doStart() {
        try {
            startProcess();
            notifyStarted();
        } catch (IOException e) {
            log.error("Failed to start process", e);
            notifyFailed(e);
            return;
        }

        watchdog = Executors.newSingleThreadScheduledExecutor();
        watchdog.scheduleWithFixedDelay(() -> {
            if (!process.isAlive() && isRunning()) {
                log.warn("Process terminated with exit value {}. Starting new process", process.exitValue());
                try {
                    startProcess();
                } catch (IOException e) {
                    log.error("Failed to start process", e);
                }
            }
        }, 5, 1, TimeUnit.SECONDS);
    }

    private void startProcess() throws IOException {
        process = pb.start();

        // Starts a thread for logging process output. The thread lifecycle is linked to the process.
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                reader.lines().forEach(line -> {
                    switch (logLevel) {
                    case "DEBUG":
                        log.debug(line);
                        break;
                    case "TRACE":
                        log.trace(line);
                        break;
                    case "WARN":
                        log.warn(line);
                        break;
                    case "ERROR":
                        log.error(line);
                        break;
                    default:
                        log.info(line);
                    }
                });
            } catch (IOException e) {
                log.error("Exception while gobbling process output", e);
            }
        }).start();
    }

    @Override
    protected void doStop() {
        watchdog.shutdown();
        process.destroy();
        notifyStopped();
    }
}

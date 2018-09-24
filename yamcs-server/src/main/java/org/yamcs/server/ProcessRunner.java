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

    protected Logger log;

    private ProcessBuilder pb;
    private String logLevel;
    private String logPrefix;

    private Process process;
    private ScheduledExecutorService watchdog;

    public ProcessRunner(Map<String, Object> args) {
        log = LoggerFactory.getLogger(getClass());

        if (YConfiguration.isList(args, "command")) {
            String[] command = YConfiguration.getList(args, "command").toArray(new String[0]);
            pb = new ProcessBuilder(command);
        } else {
            String command = YConfiguration.getString(args, "command");
            pb = new ProcessBuilder(command);
        }

        pb.redirectErrorStream(true);
        pb.environment().put("YAMCS", "1");

        if (args.containsKey("directory")) {
            pb.directory(new File(YConfiguration.getString(args, "directory")));
        }

        logLevel = YConfiguration.getString(args, "logLevel", "INFO");
        logPrefix = YConfiguration.getString(args, "logPrefix", "[" + pb.command().get(0) + "] ");
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
        }, 5, 5, TimeUnit.SECONDS);
    }

    private void startProcess() throws IOException {
        process = pb.start();

        // Start a thread for reading process output. The thread lifecycle is linked to the process.
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                reader.lines().forEach(line -> {
                    switch (logLevel) {
                    case "DEBUG":
                        log.debug("{}{}", logPrefix, line);
                        break;
                    case "TRACE":
                        log.trace("{}{}", logPrefix, line);
                        break;
                    case "WARN":
                        log.warn("{}{}", logPrefix, line);
                        break;
                    case "ERROR":
                        log.error("{}{}", logPrefix, line);
                        break;
                    default:
                        log.info("{}{}", logPrefix, line);
                    }
                    onProcessOutput(line);
                });
            } catch (IOException e) {
                log.error("Exception while gobbling process output", e);
            }
        }).start();
    }

    protected void onProcessOutput(String line) {
        // NOP by default
    }

    @Override
    protected void doStop() {
        watchdog.shutdown();
        process.destroy();
        notifyStopped();
    }
}

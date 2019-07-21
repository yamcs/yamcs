package org.yamcs;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.yamcs.api.AbstractYamcsService;
import org.yamcs.api.InitException;
import org.yamcs.api.Spec;
import org.yamcs.api.Spec.OptionType;

import com.google.common.base.CharMatcher;

/**
 * Global service that launches and supervises a configured program or script. The primary purpose is to run non-java
 * code, or to decouple java code that uses a fragile or untested JNI layer.
 */
public class ProcessRunner extends AbstractYamcsService {

    private ProcessBuilder pb;
    private String logLevel;
    private String logPrefix;

    private Process process;
    private ScheduledExecutorService watchdog;

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("command", OptionType.LIST_OR_ELEMENT).withElementType(OptionType.STRING)
                .withRequired(true);
        spec.addOption("directory", OptionType.STRING);
        spec.addOption("logLevel", OptionType.STRING).withDefault("INFO");
        spec.addOption("logPrefix", OptionType.STRING);
        return spec;
    }

    @Override
    public void init(String yamcsInstance, YConfiguration config) throws InitException {
        super.init(yamcsInstance, config);

        List<String> command = config.getList("command");
        pb = new ProcessBuilder(command);

        pb.redirectErrorStream(true);
        pb.environment().put("YAMCS", "1");

        if (config.containsKey("directory")) {
            pb.directory(new File(config.getString("directory")));
        }

        logLevel = config.getString("logLevel");
        logPrefix = config.getString("logPrefix", "[" + pb.command().get(0) + "] ");
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
                    line = CharMatcher.whitespace().trimTrailingFrom(line);
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

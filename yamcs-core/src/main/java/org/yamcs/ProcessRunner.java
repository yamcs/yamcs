package org.yamcs;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.yamcs.Spec.OptionType;

import com.google.common.base.CharMatcher;

/**
 * Global service that launches and supervises a configured program or script. The primary purpose is to run non-java
 * code, or to decouple java code that uses a fragile or untested JNI layer.
 */
public class ProcessRunner extends AbstractYamcsService {

    private static enum RestartMode {
        ALWAYS("always"),
        ON_SUCCESS("on-success"),
        ON_FAILURE("on-failure"),
        NEVER("never");

        String userOption;

        RestartMode(String userOption) {
            this.userOption = userOption;
        }

        static RestartMode fromUserOption(String userOption) {
            for (RestartMode value : values()) {
                if (value.userOption.equals(userOption)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Unexpected restart mode: '" + userOption + "'");
        }
    }

    private ProcessBuilder pb;
    private String logLevel;
    private String logPrefix;

    private Process process;
    private ScheduledFuture<?> watchdog;

    private RestartMode restartMode;
    private List<Integer> successExitCodes;

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("command", OptionType.LIST_OR_ELEMENT)
                .withElementType(OptionType.STRING)
                .withRequired(true);
        spec.addOption("directory", OptionType.STRING);
        spec.addOption("logLevel", OptionType.STRING).withDefault("INFO");
        spec.addOption("logPrefix", OptionType.STRING);
        spec.addOption("restart", OptionType.STRING)
                .withChoices("always", "on-success", "on-failure", "never")
                .withDefault("never");
        spec.addOption("successExitCode", OptionType.LIST_OR_ELEMENT)
                .withElementType(OptionType.INTEGER)
                .withDefault(0);
        spec.addOption("environment", OptionType.MAP).withSpec(Spec.ANY);
        return spec;
    }

    @Override
    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        super.init(yamcsInstance, serviceName, config);

        restartMode = RestartMode.fromUserOption(config.getString("restart"));
        successExitCodes = config.getList("successExitCode");

        List<String> command = config.getList("command");
        pb = new ProcessBuilder(command);

        pb.redirectErrorStream(true);
        pb.environment().put("YAMCS", "1");

        if (config.containsKey("environment")) {
            Map<String, Object> map = config.getMap("environment");
            for (var entry : map.entrySet()) {
                pb.environment().put(entry.getKey(), "" + entry.getValue());
            }
        }

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

        YamcsServer yamcs = YamcsServer.getServer();
        ScheduledExecutorService exec = yamcs.getThreadPoolExecutor();
        watchdog = exec.scheduleWithFixedDelay(() -> {
            if (!process.isAlive() && isRunning() && !yamcs.isShuttingDown()) {
                int code = process.exitValue();

                boolean restart = false;
                if (successExitCodes.contains(code)) {
                    if (restartMode == RestartMode.ALWAYS || restartMode == RestartMode.ON_SUCCESS) {
                        log.info("Process exited with code {}. Starting new process", code);
                        restart = true;
                    } else {
                        log.info("Process exited with code {}. Stopping service", code);
                        stopAsync();
                    }
                } else {
                    if (restartMode == RestartMode.ALWAYS || restartMode == RestartMode.ON_FAILURE) {
                        log.warn("Process exited with code {}. Starting new process", code);
                        restart = true;
                    } else {
                        log.warn("Process exited with code {}. Stopping service", code);
                        stopAsync();
                    }
                }

                if (restart) {
                    try {
                        startProcess();
                    } catch (IOException e) {
                        log.error("Failed to start process", e);
                    }
                }
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    private void startProcess() throws IOException {
        process = pb.start();

        // Start a thread for reading process output. The thread lifecycle is linked to the process.
        new Thread(() -> {
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
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
        }, getClass().getSimpleName() + " Gobbler").start();
    }

    protected void onProcessOutput(String line) {
        // NOP by default
    }

    @Override
    protected void doStop() {
        watchdog.cancel(true);
        process.destroy();

        // Give the process some time to stop before reporting success. During
        // shutdown, this reduces the chance of subprocess to be momentarily
        // alive after the main Yamcs process has already stopped.
        try {
            boolean exited = process.waitFor(1000, TimeUnit.MILLISECONDS);
            if (!exited) {
                // This is also no "guarantee", but we did our best.
                process.destroyForcibly();
            }
            notifyStopped();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

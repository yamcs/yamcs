package org.yamcs.activities;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.yamcs.YamcsServer;
import org.yamcs.http.HttpServer;
import org.yamcs.security.User;

import com.google.common.base.CharMatcher;

public class ScriptExecution extends ActivityExecution {

    private String processor;
    private String program;
    private List<String> scriptArgs;
    private User user;

    private Process process;

    public ScriptExecution(
            ActivityService activityService,
            ScriptExecutor executor,
            Activity activity,
            String processor,
            String program,
            List<String> scriptArgs,
            User user) {
        super(activityService, executor, activity);
        this.processor = processor;
        this.program = program;
        this.scriptArgs = scriptArgs;
        this.user = user;
    }

    @Override
    public Void run() throws Exception {
        var cmdline = program;
        for (var arg : scriptArgs) {
            cmdline += " " + arg;
        }

        var yamcs = YamcsServer.getServer();
        var securityStore = yamcs.getSecurityStore();
        var httpServer = yamcs.getGlobalService(HttpServer.class);

        String apiKey = null;
        if (securityStore.isEnabled()) {
            apiKey = securityStore.generateApiKey(user.getName());
        }

        try {
            var pb = new ProcessBuilder(cmdline.split("\\s+"));
            pb.environment().put("YAMCS", "1");
            pb.environment().put("YAMCS_INSTANCE", yamcsInstance);

            if (processor != null) {
                pb.environment().put("YAMCS_PROCESSOR", processor);
            }

            var url = httpServer.getBindings().iterator().next() + httpServer.getContextPath();
            pb.environment().put("YAMCS_URL", url);

            if (apiKey != null) {
                pb.environment().put("YAMCS_API_KEY", apiKey);
            }

            process = pb.start();
            logServiceInfo("Started process, pid=" + process.pid());

            new Thread(() -> {
                try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    reader.lines().forEach(line -> {
                        line = CharMatcher.whitespace().trimTrailingFrom(line);
                        logActivityInfo(line);
                    });
                } catch (IOException e) {
                    log.error("Exception while gobbling process output", e);
                }
            }, getClass().getSimpleName() + " Gobbler").start();

            new Thread(() -> {
                try (var reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    reader.lines().forEach(line -> {
                        line = CharMatcher.whitespace().trimTrailingFrom(line);
                        logActivityError(line);
                    });
                } catch (IOException e) {
                    log.error("Exception while gobbling process error output", e);
                }
            }, getClass().getSimpleName() + " Gobbler").start();

            process.waitFor();
            var exitValue = process.exitValue();
            if (exitValue == 0) {
                logServiceInfo("Process has terminated");
            } else {
                var errorMessage = "Process returned with exit value " + exitValue;
                logServiceError(errorMessage);
                throw new RuntimeException(errorMessage);
            }
            return null;
        } finally {
            if (apiKey != null) {
                securityStore.removeApiKey(apiKey);
            }
        }
    }

    @Override
    public void stop() throws Exception {
        if (process != null && process.isAlive()) {
            log.debug("Destroying process {}", process.pid());
            process.destroy();
            process.onExit().get(2000, TimeUnit.MILLISECONDS);
            if (process.isAlive()) {
                log.debug("Forcing destroy of process {}", process.pid());
                process.destroyForcibly();
                process.onExit().get();
            }
        }
    }
}

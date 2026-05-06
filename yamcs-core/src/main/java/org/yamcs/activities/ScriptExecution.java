package org.yamcs.activities;

import java.util.List;

import org.yamcs.logging.Log;
import org.yamcs.security.User;

public class ScriptExecution extends ActivityExecution {

    private ScriptRunner runner;

    private String processor;
    private String script;
    private List<String> scriptArgs;
    private User user;

    private ScriptRun scriptRun;

    public ScriptExecution(
            ActivityService activityService,
            ScriptExecutor executor,
            Activity activity,
            ScriptRunner runner,
            String processor,
            String script,
            List<String> scriptArgs,
            User user) {
        super(activityService, executor, activity);
        this.runner = runner;
        this.processor = processor;
        this.script = script;
        this.scriptArgs = scriptArgs;
        this.user = user;
    }

    @Override
    public Void run() throws Exception {
        scriptRun = runner.createRun(script, scriptArgs);
        scriptRun.run(this);
        return null;
    }

    @Override
    public void stop() throws Exception {
        scriptRun.stop(this);
    }

    public Log getLog() {
        return log;
    }

    public String getYamcsInstance() {
        return yamcsInstance;
    }

    public String getProcessor() {
        return processor;
    }

    public User getUser() {
        return user;
    }
}

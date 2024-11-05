package org.yamcs.algorithms;

import java.util.List;
import java.util.Map;

import javax.script.ScriptEngineManager;

import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;

public class ScriptAlgorithmEngine implements AlgorithmEngine {

    @Override
    @SuppressWarnings("unchecked")
    public AlgorithmExecutorFactory makeExecutorFactory(AlgorithmManager algorithmManager,
            AlgorithmExecutionContext context, String language, YConfiguration config) {
        List<String> libs = null;
        Map<String, List<String>> libraries = (Map<String, List<String>>) config.get("libraries");
        if (libraries != null) {
            libs = libraries.get(language);
        }
        ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
        scriptEngineManager.put("EventLog", new EventLogFunctions(algorithmManager.getYamcsInstance()));
        scriptEngineManager.put("Verifier", new VerifierFunctions());
        scriptEngineManager.put("Yamcs", new AlgorithmFunctions(algorithmManager.getProcessor(), context));

        // add the link manager functions but only if the link manager is present (some units tests will not have this)
        var ysi = YamcsServer.getServer().getInstance(algorithmManager.getYamcsInstance());
        if (ysi != null) {
            var linkManager = ysi.getLinkManager();
            if (linkManager != null) {
                scriptEngineManager.put("Links", new LinksFunctions(linkManager));
            }
        }

        return new ScriptAlgorithmExecutorFactory(scriptEngineManager, language, libs);

    }
}

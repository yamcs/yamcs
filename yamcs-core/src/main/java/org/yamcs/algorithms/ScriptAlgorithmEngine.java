package org.yamcs.algorithms;

import java.util.List;
import java.util.Map;

import javax.script.ScriptEngineManager;

import org.yamcs.YConfiguration;

public class ScriptAlgorithmEngine implements AlgorithmEngine {

    @Override
    public AlgorithmExecutorFactory makeExecutorFactory(AlgorithmManager algorithmManager, String language,
            YConfiguration config) {
        List<String> libs = null;
        Map<String, List<String>> libraries = (Map<String, List<String>>) config.get("libraries");
        if (libraries != null) {
            libs = libraries.get(language);
        }
        ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
        scriptEngineManager.put("Yamcs", new AlgorithmUtils(algorithmManager.getProcessor()));

        return new ScriptAlgorithmExecutorFactory(scriptEngineManager, language, libs);

    }

}

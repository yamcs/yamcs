package org.yamcs.algorithms;

import java.util.List;
import java.util.Map;

import javax.script.ScriptEngineManager;

public class ScriptAlgorithmEngine implements AlgorithmEngine {
    
    @Override
    public AlgorithmExecutorFactory makeExecutorFactory(AlgorithmManager algorithmManager, String language, Map<String, Object> config) {
        List<String> libs = null;
        if (config != null) {
            Map<String, List<String>> libraries = (Map<String, List<String>>) config.get("libraries");
            if(libraries!=null) {
               libs = libraries.get(language);
            }
        }
        ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
        scriptEngineManager.put("Yamcs", new AlgorithmUtils(algorithmManager.getProcessor()));

        return new ScriptAlgorithmExecutorFactory(scriptEngineManager, language, libs);
        
    }

}

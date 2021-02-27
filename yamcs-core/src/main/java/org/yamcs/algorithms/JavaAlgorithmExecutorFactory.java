package org.yamcs.algorithms;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.xtce.CustomAlgorithm;
import org.yaml.snakeyaml.Yaml;

public class JavaAlgorithmExecutorFactory implements AlgorithmExecutorFactory {
    private static final Logger log = LoggerFactory.getLogger(JavaAlgorithmExecutorFactory.class);

    @Override
    public AlgorithmExecutor makeExecutor(CustomAlgorithm alg, AlgorithmExecutionContext execCtx) {

        Pattern p = Pattern.compile("([\\w\\$\\.]+)(\\(.*\\))?", Pattern.DOTALL);
        Matcher m = p.matcher(alg.getAlgorithmText().trim());
        if (!m.matches()) {
            log.warn("Cannot parse algorithm text '{}'", alg.getAlgorithmText());
            throw new IllegalArgumentException("Cannot parse algorithm text '" + alg.getAlgorithmText() + "'");
        }
        String className = m.group(1);

        String s = m.group(2); // this includes the parentheses
        Object arg = null;
        if (s != null && s.length() > 2) {
            // s.length>2 is to make sure there is something in between the parentheses
            Yaml yaml = new Yaml();
            arg = yaml.load(s.substring(1, s.length() - 1));
        }

        if (arg == null) {
            return YObjectLoader.loadObject(className, alg, execCtx);
        } else {
            return YObjectLoader.loadObject(className, alg, execCtx, arg);
        }
    }

    @Override
    public List<String> getLanguages() {
        return Arrays.asList("java", "Java");
    }
}

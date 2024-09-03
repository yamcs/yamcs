package org.yamcs.mdb;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yamcs.algorithms.AlgorithmExecutionContext;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.CustomAlgorithm;
import org.yaml.snakeyaml.Yaml;

public class DataDecoderFactory {

    public static DataDecoder get(Algorithm a, ProcessorData pdata) {
        if (!(a instanceof CustomAlgorithm)) {
            throw new XtceProcessingException(
                    "Unsupported algorithm: '" + a + "'. Only Java custom algorithms supported");
        }
        CustomAlgorithm ca = (CustomAlgorithm) a;

        if (!"java".equals(ca.getLanguage().toLowerCase())) {
            throw new XtceProcessingException(
                    "Unsupported language for Data Decoder: '" + ca.getLanguage() + "'. Only Java supported");
        }

        AlgorithmExecutionContext execCtx = new AlgorithmExecutionContext("DataDecoder", pdata, Integer.MAX_VALUE);
        return loadJavaAlgo(ca, execCtx);
    }

    static <T> T loadJavaAlgo(CustomAlgorithm alg, AlgorithmExecutionContext execCtx) {
        Pattern p = Pattern.compile("([\\w\\$\\.]+)(\\(.*\\))?", Pattern.DOTALL);
        Matcher m = p.matcher(alg.getAlgorithmText().trim());
        if (!m.matches()) {
            throw new XtceProcessingException("Cannot parse algorithm text '" + alg.getAlgorithmText() + "'");
        }
        String className = m.group(1);

        String s = m.group(2); // this includes the parentheses
        Object arg = null;
        if (s != null && s.length() > 2) {// s.length>2 is to make sure there is something in between the parentheses
            Yaml yaml = new Yaml();
            arg = yaml.load(s.substring(1, s.length() - 1));
        }

        if (arg == null) {
            return YObjectLoader.loadObject(className, alg, execCtx);
        } else {
            return YObjectLoader.loadObject(className, alg, execCtx, arg);
        }
    }
}

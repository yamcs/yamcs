package org.yamcs.xtceproc;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yamcs.algorithms.AlgorithmExecutionContext;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.CustomAlgorithm;
import org.yaml.snakeyaml.Yaml;

public class DataDecoderFactory {

    public static DataDecoder get(Algorithm a) {
        if(!(a instanceof CustomAlgorithm)) {
            throw new XtceProcessingException("Unsupported algorithm: '"+a+"'. Only Java custom algorithms supported");
        }
        CustomAlgorithm ca = (CustomAlgorithm)a;

        if(!"java".equals(ca.getLanguage().toLowerCase())) {
            throw new XtceProcessingException("Unsupported language for Data Decoder: '"+ca.getLanguage()+"'. Only Java supported");
        }

        return loadJavaDecoder(ca, null);
    }


    private static DataDecoder loadJavaDecoder(CustomAlgorithm alg, AlgorithmExecutionContext execCtx) {
        Pattern p = Pattern.compile("([\\w\\$\\.]+)(\\(.*\\))?", Pattern.DOTALL);
        Matcher m = p.matcher(alg.getAlgorithmText());
        if(!m.matches()) {
            throw new XtceProcessingException("Cannot parse algorithm text '"+alg.getAlgorithmText()+"'");
        }
        String className = m.group(1);

        try {
            String s = m.group(2); //this includes the parentheses
            Object arg = null;
            if(s!=null && s.length()>2) {//s.length>2 is to make sure there is something in between the parentheses
                Yaml yaml = new Yaml();
                arg = yaml.load(s.substring(1, s.length()-1));
            }

            if(arg==null){
                return YObjectLoader.loadObject(className, alg, execCtx);
            } else {
                return YObjectLoader.loadObject(className, alg, execCtx, arg);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }
}

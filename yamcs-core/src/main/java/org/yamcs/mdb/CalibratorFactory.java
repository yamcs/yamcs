package org.yamcs.mdb;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yamcs.YConfiguration;
import org.yamcs.algorithms.AlgorithmExecutionContext;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.AlgorithmCalibrator;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.Calibrator;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.JavaExpressionCalibrator;
import org.yamcs.xtce.MathOperationCalibrator;
import org.yamcs.xtce.PolynomialCalibrator;
import org.yamcs.xtce.SplineCalibrator;
import org.yaml.snakeyaml.Yaml;

/**
 * Factory for the different supported calibrators
 */
public class CalibratorFactory {
    public static CalibratorProc get(Calibrator c, BaseDataType dataType, ProcessorData pdata) {
        if (c instanceof PolynomialCalibrator pc) {
            return new NumericCalibratorProc(dataType, new PolynomialCalibratorProc(pc));
        } else if (c instanceof SplineCalibrator sc) {
            return new NumericCalibratorProc(dataType, new SplineCalibratorProc(sc));
        } else if (c instanceof JavaExpressionCalibrator jec) {
            return new NumericCalibratorProc(dataType, JavaExpressionNumericCalibratorFactory.compile(jec));
        } else if (c instanceof MathOperationCalibrator moc) {
            return new NumericCalibratorProc(dataType, MathOperationCalibratorFactory.compile(moc));
        } else if (c instanceof AlgorithmCalibrator ac) {
            AlgorithmExecutionContext execCtx = new AlgorithmExecutionContext("Calibrator", pdata, Integer.MAX_VALUE);
            return loadAlgorithmCalibrator(ac.getAlgorithm(), execCtx, dataType);
        } else {
            throw new IllegalStateException("No calibrator processor for " + c);
        }
    }

    static AlgorithmCalibratorProc loadAlgorithmCalibrator(Algorithm a, AlgorithmExecutionContext execCtx,
            BaseDataType dtype) {
        if (!(a instanceof CustomAlgorithm)) {
            throw new XtceProcessingException(
                    "Unsupported algorithm: '" + a + "'. Only Java custom algorithms supported");
        }
        CustomAlgorithm calg = (CustomAlgorithm) a;

        if (!"java".equals(calg.getLanguage().toLowerCase())) {
            throw new XtceProcessingException(
                    "Unsupported language for Calibrator: '" + calg.getLanguage() + "'. Only Java supported");
        }

        Pattern p = Pattern.compile("([\\w\\$\\.]+)(\\(.*\\))?", Pattern.DOTALL);
        Matcher m = p.matcher(calg.getAlgorithmText().trim());
        if (!m.matches()) {
            throw new XtceProcessingException("Cannot parse algorithm text '" + calg.getAlgorithmText() + "'");
        }
        String className = m.group(1);

        String s = m.group(2); // this includes the parentheses
        YConfiguration conf;
        if (s != null && s.length() > 2) {// s.length>2 is to make sure there is something in between the parentheses
            Yaml yaml = new Yaml();
            conf = YConfiguration.wrap(yaml.load(s.substring(1, s.length() - 1)));
        } else {
            conf = YConfiguration.emptyConfig();
        }

        AlgorithmCalibratorProc calib = YObjectLoader.loadObject(className);
        calib.init(calg, execCtx, conf, dtype);
        return calib;
    }
}

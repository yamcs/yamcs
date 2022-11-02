package org.yamcs.mdb;

import org.codehaus.commons.compiler.LocatedException;
import org.codehaus.commons.compiler.Location;
import org.codehaus.janino.SimpleCompiler;
import org.yamcs.xtce.JavaExpressionCalibrator;

public class JavaExpressionCalibratorFactory {
    /**
     * Compiles the expression into an executable calibrator processor
     * @param c
     * @return a calibrator processor
     * @throws IllegalArgumentException if the expression cannot be compiled
     */
    public static CalibratorProc compile(JavaExpressionCalibrator c) {
        String className = "Expression"+c.hashCode();
        StringBuilder sb = new StringBuilder();
        sb.append("package org.yamcs.mdb.jecf;\n")
                .append("public class ").append(className).append(" implements org.yamcs.mdb.CalibratorProc {\n")
        .append("   public double calibrate(double v) {\n")
        .append("return ").append(c.getFormula()).append(";\n")
        .append("   }\n")
        .append("}\n");
        try {
            SimpleCompiler compiler = new SimpleCompiler();
            compiler.cook(sb.toString());
            Class<?> cexprClass = compiler.getClassLoader().loadClass("org.yamcs.mdb.jecf." + className);
            return (CalibratorProc) cexprClass.newInstance();
        } catch (LocatedException e) {
            String msg = e.getMessage();
            Location l = e.getLocation();
            if(l!=null) {
                //we change the location in the message because it refers to the fabricated code
                //it is still not perfect, if the expression is not properly closed
                // , janino will complain about the next line that the user doesn't know about...
                Location l1 = new Location(null, (short)(l.getLineNumber()-3), (short)(l.getColumnNumber()-7)); 
                msg = l1.toString()+": "+msg.substring(l.toString().length()+1);
            }
            throw new IllegalArgumentException("Cannot compile expression '"+c.getFormula()+"': "+msg, e);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot compile expression '"+c.getFormula()+"'", e);
        }
    }
}

package org.yamcs.utils;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.xtce.AggregateParameterType;
import org.yamcs.xtce.ArrayParameterType;
import org.yamcs.xtce.Member;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.PathElement;

import com.google.common.base.Splitter;

/**
 * operations to aggrgates or arrays
 * 
 * @author nm
 *
 */
public class AggregateUtil {
    /**
     * finds the first occurrence of . or [
     * @param s
     * @return
     */
    public static int findSeparator(String s) {
        for (int i = 0; i < s.length(); i++) {
            if ((s.charAt(i) == '.') || (s.charAt(i) == '[')) {
                return i;
            }
        }
        return -1;
    }
    
    
    /**
     * parses a reference of shape
     * <pre>
     * x.y[3][4].z
     * </pre> 
     * into an array of PathElement
     * 
     * @param name
     * @return
     */
    public static PathElement[] parseReference(String name) {
        List<PathElement> tmp = new ArrayList<>();
        for (String ps : Splitter.on('.').omitEmptyStrings().split(name)) {
            tmp.add(PathElement.fromString(ps));
        }
        return tmp.toArray(new PathElement[0]);
    }

    /**
     * Verify that the path exists in the parameter type
     * 
     * @param parameterType
     * @param path
     * @return
     */
    public static boolean verifyPath(ParameterType parameterType, PathElement[] path) {
        ParameterType ptype = parameterType;
       for(PathElement pe: path) {
           if(pe.getName()!=null) {
               if(!(ptype instanceof AggregateParameterType)) {
                   return false;
               }
               Member m = ((AggregateParameterType)ptype).getMember(pe.getName());
               if(m==null) {
                   return false;
               }
               ptype = (ParameterType) m.getType();
           }
           if(pe.getIndex()!=null) {
               int[] idx = pe.getIndex();
               if(!(ptype instanceof ArrayParameterType)) {
                   return false;
               }
               ArrayParameterType at = (ArrayParameterType)ptype;
               if(at.getNumberOfDimensions()!=idx.length) {
                   return false;
               }
               ptype = (ParameterType) at.getElementType();
           }
       }
       
       return true;
    }

    /**
     * Create a parameter value with a member from the passed parameter value as found following the path.
     * 
     * Returns null if there is no such member.
     * 
     * @param pv
     * @param path
     * @return
     */
    public static ParameterValue extractMember(ParameterValue pv, PathElement[] path) {
        Value engValue = pv.getEngValue();
        Value rawValue = pv.getRawValue();
        for (PathElement pe : path) {
            if (pe.getName() != null) {
                engValue = ((AggregateValue) engValue).getMemberValue(pe.getName());
                if (rawValue != null) {
                    rawValue = ((AggregateValue) rawValue).getMemberValue(pe.getName());
                }
            }
            int[] idx = pe.getIndex();
            if (idx != null) {
                ArrayValue av = (ArrayValue) engValue;
                if(!av.hasElement(idx)) {
                    return null;
                }
                engValue = av.getElementValue(idx);
                if (engValue == null) {
                    return null;
                }
                if (rawValue != null) {
                    rawValue = ((ArrayValue) rawValue).getElementValue(idx);
                }
            }
        }
        ParameterValue pv1 = new ParameterValue(pv.getParameter());
        pv1.setEngineeringValue(engValue);
        pv1.setRawValue(rawValue);
        pv1.setGenerationTime(pv.getGenerationTime());
        pv1.setAcquisitionTime(pv.getAcquisitionTime());

        return pv1;
    }
}

package org.yamcs.xtce.util;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.xtce.AggregateParameterType;
import org.yamcs.xtce.ArrayParameterType;
import org.yamcs.xtce.Member;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.PathElement;


/**
 * operations to aggregates or arrays
 * 
 * @author nm
 *
 */
public class AggregateTypeUtil {
    /**
     * finds the first occurrence of . or [ after the last /
     * 
     * @param s
     * @return the position of the first occurrence of . or [ after the last slash; returns -1 if not found
     */
    public static int findSeparator(String s) {
        int found = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (found == -1 && ((c == '.') || (c == '['))) {
                found = i;
            } else if (c == '/') {
                found = -1;
            }
        }
        return found;
    }

    /**
     * parses a reference of shape
     * 
     * <pre>
     * x.y[3][4].z
     * </pre>
     * 
     * into an array of PathElement
     * 
     * @param name
     * @return
     */
    public static PathElement[] parseReference(String name) {
        List<PathElement> tmp = new ArrayList<>();
        String[] a = name.split("\\.");
        for (String ps : a) {
            if (!ps.isEmpty()) {
                tmp.add(PathElement.fromString(ps));
            }
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
        for (PathElement pe : path) {
            if (pe.getName() != null) {
                if (!(ptype instanceof AggregateParameterType)) {
                    return false;
                }
                Member m = ((AggregateParameterType) ptype).getMember(pe.getName());
                if (m == null) {
                    return false;
                }
                ptype = (ParameterType) m.getType();
            }
            if (pe.getIndex() != null) {
                int[] idx = pe.getIndex();
                if (!(ptype instanceof ArrayParameterType)) {
                    return false;
                }
                ArrayParameterType at = (ArrayParameterType) ptype;
                if (at.getNumberOfDimensions() != idx.length) {
                    return false;
                }
                ptype = (ParameterType) at.getElementType();
            }
        }
        return true;
    }

    public static ParameterType getMemberType(ParameterType parameterType, PathElement[] path) {
        ParameterType ptype = parameterType;
        for (PathElement pe : path) {
            if (pe.getName() != null) {
                if (!(ptype instanceof AggregateParameterType)) {
                    return null;
                }
                Member m = ((AggregateParameterType) ptype).getMember(pe.getName());
                if (m == null) {
                    return null;
                }
                ptype = (ParameterType) m.getType();
            }
            if (pe.getIndex() != null) {
                int[] idx = pe.getIndex();
                if (!(ptype instanceof ArrayParameterType)) {
                    return null;
                }
                ArrayParameterType at = (ArrayParameterType) ptype;
                if (at.getNumberOfDimensions() != idx.length) {
                    return null;
                }
                ptype = (ParameterType) at.getElementType();
            }
        }
        return ptype;
    }


    public static String toString(PathElement[] path) {
        StringBuilder sb = new StringBuilder();
        for (PathElement pe : path) {
            if (pe.getName() != null) {
                sb.append(".").append(pe.getName());
            }
            if (pe.getIndex() != null) {
                for(int x: pe.getIndex()) {
                    sb.append("[").append(x).append("]");
                }
            }
        }
        return sb.toString();
    }

}

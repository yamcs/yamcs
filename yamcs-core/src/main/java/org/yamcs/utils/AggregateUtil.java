package org.yamcs.utils;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.commanding.ArgumentValue;
import org.yamcs.commanding.PartialArgumentValue;
import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.PartialParameterValue;
import org.yamcs.parameter.RawEngValue;
import org.yamcs.parameter.Value;
import org.yamcs.xtce.AggregateParameterType;
import org.yamcs.xtce.ArrayParameterType;
import org.yamcs.xtce.Member;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.PathElement;
import org.yamcs.xtce.util.AggregateMemberNames;

import com.google.common.base.Splitter;

/**
 * operations to aggregates or arrays
 * 
 * @author nm
 *
 */
public class AggregateUtil {
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
     * Parses a reference of shape
     * 
     * <pre>
     * /a/b/c/x.y[3][4].z
     * </pre>
     * 
     * into an array of PathElement: {"a/b/c/x", "y"[3,4], "z"}
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

    /**
     * Create a parameter value with a member from the passed parameter value as found following the path.
     * 
     * Returns null if there is no such member.
     * 
     * @param rev
     * @param path
     * @return
     */
    public static <T extends RawEngValue> T extractMember(T rev, PathElement[] path) {
        Value engValue = rev.getEngValue();
        Value rawValue = rev.getRawValue();
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
                if (!av.hasElement(idx)) {
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
        if (rev instanceof ParameterValue) {
            ParameterValue pv = (ParameterValue) rev;
            PartialParameterValue pv1 = new PartialParameterValue(pv.getParameter(), path);
            pv1.setEngValue(engValue);
            pv1.setRawValue(rawValue);
            pv1.setGenerationTime(rev.getGenerationTime());
            pv1.setAcquisitionTime(pv.getAcquisitionTime());
            pv1.setAcquisitionStatus(pv.getAcquisitionStatus());
            pv1.setExpireMillis(pv.getExpireMills());
            return (T) pv1;
        } else {
            ArgumentValue av = (ArgumentValue) rev;
            PartialArgumentValue av1 = new PartialArgumentValue(av.getArgument(), path);
            av1.setEngValue(engValue);
            av1.setRawValue(rawValue);
            av1.setGenerationTime(rev.getGenerationTime());
            return (T) av;
        }
    }

    /**
     * Patches a parameter value with a new value for one member of an aggregate or array
     * 
     * Currently this does not extend an array
     * 
     * @param pv
     * @param patch
     * @throws IllegalArgumentException
     *             if the member to be updated or the array element does not exist
     */
    public static void updateMember(ParameterValue pv, PartialParameterValue patch) {
        Value engValue = pv.getEngValue();
        Value rawValue = pv.getRawValue();
        PathElement[] path = patch.getPath();

        for (int i = 0; i < path.length - 1; i++) {
            PathElement pe = path[i];
            if (pe.getName() != null) {
                engValue = ((AggregateValue) engValue).getMemberValue(pe.getName());
                if (rawValue != null) {
                    rawValue = ((AggregateValue) rawValue).getMemberValue(pe.getName());
                }
            }
            int[] idx = pe.getIndex();
            if (idx != null) {
                ArrayValue av = (ArrayValue) engValue;
                if (!av.hasElement(idx)) {
                    throw new IllegalArgumentException("Invalid path element (array element does not exist) ");
                }
                engValue = av.getElementValue(idx);
                if (engValue == null) {
                    throw new IllegalArgumentException("Invalid path element");
                }
                if (rawValue != null) {
                    rawValue = ((ArrayValue) rawValue).getElementValue(idx);
                }
            }
        }
        PathElement pe = path[path.length - 1];
        if (pe.getIndex() == null) {
            ((AggregateValue) engValue).setMemberValue(pe.getName(), patch.getEngValue());
            if (rawValue != null && patch.getRawValue() != null) {
                ((AggregateValue) rawValue).setMemberValue(pe.getName(), patch.getRawValue());
            }
        } else {
            if (pe.getName() != null) {
                engValue = ((AggregateValue) engValue).getMemberValue(pe.getName());
                if (rawValue != null) {
                    rawValue = ((AggregateValue) rawValue).getMemberValue(pe.getName());
                }
            }
            ((ArrayValue) engValue).setElementValue(pe.getIndex(), patch.getEngValue());
            if (rawValue != null && patch.getRawValue() != null) {
                ((ArrayValue) rawValue).setElementValue(pe.getIndex(), patch.getRawValue());
            }
        }
    }

    /**
     * This function is used to retrieve values from hierarchical aggregates.
     * 
     * It is equivalent with a chain of getMemberValue() calls:
     * 
     * <pre>
     *   getMemberValue(getMemberValue(getMemberValue(value, path[0]),path[1])...,path[n])
     * </pre>
     * 
     * It returns null if the path does not lead to a valid aggregate member.
     * 
     * @param path
     *            - the path to be traversed, can be empty.
     * @return the member value found by traversing the path or null if no such member exists. In case the path is
     *         empty, this value itself will be returned.
     */
    public static Value getMemberValue(Value value, PathElement[] path) {
        Value v = value;
        for (int i = 0; i < path.length; i++) {
            PathElement pe = path[i];
            String name = pe.getName();
            int[] idx = pe.getIndex();
            if (v instanceof AggregateValue) {
                if (name == null) {
                    return null;
                }
                v = ((AggregateValue) v).getMemberValue(name);
            } else if (name != null) {
                return null;
            }

            if (v instanceof ArrayValue) {
                if (idx == null) {
                    return null;
                }
                v = ((ArrayValue) v).getElementValue(idx);
            } else if (idx != null) {
                return null;
            }

            if (v == null) {
                return null;
            }

        }
        return v;
    }

    public static String toString(PathElement[] path) {
        StringBuilder sb = new StringBuilder();
        for (PathElement pe : path) {
            if (pe.getName() != null) {
                sb.append(".").append(pe.getName());
            }
            if (pe.getIndex() != null) {
                for (int x : pe.getIndex()) {
                    sb.append("[").append(x).append("]");
                }
            }
        }
        return sb.toString();
    }

    public static AggregateParameterType createParameterType(String name, AggregateValue v) {
        AggregateParameterType.Builder apt = new AggregateParameterType.Builder();
        apt.setName(name);
        AggregateMemberNames amn = v.getMemberNames();
        for (int i = 0; i < amn.size(); i++) {
            apt.addMember(new Member(amn.get(i)));
        }
        return apt.build();
    }
}

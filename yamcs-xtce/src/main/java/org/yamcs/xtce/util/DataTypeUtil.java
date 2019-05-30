package org.yamcs.xtce.util;

import org.yamcs.xtce.AggregateDataType;
import org.yamcs.xtce.ArrayDataType;
import org.yamcs.xtce.DataType;
import org.yamcs.xtce.Member;
import org.yamcs.xtce.PathElement;

public class DataTypeUtil {
    /**
     * traverses the type hierarchy to retrieve the type referenced by path
     * 
     * @param type
     *            - the type for which the hierarchy is traversed
     * @param path
     *            - the elements used to traverse the hierarchy
     * @return - the found sub-member of the type or null if no member has been found.
     */
    public static DataType getMemberType(DataType type, PathElement[] path) {
        DataType ptype = type;
        if (path.length == 0) {
            throw new IllegalArgumentException("path cannot be empty");
        }

        for (PathElement pe : path) {
            String name = pe.getName();
            if (ptype instanceof AggregateDataType) {
                if (name == null) {
                    return null;
                }
                Member m = ((AggregateDataType) ptype).getMember(name);
                if (m == null) {
                    return null;
                }
                ptype = m.getType();
            } else if (name != null) {
                return null;
            }

            if (ptype instanceof ArrayDataType) {
                if (pe.getIndex() != null) {
                    ptype = ((ArrayDataType) ptype).getElementType();
                } else {
                    return null;
                }
            }
        }
        return ptype;
    }
}

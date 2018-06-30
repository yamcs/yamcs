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
     * @param memberPath
     * @return
     */
    public static DataType getMemberType(DataType type, PathElement[] path) {
        DataType ptype = type;
        if (path.length == 0) {
            throw new IllegalArgumentException("path cannot be empty");
        }

        for (PathElement pe : path) {
            String name = pe.getName();
            if (type instanceof AggregateDataType) {
                if (name == null) {
                    return null;
                }
                Member m = ((AggregateDataType) type).getMember(name);
                if(m==null) {
                    return null;
                }
                ptype = m.getType();
            } else if (name != null) {
                return null;
            }

            if (type instanceof ArrayDataType) {
                if(pe.getIndex()!=null) {
                    ptype = ((ArrayDataType) type).getElementType();
                } else {
                    return null;
                }
            }          
        }
        return ptype;
    }

}

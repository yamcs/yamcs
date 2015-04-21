package org.yamcs.xtceproc;

import java.util.Comparator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.xtce.Comparison;
import org.yamcs.xtce.ComparisonList;
import org.yamcs.xtce.MatchCriteria;
import org.yamcs.xtce.ParameterInstanceRef;

import com.google.common.primitives.UnsignedBytes;
import com.google.common.primitives.UnsignedLongs;

/**
 * Implements XTCE processing for {@link org.yamcs.xtce.Comparison}
 * @author nm
 *
 */
public class ComparisonProcessor {
    Logger log=LoggerFactory.getLogger(this.getClass().getName());
    final ParameterValueList params;
    /**
     * @param params pvals within 'scope'. Identifier resolution is performed against these pvals
     */
    public ComparisonProcessor(ParameterValueList params) {
        this.params = params;
    }

    public boolean matches(MatchCriteria mc) {
        if(mc instanceof ComparisonList) {
            return matchesComparisonList((ComparisonList)mc);
        } else if(mc instanceof Comparison) {
            Comparison c=(Comparison)mc;

            Object v = c.getValue();
            if(v instanceof Integer) {
                return matchesIntegerComparison(c, (Integer)v);
            } else if(v instanceof Long) {
                return matchesIntegerComparison(c, (Long) v);
            } else if (v instanceof Float) {
                return matchesFloatComparison(c, (Float) v);
            } else if (v instanceof Double) {
                return matchesFloatComparison(c, (Double) v);
            } else if (v instanceof String) {
                return matchesStringComparison(c, (String)v);
            } else if (v instanceof byte[]) {
                return matchesBinaryComparison(c, (byte[])v);
            }  else if (v instanceof Boolean) {
                return matchesBooleanComparison(c, (Boolean)v);
            } else {
                log.error("checking values of type"+v.getClass()+" not supported");
                return false;
            }
        }

        throw new UnsupportedOperationException("matching "+mc+" not supported");
    }

    private boolean matchesIntegerComparison(Comparison ic, long comparisonValue) {
        //Because there is no mechanism in place yet to decide which value of the parameter should be used
        // in case of multiple values present, traverse the list in reverse order to get the value of the most recently 
        // extracted parameter first.
        ParameterInstanceRef paraRef=ic.getParameterRef();
        ParameterValue pv = params.getLast(paraRef.getParameter());
        if(pv==null) return false;

        Value v;
        if(paraRef.useCalibratedValue()) {
            v = pv.getEngValue(); 
        } else {
            v = pv.getRawValue();
        }
        long pvalue;
        boolean unsigned;
        if(v.getType()==Type.SINT32) {
            pvalue = v.getSint32Value();
            unsigned = false;
        } else if(v.getType()==Type.SINT64) {
            pvalue = v.getSint64Value();
            unsigned = false;
        } else if(v.getType()==Type.UINT32) {
            pvalue = v.getUint32Value() & 0xFFFFFFFFFFFFFFFFL;
            unsigned = true;
        } else if(v.getType()==Type.UINT64) {
            pvalue = v.getUint64Value();
            unsigned = true;
        } else {
            throw new IllegalArgumentException("Cannot compare "+ic.getValue().getClass()+" to "+v.getType());
        }
        if(unsigned) {
            switch (ic.getComparisonOperator()) {
            case EQUALITY:
                return (pvalue == comparisonValue);
            case INEQUALITY:
                return (pvalue != comparisonValue);
            case LARGERTHAN:
                return UnsignedLongs.compare(pvalue, comparisonValue)>0;
            case LARGEROREQUALTHAN:
                return UnsignedLongs.compare(pvalue, comparisonValue)>=0;
            case SMALLERTHAN:
                return UnsignedLongs.compare(pvalue, comparisonValue)<0;
            case SMALLEROREQUALTHAN:
                return UnsignedLongs.compare(pvalue, comparisonValue)<=0;
            }
        } else {
            switch (ic.getComparisonOperator()) {
            case EQUALITY:
                return (pvalue == comparisonValue);
            case INEQUALITY:
                return (pvalue != comparisonValue);
            case LARGERTHAN:
                return (pvalue > comparisonValue);
            case LARGEROREQUALTHAN:
                return (pvalue >= comparisonValue);
            case SMALLERTHAN:
                return (pvalue < comparisonValue);
            case SMALLEROREQUALTHAN:
                return (pvalue <= comparisonValue);
            }
        }
        return false;
    }

    private boolean matchesFloatComparison(Comparison fc, double comparisonValue) {
        ParameterValue pv= params.getLast(fc.getParameterRef().getParameter());
        if(pv==null) return false;

        double pvalue;
        Value v;
        if(fc.getParameterRef().useCalibratedValue()) {
            v = pv.getEngValue();
        } else {
            v = pv.getRawValue();
        }
        if(v.getType()==Type.FLOAT) {
            pvalue = v.getFloatValue();
        } else if(v.getType()==Type.DOUBLE) {
            pvalue = v.getDoubleValue();
        } else {
            throw new IllegalArgumentException("Cannot compare "+fc.getValue().getClass()+" to "+v.getType());
        }

        switch (fc.getComparisonOperator()) {
        case EQUALITY:
            return (pvalue == comparisonValue);
        case INEQUALITY:
            return (pvalue != comparisonValue);
        case LARGERTHAN:
            return (pvalue > comparisonValue);
        case LARGEROREQUALTHAN:
            return (pvalue >= comparisonValue);
        case SMALLERTHAN:
            return (pvalue < comparisonValue);
        case SMALLEROREQUALTHAN:
            return (pvalue <= comparisonValue);
        }
        return false;
    }



    private boolean matchesStringComparison(Comparison sc, String svalue) {
        //Because there is no mechanism in place yet to decide which value of the parameter should be used
        // in case of multiple values present, traverse the list in reverse order to get the value of the most recently 
        // extracted parameter first.

        ParameterInstanceRef paraRef=sc.getParameterRef();
        ParameterValue pv = params.getLast(paraRef.getParameter());
        if(pv==null) return false;

        String pvalue;
        if(paraRef.useCalibratedValue()) {
            pvalue = pv.getEngValue().getStringValue();
        }  else {
            pvalue = pv.getRawValue().getStringValue();
        }
        switch (sc.getComparisonOperator()) {
        case EQUALITY:
            return (svalue.compareTo(pvalue) == 0);
        case INEQUALITY:
            return (svalue.compareTo(pvalue) != 0);
        case LARGERTHAN:
            return (svalue.compareTo(pvalue) > 0);
        case LARGEROREQUALTHAN:
            return (svalue.compareTo(pvalue) >= 0);
        case SMALLERTHAN:
            return (svalue.compareTo(pvalue) < 0);
        case SMALLEROREQUALTHAN:
            return (svalue.compareTo(pvalue) <= 0);
        }

        return false;
    }

    private boolean matchesBinaryComparison(Comparison sc, byte[] comparisonValue) {
        ParameterInstanceRef paraRef=sc.getParameterRef();
        ParameterValue pv = params.getLast(paraRef.getParameter());
        if(pv==null) return false;

        Comparator<byte[]> comparator=UnsignedBytes.lexicographicalComparator();
        byte[] pvb;
        if(paraRef.useCalibratedValue()) {
            pvb=  pv.getEngValue().getBinaryValue().toByteArray();
        }  else {
            pvb=  pv.getRawValue().getBinaryValue().toByteArray();
        }


        switch (sc.getComparisonOperator()) {
        case EQUALITY:
            return (comparator.compare(pvb, comparisonValue) == 0);
        case INEQUALITY:
            return (comparator.compare(pvb, comparisonValue) != 0);
        case LARGERTHAN:
            return (comparator.compare(pvb, comparisonValue) > 0);
        case LARGEROREQUALTHAN:
            return (comparator.compare(pvb, comparisonValue) >= 0);
        case SMALLERTHAN:
            return (comparator.compare(pvb, comparisonValue) < 0);
        case SMALLEROREQUALTHAN:
            return (comparator.compare(pvb, comparisonValue) <= 0);
        }
        return false;
    }

    
    private boolean matchesBooleanComparison(Comparison bc, boolean comparisonValue) {
        //Because there is no mechanism in place yet to decide which value of the parameter should be used
        // in case of multiple values present, traverse the list in reverse order to get the value of the most recently 
        // extracted parameter first.
        ParameterInstanceRef paraRef=bc.getParameterRef();
        ParameterValue pv = params.getLast(paraRef.getParameter());
        if(pv==null) return false;

        boolean v;
        if(paraRef.useCalibratedValue()) {
            v = pv.getEngValue().getBooleanValue(); 
        } else {
            v = pv.getRawValue().getBooleanValue();
        }
        
        switch (bc.getComparisonOperator()) {
        case EQUALITY:
            return v== comparisonValue;
        case INEQUALITY:
            return v!=comparisonValue;
        case LARGERTHAN:
            return Boolean.compare(v, comparisonValue) > 0;
        case LARGEROREQUALTHAN:
            return Boolean.compare(v, comparisonValue) >= 0;
        case SMALLERTHAN:
            return Boolean.compare(v, comparisonValue) < 0;
        case SMALLEROREQUALTHAN:
            return Boolean.compare(v, comparisonValue) <= 0;
        }
        return false;
    }
    
    
    public boolean matchesComparisonList(ComparisonList cl) {
        for (Comparison c:cl.getComparisonList()) {
            if(!matches(c)) return false;
        }
        return true;
    }
}

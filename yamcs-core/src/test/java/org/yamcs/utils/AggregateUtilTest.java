package org.yamcs.utils;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Map;

import org.junit.Test;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.PartialParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.xtce.AggregateParameterType;
import org.yamcs.xtce.ArrayParameterType;
import org.yamcs.xtce.DataType;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.Member;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.PathElement;
import org.yamcs.xtce.util.DataTypeUtil;
import org.yamcs.xtceproc.DataTypeProcessor;

public class AggregateUtilTest {
    
    @Test
    public void testSubtype() {
        IntegerParameterType intType =   new IntegerParameterType("m1type");
        AggregateParameterType aggType = new AggregateParameterType("aggType");
        Member m1 = new Member("m1");
        m1.setDataType(intType);
        aggType.addMember(m1);
        ArrayParameterType arrayType = new ArrayParameterType("test", 1);
        arrayType.setElementType(aggType);
        PathElement[] path = AggregateUtil.parseReference("[2].m1");
 
        DataType dt = DataTypeUtil.getMemberType(arrayType, path);
        
        assertEquals(intType, dt);
    }
    
    
    @Test
    public void testPatchAggregate() {
        Parameter p = getAggregateParameter("p");
        AggregateParameterType adt = (AggregateParameterType) p.getParameterType();
        Map<String, Object> m = adt.parseString("{ m1: 3, m2: { s1: 5, s2:7}}"); 
        Value v = DataTypeProcessor.getValueForType(adt, m);
        
        ParameterValue pv = new ParameterValue(p);
        pv.setEngineeringValue(v);
        PathElement[] pem1 = AggregateUtil.parseReference("m1");
        PathElement[] pes2 = AggregateUtil.parseReference("m2.s2");
        
        PartialParameterValue patch1 = new PartialParameterValue(p, pem1);
        patch1.setEngineeringValue(ValueUtility.getSint32Value(31));
        AggregateUtil.updateMember(pv, patch1);
        assertEquals(31, AggregateUtil.getMemberValue(pv.getEngValue(), pem1).getSint32Value());

        
        
        PartialParameterValue patch2 = new PartialParameterValue(p, pes2);
        patch2.setEngineeringValue(ValueUtility.getSint32Value(51));
        AggregateUtil.updateMember(pv, patch2);
        
        assertEquals(51, AggregateUtil.getMemberValue(pv.getEngValue(), pes2).getSint32Value());
       
        
    }
    @Test
    public void testPatchArray() {
        Parameter p = new Parameter("p");
        ArrayParameterType apt = new ArrayParameterType("p",1 );
        apt.setElementType(new IntegerParameterType("etype"));
        p.setParameterType(apt);
        
        Object[] o = apt.parseString("[1,2,3,4]");
        Value v = DataTypeProcessor.getValueForType(apt, o);
        
        ParameterValue pv = new ParameterValue(p);
        pv.setEngineeringValue(v);
        
        PathElement[] pe3 = AggregateUtil.parseReference("[2]");
        PartialParameterValue patch1 = new PartialParameterValue(p, pe3);
        patch1.setEngineeringValue(ValueUtility.getSint32Value(100));
        
        AggregateUtil.updateMember(pv, patch1);
        assertEquals(100, AggregateUtil.getMemberValue(pv.getEngValue(), pe3).getSint32Value());
        
    }
    
    @Test
    public void testPatchArrayInsideAggregate() {
        Parameter p = getArrayInsideAggregateParameter();
        AggregateParameterType adt = (AggregateParameterType) p.getParameterType();
        Map<String, Object> m = adt.parseString("{ m1: 3, m2: { s1: 5, a2:[7, 9, 10]}}"); 
        Value v = DataTypeProcessor.getValueForType(adt, m);
       
        ParameterValue pv = new ParameterValue(p);
        pv.setEngineeringValue(v);

        PathElement[] pea1 = AggregateUtil.parseReference("m2.a2[1]");
        
        PartialParameterValue patch1 = new PartialParameterValue(p, pea1);
        patch1.setEngineeringValue(ValueUtility.getSint32Value(1000));
        AggregateUtil.updateMember(pv, patch1);
        
        assertEquals(1000, AggregateUtil.getMemberValue(pv.getEngValue(), pea1).getSint32Value());
    }

    @Test
    public void testVerifyPath() {
        Parameter p = getAggregateParameter("/yamcs/nm/UDP_FRAME_OUT.tc/cop1Status");
        String name = "/yamcs/nm/UDP_FRAME_OUT.tc/cop1Status.m1";
        int x = AggregateUtil.findSeparator(name);
        PathElement[] path = AggregateUtil.parseReference(name.substring(x));
        assertTrue(AggregateUtil.verifyPath(p.getParameterType(), path));
    }
    
    
    @Test
    public void testFindSeparator() {
        
    }
    
    private Parameter getArrayInsideAggregateParameter() {
        Parameter p = new Parameter("p");
        AggregateParameterType adt = new AggregateParameterType("p");
        adt.addMember(getIntegerMember("m1"));
        adt.addMember(getAggregateMemberWithArray("m2"));
        p.setParameterType(adt);
        return p;
    }
    
    private Parameter getAggregateParameter(String pname) {
        Parameter p = new Parameter(pname);
        AggregateParameterType adt = new AggregateParameterType(pname);
        adt.addMember(getIntegerMember("m1"));
        adt.addMember(getAggregateMember("m2"));
        p.setParameterType(adt);
        return p;
    }

    private Member getIntegerMember(String name) {
        Member m = new Member(name);
        IntegerParameterType mType = new IntegerParameterType(name+"Type");
        m.setDataType(mType);
        return m;
    }
    private Member getIntegerArrayMember(String name) {
        Member m = new Member(name);
        ArrayParameterType apt = new ArrayParameterType(name+"Type", 1);
        apt.setElementType(new IntegerParameterType(name+"ElementType"));
        m.setDataType(apt);
        return m;
    }
    private Member getAggregateMember(String name) {
        Member m = new Member(name);
        AggregateParameterType adt = new AggregateParameterType(name+"Type");
        adt.addMember(getIntegerMember("s1"));
        adt.addMember(getIntegerMember("s2"));
        m.setDataType(adt);
        return m;
    }
    
    private Member getAggregateMemberWithArray(String name) {
        Member m = new Member(name);
        AggregateParameterType adt = new AggregateParameterType(name+"Type");
        adt.addMember(getIntegerMember("s1"));
        adt.addMember(getIntegerArrayMember("a2"));
        m.setDataType(adt);
        return m;
    }
    
    
    
    
}

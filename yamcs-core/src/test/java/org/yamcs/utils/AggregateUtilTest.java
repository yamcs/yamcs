package org.yamcs.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yamcs.mdb.DataTypeProcessor;
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

public class AggregateUtilTest {

    @Test
    public void testSubtype() {
        IntegerParameterType.Builder itb = new IntegerParameterType.Builder();
        AggregateParameterType.Builder aggType = new AggregateParameterType.Builder();
        Member m1 = new Member("m1");
        IntegerParameterType intType = itb.build();
        m1.setDataType(intType);
        aggType.addMember(m1);
        ArrayParameterType.Builder arrayType = new ArrayParameterType.Builder().setNumberOfDimensions(1);
        arrayType.setElementType(aggType.build());
        PathElement[] path = AggregateUtil.parseReference("[2].m1");

        DataType dt = DataTypeUtil.getMemberType(arrayType.build(), path);

        assertEquals(intType, dt);
    }

    @Test
    public void testPatchAggregate() {
        Parameter p = getAggregateParameter("p");
        AggregateParameterType adt = (AggregateParameterType) p.getParameterType();
        Map<String, Object> m = adt.convertType("{ m1: 3, m2: { s1: 5, s2:7}}");
        Value v = DataTypeProcessor.getValueForType(adt, m);

        ParameterValue pv = new ParameterValue(p);
        pv.setEngValue(v);
        PathElement[] pem1 = AggregateUtil.parseReference("m1");
        PathElement[] pes2 = AggregateUtil.parseReference("m2.s2");

        PartialParameterValue patch1 = new PartialParameterValue(p, pem1);
        patch1.setEngValue(ValueUtility.getSint32Value(31));
        AggregateUtil.updateMember(pv, patch1);
        assertEquals(31, AggregateUtil.getMemberValue(pv.getEngValue(), pem1).getSint32Value());

        PartialParameterValue patch2 = new PartialParameterValue(p, pes2);
        patch2.setEngValue(ValueUtility.getSint32Value(51));
        AggregateUtil.updateMember(pv, patch2);

        assertEquals(51, AggregateUtil.getMemberValue(pv.getEngValue(), pes2).getSint32Value());

    }

    @Test
    public void testPatchArray() {
        Parameter p = new Parameter("p");
        ArrayParameterType.Builder aptb = new ArrayParameterType.Builder().setNumberOfDimensions(1);
        aptb.setElementType(new IntegerParameterType.Builder().build());
        ArrayParameterType apt = aptb.build();
        p.setParameterType(apt);

        Object[] o = apt.convertType("[1,2,3,4]");
        Value v = DataTypeProcessor.getValueForType(apt, o);

        ParameterValue pv = new ParameterValue(p);
        pv.setEngValue(v);

        PathElement[] pe3 = AggregateUtil.parseReference("[2]");
        PartialParameterValue patch1 = new PartialParameterValue(p, pe3);
        patch1.setEngValue(ValueUtility.getSint32Value(100));

        AggregateUtil.updateMember(pv, patch1);
        assertEquals(100, AggregateUtil.getMemberValue(pv.getEngValue(), pe3).getSint32Value());

    }

    @Test
    public void testPatchArrayInsideAggregate() {
        Parameter p = getArrayInsideAggregateParameter();
        AggregateParameterType adt = (AggregateParameterType) p.getParameterType();
        Map<String, Object> m = adt.convertType("{ m1: 3, m2: { s1: 5, a2:[7, 9, 10]}}");
        Value v = DataTypeProcessor.getValueForType(adt, m);

        ParameterValue pv = new ParameterValue(p);
        pv.setEngValue(v);

        PathElement[] pea1 = AggregateUtil.parseReference("m2.a2[1]");

        PartialParameterValue patch1 = new PartialParameterValue(p, pea1);
        patch1.setEngValue(ValueUtility.getSint32Value(1000));
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
        AggregateParameterType.Builder adt = new AggregateParameterType.Builder();
        adt.addMember(getIntegerMember("m1"));
        adt.addMember(getAggregateMemberWithArray("m2"));
        p.setParameterType(adt.build());
        return p;
    }

    private Parameter getAggregateParameter(String pname) {
        Parameter p = new Parameter(pname);
        AggregateParameterType.Builder adt = new AggregateParameterType.Builder();
        adt.addMember(getIntegerMember("m1"));
        adt.addMember(getAggregateMember("m2"));
        p.setParameterType(adt.build());
        return p;
    }

    private Member getIntegerMember(String name) {
        Member m = new Member(name);
        IntegerParameterType mType = new IntegerParameterType.Builder().setName(name + "Type").build();
        m.setDataType(mType);
        return m;
    }

    private Member getIntegerArrayMember(String name) {
        Member m = new Member(name);
        ArrayParameterType.Builder apt = new ArrayParameterType.Builder();
        apt.setNumberOfDimensions(1);
        apt.setElementType(new IntegerParameterType.Builder().setName(name + "ElementType").build());
        m.setDataType(apt.build());
        return m;
    }

    private Member getAggregateMember(String name) {
        Member m = new Member(name);
        AggregateParameterType.Builder adt = new AggregateParameterType.Builder();
        adt.addMember(getIntegerMember("s1"));
        adt.addMember(getIntegerMember("s2"));
        m.setDataType(adt.build());
        return m;
    }

    private Member getAggregateMemberWithArray(String name) {
        Member m = new Member(name);
        AggregateParameterType.Builder adt = new AggregateParameterType.Builder();
        adt.addMember(getIntegerMember("s1"));
        adt.addMember(getIntegerArrayMember("a2"));
        m.setDataType(adt.build());
        return m;
    }
}

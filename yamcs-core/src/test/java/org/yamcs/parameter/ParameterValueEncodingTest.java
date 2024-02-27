package org.yamcs.parameter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.http.api.SubscribeParameterObserver.SubscribeParametersData;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.util.AggregateMemberNames;
import org.yamcs.xtce.util.DoubleRange;

import com.google.protobuf.CodedOutputStream;

public class ParameterValueEncodingTest {

    @BeforeAll
    public static void beforeClass() {
        TimeEncoding.setUp();
    }

    @Test
    void test1() throws Exception {
        ParameterValue pv = getPv1();

        byte[] buffer = new byte[pv.getSerializedSize(90)];
        CodedOutputStream output = CodedOutputStream.newInstance(buffer);

        pv.writeTo(output, 90);

        output.checkNoSpaceLeft();

        org.yamcs.protobuf.Pvalue.ParameterValue pv1 = org.yamcs.protobuf.Pvalue.ParameterValue.parseFrom(buffer);
        org.yamcs.protobuf.Pvalue.ParameterValue pv2 = pv.toGpb(90);

        assertEquals(pv2, pv1);
    }

    @Test
    void testEmptyAggregate() throws Exception {
        AggregateValue aggrv = new AggregateValue(AggregateMemberNames.get(new String[0]));

        byte[] buffer = new byte[aggrv.getSerializedSize()];
        CodedOutputStream output = CodedOutputStream.newInstance(buffer);
        aggrv.writeTo(output);
        output.checkNoSpaceLeft();

        Value v1 = Value.parseFrom(buffer);
        Value v2 = ValueUtility.toGbp((org.yamcs.parameter.Value) aggrv);

        assertEquals(v1, v2);
    }

    @Test
    void testAggregate() throws Exception {
        AggregateValue aggrv = getAggrValue();

        byte[] buffer = new byte[aggrv.getSerializedSize()];
        CodedOutputStream output = CodedOutputStream.newInstance(buffer);
        aggrv.writeTo(output);
        output.checkNoSpaceLeft();

        Value v1 = Value.parseFrom(buffer);
        Value v2 = ValueUtility.toGbp((org.yamcs.parameter.Value) aggrv);

        assertEquals(v1, v2);
    }

    @Test
    void testAggregatePv() throws Exception {
        ParameterValue pv = getPv2();

        byte[] buffer = new byte[pv.getSerializedSize(90)];
        CodedOutputStream output = CodedOutputStream.newInstance(buffer);

        pv.writeTo(output, 90);

        output.checkNoSpaceLeft();

        org.yamcs.protobuf.Pvalue.ParameterValue pv1 = org.yamcs.protobuf.Pvalue.ParameterValue.parseFrom(buffer);
        org.yamcs.protobuf.Pvalue.ParameterValue pv2 = pv.toGpb(90);
        assertEquals(pv2, pv1);
    }

    @Test
    void testSpd() throws Exception {
        SubscribeParametersData spd = new SubscribeParametersData();
        spd.addValues(100, getPv1());
        spd.addValues(101, getPv2());

        spd.addAllInvalid(Arrays.asList(getId()));
        spd.putMapping(100, getId());

        byte[] buffer = new byte[spd.getSerializedSize()];
        CodedOutputStream output = CodedOutputStream.newInstance(buffer);

        spd.writeTo(output);
        output.checkNoSpaceLeft();
    }

    NamedObjectId getId() {
        return NamedObjectId.newBuilder().setName("abcd").setNamespace("aaa").build();
    }

    ParameterValue getPv1() {
        ParameterValue pv = new ParameterValue("bubu");
        pv.setEngValue(ValueUtility.getDoubleValue(3.14));
        pv.setRawValue(ValueUtility.getUint32Value(100));
        pv.setGenerationTime(1000);
        pv.setAcquisitionTime(233);
        
        pv.setWatchRange(new DoubleRange(100.0, 200.0));
        return pv;
    }

    ParameterValue getPv2() {

        ParameterValue pv = new ParameterValue("bubu");
        pv.setEngValue(getAggrValue());
        return pv;
    }

    AggregateValue getAggrValue() {
        AggregateMemberNames names = AggregateMemberNames.get(new String[] { "x", "y", "z", "toto" });
        AggregateValue aggrv = new AggregateValue(names);
        aggrv.setMemberValue("x", ValueUtility.getUint32Value(1));
        aggrv.setMemberValue("y", ValueUtility.getSint32Value(1));
        aggrv.setMemberValue("z", ValueUtility.getDoubleValue(3.15));
        aggrv.setMemberValue("toto", ValueUtility.getStringValue("Cutugno"));

        return aggrv;
    }

}

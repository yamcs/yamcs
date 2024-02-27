package org.yamcs.parameter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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

        byte[] buffer = new byte[pv.getSerializedSize()];

        CodedOutputStream output = CodedOutputStream.newInstance(buffer);

        pv.writeTo(output);

        output.checkNoSpaceLeft();

        org.yamcs.protobuf.Pvalue.ParameterValue pv1 = org.yamcs.protobuf.Pvalue.ParameterValue.parseFrom(buffer);
        org.yamcs.protobuf.Pvalue.ParameterValue pv2 = pv.toProtobufParameterValue(Optional.empty(),
                OptionalInt.empty());

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

        byte[] buffer = new byte[pv.getSerializedSize()];
        CodedOutputStream output = CodedOutputStream.newInstance(buffer);

        pv.writeTo(output);

        output.checkNoSpaceLeft();

        org.yamcs.protobuf.Pvalue.ParameterValue pv1 = org.yamcs.protobuf.Pvalue.ParameterValue.parseFrom(buffer);
        org.yamcs.protobuf.Pvalue.ParameterValue pv2 = pv.toProtobufParameterValue(Optional.empty(),
                OptionalInt.empty());
        assertEquals(pv2, pv1);
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
        AggregateMemberNames names = AggregateMemberNames
                .get(new String[] { "uint32", "sint32", "double", "str", "binary" });
        AggregateValue aggrv = new AggregateValue(names);
        aggrv.setMemberValue("uint32", ValueUtility.getUint32Value(1));
        aggrv.setMemberValue("sint32", ValueUtility.getSint32Value(1));
        aggrv.setMemberValue("double", ValueUtility.getDoubleValue(3.15));
        aggrv.setMemberValue("str", ValueUtility.getStringValue("Cutugno"));
        aggrv.setMemberValue("binary", ValueUtility.getBinaryValue(new byte[] { 100, 101, 102 }));
        return aggrv;
    }

}

package org.yamcs.yfe;

import org.junit.jupiter.api.Test;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.AggregateParameterType;
import org.yamcs.xtce.Member;
import org.yamcs.yfe.protobuf.Yfe.Value;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProtoConverterUnitTest {

    @Test
    public void testAggregatetype() {
        AggregateParameterType aggrType = new AggregateParameterType.Builder().setName("protoargument")
                .addMember(new Member("booleanname"))
                .addMember(new Member("integername"))
                //  .addMember(new Member("enumeratedname"))
                .build();

        org.yamcs.parameter.AggregateValue tmp = new org.yamcs.parameter.AggregateValue(aggrType.getMemberNames());
        tmp.setMemberValue("booleanname", ValueUtility.getBooleanValue(true));
        tmp.setMemberValue("integername", ValueUtility.getUint32Value(32));
        // tmp.setMemberValue("enumeratedname", ValueUtility.getEnumeratedValue(100, "enum"));

        org.yamcs.parameter.Value val = tmp;

        Value protoVal = ProtoConverter.toProto(val);
        org.yamcs.parameter.Value converterValue = ProtoConverter.fromProto(protoVal);

        assertEquals(val, converterValue);
    }

    @Test
    public void testArraytype() {
        int[] dimensions = { 5 };
        org.yamcs.parameter.Value str = ValueUtility.getStringValue("testing");
        org.yamcs.parameter.ArrayValue arrval = new org.yamcs.parameter.ArrayValue(dimensions,
                org.yamcs.protobuf.Yamcs.Value.Type.STRING);
        for (int i = 0; i < dimensions[0]; i++) {
            arrval.setElementValue(i, str);
        }
        org.yamcs.parameter.Value val = arrval;

        Value protoVal = ProtoConverter.toProto(val);
        org.yamcs.parameter.Value converterValue = ProtoConverter.fromProto(protoVal);

        assertEquals(arrval, converterValue);
    }

    @Test
    public void testBasicType() {
        org.yamcs.parameter.Value val = ValueUtility.getSint32Value(50);
        Value protoVal = ProtoConverter.toProto(val);
        org.yamcs.parameter.Value convertedVal = ProtoConverter.fromProto(protoVal);
        assertEquals(val, convertedVal);
    }
}

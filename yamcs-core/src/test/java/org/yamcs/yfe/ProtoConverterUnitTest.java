package org.yamcs.yfe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yamcs.mdb.DataTypeProcessor;
import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.PartialParameterValue;
import org.yamcs.parameter.SInt32Value;
import org.yamcs.parameter.StringValue;
import org.yamcs.protobuf.Cop1State;
import org.yamcs.utils.AggregateUtil;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.AggregateParameterType;
import org.yamcs.xtce.ArrayParameterType;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.Member;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.PathElement;
import org.yamcs.yarch.DataType;
import org.yamcs.yfe.protobuf.Yfe;
import org.yamcs.yfe.protobuf.Yfe.Value;



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
        int[] dimensions = {5};
        org.yamcs.parameter.Value str = ValueUtility.getStringValue("testing");
        org.yamcs.parameter.ArrayValue arrval = new org.yamcs.parameter.ArrayValue(dimensions, org.yamcs.protobuf.Yamcs.Value.Type.STRING);
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

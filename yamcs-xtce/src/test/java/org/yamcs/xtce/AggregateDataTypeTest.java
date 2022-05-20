package org.yamcs.xtce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.xtce.util.AggregateMemberNames;

public class AggregateDataTypeTest {
    Member m11, m1, m2, m3;
    AggregateParameterType aptm11, apt;

    @BeforeEach
    public void before() {
        IntegerParameterType ipt = new IntegerParameterType.Builder().setSizeInBits(32)
                .setEncoding(new IntegerDataEncoding.Builder()).build();
        IntegerParameterType ipt1 = new IntegerParameterType.Builder().setSizeInBits(32)
                .setEncoding(new IntegerDataEncoding.Builder()).setInitialValue("5").build();

        m11 = new Member("m11", ipt);
        aptm11 = new AggregateParameterType.Builder().addMember(m11).build();
        m1 = new Member("m1", aptm11);
        m2 = new Member("m2", ipt);
        m3 = new Member("m3", ipt1);

        apt = new AggregateParameterType.Builder().addMembers(Arrays.asList(m1, m2, m3)).build();

    }

    @Test
    public void testGetMember() {
        assertEquals(1, aptm11.numMembers());

        assertEquals(3, apt.numMembers());

        assertEquals(m1, apt.getMember(0));
        assertEquals(m2, apt.getMember(1));
        assertEquals(m3, apt.getMember(2));

        assertEquals(m1, apt.getMember("m1"));
        assertEquals(m2, apt.getMember("m2"));
        assertEquals(m3, apt.getMember("m3"));

        assertEquals(m11, apt.getMember(new String[] { "m1", "m11" }));
        assertNull(apt.getMember(new String[] { "m1", "m2" }));
        assertNull(apt.getMember(new String[] { "m2", "m1" }));
        assertNull(apt.getMember(new String[] { "m1", "m11", "m3" }));
    }

    @Test
    public void testGetMemberInvalid() {
        assertThrows(IllegalArgumentException.class, () -> {
            apt.getMember(new String[] {});
        });
    }

    @Test
    public void testGetMemberNames() {
        AggregateMemberNames amn = apt.getMemberNames();
        assertEquals("m1", amn.get(0));
        assertEquals("m2", amn.get(1));

    }

    @Test
    public void testCopyConstructor() {
        AggregateParameterType apt2 = new AggregateParameterType(apt);
        assertEquals(m1, apt2.getMember("m1"));
        assertEquals(m2, apt2.getMember("m2"));

        AggregateParameterType apt3 = new AggregateParameterType.Builder(apt2).build();
        assertEquals(m1, apt3.getMember("m1"));
        assertEquals(m2, apt3.getMember("m2"));
        assertEquals(m3, apt3.getMember("m3"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testConvertType() {

        // convert string to map
        String s = "{\"m1\":{\"m11\":3},\"m2\":4}";
        Map<String, Object> v = apt.convertType(s);

        assertEquals(4l, v.get("m2"));
        Map<String, Object> vm1 = (Map<String, Object>) v.get("m1");
        assertEquals(3l, vm1.get("m11"));
        assertEquals(5l, v.get("m3"));

        // convert map to map
        Map<String, Object> v2 = apt.convertType(v);
        assertEquals(4l, v2.get("m2"));
        Map<String, Object> vm2 = (Map<String, Object>) v2.get("m1");
        assertEquals(3l, vm2.get("m11"));
        assertEquals(5l, v2.get("m3"));

        // convert map missing m3 to map
        v.remove("m3");
        Map<String, Object> v3 = apt.convertType(v);

        assertEquals(4l, v3.get("m2"));
        Map<String, Object> vm3 = (Map<String, Object>) v3.get("m1");
        assertEquals(3l, vm3.get("m11"));
        assertEquals(5l, v3.get("m3"));

    }

    @Test
    public void testConvertTypeIllegal1() {
        assertThrows(IllegalArgumentException.class, () -> {
            apt.convertType(3);
        });
    }

    @Test
    public void testConvertTypeIllegal2() {
        assertThrows(IllegalArgumentException.class, () -> {
            apt.convertType("[3, 4]");
        });
    }

    @Test
    public void testConvertTypeIllegal3() {
        assertThrows(IllegalArgumentException.class, () -> {
            apt.convertType("{m1' 3}");
        });
    }

    @Test
    public void testConvertTypeIllegal4() {
        assertThrows(IllegalArgumentException.class, () -> {
            apt.convertType("{m2: 3}");
        });
    }

    @Test
    public void testConvertTypeIllegal5() {
        assertThrows(IllegalArgumentException.class, () -> {
            String s = "{\"m1\":{\"m11\":3},\"m2\":4}";
            Map<String, Object> m = apt.convertType(s);
            m.remove("m1");

            apt.convertType(m);
        });
    }

    @Test
    public void testConvertTypeIllegal6() {
        assertThrows(IllegalArgumentException.class, () -> {
            String s = "{\"m1\":{\"m11\":3},\"m2\":4}";
            Map<String, Object> m = apt.convertType(s);
            m.put("m10", 10);

            apt.convertType(m);
        });
    }

    @Test
    public void testConvertTypeIllegal7() {
        assertThrows(IllegalArgumentException.class, () -> {
            String s = "{\"m1\":{\"m11\":3},\"m2\":4, \"m10\": 10}";
            apt.convertType(s);
        });
    }

    @Test
    public void testToStringIllegal() {
        assertThrows(IllegalArgumentException.class, () -> {
            String s = "{\"m1\":{\"m11\":3},\"m2\":4}";
            Map<String, Object> m = apt.convertType(s);
            m.put("m10", 5);
            apt.toString(m);
        });
    }

    @Test
    public void testToStringIllegal1() {
        assertThrows(IllegalArgumentException.class, () -> {
            String s = "{\"m1\":{\"m11\":3},\"m2\":4}";
            Map<String, Object> m = apt.convertType(s);
            m.remove("m2");
            apt.toString(m);
        });
    }

    @Test
    public void testToStringIllegal2() {
        assertThrows(IllegalArgumentException.class, () -> {
            apt.toString(3);
        });
    }

    @Test
    public void testGetInitialValue() {
        assertNull(apt.getInitialValue());
        AggregateParameterType aptm3 = new AggregateParameterType.Builder().addMember(m3).build();
        assertEquals("{\"m3\":5}", aptm3.toString(aptm3.getInitialValue()));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testConvertRaw() {
        String s = "{\"m1\":{\"m11\":3},\"m2\":4, \"m3\": 9}";
        Map<String, Object> v = apt.parseStringForRawValue(s);

        assertEquals(4, v.get("m2"));
        Map<String, Object> vm1 = (Map<String, Object>) v.get("m1");
        assertEquals(3, vm1.get("m11"));
        assertEquals(9, v.get("m3"));
    }

    @Test
    public void testConvertRawIllegal1() {
        assertThrows(IllegalArgumentException.class, () -> {
            String s = "{\"m1\":{\"m11\":3},\"m2\":4}";
            apt.parseStringForRawValue(s);
        });
    }

    @Test
    public void testConvertRawIllegal2() {
        assertThrows(IllegalArgumentException.class, () -> {
            String s = "{\"m1\":{\"m11\":3},\"m2\":4, \"m3\": 9, \"m10\": 10}";
            apt.parseStringForRawValue(s);
        });
    }

    @Test
    public void testConvertRawIllegal4() {
        assertThrows(IllegalArgumentException.class, () -> {
            String s = "{\"m1 :4}";
            apt.parseStringForRawValue(s);
        });
    }

    @Test
    public void testConvertRawIllegal5() {
        assertThrows(IllegalArgumentException.class, () -> {
            String s = "[5, 6]";
            apt.parseStringForRawValue(s);
        });
    }
}

package org.yamcs.pus;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.BooleanParameterType;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.PolynomialCalibrator;

public class PusParameterManagerTest {
    static final String NS = "PUS20_Parameter_ID";
    PusParameterManager mgr;

    private static EnumeratedParameterType idType() {
        var b = new EnumeratedParameterType.Builder().setName("id_t")
                .setEncoding(new IntegerDataEncoding.Builder().setSizeInBits(8)
                        .setEncoding(IntegerDataEncoding.Encoding.UNSIGNED));
        long[] ids = {1, 2, 3, 4, 5, 6, 10, 11, 258};
        for (long id : ids) {
            b.addEnumerationValue(id, "P" + id);
        }
        return b.build();
    }

    private static Parameter intParam(String name, int id, int bits, IntegerDataEncoding.Encoding enc) {
        var type = new IntegerParameterType.Builder().setName(name + "_t")
                .setSigned(enc == IntegerDataEncoding.Encoding.TWOS_COMPLEMENT)
                .setEncoding(new IntegerDataEncoding.Builder().setSizeInBits(bits).setEncoding(enc)).build();
        return param(name, id, type);
    }

    private static Parameter param(String name, int id, org.yamcs.xtce.ParameterType type) {
        var p = new Parameter(name);
        p.setParameterType(type);
        if (id >= 0) {
            p.addAlias(NS, "P" + id);
        }
        return p;
    }

    private static ParameterValue pv(Parameter p, Value v) {
        var pv = new ParameterValue(p);
        pv.setEngValue(v);
        return pv;
    }

    @BeforeEach
    void setup() {
        mgr = new PusParameterManager();
        mgr.configure(NS, idType(), 8, 8);
    }

    @Test
    void integers() {
        var u8 = intParam("u8", 3, 8, IntegerDataEncoding.Encoding.UNSIGNED);
        var u16 = intParam("u16", 1, 16, IntegerDataEncoding.Encoding.UNSIGNED);
        var u32 = intParam("u32", 4, 32, IntegerDataEncoding.Encoding.UNSIGNED);
        var i16 = intParam("i16", 6, 16, IntegerDataEncoding.Encoding.TWOS_COMPLEMENT);
        byte[] b = mgr.encodeInstructions(List.of(
                pv(u16, ValueUtility.getUint32Value(250)),
                pv(u8, ValueUtility.getUint32Value(7)),
                pv(u32, ValueUtility.getUint32Value(0xFFFFFFFF)),
                pv(i16, ValueUtility.getSint32Value(-300))));
        assertArrayEquals(new byte[]{
                4,
                1, 0, (byte) 250,
                3, 7,
                4, -1, -1, -1, -1,
                6, (byte) 0xfe, (byte) 0xd4}, b);
    }

    @Test
    void floats() {
        var f32 = param("f32", 2, new FloatParameterType.Builder().setName("f32_t")
                .setEncoding(new FloatDataEncoding.Builder().setSizeInBits(32)).build());
        var f64 = param("f64", 5, new FloatParameterType.Builder().setName("f64_t")
                .setEncoding(new FloatDataEncoding.Builder().setSizeInBits(64)).build());
        byte[] b = mgr.encodeInstructions(List.of(
                pv(f32, ValueUtility.getFloatValue(-3.5f)),
                pv(f64, ValueUtility.getDoubleValue(2.75))));
        ByteBuffer bb = ByteBuffer.wrap(b);
        assertEquals(2, bb.get());
        assertEquals(2, bb.get());
        assertEquals(-3.5f, bb.getFloat());
        assertEquals(5, bb.get());
        assertEquals(2.75, bb.getDouble());
        assertEquals(0, bb.remaining());
    }

    @Test
    void enumAndBoolean() {
        var et = new EnumeratedParameterType.Builder().setName("e_t")
                .setEncoding(new IntegerDataEncoding.Builder().setSizeInBits(8)
                        .setEncoding(IntegerDataEncoding.Encoding.UNSIGNED))
                .addEnumerationValue(1, "ON").addEnumerationValue(2, "OFF").build();
        var e = param("e", 10, et);
        var bt = new BooleanParameterType.Builder().setName("b_t")
                .setEncoding(new IntegerDataEncoding.Builder().setSizeInBits(8)
                        .setEncoding(IntegerDataEncoding.Encoding.UNSIGNED))
                .build();
        var b = param("b", 11, bt);
        byte[] r = mgr.encodeInstructions(List.of(
                pv(e, ValueUtility.getStringValue("OFF")),
                pv(b, ValueUtility.getBooleanValue(true))));
        assertArrayEquals(new byte[]{2, 10, 2, 11, 1}, r);
        assertThrows(IllegalArgumentException.class,
                () -> mgr.encodeInstructions(List.of(pv(e, ValueUtility.getStringValue("XX")))));
    }

    @Test
    void lastValueWinsForRepeatedParameter() {
        var u8 = intParam("u8", 3, 8, IntegerDataEncoding.Encoding.UNSIGNED);
        byte[] b = mgr.encodeInstructions(List.of(
                pv(u8, ValueUtility.getUint32Value(1)), pv(u8, ValueUtility.getUint32Value(2))));
        assertArrayEquals(new byte[]{1, 3, 2}, b);
    }

    @Test
    void errors() {
        var noAlias = intParam("noalias", -1, 8, IntegerDataEncoding.Encoding.UNSIGNED);
        assertThrows(IllegalArgumentException.class,
                () -> mgr.encodeInstructions(List.of(pv(noAlias, ValueUtility.getUint32Value(1)))));

        var u8 = intParam("u8", 3, 8, IntegerDataEncoding.Encoding.UNSIGNED);
        assertThrows(IllegalArgumentException.class, // out of range
                () -> mgr.encodeInstructions(List.of(pv(u8, ValueUtility.getUint32Value(256)))));
        assertThrows(IllegalArgumentException.class,
                () -> mgr.encodeInstructions(List.of()));

        var big = intParam("big", 258, 8, IntegerDataEncoding.Encoding.UNSIGNED); // ID does not fit in 8 bits
        assertThrows(IllegalArgumentException.class,
                () -> mgr.encodeInstructions(List.of(pv(big, ValueUtility.getUint32Value(1)))));

        var enc = new IntegerDataEncoding.Builder().setSizeInBits(8).setEncoding(IntegerDataEncoding.Encoding.UNSIGNED)
                .setDefaultCalibrator(new PolynomialCalibrator(new double[]{0, 2}));
        var calibrated = param("cal", 4, new IntegerParameterType.Builder().setName("cal_t").setEncoding(enc).build());
        assertThrows(IllegalArgumentException.class,
                () -> mgr.encodeInstructions(List.of(pv(calibrated, ValueUtility.getUint32Value(1)))));
    }

    @Test
    void widerIdAndCount() {
        mgr.configure(NS, idType(), 16, 16);
        var u8 = intParam("u8", 258, 8, IntegerDataEncoding.Encoding.UNSIGNED);
        byte[] b = mgr.encodeInstructions(List.of(pv(u8, ValueUtility.getUint32Value(9))));
        assertArrayEquals(new byte[]{0, 1, 1, 2, 9}, b);
    }
}
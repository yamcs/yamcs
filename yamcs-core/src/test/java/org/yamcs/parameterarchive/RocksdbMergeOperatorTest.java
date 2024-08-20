package org.yamcs.parameterarchive;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.YamcsParchiveMergeOperator;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.SortedIntArray;
import org.yamcs.utils.ValueUtility;

public class RocksdbMergeOperatorTest {

    private static RocksDB db;
    private static Options options;

    @BeforeAll
    public static void loadRocksdb() throws Exception {
        RocksDB.loadLibrary();
        var path = Files.createTempDirectory("RocksdbMergeOperatorTest");

        options = new Options();
        options.setMergeOperator(new YamcsParchiveMergeOperator());
        options.setCreateIfMissing(true);
        db = RocksDB.open(options, path.toString());

    }

    /***** Int segment ****/
    @Test
    public void testIntSegment() throws Exception {
        IntValueSegment ivs1 = new IntValueSegment(true);
        ivs1.add(ValueUtility.getSint32Value(1));
        byte[] data1 = SegmentEncoderDecoder.encode(ivs1);

        IntValueSegment ivs2 = new IntValueSegment(true);
        ivs2.add(ValueUtility.getSint32Value(2));
        byte[] data2 = SegmentEncoderDecoder.encode(ivs2);

        var key = "testIntSegment".getBytes();
        db.put(key, data1);
        db.merge(key, data2);

        var data12_out = db.get(key);
        var ivs12_out = (IntValueSegment) SegmentEncoderDecoder.decode(data12_out, 0);

        verify_merge(ivs12_out, ivs1, ivs2);

        IntValueSegment ivs3 = new IntValueSegment(true);
        ivs3.add(ValueUtility.getSint32Value(268435456));
        byte[] data3 = SegmentEncoderDecoder.encode(ivs3);
        db.merge(key, data3);

        var data123_out = db.get(key);

        var ivs123_out = (IntValueSegment) SegmentEncoderDecoder.decode(data123_out, 0);

        verify_merge(ivs123_out, ivs1, ivs2, ivs3);

        IntValueSegment ivs4 = new IntValueSegment(true);
        for (int i = 0; i < 124; i++) {
            if (i % 2 == 0) {
                ivs4.add(ValueUtility.getSint32Value(268435456 + i));
            } else {
                ivs4.add(ValueUtility.getSint32Value(0));
            }
        }
        byte[] data4 = SegmentEncoderDecoder.encode(ivs4);

        db.merge(key, data4);

        var data1234_out = db.get(key);
        var ivs1234_out = (IntValueSegment) SegmentEncoderDecoder.decode(data1234_out, 0);
        verify_merge(ivs1234_out, ivs1, ivs2, ivs3, ivs4);

        IntValueSegment ivs5 = new IntValueSegment(true);
        ivs5.add(ValueUtility.getSint32Value(-1000));
        byte[] data5 = SegmentEncoderDecoder.encode(ivs5);
        db.merge(key, data5);

        var data12345_out = db.get(key);
        var ivs12345_out = (IntValueSegment) SegmentEncoderDecoder.decode(data12345_out, 0);

        verify_merge(ivs12345_out, ivs1, ivs2, ivs3, ivs4, ivs5);

        IntValueSegment ivs6 = new IntValueSegment(true);
        for (int i = 0; i < 128; i++) {
            ivs6.add(ValueUtility.getSint32Value(-i));
        }
        byte[] data6 = SegmentEncoderDecoder
                .encode(ivs6);

        db.merge(key, data6);

        var data123456_out = db.get(key);
        var ivs123456_out = (IntValueSegment) SegmentEncoderDecoder.decode(data123456_out, 0);

        verify_merge(ivs123456_out, ivs1, ivs2, ivs3, ivs4, ivs5, ivs6);
    }

    @Test
    public void testIntSegment2() throws Exception {
        IntValueSegment ivs1 = new IntValueSegment(false);
        ivs1.add(ValueUtility.getUint32Value(1));
        byte[] data1 = SegmentEncoderDecoder.encode(ivs1);

        IntValueSegment ivs2 = new IntValueSegment(false);
        for (int i = 0; i < 2000; i++) {
            ivs2.add(ValueUtility.getUint32Value(i));
        }
        byte[] data2 = SegmentEncoderDecoder.encode(ivs2);

        var key = "testIntSegment".getBytes();
        db.put(key, data1);
        db.merge(key, data2);

        var data12_out = db.get(key);
        var ivs12_out = (IntValueSegment) SegmentEncoderDecoder.decode(data12_out, 0);

        verify_merge(ivs12_out, ivs1, ivs2);

    }


    /***** Long segment ****/
    @Test
    public void testLongSegment() throws Exception {

        LongValueSegment lvs1 = new LongValueSegment(Type.UINT64);
        lvs1.add(ValueUtility.getUint64Value(1));
        byte[] data1 = SegmentEncoderDecoder.encode(lvs1);

        LongValueSegment lvs2 = new LongValueSegment(Type.UINT64);
        lvs2.add(ValueUtility.getUint64Value(2));
        byte[] data2 = SegmentEncoderDecoder.encode(lvs2);

        var key = "testLongSegment".getBytes();
        db.put(key, data1);
        db.merge(key, data2);

        var data12_out = db.get(key);
        var lvs12_out = (LongValueSegment) SegmentEncoderDecoder.decode(data12_out, 0);

        verify_merge(lvs12_out, lvs1, lvs2);

    }

    /***** Boolean segment ****/
    @Test
    public void testBooleanSegment() throws Exception {

        BooleanValueSegment bvs1 = new BooleanValueSegment();
        bvs1.add(ValueUtility.getBooleanValue(true));
        byte[] data1 = SegmentEncoderDecoder.encode(bvs1);

        BooleanValueSegment bvs2 = new BooleanValueSegment();
        for (int i = 0; i < 64; i++) {
            bvs2.add(ValueUtility.getBooleanValue(i % 2 == 0));
        }
        byte[] data2 = SegmentEncoderDecoder.encode(bvs2);

        var key = "a1".getBytes();
        db.put(key, data1);
        db.merge(key, data2);

        var data12_out = db.get(key);
        var bvs12_out = (BooleanValueSegment) SegmentEncoderDecoder.decode(data12_out, 0);

        verify_merge(bvs12_out, bvs1, bvs2);
    }

    /***** Double segment ****/
    @Test
    public void testDoubleSegment() throws Exception {

        DoubleValueSegment dvs1 = new DoubleValueSegment();
        dvs1.add(ValueUtility.getDoubleValue(1.1));
        byte[] data1 = SegmentEncoderDecoder.encode(dvs1);

        DoubleValueSegment dvs2 = new DoubleValueSegment();
        for (int i = 0; i < 1; i++) {
            dvs2.add(ValueUtility.getDoubleValue(i * 1.1));
        }
        byte[] data2 = SegmentEncoderDecoder.encode(dvs2);


        var key = "testDoubleSegment".getBytes();
        db.put(key, data1);
        db.merge(key, data2);

        var data12_out = db.get(key);
        var dvs12_out = (DoubleValueSegment) SegmentEncoderDecoder.decode(data12_out, 0);

        verify_merge(dvs12_out, dvs1, dvs2);
    }

    /***** Float segment ****/
    @Test
    public void testFloatSegment() throws Exception {
        FloatValueSegment fvs = new FloatValueSegment();

        FloatValueSegment fvs1 = new FloatValueSegment();
        fvs1.add(ValueUtility.getFloatValue(1.1f));
        fvs.add(ValueUtility.getFloatValue(1.1f));
        byte[] data1 = SegmentEncoderDecoder.encode(fvs1);

        FloatValueSegment fvs2 = new FloatValueSegment();
        for (int i = 0; i < 11; i++) {
            fvs2.add(ValueUtility.getFloatValue(i * 1.1f));
            fvs.add(ValueUtility.getFloatValue(i * 1.1f));
        }
        byte[] data2 = SegmentEncoderDecoder.encode(fvs2);

        FloatValueSegment fvs3 = new FloatValueSegment();
        for (int i = 0; i < 10; i++) {
            fvs3.add(ValueUtility.getFloatValue(3.14f));
            fvs.add(ValueUtility.getFloatValue(3.14f));
        }
        byte[] data3 = SegmentEncoderDecoder.encode(fvs3);

        byte[] data = SegmentEncoderDecoder.encode(fvs);

        var key = "testFloatSegment".getBytes();
        db.put(key, data1);
        db.merge(key, data2);
        db.merge(key, data3);

        var data123_out = db.get(key);
        var fvs123_out = (FloatValueSegment) SegmentEncoderDecoder.decode(data123_out, 0);

        verify_merge(fvs123_out, fvs1, fvs2, fvs3);
    }

    /***** Object Value segment ****/
    @Test
    public void testObjectSegment_RawPlusRaw() throws Exception {
        BinaryValueSegment bvs1 = new BinaryValueSegment(true);
        bvs1.add(ValueUtility.getBinaryValue("abc".getBytes()));
        bvs1.add(ValueUtility.getBinaryValue("bcd".getBytes()));
        bvs1.consolidate();
        byte[] data1 = SegmentEncoderDecoder.encode(bvs1);
        var key = "testObjectSegment_RawPlusRaw".getBytes();
        db.put(key, data1);

        BinaryValueSegment bvs2 = new BinaryValueSegment(true);
        bvs2.add(ValueUtility.getBinaryValue("cde".getBytes()));
        bvs2.add(ValueUtility.getBinaryValue("def".getBytes()));
        bvs2.consolidate();
        byte[] data2 = SegmentEncoderDecoder.encode(bvs2);
        db.merge(key, data2);


        var data12_out = db.get(key);
        var bvs12_out = (BinaryValueSegment) SegmentEncoderDecoder.decode(data12_out, 0);

        verify_merge(bvs12_out, bvs1, bvs2);
    }

    @Test
    public void testObjectSegment_RawPlusRle() throws Exception {
        BinaryValueSegment bvs1 = new BinaryValueSegment(true);
        bvs1.add(ValueUtility.getBinaryValue("abc".getBytes()));
        bvs1.add(ValueUtility.getBinaryValue("bcd".getBytes()));
        bvs1.consolidate();
        byte[] data1 = SegmentEncoderDecoder.encode(bvs1);
        var key = "testObjectSegment_RawPlusRle".getBytes();
        db.put(key, data1);

        BinaryValueSegment bvs2 = new BinaryValueSegment(true);
        bvs2.add(ValueUtility.getBinaryValue("def".getBytes()));
        bvs2.add(ValueUtility.getBinaryValue("def".getBytes()));
        bvs2.consolidate();
        byte[] data2 = SegmentEncoderDecoder.encode(bvs2);
        db.merge(key, data2);

        var data12_out = db.get(key);
        var bvs12_out = (BinaryValueSegment) SegmentEncoderDecoder.decode(data12_out, 0);

        verify_merge(bvs12_out, bvs1, bvs2);
    }

    @Test
    public void testObjectSegment_RawPlusEnum() throws Exception {
        BinaryValueSegment bvs1 = new BinaryValueSegment(true);
        bvs1.add(ValueUtility.getBinaryValue("abc".getBytes()));
        bvs1.add(ValueUtility.getBinaryValue("bcd".getBytes()));
        bvs1.consolidate();
        byte[] data1 = SegmentEncoderDecoder.encode(bvs1);
        var key = "testObjectSegment_RawPlusEnum".getBytes();
        db.put(key, data1);

        BinaryValueSegment bvs2 = new BinaryValueSegment(true);
        bvs2.add(ValueUtility.getBinaryValue("xyz".getBytes()));
        bvs2.add(ValueUtility.getBinaryValue("abc".getBytes()));
        bvs2.add(ValueUtility.getBinaryValue("xyz".getBytes()));
        bvs2.consolidate();
        byte[] data2 = SegmentEncoderDecoder.encode(bvs2);
        db.merge(key, data2);

        var data12_out = db.get(key);
        var bvs12_out = (BinaryValueSegment) SegmentEncoderDecoder.decode(data12_out, 0);

        verify_merge(bvs12_out, bvs1, bvs2);
    }

    @Test
    public void testObjectSegment_RlePlusRaw() throws Exception {
        BinaryValueSegment bvs1 = new BinaryValueSegment(true);
        bvs1.add(ValueUtility.getBinaryValue("abc".getBytes()));
        bvs1.add(ValueUtility.getBinaryValue("abc".getBytes()));
        bvs1.consolidate();
        byte[] data1 = SegmentEncoderDecoder.encode(bvs1);
        var key = "testObjectSegment_RlePlusRaw".getBytes();
        db.put(key, data1);

        BinaryValueSegment bvs2 = new BinaryValueSegment(true);
        bvs2.add(ValueUtility.getBinaryValue("bcd".getBytes()));
        bvs2.add(ValueUtility.getBinaryValue("def".getBytes()));
        bvs2.consolidate();
        byte[] data2 = SegmentEncoderDecoder.encode(bvs2);
        db.merge(key, data2);

        var data12_out = db.get(key);
        var bvs12_out = (BinaryValueSegment) SegmentEncoderDecoder.decode(data12_out, 0);

        verify_merge(bvs12_out, bvs1, bvs2);
    }

    @Test
    public void testObjectSegment_RlePlusRle() throws Exception {
        BinaryValueSegment bvs1 = new BinaryValueSegment(true);
        bvs1.add(ValueUtility.getBinaryValue("abc".getBytes()));
        bvs1.add(ValueUtility.getBinaryValue("abc".getBytes()));
        bvs1.consolidate();
        byte[] data1 = SegmentEncoderDecoder.encode(bvs1);
        var key = "testObjectSegment_RlePlusRle".getBytes();
        db.put(key, data1);

        BinaryValueSegment bvs2 = new BinaryValueSegment(true);
        bvs2.add(ValueUtility.getBinaryValue("def".getBytes()));
        bvs2.add(ValueUtility.getBinaryValue("def".getBytes()));
        bvs2.consolidate();
        byte[] data2 = SegmentEncoderDecoder.encode(bvs2);
        db.merge(key, data2);

        var data12_out = db.get(key);
        var bvs12_out = (BinaryValueSegment) SegmentEncoderDecoder.decode(data12_out, 0);

        verify_merge(bvs12_out, bvs1, bvs2);
    }

    @Test
    public void testObjectSegment_RlePlusEnum() throws Exception {
        BinaryValueSegment bvs1 = new BinaryValueSegment(true);
        bvs1.add(ValueUtility.getBinaryValue("abc".getBytes()));
        bvs1.add(ValueUtility.getBinaryValue("abc".getBytes()));
        bvs1.consolidate();
        byte[] data1 = SegmentEncoderDecoder.encode(bvs1);
        var key = "testObjectSegment_RlePlusEnum".getBytes();
        db.put(key, data1);

        BinaryValueSegment bvs2 = new BinaryValueSegment(true);
        bvs2.add(ValueUtility.getBinaryValue("xyz".getBytes()));
        bvs2.add(ValueUtility.getBinaryValue("abc".getBytes()));
        bvs2.consolidate();
        byte[] data2 = SegmentEncoderDecoder.encode(bvs2);
        db.merge(key, data2);

        var data12_out = db.get(key);
        var bvs12_out = (BinaryValueSegment) SegmentEncoderDecoder.decode(data12_out, 0);

        verify_merge(bvs12_out, bvs1, bvs2);
    }

    @Test
    public void testObjectSegment_EnumPlusRaw() throws Exception {
        BinaryValueSegment bvs1 = new BinaryValueSegment(true);
        bvs1.add(ValueUtility.getBinaryValue("xyz".getBytes()));
        bvs1.add(ValueUtility.getBinaryValue("abc".getBytes()));
        bvs1.consolidate();
        byte[] data1 = SegmentEncoderDecoder.encode(bvs1);
        var key = "testObjectSegment_EnumPlusRaw".getBytes();
        db.put(key, data1);

        BinaryValueSegment bvs2 = new BinaryValueSegment(true);
        bvs2.add(ValueUtility.getBinaryValue("bcd".getBytes()));
        bvs2.add(ValueUtility.getBinaryValue("def".getBytes()));
        bvs2.consolidate();
        byte[] data2 = SegmentEncoderDecoder.encode(bvs2);
        db.merge(key, data2);

        var data12_out = db.get(key);
        var bvs12_out = (BinaryValueSegment) SegmentEncoderDecoder.decode(data12_out, 0);

        verify_merge(bvs12_out, bvs1, bvs2);
    }

    @Test
    public void testObjectSegment_EnumPlusRle() throws Exception {
        BinaryValueSegment bvs1 = new BinaryValueSegment(true);
        bvs1.add(ValueUtility.getBinaryValue("xyz".getBytes()));
        bvs1.add(ValueUtility.getBinaryValue("abc".getBytes()));
        bvs1.consolidate();
        byte[] data1 = SegmentEncoderDecoder.encode(bvs1);
        var key = "testObjectSegment_EnumPlusRle".getBytes();
        db.put(key, data1);

        BinaryValueSegment bvs2 = new BinaryValueSegment(true);
        bvs2.add(ValueUtility.getBinaryValue("def".getBytes()));
        bvs2.add(ValueUtility.getBinaryValue("def".getBytes()));
        bvs2.consolidate();
        byte[] data2 = SegmentEncoderDecoder.encode(bvs2);
        db.merge(key, data2);

        var data12_out = db.get(key);
        var bvs12_out = (BinaryValueSegment) SegmentEncoderDecoder.decode(data12_out, 0);

        verify_merge(bvs12_out, bvs1, bvs2);
    }

    @Test
    public void testObjectSegment_EnumPlusEnum() throws Exception {
        BinaryValueSegment bvs1 = new BinaryValueSegment(true);
        bvs1.add(ValueUtility.getBinaryValue("xyz".getBytes()));
        bvs1.add(ValueUtility.getBinaryValue("abc".getBytes()));
        bvs1.add(ValueUtility.getBinaryValue("xyz".getBytes()));
        bvs1.consolidate();
        byte[] data1 = SegmentEncoderDecoder.encode(bvs1);
        var key = "testObjectSegment_EnumPlusEnum".getBytes();
        db.put(key, data1);

        BinaryValueSegment bvs2 = new BinaryValueSegment(true);
        bvs2.add(ValueUtility.getBinaryValue("uvw".getBytes()));
        bvs2.add(ValueUtility.getBinaryValue("xyz".getBytes()));
        bvs2.add(ValueUtility.getBinaryValue("uvw".getBytes()));
        bvs2.consolidate();
        byte[] data2 = SegmentEncoderDecoder.encode(bvs2);
        db.merge(key, data2);

        var data12_out = db.get(key);
        var bvs12_out = (BinaryValueSegment) SegmentEncoderDecoder.decode(data12_out, 0);

        verify_merge(bvs12_out, bvs1, bvs2);
    }

    /***** gaps segment ****/
    @Test
    public void testGapsSegment() throws Exception {
        var gs1 = new SortedIntArray(2, 5, 6);
        var gs2 = new SortedIntArray(2, 5, 6);
        
        byte[] data1 = SegmentEncoderDecoder.encodeGaps(0, gs1);
        byte[] data2 = SegmentEncoderDecoder.encodeGaps(100, gs2);
        
        var key = "testGapsSegment".getBytes();
        db.put(key, data1);
        db.merge(key, data2);

        var data12_out = db.get(key);
        var gs12_out = (SortedIntArray) SegmentEncoderDecoder.decodeGaps(data12_out);
        assertEquals(new SortedIntArray(2, 5, 6, 102, 105, 106), gs12_out);
    }

    @Test
    public void testGapsSegment1() throws Exception {
        var gs1 = new SortedIntArray(2, 5, 6);
        var gs2 = new SortedIntArray(2, 5, 6);

        byte[] data1 = SegmentEncoderDecoder.encodeGaps(10, gs1);
        byte[] data2 = SegmentEncoderDecoder.encodeGaps(100, gs2);

        var key = "testGapsSegment1".getBytes();
        db.merge(key, data1);
        db.merge(key, data2);

        var data12_out = db.get(key);
        var gs12_out = (SortedIntArray) SegmentEncoderDecoder.decodeGaps(data12_out);
        assertEquals(new SortedIntArray(12, 15, 16, 102, 105, 106), gs12_out);
    }

    private void verify_merge(ValueSegment vs, ValueSegment... expectedSegments) {
        List<Value> expectedValues = new ArrayList<>();
        for (var segment : expectedSegments) {
            for (int i = 0; i < segment.size(); i++) {
                expectedValues.add(segment.getValue(i));
            }
        }

        List<Value> actualValues = new ArrayList<>();
        for (int i = 0; i < vs.size(); i++) {
            actualValues.add(vs.getValue(i));
        }

        assertEquals(expectedValues.size(), actualValues.size(),
                "The merged segment does not have the expected number of values.");

        for (int i = 0; i < expectedValues.size(); i++) {
            assertEquals(expectedValues.get(i), actualValues.get(i),
                    "The value at index " + i + " does not match.");
        }
    }
}

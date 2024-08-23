package org.yamcs.parameterarchive;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.yamcs.parameter.Value;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.ValueUtility;

import me.lemire.integercompression.FastPFOR128;
import me.lemire.integercompression.IntWrapper;

public class IntValueSegmentTest {
    @Test
    public void testShortNonRandom() throws IOException, DecodingException {
        int n = 3;
        List<Value> l = new ArrayList<>(n);
        IntValueSegment ivs = new IntValueSegment(true);
        for (int i = 0; i < n; i++) {
            Value v = ValueUtility.getSint32Value(100000 + i);
            l.add(v);
            ivs.add(v);
        }
        ivs.consolidate();
        ByteBuffer bb = ByteBuffer.allocate(ivs.getMaxSerializedSize());
        ivs.writeTo(bb);

        assertEquals(IntValueSegment.SUBFORMAT_ID_DELTAZG_VB, bb.get(0) & 0xF);
        bb.limit(bb.position());

        bb.rewind();
        IntValueSegment fvs1 = IntValueSegment.parseFrom(bb);

        for (int i = 0; i < n; i++) {
            assertEquals(l.get(i), fvs1.getValue(i));
        }
    }

    @Test
    public void testLongNonRandom() throws IOException, DecodingException {
        int n = 1000;
        List<Value> l = new ArrayList<>(n);
        IntValueSegment ivs = new IntValueSegment(false);
        for (int i = 0; i < n; i++) {
            Value v = ValueUtility.getUint32Value(100000 + i);
            l.add(v);
            ivs.add(v);
        }
        ivs.consolidate();
        ByteBuffer bb = ByteBuffer.allocate(ivs.getMaxSerializedSize());
        ivs.writeTo(bb);
        assertEquals(IntValueSegment.SUBFORMAT_ID_DELTAZG_FPF128_VB, bb.get(0) & 0xF);

        bb.limit(bb.position());
        bb.rewind();
        IntValueSegment fvs1 = IntValueSegment.parseFrom(bb);

        for (int i = 0; i < n; i++) {
            assertEquals(l.get(i), fvs1.getValue(i));
        }
    }

    @Test
    public void testRandom() throws IOException, DecodingException {
        int n = 10;
        Random rand = new Random(0);
        List<Value> l = new ArrayList<>(n);
        IntValueSegment ivs = new IntValueSegment(false);

        for (int i = 0; i < n; i++) {
            Value v = ValueUtility.getUint32Value(rand.nextInt());
            l.add(v);
            ivs.add(v);
        }
        ivs.consolidate();
        ByteBuffer bb = ByteBuffer.allocate(ivs.getMaxSerializedSize());
        ivs.writeTo(bb);
        assertEquals(IntValueSegment.SUBFORMAT_ID_RAW, bb.get(0) & 0xF);

        // assertEquals(5, bb.position());
        bb.limit(bb.position());

        bb.rewind();
        IntValueSegment fvs1 = IntValueSegment.parseFrom(bb);

        for (int i = 0; i < n; i++) {
            assertEquals(l.get(i), fvs1.getValue(i));
        }
    }

    // tests for equivalence of the C++ implementation
    // note that the C++ implementation has a SIMD version that produces slightly different output
    @Test
    public void test150() throws IOException, DecodingException {
        FastPFOR128 fastpfor = FastPFORFactory.get();
        int N = 9984;
        int[] mydata = new int[N];
        for (var i = 0; i < N; i += 150) {
            mydata[i] = i;
        }

        int[] compressedoutput = new int[N + 1024];

        IntWrapper inputoffset = new IntWrapper(0);
        IntWrapper outputoffset = new IntWrapper(0);

        fastpfor.compress(mydata, inputoffset, N, compressedoutput, outputoffset);

        long[] expected = { 9984, 1, 288, 16777216, 16782856, 16788489, 16794121, 16799754, 28170, 67764480, 436928768,
                806027520, 1175126272, 1544225024, 1913323776, 16777216, 16779275, 16784907, 16790540, 16796172,
                16801804, 30220, 202113280, 571212032, 940310784, 1309409536, 1678508288, 2047607040, 16777216,
                16781324, 16786956, 16792588, 16798220, 16803853, 32269, 336396544, 705495296, 1074594048, 1443692800,
                1812791552, 16777216, 16777741, 16783373, 16789005, 16794637, 16800269, 28685, 101515520, 470614272,
                839713024, 1208811776, 1577910528, 1947009280, 16777216, 16779789, 16785421, 16791053, 16796685,
                16802317, 30733, 235733248, 604832000, 973996288, 1343095040, 1712193792, 2081292544, 16777216,
                16781838, 16787470, 16793102, 16798734, 27150, 917760, 370016512, 739115264, 16256, 1, 150, 2, 230700,
                3, 944487000, 7, 1369801754, 2221353913l, 7803, 14, 1619830836, 713858921, 3303782578l, 282569956,
                792521326, 253, 27, 1377816680, 738792774, 352003757, 3050641572l, 191715602, 657460599, 3653814678l,
                3312177995l, 963494413, 2028934995, 2123620830, 12, 1748246586, 2458382870l, 1005111842, 619352402,
                2992728416l, 154 };
        for (var i = 0; i < outputoffset.intValue(); i++) {
            assertEquals((int) expected[i], compressedoutput[i]);
        }

    }

    // tests for equivalence of the C++ implementation
    @Test
    public void test256() {
        int[] data = new int[] { 2, 2, 536870906, 536870907, 536870911, 1073741828, 1073741831, 1073741836, 1073741839,
                1073741844, 1073741847, 1073741852, 1073741855, 1073741860, 1073741863, 1073741868, 1073741871,
                1073741876, 1073741879, 1073741884, 1073741887, 1073741892, 1073741895, 1073741900, 1073741903,
                1073741908, 1073741911, 1073741916, 1073741919, 1073741924, 1073741927, 1073741932, 1073741935,
                1073741940, 1073741943, 1073741948, 1073741951, 1073741956, 1073741959, 1073741964, 1073741967,
                1073741972, 1073741975, 1073741980, 1073741983, 1073741988, 1073741991, 1073741996, 1073741999,
                1073742004, 1073742007, 1073742012, 1073742015, 1073742020, 1073742023, 1073742028, 1073742031,
                1073742036, 1073742039, 1073742044, 1073742047, 1073742052, 1073742055, 1073742060, 1073742063,
                1073742068, 1073742071, 1073742076, 1073742079, 1073742084, 1073742087, 1073742092, 1073742095,
                1073742100, 1073742103, 1073742108, 1073742111, 1073742116, 1073742119, 1073742124, 1073742127,
                1073742132, 1073742135, 1073742140, 1073742143, 1073742148, 1073742151, 1073742156, 1073742159,
                1073742164, 1073742167, 1073742172, 1073742175, 1073742180, 1073742183, 1073742188, 1073742191,
                1073742196, 1073742199, 1073742204, 1073742207, 1073742212, 1073742215, 1073742220, 1073742223,
                1073742228, 1073742231, 1073742236, 1073742239, 1073742244, 1073742247, 1073742252, 1073742255,
                1073742260, 1073742263, 1073742268, 1073742271, 1073742276, 1073742279, 1073742284, 1073742287,
                1073742292, 1073742295, 1073742300, 1073742303, 1073742308, 1073742311, 536869156, 4000, 2001, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

        int N = data.length;
        int cppcompressed[] = { 256, 125, 2, (int) 2147483649l, 1744830462, (int) 4093640703l, 570425343, 503316480,
                419430400, 260046848, 171966464, 98566144, 59768832, 33030144, 19136512, 10354688, 5832704, 3112960,
                1720320, 909312, 495616, 260096, 140288, 73216, 39168, 20352, 10816, 5600, 2960, 1528, 804, 414,
                (int) 2147483865l, 1073741935, (int) 3758096442l, (int) 2415919133l, (int) 4160749583l, 603979783,
                503316484, 419430402, (int) 2407530497l, 1245708288, 635437056, 328204288, 167247872, 86245376,
                43909120, 22609920, 11501568, 5914624, 3006464, 1544192, 784384, 402432, 204288, 104704, 53120, 27200,
                13792, 7056, 3576, 1828, 926, (int) 2147484121l, 1073742063, (int) 3758096506l, (int) 2415919165l,
                (int) 4160749599l, 603979791, 503316488, 419430404, 260046850, (int) 2319450113l, 1172307968, 596639744,
                301465600, 153354240, 77463552, 39387136, 19890176, 10108928, 5103616, 2592768, 1308672, 664576, 335360,
                170240, 85888, 43584, 21984, 11152, 5624, 2852, 1438, (int) 2147484377l, 1073742191, (int) 3758096570l,
                (int) 2415919197l, (int) 4160749615l, 603979799, 503316492, 419430406, (int) 2407530499l,
                (int) 3393191937l, 1709178880, 865075200, 435683328, 220463104, 111017984, 56164352, 28278784, 14303232,
                7200768, 3641344, 1832960, 926720, 466432, 235776, 118656, 59968, 30176, 15248, 7672, 3876, 1950,
                1073738313, 7, 33554463, 65548, 2048, 2, 8200096 };

        int[] output = new int[N + 1024];

        IntWrapper inputoffset = new IntWrapper(0);
        IntWrapper outputoffset = new IntWrapper(0);
        FastPFOR128 fastpfor = FastPFORFactory.get();

        fastpfor.uncompress(cppcompressed, inputoffset, N, output, outputoffset);

        for (var i = 0; i < outputoffset.intValue(); i++) {
            assertEquals((int) data[i], output[i]);
        }
    }
}

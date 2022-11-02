package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.StringConverter;

public class StreamSelectBinaryFunctionTest extends YarchTestCase {

    final int n = 10;

    public void createFeeder1() throws YarchException {
        Stream s;
        final TupleDefinition tpdef = new TupleDefinition();
        tpdef.addColumn("x", DataType.BINARY);
        tpdef.addColumn("y", DataType.INT);
        byte[] x = new byte[300];
        s = (new Stream(ydb, "stream_in", tpdef) {
            @Override
            public void doStart() {
                for (int i = 0; i < n; i++) {
                    ByteArrayUtils.encodeInt(i, x, 0);
                    Tuple t = new Tuple(tpdef, new Object[] { x, 2 });
                    emitTuple(t);
                }
                close();
            }

            @Override
            protected void doClose() {
            }
        });
        ydb.addStream(s);
    }

    @Test
    public void testSubstring() throws Exception {
        createFeeder1();

        execute("create stream stream_out1 as select substring(x, y) from stream_in");
        List<Tuple> l = fetchAll("stream_out1");
        assertEquals(n, l.size());
        for (int i = 0; i < n; i++) {
            Tuple t = l.get(i);
            byte[] x = (byte[]) t.getColumn(0);
            assertEquals(i, ByteArrayUtils.decodeShort(x, 0));
        }
    }

    @Test
    public void testExtract_short() throws Exception {
        createFeeder1();

        execute("create stream stream_out1 as select extract_short(x, 2) from stream_in");
        List<Tuple> l = fetchAll("stream_out1");
        assertEquals(n, l.size());
        for (int i = 0; i < n; i++) {
            Tuple t = l.get(i);
            int x = (Short) t.getColumn(0);
            assertEquals(i, x);
        }
    }

    @Test
    public void testExtractShortWithCondition() throws Exception {
        createFeeder1();

        execute("create stream stream_out1 as select substring(x, 2) as name_subs from stream_in where extract_int(x, 0) = 2");
        List<Tuple> l = fetchAll("stream_out1");
        assertEquals(1, l.size());
        Tuple t0 = l.get(0);
        assertEquals("name_subs", t0.getColumnDefinition(0).getName());
        byte[] x = (byte[]) t0.getColumn(0);
        assertEquals(2, ByteArrayUtils.decodeShort(x, 0));
    }

    @Test
    public void testUnhex() throws Exception {
        createFeeder1();
        execute("create stream stream_out1 as select unhex('010203') + x as c_x from stream_in");
        List<Tuple> l = fetchAll("stream_out1");
        byte[] ic = new byte[4];
        assertEquals(n, l.size());
        for (int i = 0; i < n; i++) {
            Tuple t = l.get(i);
            ByteArrayUtils.encodeInt(i, ic, 0);
            byte[] cx = (byte[]) t.getColumn("c_x");
            assertEquals(303, cx.length);
            assertEquals("010203" + StringConverter.arrayToHexString(ic),
                    StringConverter.arrayToHexString(cx).substring(0, 14));
        }
    }

    @Test
    public void testExtractUnhex() throws Exception {
        createFeeder1();
        execute("create stream stream_out1 as select extract_short(unhex('010203'), 1) + y as c_y from stream_in");
        List<Tuple> l = fetchAll("stream_out1");
        byte[] ic = new byte[4];
        assertEquals(n, l.size());
        for (int i = 0; i < n; i++) {
            Tuple t = l.get(i);
            ByteArrayUtils.encodeInt(i, ic, 0);
            int cy = t.getIntColumn("c_y");
            assertEquals(0x0203 + 2, cy);
        }
    }

    @Test
    public void testBinaryColName() throws Exception {
        createFeeder1();
        execute("create stream stream_out1 as select x as binary from stream_in");
        List<Tuple> l = fetchAll("stream_out1");
        assertEquals(n, l.size());
        for (int i = 0; i < n; i++) {
            Tuple t = l.get(i);
            byte[] cx = (byte[]) t.getColumn("binary");
            assertEquals(300, cx.length);
        }
    }
}

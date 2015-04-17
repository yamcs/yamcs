package org.yamcs.yarch;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import org.junit.Test;

public class EnumTest extends YarchTestCase {
    int n=10;

    @Test
    public void test1() throws Exception {
        ydb.execute("create table testenum(gentime timestamp, packetName enum, packet binary, primary key(gentime,packetName))");
        ydb.execute("create stream testenum_in(gentime timestamp, packetName enum, packet binary)");
        ydb.execute("insert into testenum select * from testenum_in");

        Stream s=ydb.getStream("testenum_in");
        TupleDefinition td=new TupleDefinition();
        td.addColumn(new ColumnDefinition("gentime", DataType.TIMESTAMP));
        td.addColumn(new ColumnDefinition("packetName", DataType.ENUM));
        td.addColumn(new ColumnDefinition("packet", DataType.BINARY));

        for (int i=0;i<n;i++) {
            ByteBuffer bb=ByteBuffer.allocate(2000);
            while(bb.remaining()>0) bb.putInt(i);
            Tuple t=new Tuple(td, new Object[]{
                    1000L*i, "pn"+(i%10), bb.array()
            });
            s.emitTuple(t);
        }
        execute("close stream testenum_in");

        ydb.execute("create stream testenum_out as select * from testenum");
        Stream sout=ydb.getStream("testenum_out");
        final Semaphore semaphore=new Semaphore(0);
        final ArrayList<Tuple> tuples=new ArrayList<Tuple>();
        sout.addSubscriber(new StreamSubscriber() {
            @Override
            public void streamClosed(Stream stream) {
                semaphore.release();
            }

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                tuples.add(tuple);
            }
        });
        sout.start();
        semaphore.acquire();
        for(int i=0;i<n;i++) {
            Tuple t=tuples.get(i);
            assertEquals(i*1000l, (long)(Long)t.getColumn(0));
            assertEquals("pn"+(i%10), (String)t.getColumn(1));
        }
    }

}

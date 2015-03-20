package org.yamcs.yarch;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.yamcs.yarch.AbstractStream;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.streamsql.StreamSqlResult;


import static org.junit.Assert.*;
/* Results with compiled expression:
 *    Read 100000000 tuples in 27733 ms
 *
 */
public class StarSelectTest extends YarchTestCase {
    StreamSqlResult res;
    int n=10;

    class InputStreamFeeder implements Runnable {
        int isport;
        AbstractStream s;
        InputStreamFeeder() throws Exception {
            YarchDatabase dict=YarchDatabase.getInstance(context.getDbName());
            final TupleDefinition tpdef=new TupleDefinition();
            tpdef.addColumn("time", DataType.TIMESTAMP);
            tpdef.addColumn("id", DataType.INT);

            s=(new AbstractStream(dict,"tm_in",tpdef) {
                @Override
                public void start() {
                    for (int i=0;i<n;i++) {
                        Long time=(long)(i*1000);
                        Tuple t=new Tuple(tpdef, new Object[]{time,i});
                        emitTuple(t);
                    }
                }

                @Override
                protected void doClose() {
                }
            });
            dict.addStream(s);
        }

        public void run() {
            try {
                s.start();
            } catch (Exception e) {
                System.err.println("got exception in the InputStreamFeeder: "+e);
            }
        }
    }

    @Test
    public void testStar1() throws Exception {
        Thread t=new Thread(new InputStreamFeeder());
        res=execute("create stream tm_out1 as select 3,* from tm_in");
        YarchDatabase dict=YarchDatabase.getInstance(context.getDbName());
        Stream s=dict.getStream("tm_out1");
        final Semaphore finished=new Semaphore(0);
        s.addSubscriber(new StreamSubscriber() {
            long t0;
            @Override
            public void streamClosed(Stream stream) {
                // TODO Auto-generated method stub

            }
            int k=0;
            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                int const_three=(Integer)tuple.getColumn(0);
                assertEquals(const_three,3);
                long time=(Long)tuple.getColumn(1);
                assertEquals(1000*k,time);
                int i=(Integer)tuple.getColumn(2);
                //          System.out.println("id: "+id+", time: "+time);
                assertEquals(k, i);
                k++;
                if(k>=n) {
                    finished.release();
                }
               
            }
        });
        s.start();
        // t.start();

        assertTrue(finished.tryAcquire(10, TimeUnit.SECONDS));
        execute("close stream tm_in");
    }
}

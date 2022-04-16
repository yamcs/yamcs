package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.Semaphore;

import org.junit.jupiter.api.Test;

/* Results with compiled expression:
 *    Read 100000000 tuples in 27733 ms
 *
 */
public class StreamSelectPerfTest extends YarchTestCase {

    int n = 10000000;

    class InputStreamFeeder implements Runnable {
        int isport;
        Stream s;

        InputStreamFeeder() throws Exception {
            final TupleDefinition tpdef = new TupleDefinition();
            tpdef.addColumn("time", DataType.TIMESTAMP);
            tpdef.addColumn("id", DataType.INT);

            s = (new Stream(ydb, "tm_in", tpdef) {
                @Override
                public void doStart() {
                    for (int i = 0; i < n; i++) {
                        Long time = (long) (i * 1000);
                        Integer id = i % 10;
                        Tuple t = new Tuple(tpdef, new Object[] { time, id });
                        emitTuple(t);
                    }
                }

                @Override
                protected void doClose() {
                }
            });
            ydb.addStream(s);
        }

        @Override
        public void run() {
            try {
                s.start();
            } catch (Exception e) {
                System.err.println("got exception in the InputStreamFeeder: " + e);
            }
        }
    }

    @Test
    public void testFilter1() throws Exception {
        Thread t = new Thread(new InputStreamFeeder());
        execute("create stream tm_out1 as select * from tm_in where id>4 or id=3");
        // res=execute("create output stream tm_out1 as select * from tm_in where id=3");
        Stream s = ydb.getStream("tm_out1");
        final Semaphore finished = new Semaphore(0);
        s.addSubscriber(new StreamSubscriber() {
            long t0;

            @Override
            public void streamClosed(Stream stream) {

            }

            int k = 3;

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                if (k == 3) {
                    t0 = System.currentTimeMillis();
                }
                long time = (Long) tuple.getColumn("time");
                assertEquals(1000 * k, time);
                int id = (Integer) tuple.getColumn("id");

                // System.out.println("id: "+id+", time: "+time);
                assertEquals(k % 10, id);
                do {
                    k++;
                } while (!((k % 10 > 4) || (k % 10 == 3)));
                if (k >= n) {
                    // System.out.println("Read "+n+" tuples in "+(System.currentTimeMillis()-t0)+" ms");
                    finished.release();
                }
            }
        });
        s.start();
        // t.start();

        finished.acquire();
        execute("close stream tm_in");
    }
}

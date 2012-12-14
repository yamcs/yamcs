package org.yamcs.yarch;

import static org.junit.Assert.*;

import org.junit.Test;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlResult;


public class StreamSelectTest extends YarchTestCase {
    StreamSqlResult res;
    int blength=100;
    int n=1000;

    void feed(Stream s) throws StreamSqlException, ParseException {
        for (int i=0;i<n;i++) {
            Tuple t=new Tuple(s.getDefinition(), new Object[]{i*1000L, i%10});
            s.emitTuple(t);
        }
    }

    @Test
    public void testFilter1() throws Exception {
        ydb.execute("create stream tm_in(gentime timestamp, id int)");

        new StreamChecker("tm_out1", "select * from tm_in where id=5 or id=3",
                new TupleChecker() {
            int x=3;
            @Override
            public void check(int count, long time, int id) {
                assertEquals(1000*x, time);
                assertEquals(x%10, id);
                do { x++;} while(!((x%10==5)||(x%10==3)));
            }
        });
        Stream s=ydb.getStream("tm_in");
        feed(s);
        s.close();
    }

    @Test
    public void testFilter2() throws Exception {
        ydb.execute("create stream tm_in(gentime timestamp, id int)");
        new StreamChecker("tm_out1", "select * from tm_in where id=5 or id=3",
                new TupleChecker() {
            int x=3;
            @Override
            public void check(int count, long time, int id) {
                assertEquals(1000*x, time);
                assertEquals(x%10, id);
                do { x++;} while(!((x%10==5)||(x%10==3)));
            }
        });


        new StreamChecker("tm_out2", "select * from tm_in where id>5 and id<9",
                new TupleChecker() {
            int x=6;
            @Override
            public void check(int count, long time, int id) {
                assertEquals(1000*x, time);
                assertEquals(x%10, id);
                do { x++;} while(!((x%10>5)&&(x%10<9)));
            }
        });

        Stream s=ydb.getStream("tm_in");
        feed(s);
        s.close();
    }

    @Test
    public void testNegative() throws Exception {
        ydb.execute("create stream tm_negative_in(gentime timestamp, id int)");

        new StreamChecker("tm_negative_out", "select * from tm_negative_in where id=-5 or id > -3",
                new TupleChecker() {
            int[] x=new int[] {-5, -2, -1};
            @Override
            public void check(int count, long time, int id) {
                assertEquals(1000*x[count], time);
                assertEquals(x[count], id);
            }
        });
        Stream s=ydb.getStream("tm_negative_in");
        for (int i=-10;i<0;i++) {
            Tuple t=new Tuple(s.getDefinition(), new Object[]{i*1000L, i});
            s.emitTuple(t);
        }
        s.close();
    }
    
    
    class StreamChecker implements StreamSubscriber {
        TupleChecker tc;
        int count=0;
        StreamChecker(String name, String query, TupleChecker tc) throws StreamSqlException, ParseException {
            this.tc=tc;
            ydb.execute("create stream "+name+" as "+query);
            ydb.getStream(name).addSubscriber(this);
        }

        @Override
        public void onTuple(Stream stream, Tuple tuple) {
            long time=(Long)tuple.getColumn(0);
            int id=(Integer)tuple.getColumn(1);
            tc.check(count, time, id);
            count++;
        }

        @Override
        public void streamClosed(Stream stream) {
        }
    }

    interface TupleChecker {
        public void check(int count, long time, int id);
    }
}
package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

public class StreamSelectTest extends YarchTestCase {
    int blength = 100;
    int n = 1000;

    void feed(Stream s, long start) throws StreamSqlException, ParseException {
        long m = start / 1000;
        for (long i = m; i < m + n; i++) {
            int x = (int) (i % 10);
            Tuple t = new Tuple(s.getDefinition(), new Object[] { i * 1000L, x });
            s.emitTuple(t);
        }
    }

    @Test
    public void testFilter1() throws Exception {
        execute("create stream tm_in(gentime timestamp, id int)");

        new StreamChecker("tm_out1", "select * from tm_in where id=5 or id=3",
                new TupleChecker() {
                    int x = 3;

                    @Override
                    public void check(int count, long time, int id) {
                        assertEquals(1000l * x, time);
                        assertEquals(x % 10, id);
                        do {
                            x++;
                        } while (!((x % 10 == 5) || (x % 10 == 3)));
                    }
                });
        Stream s = ydb.getStream("tm_in");
        feed(s, 0);
        s.close();
    }

    @Test
    public void testFilter2() throws Exception {
        execute("create stream tm_in(gentime timestamp, id int)");
        StreamChecker sc1 = new StreamChecker("tm_out1", "select * from tm_in where id=5 or id=3",
                new TupleChecker() {
                    int x = 3;

                    @Override
                    public void check(int count, long time, int id) {
                        assertEquals(1000l * x, time);
                        assertEquals(x % 10, id);
                        do {
                            x++;
                        } while (!((x % 10 == 5) || (x % 10 == 3)));
                    }
                });

        StreamChecker sc2 = new StreamChecker("tm_out2", "select * from tm_in where id>5 and id<9",
                new TupleChecker() {
                    int x = 6;

                    @Override
                    public void check(int count, long time, int id) {
                        assertEquals(1000l * x, time);
                        assertEquals(x % 10, id);
                        do {
                            x++;
                        } while (!((x % 10 > 5) && (x % 10 < 9)));
                    }
                });

        Stream s = ydb.getStream("tm_in");
        feed(s, 0);
        s.close();
        assertEquals(n * 2 / 10, sc1.count);
        assertEquals(n * 3 / 10, sc2.count);
    }

    @Test
    public void testFilter3() throws Exception {
        long t0 = TimeEncoding.parse("2020-07-10T00:00:00");
        execute("create stream tm_in(gentime timestamp, id int)");
        StreamChecker sc1 = new StreamChecker("tm_out1",
                "select * from tm_in where gentime > '2020-07-10T00:00:02' and '2020-07-10T00:00:05' >= gentime",
                new TupleChecker() {
                    int x = 3;

                    @Override
                    public void check(int count, long time, int id) {
                        assertEquals(t0 + 1000l * x, time);
                        x++;
                    }
                });

        Stream s = ydb.getStream("tm_in");
        feed(s, t0);
        s.close();

        assertEquals(3, sc1.count);
    }

    @Test
    public void testNegative() throws Exception {
        execute("create stream tm_negative_in(gentime timestamp, id int)");

        new StreamChecker("tm_negative_out", "select * from tm_negative_in where id=-5 or id > -3",
                new TupleChecker() {
                    int[] x = new int[] { -5, -2, -1 };

                    @Override
                    public void check(int count, long time, int id) {
                        assertEquals(1000l * x[count], time);
                        assertEquals(x[count], id);
                    }
                });
        Stream s = ydb.getStream("tm_negative_in");
        for (int i = -10; i < 0; i++) {
            Tuple t = new Tuple(s.getDefinition(), new Object[] { i * 1000L, i });
            s.emitTuple(t);
        }
        s.close();
    }

    class StreamChecker implements StreamSubscriber {
        TupleChecker tc;
        int count = 0;

        StreamChecker(String name, String query, TupleChecker tc) throws StreamSqlException, ParseException {
            this.tc = tc;
            execute("create stream " + name + " as " + query);
            ydb.getStream(name).addSubscriber(this);
        }

        @Override
        public void onTuple(Stream stream, Tuple tuple) {
            long time = (Long) tuple.getColumn(0);
            int id = (Integer) tuple.getColumn(1);
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

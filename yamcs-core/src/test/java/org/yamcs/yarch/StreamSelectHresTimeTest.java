package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.yamcs.time.Instant;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

public class StreamSelectHresTimeTest extends YarchTestCase {
    int blength = 100;
    int n = 1000;

    void feed(Stream s, long start) throws StreamSqlException, ParseException {
        long m = start / 1000;
        for (long i = m; i < m + n; i++) {
            int x = (int) (i % 10);
            Tuple t = new Tuple(s.getDefinition(), new Object[] { Instant.get(i * 1000L), x });
            s.emitTuple(t);
        }
    }

    @Test
    public void testFilterAnd() throws Exception {
        execute("create stream tm_in(gentime hres_timestamp, id int)");
        long t = TimeEncoding.parse("2025-09-02T15:29:00");
        var checker = new TupleChecker() {
            int x = 60;

            @Override
            public void check(int count, Instant time, int id) {
                assertEquals(t + 1000l * x, time.getMillis());
                x++;
            }
        };

        new StreamChecker("tm_out1",
                "select * from tm_in where gentime>='2025-09-02T15:30:00' and gentime<'2025-09-02T15:30:05'",
                checker);
        Stream s = ydb.getStream("tm_in");
        feed(s, t);
        s.close();
        assertEquals(65, checker.x);
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
            Instant time = (Instant) tuple.getColumn(0);
            int id = (Integer) tuple.getColumn(1);
            tc.check(count, time, id);
            count++;
        }

        @Override
        public void streamClosed(Stream stream) {
        }
    }

    interface TupleChecker {
        public void check(int count, Instant time, int id);
    }
}

package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

public class StreamSqlParserTest extends YarchTestCase {

    @Test
    public void testCreateDropTable() throws ParseException, StreamSqlException {
        execute(
                "create table droptabletest_test1(\"time\" timestamp, apidSeqCount int, packet binary, primary key(\"time\",apidSeqCount))");
        TableDefinition tbl = ydb.getTable("droptabletest_test1");
        assertNotNull(tbl);
        assertEquals(tbl.getName(), "droptabletest_test1");
        ColumnDefinition ctime = tbl.getColumnDefinition("time");
        assertEquals(ctime.getType(), DataType.TIMESTAMP);

        ColumnDefinition capidSeqCount = tbl.getColumnDefinition("apidSeqCount");
        assertEquals(capidSeqCount.getType(), DataType.INT);

        ColumnDefinition cpacket = tbl.getColumnDefinition("packet");
        assertEquals(cpacket.getType(), DataType.BINARY);

        execute("drop table droptabletest_test1");
        tbl = ydb.getTable("droptabletest_test1");

        assertNull(tbl);
    }

    @Test
    public void testExists() throws ParseException, StreamSqlException {
        execute("create table if not exists existstest_test1(col1 int, primary key(col1))");
        TableDefinition tbl = ydb.getTable("existstest_test1");
        assertNotNull(tbl);
        assertEquals(tbl.getName(), "existstest_test1");
        assertNotNull(tbl.getColumnDefinition("col1"));

        execute("create table if not exists existstest_test1(col1 int, col2 int, primary key(col1))");
        tbl = ydb.getTable("existstest_test1");
        assertNotNull(tbl);
        assertEquals(tbl.getName(), "existstest_test1");
        assertNotNull(tbl.getColumnDefinition("col1"));
        assertNull(tbl.getColumnDefinition("col2"));

        execute("drop table if exists existstest_test1");
        tbl = ydb.getTable("existstest_test1");

        assertNull(tbl);

        execute("drop table if exists sometablethatreallydoesntexist");
    }

    @Test
    public void testErrors() throws Exception {
        StreamSqlException e = null;
        try {
            execute("close stream testerr_stream");
        } catch (StreamSqlException e1) {
            e = e1;
        }
        assertNotNull(e);
        assertEquals("RESOURCE_NOT_FOUND Stream or table 'testerr_stream' not found", e.getMessage());

        e = null;
        try {
            execute("show stream unexistent_stream");
        } catch (StreamSqlException e1) {
            e = e1;
        }
        assertNotNull(e);
        assertEquals("RESOURCE_NOT_FOUND Stream or table 'unexistent_stream' not found", e.getMessage());
    }

    @Test
    public void testArrayColumn() throws ParseException, StreamSqlException {
        execute(
                "create table arraycol_test1(id long, tag string[], primary key(id))");
        TableDefinition tbl = ydb.getTable("arraycol_test1");
        assertNotNull(tbl);
        ColumnDefinition ctime = tbl.getColumnDefinition("tag");
        ArrayDataType dt = (ArrayDataType) ctime.getType();
        assertEquals(dt.getElementType(), DataType.STRING);

        execute("drop table arraycol_test1");
        tbl = ydb.getTable("arraycol_test1");

        assertNull(tbl);
    }
    /* @Test
    public void testShowStreams() throws Exception {
        ydb.execute("create input stream testshow_is1(a int, b timestamp)");
    
        execute("create input stream testshow_is2(c binary, d int)");
        int iport2 = (Integer) res.getParam("port");
    
        execute("create output stream testshow_os as select * from testshow_is1");
        int oport = (Integer) res.getParam("port");
    
        execute("show streams");
        assertEquals(
                "INPUT STREAM testshow_is1(a INT, b TIMESTAMP)\nOUTPUT STREAM testshow_os(a INT, b TIMESTAMP)\nINPUT STREAM testshow_is2(c BINARY, d INT)\n",
                res.toString());
    
        execute("show stream testshow_is1");
        assertEquals("INPUT STREAM testshow_is1(a INT, b TIMESTAMP)", res.toString());
    
        execute("show stream testshow_is2 port");
        assertEquals("port=" + iport2, res.toString());
        
        execute("show stream testshow_os port");
        assertEquals("port=" + oport, res.toString());
    }*/
}

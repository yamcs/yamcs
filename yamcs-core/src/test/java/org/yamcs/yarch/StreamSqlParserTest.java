package org.yamcs.yarch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlResult;



public class StreamSqlParserTest extends YarchTestCase {
	StreamSqlResult res;
	
	@Test
	public void testCreateDropTable() throws ParseException, StreamSqlException {
	    ydb.execute("create table droptabletest_test1(\"time\" timestamp, apidSeqCount int, packet binary, primary key(\"time\",apidSeqCount))");
		TableDefinition tbl=ydb.getTable("droptabletest_test1");
		assertNotNull(tbl);
		assertEquals(tbl.getName(), "droptabletest_test1");
		ColumnDefinition ctime=tbl.getColumnDefinition("time");
		assertEquals(ctime.getType(),DataType.TIMESTAMP);
		
		ColumnDefinition capidSeqCount=tbl.getColumnDefinition("apidSeqCount");
		assertEquals(capidSeqCount.getType(),DataType.INT);
		
		ColumnDefinition cpacket=tbl.getColumnDefinition("packet");
		assertEquals(cpacket.getType(),DataType.BINARY);
		
		ydb.execute("drop table droptabletest_test1");
		tbl=ydb.getTable("droptabletest_test1");
		
		assertNull(tbl);
	}
	
    @Test
    public void testExists() throws ParseException, StreamSqlException {
        ydb.execute("create table if not exists existstest_test1(col1 int, primary key(col1))");
        TableDefinition tbl=ydb.getTable("existstest_test1");
        assertNotNull(tbl);
        assertEquals(tbl.getName(), "existstest_test1");
        assertNotNull(tbl.getColumnDefinition("col1"));
        
        ydb.execute("create table if not exists existstest_test1(col1 int, col2 int, primary key(col1))");
        tbl=ydb.getTable("existstest_test1");
        assertNotNull(tbl);
        assertEquals(tbl.getName(), "existstest_test1");
        assertNotNull(tbl.getColumnDefinition("col1"));
        assertNull(tbl.getColumnDefinition("col2"));
        
        ydb.execute("drop table if exists existstest_test1");
        tbl=ydb.getTable("existstest_test1");
        
        assertNull(tbl);
        
        ydb.execute("drop table if exists sometablethatreallydoesntexist");
    }
	
	@Test
    public void testErrors() throws Exception{
		StreamSqlException e=null;
		try {
			res=execute("close stream testerr_stream");
		} catch (StreamSqlException e1) {
			e=e1;
		}
		assertNotNull(e);
		assertEquals("RESOURCE_NOT_FOUND Stream or table 'testerr_stream' not found", e.getMessage());
		
		e=null;
		try {
			res=execute("show stream unexistent_stream");
		} catch (StreamSqlException e1) {
			e=e1;
		}
		assertNotNull(e);
		assertEquals("RESOURCE_NOT_FOUND Stream or table 'unexistent_stream' not found", e.getMessage());
	}
	
	//@Test
	public void testShowStreams() throws Exception {
	    ydb.execute("create input stream testshow_is1(a int, b timestamp)");
		
		res=execute("create input stream testshow_is2(c binary, d int)");
		int iport2=(Integer)res.getParam("port");
		
		res=execute("create output stream testshow_os as select * from testshow_is1");
		int oport=(Integer)res.getParam("port");
		
		res=execute("show streams");
		assertEquals("INPUT STREAM testshow_is1(a INT, b TIMESTAMP)\nOUTPUT STREAM testshow_os(a INT, b TIMESTAMP)\nINPUT STREAM testshow_is2(c BINARY, d INT)\n",res.toString());
		
		res=execute("show stream testshow_is1");
		assertEquals("INPUT STREAM testshow_is1(a INT, b TIMESTAMP)",res.toString());
		
		
		res=execute("show stream testshow_is2 port");
		assertEquals("port="+iport2,res.toString());
		
		res=execute("show stream testshow_os port");
		assertEquals("port="+oport,res.toString());		
	}
}

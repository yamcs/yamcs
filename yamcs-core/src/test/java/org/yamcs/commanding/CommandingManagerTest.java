package org.yamcs.commanding;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.ErrorInCommand;
import org.yamcs.utils.CcsdsPacket;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.MetaCommandProcessor;
import org.yamcs.xtceproc.XtceDbFactory;

public class CommandingManagerTest {
	static XtceDb xtceDb;
	
	@BeforeClass 
	public static void beforeClass() throws ConfigurationException {
		xtceDb = XtceDbFactory.getInstanceByConfig("refmdb");
	}
	
	@Test
	public void testParsingCmdSpreadhseet() throws Exception {
		MetaCommand mc = xtceDb.getMetaCommand("/REFMDB/SUBSYS1/INTEGER_ARG_TC");
		List<Argument> argList = mc.getArgumentList();
		assertNotNull(argList);
		assertEquals(4, argList.size());
		
	}
	@Test
	public void testIntegerArg() throws Exception {
		MetaCommand mc = xtceDb.getMetaCommand("/REFMDB/SUBSYS1/INTEGER_ARG_TC");
		assertNotNull(mc);
		
		List<ArgumentAssignment> aaList = Arrays.asList(new ArgumentAssignment("uint8_arg", "1"),
					new ArgumentAssignment("uint16_arg", "2"),
					new ArgumentAssignment("int32_arg", "-3"),
					new ArgumentAssignment("uint64_arg", "4"));
		
		byte[] b= MetaCommandProcessor.buildCommand(mc, aaList);
		assertEquals(31, b.length);
		
		CcsdsPacket p = new CcsdsPacket(b);
		
		assertEquals(100, p.getAPID());
		ByteBuffer bb = ByteBuffer.wrap(b);
		assertEquals(1, bb.get(16));
		assertEquals(2, bb.getShort(17));
		assertEquals(-3, bb.getInt(19));
		assertEquals(4, bb.getLong(23));
		
		
	}
	
	@Test
	public void testValidIntegerRange() throws Exception {
		MetaCommand mc = xtceDb.getMetaCommand("/REFMDB/SUBSYS1/INTEGER_ARG_TC");
		assertNotNull(mc);
		List<ArgumentAssignment> aaList = Arrays.asList(new ArgumentAssignment("uint8_arg", "5"),
				new ArgumentAssignment("uint16_arg", "2"),
				new ArgumentAssignment("int32_arg", "-3"),
				new ArgumentAssignment("uint64_arg", "4"));
		ErrorInCommand e = null;
		try {
			MetaCommandProcessor.buildCommand(mc, aaList);
		} catch (ErrorInCommand e1) {
			e=e1;
		}		
		assertNotNull(e);
		assertTrue(e.getMessage().contains("not in the range"));
	}
	
}

package org.yamcs.archive;

import static org.junit.Assert.*;

import org.hornetq.core.server.embedded.EmbeddedHornetQ;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.YamcsServer;

public class TestPpTupleTranslator {
	static EmbeddedHornetQ hornetServer;
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		hornetServer=YamcsServer.setupHornet();
	}
	
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		hornetServer.stop();
	}
	
	@Test
	public void testBuildMessage() {
		fail("Not yet implemented");
	}

	@Test
	public void testBuildTuple() {
		fail("Not yet implemented");
	}

}

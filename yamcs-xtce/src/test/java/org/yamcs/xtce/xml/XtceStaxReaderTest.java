package org.yamcs.xtce.xml;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Iterator;

import javax.xml.stream.XMLStreamException;

import org.junit.Before;
import org.junit.Test;
import org.yamcs.xtce.SpaceSystem;

/**
 * Implements unit tests for {@link XtceStaxReader}.
 */
public class XtceStaxReaderTest {

	private XtceStaxReader reader;
	
	@Before
	public void setup() {
		reader = new XtceStaxReader();
	}
	
	/**
	 * Tests that a space system created by reading a single XML file
	 * is the same as that created by reading an XML file broken into
	 * three parts using xi:include.
	 * 
	 * @throws XMLStreamException if there is an XML parsing error
	 * @throws IOException if there is a problem reading an XML file
	 */
	@Test
	public void testIncludedAgainstCombined() throws XMLStreamException, IOException {
		SpaceSystem combined = reader.readXmlDocument("src/test/resources/xinclude/combined-space-system.xml");
		SpaceSystem included = reader.readXmlDocument("src/test/resources/xinclude/top-space-system.xml");
		
		compareSpaceSystems(combined, included);
	}
	
	/**
	 * Compares two SpaceSystem objects. Verifies only that the lengths of
	 * the telemetry types, parameters, and containers are the same, that 
	 * the lengths of the metacommands are the same, and verifies each
	 * subsystem recursively.
	 * 
	 * @param expected the space system with the expected structure
	 * @param actual the space system to compare against
	 */
	private void compareSpaceSystems(SpaceSystem expected, SpaceSystem actual) {
		assertEquals(expected.getName(), actual.getName());
		assertEquals(expected.getParameterTypes().size(), actual.getParameterTypes().size());
		assertEquals(expected.getParameters().size(), actual.getParameters().size());
		assertEquals(expected.getSequenceContainers().size(), actual.getSequenceContainers().size());
		assertEquals(expected.getMetaCommands().size(), actual.getMetaCommands().size());
		assertEquals(expected.getSubSystems().size(), actual.getSubSystems().size());
		
		Iterator<SpaceSystem> expectedIter = expected.getSubSystems().iterator();
		Iterator<SpaceSystem> actualIter = actual.getSubSystems().iterator();
		while (expectedIter.hasNext()) {
			compareSpaceSystems(expectedIter.next(), actualIter.next());
		}
	}
	
}

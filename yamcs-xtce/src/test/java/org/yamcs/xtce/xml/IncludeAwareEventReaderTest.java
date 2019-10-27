package org.yamcs.xtce.xml;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.FileNotFoundException;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

import org.junit.Before;
import org.junit.Test;

/**
 * Implements unit tests for {@link IncludeAwareEventReader}.
 */
public class IncludeAwareEventReaderTest {
	
	private static final String XTCE_NAMESPACE = "http://www.omg.org/spec/XTCE/20180204";
	private static final QName SPACE_SYSTEM_TAG = new QName(XTCE_NAMESPACE, "SpaceSystem");

    private XMLInputFactory factory;
    
	@Before
	public void setup() {
		factory = XMLInputFactory.newInstance();
	}
	
	/**
	 * Tests the event sequence when reading a file with no includes.
	 * 
	 * @throws FileNotFoundException if the included file is not found
	 * @throws XMLStreamException if there is an XML parsing error
	 */
	@Test
	public void testNoXInclude() throws FileNotFoundException, XMLStreamException {
		XMLEventReader reader = new IncludeAwareEventReader(factory, "src/test/resources/xinclude/simple-document.xml");
		assertTrue(reader.nextEvent().isStartDocument());
		assertTrue(reader.nextEvent().isStartElement());
		assertTrue(reader.nextEvent().isEndElement());
		assertTrue(reader.nextEvent().isEndDocument());
		assertFalse(reader.hasNext());
		reader.close();
	}
	
	/**
	 * Tests the sequence of events when reading an XML file containing an
	 * include.
	 * 
	 * @throws FileNotFoundException if the included file is not found
	 * @throws XMLStreamException if there is an XML parsing error
	 */
	@Test
	public void testWithXInclude() throws FileNotFoundException, XMLStreamException {
		checkWithXInclude("src/test/resources/xinclude/top-level.xml");
		checkWithXInclude("src/test/resources/xinclude/top-level-with-parse.xml");
	}
	
	private void checkWithXInclude(String path) throws FileNotFoundException, XMLStreamException {
		XMLEventReader reader = new IncludeAwareEventReader(factory, path);
		assertTrue(reader.nextEvent().isStartDocument());
		XMLEvent topTag = reader.nextEvent();
		assertTrue(topTag.isStartElement());
		assertTrue(topTag.asStartElement().getName().equals(SPACE_SYSTEM_TAG));
		
		XMLEvent includedTag = reader.nextTag();
		assertTrue(includedTag.isStartElement());
		assertTrue(includedTag.asStartElement().getName().equals(SPACE_SYSTEM_TAG));
		assertTrue(reader.nextTag().isEndElement());
		
		assertTrue(reader.nextTag().isEndElement());
		assertTrue(reader.nextEvent().isEndDocument());
		assertFalse(reader.hasNext());
		reader.close();
	}
	
	/**
	 * Tests that including a file that doesn't exist throws an exception.
	 * 
	 * @throws FileNotFoundException if the included file is not found
	 * @throws XMLStreamException if there is an XML parsing error
	 */
	@Test
	public void testIncludedFileNotFound() throws FileNotFoundException, XMLStreamException {
		XMLEventReader reader = new IncludeAwareEventReader(factory, "src/test/resources/xinclude/file-not-found.xml");
		assertTrue(reader.nextEvent().isStartDocument());
		XMLEvent topTag = reader.nextEvent();
		assertTrue(topTag.isStartElement());
		assertTrue(topTag.asStartElement().getName().equals(SPACE_SYSTEM_TAG));
		
		// Try to read the included file.
		try {
			reader.nextTag();
			fail("Should have gotten an exception reading into the xi:include");
		} catch (XMLStreamException e) {
			// If we get here, test passes.
		}
	}
	
	@Test
	public void testNonXMLInclude() throws FileNotFoundException, XMLStreamException {
		XMLEventReader reader = new IncludeAwareEventReader(factory, "src/test/resources/xinclude/non-xml-include.xml");
		assertTrue(reader.nextEvent().isStartDocument());
		XMLEvent topTag = reader.nextEvent();
		assertTrue(topTag.isStartElement());
		assertTrue(topTag.asStartElement().getName().equals(SPACE_SYSTEM_TAG));
		
		// Try to read the included file.
		try {
			reader.nextTag();
			fail("Should have gotten an exception reading into the xi:include");
		} catch (XMLStreamException e) {
			// If we get here, test passes.
		}
	}
	
}

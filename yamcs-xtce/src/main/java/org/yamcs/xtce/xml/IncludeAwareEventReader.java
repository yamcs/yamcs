package org.yamcs.xtce.xml;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

/**
 * Implements an {@link XMLEventReader} that handles <code>xi:include</code>
 * elements, in a limited way. Only file relative or absolute file references
 * in the <code>href</code> attribute are supported, and the <code>xi:fallback</code>
 * sub-element is not used. Also, only <code>xml</code> parsing of the included
 * file is supported.
 */
public class IncludeAwareEventReader implements XMLEventReader {
	
	private static final String XINCLUDE_NAMESPACE = "http://www.w3.org/2001/XInclude";
	private static final QName XINCLUDE_TAG = new QName(XINCLUDE_NAMESPACE, "include");
	private static final QName XINCLUDE_HREF_ATTR = new QName("href");
	private static final QName XINCLUDE_PARSE_ATTR = new QName("parse");

	private XMLInputFactory factory;
	private URI baseURI;
	private Deque<XMLEventReader> readers = new ArrayDeque<>();
	
	/**
	 * Creates a new instance.
	 * 
	 * @param factory the input factory to use for creating the {@link XMLEventReader}
	 *     for the main file and any included files
	 * @param path the path to the main XML file
	 * @throws FileNotFoundException if any file cannot be found
	 * @throws XMLStreamException if there is an XML parsing error in the main file
	 *     or any included file
	 */
	public IncludeAwareEventReader(XMLInputFactory factory, String path) throws FileNotFoundException, XMLStreamException {
		this.factory = factory;
		File f = new File(path);
		baseURI = f.toURI();
		readers.add(createFileEventReader(f));
	}
	
	/**
	 * Creates an {@link XMLEventReader} for a given file.
	 * 
	 * @param f the file to read
	 * @return an {@link XMLEventReader} for the file
	 * @throws FileNotFoundException if the file does not exist
	 * @throws XMLStreamException if there is a problem creating the event reader
	 */
	private XMLEventReader createFileEventReader(File f) throws FileNotFoundException, XMLStreamException {
		return factory.createXMLEventReader(new BufferedInputStream(new FileInputStream(f)));
	}

	/**
	 * Starts a new include level for an XML file.
	 * 
	 * @param reader the XML event reader for the file
	 */
	private void pushReader(XMLEventReader reader) {
		readers.addFirst(reader);
	}
	
	/**
	 * Leaves an include level to return to the parent file.
	 */
	private void popReader() {
		readers.removeFirst();
	}

	/**
	 * Gets the {@link XMLEventReader} for the current include level.
	 * 
	 * @return the current {@link XMLEventReader}
	 * @throw IllegalStateException if an attempt is made to leave the top reading level
	 */
	private XMLEventReader getReader() {
		if (readers.isEmpty()) {
			throw new IllegalStateException("Should not happen: all readers are popped");
		}
		return readers.getFirst();
	}

	/**
	 * Tests whether we are currently reading events from an included file.
	 * 
	 * @return true, if we are reading events from an included file instead of the
	 *     top-level file
	 */
	private boolean isProcessingInclude() {
		return readers.size() > 1;
	}
	
	@Override
	public Object next() {
		try {
			return nextEvent();
		} catch (XMLStreamException e) {
			throw new IllegalStateException("Unexpected exception while iterating over events", e);
		}
	}

	@Override
	public XMLEvent nextEvent() throws XMLStreamException {
		peek();
		return getReader().nextEvent();
	}

	@Override
	public boolean hasNext() {
		try {
			peek();
		} catch (XMLStreamException e) {
			// Ignore
		}
		return getReader().hasNext();
	}

	@Override
	public XMLEvent peek() throws XMLStreamException {
		for (;;) {
			XMLEvent event = getReader().peek();
			
			if (isIncludeStartElement(event)) {
				// Start a new include.
				pushReader(createIncludeReader(event.asStartElement()));
			} else if (!isProcessingInclude()) {
				// Otherwise, if we're not in an include, just return the
				// event from the base reader.
				return event;
			} else if (event == null) {
				// End-of-file in an include. Move to enclosing reader.
				popReader();
			} else if (event.isStartDocument() || event.isEndDocument()) {
				// Skip start/end document events in an included file.
				getReader().nextEvent();
			} else {
				// Otherwise a good event from an included file.
				return event;
			}
		}
	}
	
	/**
	 * Tests whether an event is an <code>xi:include</code> start element.
	 * 
	 * @param event the {@link XMLEvent}
	 * @return true, if the event represents the start of an <code>xi:include</code>
	 */
	private boolean isIncludeStartElement(XMLEvent event) {
		if (event==null || !event.isStartElement()) {
			return false;
		}
		
		StartElement element = event.asStartElement();
		return element.getName().equals(XINCLUDE_TAG);
	}
	
	/**
	 * Gets a new {@link XMLEventReader} for an included file.
	 * 
	 * @param element the starting element for the <code>xi:include</code>
	 * @return a new {@link XMLEventReader} for the included file
	 * @throws XMLStreamException if there is an error accessing the included file or
	 *    creating the <code>XMLEventReader</code>
	 */
	private XMLEventReader createIncludeReader(StartElement element) throws XMLStreamException {
		// Read the xi:include start element event.
		getReader().nextEvent();
		
		// Read to the closing xi:include tag.
		getReader().getElementText();
		
		Attribute parseAttr = element.getAttributeByName(XINCLUDE_PARSE_ATTR);
		if (parseAttr!=null && !parseAttr.getValue().equals("xml")) {
			throw new XMLStreamException("Only XML parsing of <xi:include> is supported");
		}
		
		String uri = element.getAttributeByName(XINCLUDE_HREF_ATTR).getValue();
		File includeFile = new File(baseURI.resolve(uri));
		try {
			return createFileEventReader(includeFile);
		} catch (FileNotFoundException e) {
			throw new XMLStreamException("Cannot read file: " + includeFile.getAbsolutePath(), e);
		}
	}

	@Override
	public String getElementText() throws XMLStreamException {
		return getReader().getElementText();
	}

	@Override
	public XMLEvent nextTag() throws XMLStreamException {
		for (;;) {
			XMLEvent event = peek();
			if (event==null || event.isStartElement() || event.isEndElement()) {
				if (event != null) {
					// Move into the element, if not end-of-file.
					getReader().nextEvent();
				}
				return event;
			}
			
			if (!event.isCharacters()) {
				throw new XMLStreamException("Unexpected event in nextTag(): " + event);
			}
			
			Characters characters = event.asCharacters();
			if (!characters.isWhiteSpace()) {
				throw new XMLStreamException("Nonwhitespace character encountered in nextTag(): " + characters);
			}
			
			// Otherwise we found whitespace characters. Remove the event.
			getReader().nextEvent();
		}
	}

	@Override
	public Object getProperty(String name) throws IllegalArgumentException {
		// Always return the properties of the base reader.
		return readers.getLast().getProperty(name);
	}

	@Override
	public void close() throws XMLStreamException {
		while (!readers.isEmpty()) {
			getReader().close();
			popReader();
		}
	}

}

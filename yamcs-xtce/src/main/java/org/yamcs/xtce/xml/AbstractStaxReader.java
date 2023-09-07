package org.yamcs.xtce.xml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

/**
 * Abstract class for XML readers
 */
public class AbstractStaxReader implements AutoCloseable {

    protected final XMLEventReader xmlEventReader;
    protected final String fileName;
    final InputStream in;
    protected XMLEvent xmlEvent = null;
    
    protected AbstractStaxReader(String fileName) throws IOException, XMLStreamException {
        this.fileName = fileName;
        in = new FileInputStream(new File(fileName));
        xmlEventReader = initEventReader(in);
    }

    private XMLEventReader initEventReader(InputStream in) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        // Merge multiple character data blocks into a single event (e.g. algorithm text)
        factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);

        // Sonarqube suggestion to protect Java XML Parsers from XXE attack
        // see https://rules.sonarsource.com/java/RSPEC-2755
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        return factory.createXMLEventReader(in);
    }

    /**
     * Examines element for presence of attributes
     * 
     * @param element
     *            Element to be examined, should not be null
     * @return True, if the element contains attributes, otherwise false
     */
    protected boolean hasAttributes(StartElement element) {
        if (element == null) {
            return false;
        }
        return element.getAttributes().hasNext();
    }

    /**
     * Check if xml element is a start element with particular name
     * 
     * @param localName
     *            Name of the element
     * @return True if element is start element with the given name, otherwise false
     */
    protected boolean isStartElementWithName(String localName) {
        return (xmlEvent.getEventType() == XMLStreamConstants.START_ELEMENT && xmlEvent
                .asStartElement().getName().getLocalPart().equals(localName));
    }

    protected String readStringBetweenTags(String tagName) throws XMLStreamException {
        checkStartElementPreconditions();

        StringBuilder longDescr = new StringBuilder();
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isEndElementWithName(tagName)) {
                break;
            }
            if (!xmlEvent.isCharacters()) {
                throw new IllegalStateException(
                        tagName + " characters or end element expected but instead got " + xmlEvent);
            }
            longDescr.append(xmlEvent.asCharacters().getData());
        }

        return longDescr.toString();
    }

    /**
     * Checks preconditions before the dedicated code for section reading will run
     * 
     * @return
     * 
     * @throws IllegalStateException
     *             If the conditions are not met
     */
    protected StartElement checkStartElementPreconditions() throws IllegalStateException {
        if (xmlEvent == null) {
            throw new IllegalStateException("xmlEvent is null");
        }
        if (xmlEvent.getEventType() != XMLStreamConstants.START_ELEMENT) {
            throw new IllegalStateException("xmlEvent type is not start element");
        }
        return xmlEvent.asStartElement();
    }

    protected boolean hasAttribute(String attName, StartElement element) throws XMLStreamException {
        Attribute attribute = element.getAttributeByName(new QName(attName));
        return (attribute != null);

    }

    /**
     * Get attribute values as string
     * 
     * @param attName
     *            Name of the attribute
     * @param element
     *            Start element which the attribute is read from
     * @return Attribute's value as string
     * @throws XMLStreamException
     *             - if the attribute does not exist
     */
    protected String readMandatoryAttribute(String attName, StartElement element) throws XMLStreamException {
        Attribute attribute = element.getAttributeByName(new QName(attName));
        if (attribute != null) {
            return attribute.getValue();
        } else {
            throw new XMLStreamException("Mandatory attribute '" + attName + "' not defined", element.getLocation());
        }
    }

    protected String readAttribute(String attName, StartElement element, String defaultValue) {
        Attribute attribute = element.getAttributeByName(new QName(attName));
        if (attribute != null) {
            return attribute.getValue();
        }
        return defaultValue;
    }

    protected int readIntAttribute(String attName, StartElement element, int defaultValue) throws XMLStreamException {
        String v = readAttribute(attName, element, null);
        if (v == null) {
            return defaultValue;
        } else {
            try {
                return Integer.parseInt(v);
            } catch (IllegalArgumentException e) {
                throw new XMLStreamException("Cannot parse '" + v + "' to integer");
            }
        }
    }

    protected int readIntAttribute(String attName, StartElement element) throws XMLStreamException {
        String v = readAttribute(attName, element, null);
        if (v == null) {
            throw new XMLStreamException("Mandatory attribute '" + attName + "' not defined");
        } else {
            try {
                return Integer.parseInt(v);
            } catch (IllegalArgumentException e) {
                throw new XMLStreamException("Cannot parse '" + v + "' to integer");
            }
        }
    }

    protected long readLongAttribute(String attName, StartElement element) throws XMLStreamException {
        String v = readAttribute(attName, element, null);
        if (v == null) {
            throw new XMLStreamException("Mandatory attribute '" + attName + "' not defined");
        } else {
            try {
                return Long.parseLong(v);
            } catch (IllegalArgumentException e) {
                throw new XMLStreamException("Cannot parse '" + v + "' to integer");
            }
        }
    }

    protected long readLongAttribute(String attName, StartElement element, long defaultValue)
            throws XMLStreamException {
        String v = readAttribute(attName, element, null);
        if (v == null) {
            return defaultValue;
        } else {
            try {
                return Long.parseLong(v);
            } catch (IllegalArgumentException e) {
                throw new XMLStreamException("Cannot parse '" + v + "' to integer");
            }
        }
    }

    protected double readDoubleAttribute(String attName, StartElement element) throws XMLStreamException {
        String v = readAttribute(attName, element, null);
        if (v == null) {
            throw new XMLStreamException("Mandatory attribute '" + attName + "' not defined");
        } else {
            try {
                return Double.parseDouble(v);
            } catch (IllegalArgumentException e) {
                throw new XMLStreamException("Cannot parse '" + v + "' to double float number");
            }
        }

    }

    protected double readDoubleAttribute(String attName, StartElement element, double defaultValue)
            throws XMLStreamException {
        String v = readAttribute(attName, element, null);
        if (v == null) {
            return defaultValue;
        } else {
            try {
                return Double.parseDouble(v);
            } catch (IllegalArgumentException e) {
                throw new XMLStreamException("Cannot parse '" + v + "' to double floating point number");
            }
        }

    }

    protected boolean readBooleanAttribute(String attName, StartElement element, boolean defaultValue)
            throws XMLStreamException {
        String v = readAttribute(attName, element, null);
        if (v == null) {
            return defaultValue;
        } else {
            if ("true".equalsIgnoreCase(v) || "1".equals(v)) {
                return true;
            } else if ("false".equalsIgnoreCase(v) || "0".equals(v)) {
                return false;
            } else {
                throw new XMLStreamException("Cannot parse '" + v + "' to boolean");
            }
        }
    }

    /**
     * Test if the xmlEvent is of type END_ELEMENT and has particular local name. This test is used to identify the end
     * of section.
     * 
     * @param localName
     *            Local name of the element (we neglect namespace for now)
     * @return True if current xmlEvent is of type END_ELEMENT and has particular local name, otherwise false
     */
    protected boolean isEndElementWithName(String localName) {
        return (xmlEvent.getEventType() == XMLStreamConstants.END_ELEMENT && xmlEvent
                .asEndElement().getName().getLocalPart().equals(localName));
    }

    @Override
    public void close() throws XMLStreamException, IOException {
        xmlEventReader.close();
        in.close();
    }
}

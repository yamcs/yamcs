package org.yamcs.simulator.pus;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class MdbLoader {

    private static final Logger log = LoggerFactory.getLogger(MdbLoader.class);

    private static final String EVENT_PARAM_TYPE_NAME = "event_definition_id";
    private static final String APID_PARAM_TYPE_NAME = "apid";
    private static final String XTCE_NS = "http://www.omg.org/spec/XTCE/20180204";

    public static Map<Integer, String> loadEventDefinitions(String resourcePath) {
        Map<Integer, String> events = new LinkedHashMap<>();

        Document doc = loadDocument(resourcePath);
        if (doc == null) {
            return events;
        }

        NodeList enumTypes = getElements(doc, "EnumeratedParameterType");
        for (int i = 0; i < enumTypes.getLength(); i++) {
            Element enumType = (Element) enumTypes.item(i);
            if (!EVENT_PARAM_TYPE_NAME.equals(enumType.getAttribute("name"))) {
                continue;
            }

            NodeList enumerations = getElements(enumType, "Enumeration");
            for (int j = 0; j < enumerations.getLength(); j++) {
                Element enumEntry = (Element) enumerations.item(j);
                String valueStr = enumEntry.getAttribute("value");
                String label = enumEntry.getAttribute("label");

                try {
                    int eventId = Integer.parseInt(valueStr);
                    events.put(eventId, label);
                    log.debug("Loaded event definition: id={}, label={}", eventId, label);
                } catch (NumberFormatException e) {
                    log.warn("Skipping invalid event value '{}' with label '{}'", valueStr, label);
                }
            }
            break;
        }

        if (events.isEmpty()) {
            log.warn("No event definitions found in MDB at {}. Check the file has "
                    + "an EnumeratedParameterType named '{}'", resourcePath, EVENT_PARAM_TYPE_NAME);
        } else {
            log.info("Loaded {} event definition(s) from MDB: {}", events.size(), events);
        }

        return events;
    }

    public static Map<String, Integer> loadApids(String resourcePath) {
        Map<String, Integer> apids = new LinkedHashMap<>();

        Document doc = loadDocument(resourcePath);
        if (doc == null) {
            return apids;
        }

        NodeList enumTypes = getElements(doc, "EnumeratedParameterType");
        for (int i = 0; i < enumTypes.getLength(); i++) {
            Element enumType = (Element) enumTypes.item(i);
            if (!APID_PARAM_TYPE_NAME.equals(enumType.getAttribute("name"))) {
                continue;
            }

            NodeList enumerations = getElements(enumType, "Enumeration");
            for (int j = 0; j < enumerations.getLength(); j++) {
                Element enumEntry = (Element) enumerations.item(j);
                String valueStr = enumEntry.getAttribute("value");
                String label = enumEntry.getAttribute("label");
                try {
                    apids.put(label, Integer.parseInt(valueStr));
                } catch (NumberFormatException e) {
                    log.warn("Skipping invalid APID value '{}' with label '{}'", valueStr, label);
                }
            }
            break;
        }

        return apids;
    }

    public static List<Integer> loadEventReportSubtypes(String resourcePath) {
        TreeSet<Integer> subtypes = new TreeSet<>();

        Document doc = loadDocument(resourcePath);
        if (doc == null) {
            return List.of();
        }

        NodeList containers = getElements(doc, "SequenceContainer");
        for (int i = 0; i < containers.getLength(); i++) {
            Element container = (Element) containers.item(i);
            if (!containsParameterRef(container, "event_definition_id")) {
                continue;
            }

            Element baseContainer = getFirstChild(container, "BaseContainer");
            if (baseContainer == null) {
                continue;
            }

            Integer serviceType = findRestrictionValue(baseContainer, "service_type");
            Integer subtype = findRestrictionValue(baseContainer, "subservice_type");
            if (serviceType != null && serviceType == 5 && subtype != null) {
                subtypes.add(subtype);
            }
        }

        return new ArrayList<>(subtypes);
    }

    private static Document loadDocument(String resourcePath) {
        try (InputStream in = MdbLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                log.error("MDB file not found at resource path: {}", resourcePath);
                return null;
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(in);
            doc.getDocumentElement().normalize();
            return doc;
        } catch (Exception e) {
            log.error("Failed to load MDB from {}", resourcePath, e);
            return null;
        }
    }

    private static NodeList getElements(Document doc, String name) {
        NodeList nodes = doc.getElementsByTagNameNS(XTCE_NS, name);
        if (nodes.getLength() == 0) {
            nodes = doc.getElementsByTagName(name);
        }
        return nodes;
    }

    private static NodeList getElements(Element element, String name) {
        NodeList nodes = element.getElementsByTagNameNS(XTCE_NS, name);
        if (nodes.getLength() == 0) {
            nodes = element.getElementsByTagName(name);
        }
        return nodes;
    }

    private static Element getFirstChild(Element element, String name) {
        NodeList nodes = getElements(element, name);
        return nodes.getLength() > 0 ? (Element) nodes.item(0) : null;
    }

    private static boolean containsParameterRef(Element container, String parameterRef) {
        NodeList entries = getElements(container, "ParameterRefEntry");
        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);
            if (parameterRef.equals(entry.getAttribute("parameterRef"))) {
                return true;
            }
        }
        return false;
    }

    private static Integer findRestrictionValue(Element baseContainer, String parameterRef) {
        NodeList conditions = getElements(baseContainer, "Condition");
        for (int i = 0; i < conditions.getLength(); i++) {
            Element condition = (Element) conditions.item(i);
            Element paramRef = getFirstChild(condition, "ParameterInstanceRef");
            Element value = getFirstChild(condition, "Value");
            if (paramRef == null || value == null) {
                continue;
            }
            if (!parameterRef.equals(paramRef.getAttribute("parameterRef"))) {
                continue;
            }
            try {
                return Integer.parseInt(value.getTextContent().trim());
            } catch (NumberFormatException e) {
                log.debug("Ignoring non-numeric restriction value '{}' for {}", value.getTextContent(), parameterRef);
            }
        }
        return null;
    }
}

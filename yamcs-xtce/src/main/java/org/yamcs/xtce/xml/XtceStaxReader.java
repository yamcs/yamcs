package org.yamcs.xtce.xml;

import static org.yamcs.xtce.XtceDb.YAMCS_CMDARG_SPACESYSTEM_NAME;
import static org.yamcs.xtce.xml.Constants.*;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteOrder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartDocument;
import javax.xml.stream.events.StartElement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.xtce.*;
import org.yamcs.xtce.Algorithm.Scope;
import org.yamcs.xtce.CheckWindow.TimeWindowIsRelativeToType;
import org.yamcs.xtce.CommandVerifier.TerminationAction;
import org.yamcs.xtce.FloatDataEncoding.Encoding;
import org.yamcs.xtce.ParameterInstanceRef.InstanceRelativeTo;
import org.yamcs.xtce.SequenceEntry.ReferenceLocationType;
import org.yamcs.xtce.Significance.Levels;
import org.yamcs.xtce.StringDataEncoding.SizeType;
import org.yamcs.xtce.util.AggregateTypeUtil;
import org.yamcs.xtce.util.ArgumentReference;
import org.yamcs.xtce.util.DoubleRange;
import org.yamcs.xtce.util.HexUtils;
import org.yamcs.xtce.util.IncompleteType;
import org.yamcs.xtce.util.NameReference;
import org.yamcs.xtce.util.NameReference.Type;
import org.yamcs.xtce.util.ParameterReference;
import org.yamcs.xtce.util.ParameterReference.ParameterResolvedAction;
import org.yamcs.xtce.util.ReferenceFinder;
import org.yamcs.xtce.util.ReferenceFinder.FoundReference;

/**
 * This class reads the XTCE XML files. XML document is accessed with the use of the Stax Iterator API.
 * 
 */
public class XtceStaxReader extends AbstractStaxReader {

    // XTCE Schema defined tags, to minimize the mistyping errors

    private static final String YAMCS_IGNORE = "_yamcs_ignore";

    public static final DynamicIntegerValue IGNORED_DYNAMIC_VALUE = new DynamicIntegerValue(
            new ParameterInstanceRef(new Parameter(YAMCS_IGNORE)));
    /**
     * Logging subsystem
     */
    private static Logger log = LoggerFactory.getLogger(XtceStaxReader.class);

    /**
     * Statistics about the skipped sections. (good for overview about unimplemented features)
     */
    private Map<String, Integer> xtceSkipStatistics = new HashMap<>();
    private Set<String> excludedContainers = new HashSet<>();

    public XtceStaxReader(String fileName) throws IOException, XMLStreamException {
        super(fileName);
    }

    /**
     * Reads the XML XTCE file
     * 
     * @return returns the SpaceSystem read from the XML file
     * @throws XMLStreamException
     * @throws IOException
     * 
     */
    public SpaceSystem readXmlDocument() throws XMLStreamException, IOException, XtceLoadException {
        log.info("Parsing XTCE file {}", fileName);
        xmlEvent = null;
        SpaceSystem spaceSystem = null;
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            int eventType = xmlEvent.getEventType();
            if (eventType == XMLStreamConstants.COMMENT) {
                continue;
            }

            if (eventType == XMLStreamConstants.START_DOCUMENT) {
                onStartDocument((StartDocument) xmlEvent);
            } else if (eventType == XMLStreamConstants.START_ELEMENT) {
                spaceSystem = readSpaceSystem();
            } else if (eventType == XMLStreamConstants.END_DOCUMENT) {
                onEndDocument();
                break;
            } else if (isStartElementWithName(ELEM_SPACE_SYSTEM)) {
                SpaceSystem ss = readSpaceSystem();
                spaceSystem.addSpaceSystem(ss);
            } else if (eventType == XMLStreamConstants.PROCESSING_INSTRUCTION) {
                log.debug("Skipping processing instruction: {} ", xmlEvent);
            } else {
                // something went wrong, all options should be handled by the
                // upper if-branches
                // log.error("XML document parser error, unhandled event type."
                // + XMLStreamConstants.CHARACTERS);
                // throw new IllegalStateException();
                log.error("Unhandled event: {} ", xmlEvent);
            }

            if (xmlEventReader.peek() == null) {
                // assert false; document must be closed gracefully
                this.xmlEvent = null;
                throw new IllegalStateException("XML file parsing error");
            }
        }
        log.info("XTCE file parsing finished, loaded: {} parameters, {} tm containers, {} commands",
                spaceSystem.getParameterCount(true), spaceSystem.getSequenceContainerCount(true),
                spaceSystem.getMetaCommandCount(true));
        // try to resolve some internal references
        ReferenceFinder refFinder = new ReferenceFinder(s -> log.warn(s));
        while (resolveReferences(spaceSystem, spaceSystem, refFinder) > 0) {
        }

        return spaceSystem;
    }

    /**
     * * resolves references in ss by going recursively to all sub-space systems (in the first call ss=topSs)
     * 
     * Return the number of reference resolved or -1 if there was nothing to resolve
     * <p>
     * Do not resolve references that start with the root container ("/a/b/c")
     */
    private static int resolveReferences(SpaceSystem topSs, SpaceSystem ss, ReferenceFinder refFinder) {
        List<NameReference> refs = ss.getUnresolvedReferences();

        if (refs == null) {// need to continue to look in subsystems
            refs = Collections.emptyList();
        }

        int n = refs.isEmpty() ? -1 : 0;

        Iterator<NameReference> it = refs.iterator();
        while (it.hasNext()) {
            NameReference nr = it.next();
            if (nr.getReference().charAt(0) == NameDescription.PATH_SEPARATOR) {
                continue;
            }
            FoundReference rr = refFinder.findReference(topSs, nr, ss);
            if (rr == null) { // look for aliases up the hierarchy
                rr = refFinder.findAliasReference(topSs, nr, ss);
            }
            if (rr != null && rr.isComplete()) {
                rr.resolved(nr);
                n++;
                it.remove();
            }
        }
        for (SpaceSystem ss1 : ss.getSubSystems()) {
            int m = resolveReferences(topSs, ss1, refFinder);
            if (n == -1) {
                n = m;
            } else if (m > 0) {
                n += m;
            }
        }
        return n;
    }

    /**
     * Method called on start document event. Currently just logs the information contained in the xml preamble of the
     * parsed file.
     * 
     * @param start
     *            Start document event object
     */
    private void onStartDocument(StartDocument start) {
        log.trace("XML version='{} encoding: '{}'", start.getVersion(), start.getCharacterEncodingScheme());
    }

    /**
     * Start of reading at the root of the document. According to the XTCE schema the root element is
     * &lt;SpaceSystem&gt;
     * 
     * @throws XMLStreamException
     */
    private SpaceSystem readSpaceSystem() throws XMLStreamException {
        checkStartElementPreconditions();

        var startElement = xmlEvent.asStartElement();
        String value = readMandatoryAttribute(ATTR_NAME, startElement);
        SpaceSystem spaceSystem = new SpaceSystem(value);
        spaceSystem.setShortDescription(readAttribute(ATTR_SHORT_DESCRIPTION, startElement, null));

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isNamedItemProperty()) {
                readNamedItemProperty(spaceSystem);
            } else if (isStartElementWithName(ELEM_HEADER)) {
                readHeader(spaceSystem);
            } else if (isStartElementWithName(ELEM_TELEMTRY_META_DATA)) {
                readTelemetryMetaData(spaceSystem);
            } else if (isStartElementWithName(ELEM_COMMAND_MEATA_DATA)) {
                readCommandMetaData(spaceSystem);
            } else if (isStartElementWithName(ELEM_SPACE_SYSTEM)) {
                SpaceSystem ss = readSpaceSystem();
                spaceSystem.addSpaceSystem(ss);
            } else if (isEndElementWithName(ELEM_SPACE_SYSTEM)) {
                break;
            } else {
                logUnknown();
            }
        }

        return spaceSystem;
    }

    /**
     * Action taken on end of the document event
     */
    private void onEndDocument() {
        log.trace("End of XML document");
    }

    /**
     * Extraction of the AliasSet section Current implementation does nothing, just skips whole section
     * 
     * @return Set of aliases defined for the object
     * @throws XMLStreamException
     */
    private XtceAliasSet readAliasSet() throws XMLStreamException {
        log.trace(ELEM_ALIAS_SET);
        checkStartElementPreconditions();

        XtceAliasSet xtceAliasSet = new XtceAliasSet();

        while (true) {

            xmlEvent = xmlEventReader.nextEvent();

            // <Alias> sections
            if (isStartElementWithName(ELEM_ALIAS)) {
                readAlias(xtceAliasSet);
            } else if (isEndElementWithName(ELEM_ALIAS_SET)) {
                return xtceAliasSet;
            } else {
                logUnknown();
            }
        }
    }

    /**
     * Extraction of the AliasSet section Current implementation does nothing, just skips whole section
     * 
     * @throws XMLStreamException
     */
    private void readAlias(XtceAliasSet aliasSet) throws XMLStreamException {
        log.trace(ELEM_ALIAS);
        checkStartElementPreconditions();

        String nameSpace = readMandatoryAttribute("nameSpace", xmlEvent.asStartElement());
        nameSpace = nameSpace.intern();
        String alias = readMandatoryAttribute("alias", xmlEvent.asStartElement());

        aliasSet.addAlias(nameSpace, alias);

        // read end element
        xmlEvent = xmlEventReader.nextEvent();
        if (!isEndElementWithName(ELEM_ALIAS)) {
            throw new XMLStreamException(ELEM_ALIAS + " end element expected");
        }
    }

    private List<AncillaryData> readAncillaryDataSet() throws XMLStreamException {
        log.trace(ELEM_ANCILLARY_DATA_SET);
        checkStartElementPreconditions();

        List<AncillaryData> ancillaryData = new ArrayList<>();

        while (true) {

            xmlEvent = xmlEventReader.nextEvent();

            // <Alias> sections
            if (isStartElementWithName(ELEM_ANCILLARY_DATA)) {
                readAncillaryData(ancillaryData);
            } else if (isEndElementWithName(ELEM_ANCILLARY_DATA_SET)) {
                return ancillaryData;
            } else {
                logUnknown();
            }
        }
    }

    private void readAncillaryData(List<AncillaryData> ancillaryData) throws XMLStreamException {
        log.trace(ELEM_ANCILLARY_DATA);
        StartElement element = checkStartElementPreconditions();

        String name = readMandatoryAttribute(ATTR_NAME, element);
        String mimeType = readAttribute("mimeType", element, null);
        String href = readAttribute("href", element, null);
        String text = null;

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (xmlEvent.isCharacters()) {
                text = xmlEvent.asCharacters().getData().trim();
                break;
            } else if (isEndElementWithName(ELEM_ANCILLARY_DATA)) {
                break;
            }
        }

        AncillaryData ad = new AncillaryData(name, text);
        if (mimeType != null) {
            ad.setMimeType(mimeType);
        }
        if (href != null) {
            ad.setHref(URI.create(href));
        }
        ancillaryData.add(ad);
    }

    /**
     * Extraction of the Header section Current implementation does nothing, just skips whole section
     * 
     * @param spaceSystem
     * 
     * @throws XMLStreamException
     */
    private void readHeader(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_HEADER);
        checkStartElementPreconditions();
        Header h = new Header();

        String value = readAttribute("version", xmlEvent.asStartElement(), null);
        if (value != null) {
            h.setVersion(value);
        }

        value = readAttribute("date", xmlEvent.asStartElement(), null);
        if (value != null) {
            h.setDate(value);
        }
        spaceSystem.setHeader(h);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(ELEM_AUTHOR_SET)) {
                skipXtceSection(ELEM_AUTHOR_SET);
            } else if (isStartElementWithName(ELEM_NOTE_SET)) {
                skipXtceSection(ELEM_NOTE_SET);
            } else if (isStartElementWithName(ELEM_HISTORY_SET)) {
                skipXtceSection(ELEM_HISTORY_SET);
            } else if (isEndElementWithName(ELEM_HEADER)) {
                return;
            } else {
                logUnknown();
            }
        }
    }

    /**
     * Extraction of the TelemetryMetaData section
     * 
     * @param spaceSystem
     * 
     * @throws XMLStreamException
     */
    private void readTelemetryMetaData(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_TELEMTRY_META_DATA);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_PARAMETER_TYPE_SET)) {
                readParameterTypeSet(spaceSystem);
            } else if (isStartElementWithName(ELEM_PARAMETER_SET)) {
                readParameterSet(spaceSystem);
            } else if (isStartElementWithName(ELEM_CONTAINER_SET)) {
                readContainerSet(spaceSystem);
            } else if (isStartElementWithName(ELEM_MESSAGE_SET)) {
                readMessageSet();
            } else if (isStartElementWithName(ELEM_STREAM_SET)) {
                readStreamSet();
            } else if (isStartElementWithName(ELEM_ALGORITHM_SET)) {
                readAlgorithmSet(spaceSystem);
            } else if (isEndElementWithName(ELEM_TELEMTRY_META_DATA)) {
                return;
            } else {
                logUnknown();
            }
        }
    }

    /**
     * @param spaceSystem
     * @throws XMLStreamException
     */
    private void readParameterTypeSet(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_PARAMETER_TYPE_SET);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            IncompleteType incompleteType = null;

            if (isStartElementWithName(ELEM_BOOLEAN_PARAMETER_TYPE)) {
                incompleteType = readBooleanParameterType(spaceSystem);
            } else if (isStartElementWithName(ELEM_ENUMERATED_PARAMETER_TYPE)) {
                incompleteType = readEnumeratedParameterType(spaceSystem);
            } else if (isStartElementWithName(ELEM_FLOAT_PARAMETER_TYPE)) {
                incompleteType = readFloatParameterType(spaceSystem);
            } else if (isStartElementWithName(ELEM_INTEGER_PARAMETER_TYPE)) {
                incompleteType = readIntegerParameterType(spaceSystem);
            } else if (isStartElementWithName(ELEM_BINARY_PARAMETER_TYPE)) {
                incompleteType = readBinaryParameterType(spaceSystem);
            } else if (isStartElementWithName(ELEM_STRING_PARAMETER_TYPE)) {
                incompleteType = readStringParameterType(spaceSystem);
            } else if (isStartElementWithName(ELEM_RELATIVE_TIME_PARAMETER_TYPE)) {
                incompleteType = readRelativeTimeParameterType();
            } else if (isStartElementWithName(ELEM_ABSOLUTE_TIME_PARAMETER_TYPE)) {
                incompleteType = readAbsoluteTimeParameterType(spaceSystem);
            } else if (isStartElementWithName(ELEM_ARRAY_PARAMETER_TYPE)) {
                incompleteType = readArrayParameterType(spaceSystem);
            } else if (isStartElementWithName(ELEM_AGGREGATE_PARAMETER_TYPE)) {
                incompleteType = readAggregateParameterType(spaceSystem);
            } else {
                logUnknown();
            }

            if (incompleteType != null) {// relative time parameter returns null
                incompleteType.scheduleCompletion();
            }

            if (isEndElementWithName(ELEM_PARAMETER_TYPE_SET)) {
                return;
            }
        }
    }

    private IncompleteType readBooleanParameterType(SpaceSystem spaceSystem)
            throws IllegalStateException, XMLStreamException {
        log.trace(ELEM_BOOLEAN_PARAMETER_TYPE);
        StartElement element = checkStartElementPreconditions();

        BooleanParameterType.Builder typeBuilder = new BooleanParameterType.Builder();
        IncompleteType incompleteType = new IncompleteType(spaceSystem, typeBuilder);
        readParameterBaseTypeAttributes(spaceSystem, element, incompleteType);

        typeBuilder.setOneStringValue(readAttribute("oneStringValue", element, null));
        typeBuilder.setZeroStringValue(readAttribute("zeroStringValue", element, null));

        // read all parameters
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (readBaseTypeProperties(typeBuilder)) {
                continue;
            } else if (isStartElementWithName(ELEM_INTEGER_DATA_ENCODING)) {
                typeBuilder.setEncoding(readIntegerDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_FLOAT_DATA_ENCODING)) {
                typeBuilder.setEncoding(readFloatDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_BINARY_DATA_ENCODING)) {
                typeBuilder.setEncoding(readBinaryDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_STRING_DATA_ENCODING)) {
                typeBuilder.setEncoding(readStringDataEncoding(spaceSystem));
            } else if (isEndElementWithName(ELEM_BOOLEAN_PARAMETER_TYPE)) {
                return incompleteType;
            } else {
                logUnknown();
            }
        }
    }

    private IncompleteType readAggregateParameterType(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_AGGREGATE_PARAMETER_TYPE);
        checkStartElementPreconditions();

        String name = readMandatoryAttribute(ATTR_NAME, xmlEvent.asStartElement());
        AggregateParameterType.Builder typeBuilder = new AggregateParameterType.Builder().setName(name);
        IncompleteType incompleteType = new IncompleteType(spaceSystem, typeBuilder);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isNamedItemProperty()) {
                readNamedItemProperty(typeBuilder);
            } else if (isStartElementWithName(ELEM_MEMBER_LIST)) {
                typeBuilder.addMembers(readMemberList(spaceSystem, true));
            } else if (isEndElementWithName(ELEM_AGGREGATE_PARAMETER_TYPE)) {
                return incompleteType;
            } else {
                logUnknown();
            }
        }
    }

    private List<Member> readMemberList(SpaceSystem spaceSystem, boolean paramOrAggreg) throws XMLStreamException {
        log.trace(ELEM_MEMBER_LIST);
        checkStartElementPreconditions();
        List<Member> l = new ArrayList<>();
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(ELEM_MEMBER)) {
                l.add(readXtceMember(spaceSystem, paramOrAggreg));
            } else if (isEndElementWithName(ELEM_MEMBER_LIST)) {
                return l;
            }
        }
    }

    private Member readXtceMember(SpaceSystem spaceSystem, boolean paramOrAggreg) throws XMLStreamException {
        log.trace(ELEM_MEMBER);
        checkStartElementPreconditions();
        StartElement element = xmlEvent.asStartElement();
        String name = readMandatoryAttribute(ATTR_NAME, element);
        Member member = new Member(name);
        member.setShortDescription(readAttribute(ATTR_SHORT_DESCRIPTION, element, null));

        String typeRef = readMandatoryAttribute("typeRef", element);
        String initialValue = readAttribute(ATTR_INITIAL_VALUE, element, null);

        if (paramOrAggreg) {
            NameReference nr = new NameReference(typeRef, Type.PARAMETER_TYPE).addResolvedAction(nd -> {
                member.setDataType((ParameterType) nd);
                if (initialValue != null) {
                    member.setInitialValue(initialValue);
                }
            });
            spaceSystem.addUnresolvedReference(nr);
        } else {
            NameReference nr = new NameReference(typeRef, Type.ARGUMENT_TYPE).addResolvedAction(nd -> {
                member.setDataType((ArgumentType) nd);
                if (initialValue != null) {
                    member.setInitialValue(initialValue);
                }
            });
            spaceSystem.addUnresolvedReference(nr);
        }
        return member;
    }

    private IncompleteType readArrayParameterType(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_ARRAY_PARAMETER_TYPE);

        StartElement element = checkStartElementPreconditions();

        String name = readMandatoryAttribute(ATTR_NAME, element);

        ArrayParameterType.Builder typeBuilder = new ArrayParameterType.Builder().setName(name);
        IncompleteType incompleteType = new IncompleteType(spaceSystem, typeBuilder);

        int dim;
        if (hasAttribute("numberOfDimensions", element)) {
            dim = readIntAttribute("numberOfDimensions", element);
            typeBuilder.setNumberOfDimensions(dim);
        } else {
            dim = -1;
        }

        String refName = readMandatoryAttribute("arrayTypeRef", xmlEvent.asStartElement());
        NameReference nr = new NameReference(refName, Type.PARAMETER_TYPE).addResolvedAction(nd -> {
            typeBuilder.setElementType((ParameterType) nd);
        });
        incompleteType.addReference(nr);
        spaceSystem.addUnresolvedReference(nr);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isNamedItemProperty()) {
                readNamedItemProperty(typeBuilder);
            } else if (isStartElementWithName(ELEM_DIMENSION_LIST)) {
                List<IntegerValue> dimList = readDimensionList(spaceSystem);
                dim = dimList.size();
                typeBuilder.setSize(dimList);
            } else if (isEndElementWithName(ELEM_ARRAY_PARAMETER_TYPE)) {
                if (dim == -1) {
                    throw new XMLStreamException("Neither numberOfDimensions (XTCE 1.1) attribute nor "
                            + ELEM_DIMENSION_LIST + " (XTCE 1.2) element defined for the ArrayParameter " + name);
                }
                return incompleteType;
            } else {
                logUnknown();
            }
        }
    }

    private void readParameterBaseTypeAttributes(SpaceSystem spaceSystem, StartElement element,
            IncompleteType incompleteType)
            throws XMLStreamException {
        BaseDataType.Builder<?> typeBuilder = (BaseDataType.Builder<?>) incompleteType.getTypeBuilder();

        String name = readMandatoryAttribute(ATTR_NAME, element);
        typeBuilder.setName(name);
        typeBuilder.setShortDescription(readAttribute(ATTR_SHORT_DESCRIPTION, element, null));

        String baseType = readAttribute("baseType", element, null);
        if (baseType != null) {
            NameReference nr = new NameReference(baseType, Type.PARAMETER_TYPE).addResolvedAction(nd -> {
                ParameterType ptype = (ParameterType) nd;
                if (!(ptype instanceof BaseDataType)) {
                    throwException(element.getLocation(), "cannot use " + ptype.getName() + " as a baseType");
                }
                typeBuilder.setBaseType((BaseDataType) ptype);
            });
            incompleteType.addReference(nr);
            spaceSystem.addUnresolvedReference(nr);
        }
    }

    private void readArgumentBaseTypeAttributes(SpaceSystem spaceSystem, StartElement element,
            IncompleteType incompleteType)
            throws XMLStreamException {
        BaseDataType.Builder<?> typeBuilder = (BaseDataType.Builder<?>) incompleteType.getTypeBuilder();

        String name = readMandatoryAttribute(ATTR_NAME, element);
        typeBuilder.setName(name);
        typeBuilder.setShortDescription(readAttribute(ATTR_SHORT_DESCRIPTION, element, null));

        String baseType = readAttribute("baseType", element, null);
        if (baseType != null) {
            NameReference nr = new NameReference(baseType, Type.ARGUMENT_TYPE).addResolvedAction(nd -> {
                ArgumentType ptype = (ArgumentType) nd;
                if (!(ptype instanceof BaseDataType)) {
                    throwException(element.getLocation(), "cannot use " + ptype.getName() + " as a baseType");
                }
                typeBuilder.setBaseType((BaseDataType) ptype);
            });
            incompleteType.addReference(nr);
            spaceSystem.addUnresolvedReference(nr);
        }
    }

    private boolean readBaseTypeProperties(BaseDataType.Builder<?> typeBuilder) throws XMLStreamException {
        if (isNamedItemProperty()) {
            readNamedItemProperty(typeBuilder);
        } else if (isStartElementWithName(ELEM_UNIT_SET)) {
            typeBuilder.addAllUnits(readUnitSet());
            return true;
        }
        return false;
    }

    private IncompleteType readAbsoluteTimeParameterType(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_ABSOLUTE_TIME_PARAMETER_TYPE);
        StartElement element = checkStartElementPreconditions();

        AbsoluteTimeParameterType.Builder typeBuilder = new AbsoluteTimeParameterType.Builder();
        IncompleteType incompleteType = new IncompleteType(spaceSystem, typeBuilder);

        readParameterBaseTypeAttributes(spaceSystem, element, incompleteType);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (readBaseTypeProperties(typeBuilder)) {
                continue;
            } else if (isStartElementWithName(ELEM_REFERENCE_TIME)) {
                typeBuilder.setReferenceTime(readReferenceTime(spaceSystem));
            } else if (isStartElementWithName(ELEM_ENCODING)) {
                readEncoding(spaceSystem, typeBuilder);
            } else if (isEndElementWithName(ELEM_ABSOLUTE_TIME_PARAMETER_TYPE)) {
                if (typeBuilder.getReferenceTime() == null) {
                    if (!(typeBuilder.getEncoding() instanceof BinaryDataEncoding.Builder bdb)
                            || bdb.getFromBinaryTransformAlgorithm() == null) {
                        throw new XMLStreamException(
                                "AbsoluteTimeParameterType without a reference time  not supported "
                                        + "(except if it used a BinaryDataEncoding with an algorithm "
                                        + "which could produce directly an absolute time)",
                                xmlEvent.getLocation());
                    }
                }
                return incompleteType;
            } else {
                logUnknown();
            }
        }
    }

    private IncompleteType readRelativeTimeParameterType() throws IllegalStateException,
            XMLStreamException {
        skipXtceSection(ELEM_RELATIVE_TIME_PARAMETER_TYPE);
        return null;
    }

    private ReferenceTime readReferenceTime(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_REFERENCE_TIME);
        checkStartElementPreconditions();

        ReferenceTime referenceTime = null;

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(ELEM_OFFSET_FROM)) {
                referenceTime = new ReferenceTime(readParameterInstanceRef(spaceSystem, null));
            } else if (isStartElementWithName(ELEM_EPOCH)) {
                referenceTime = new ReferenceTime(readEpoch());
            } else if (isEndElementWithName(ELEM_REFERENCE_TIME)) {
                return referenceTime;
            } else {
                logUnknown();
            }
        }
    }

    private TimeEpoch readEpoch() throws XMLStreamException {
        log.trace(ELEM_EPOCH);
        String s = readStringBetweenTags(ELEM_EPOCH);
        try {
            return new TimeEpoch(TimeEpoch.CommonEpochs.valueOf(s));
        } catch (IllegalArgumentException e) {
            try {
                return new TimeEpoch(s);
            } catch (DateTimeParseException e1) {
                e1.printStackTrace();
                throw new XMLStreamException(e.getMessage(), xmlEvent.getLocation());
            }
        }
    }

    // encoding used only for absolute time arguments or parameters
    private void readEncoding(SpaceSystem spaceSystem, AbsoluteTimeDataType.Builder<?> ptype)
            throws XMLStreamException {
        log.trace(ELEM_ENCODING);
        checkStartElementPreconditions();
        // name attribute
        String units = readAttribute("units", xmlEvent.asStartElement(), null);
        if ((units != null) && (!"seconds".equals(units))) {
            throw new XMLStreamException("Unsupported unit types '" + units + "' for time encoding."
                    + " Only seconds (with scaling) supported", xmlEvent.getLocation());
        }
        boolean needsScaling = false;
        double offset = 0d;
        double scale = 1d;
        String offsets = readAttribute("offset", xmlEvent.asStartElement(), null);
        if (offsets != null) {
            needsScaling = true;
            offset = parseDouble(offsets);
        }
        String scales = readAttribute("scale", xmlEvent.asStartElement(), null);
        if (scales != null) {
            needsScaling = true;
            scale = parseDouble(scales);
        }
        if (needsScaling) {
            ptype.setScaling(offset, scale);
        }

        DataEncoding.Builder<?> dataEncoding = null;
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(ELEM_INTEGER_DATA_ENCODING)) {
                dataEncoding = readIntegerDataEncoding(spaceSystem);
            } else if (isStartElementWithName(ELEM_FLOAT_DATA_ENCODING)) {
                dataEncoding = readFloatDataEncoding(spaceSystem);
            } else if (isStartElementWithName(ELEM_BINARY_DATA_ENCODING)) {
                dataEncoding = readBinaryDataEncoding(spaceSystem);
            } else if (isEndElementWithName(ELEM_ENCODING)) {
                ptype.setEncoding(dataEncoding);
                return;
            } else {
                logUnknown();
            }
        }
    }

    private IncompleteType readFloatParameterType(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_FLOAT_PARAMETER_TYPE);

        StartElement element = checkStartElementPreconditions();

        // name attribute
        FloatParameterType.Builder typeBuilder = new FloatParameterType.Builder();
        IncompleteType incompleteType = new IncompleteType(spaceSystem, typeBuilder);

        readParameterBaseTypeAttributes(spaceSystem, element, incompleteType);

        String value = readAttribute(ATTR_SIZE_IN_BITS, element, null);

        if (value != null) {
            int sizeInBits = Integer.parseInt(value);
            if (sizeInBits != 32 && sizeInBits != 64) {
                throw new XMLStreamException("Float encoding " + sizeInBits + " not supported;"
                        + " Only 32 and 64 bits are supported", xmlEvent.getLocation());
            }
            typeBuilder.setSizeInBits(sizeInBits);
        }
        value = readAttribute(ATTR_INITIAL_VALUE, element, null);
        if (value != null) {
            typeBuilder.setInitialValue(value);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (readBaseTypeProperties(typeBuilder)) {
                continue;
            } else if (isStartElementWithName(ELEM_INTEGER_DATA_ENCODING)) {
                typeBuilder.setEncoding(readIntegerDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_FLOAT_DATA_ENCODING)) {
                typeBuilder.setEncoding(readFloatDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_STRING_DATA_ENCODING)) {
                typeBuilder.setEncoding(readStringDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_BINARY_DATA_ENCODING)) {
                throw new XMLStreamException("Cannot use a binary data encoding for float parameter type");
            } else if (isStartElementWithName(ELEM_DEFAULT_ALARM)) {
                typeBuilder.setDefaultAlarm(readDefaultAlarm());
            } else if (isStartElementWithName(ELEM_CONTEXT_ALARM_LIST)) {
                typeBuilder.setContextAlarmList(readNumericContextAlarmList(spaceSystem));
            } else if (isStartElementWithName(ELEM_VALID_RANGE)) {
                typeBuilder.setValidRange(readFloatValidRange());
            } else if (isStartElementWithName(ELEM_TO_STRING)) {
                typeBuilder.setNumberFormat(readToString());
            } else if (isEndElementWithName(ELEM_FLOAT_PARAMETER_TYPE)) {
                return incompleteType;
            } else {
                logUnknown();
            }
        }
    }

    private FloatDataEncoding.Builder readFloatDataEncoding(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_FLOAT_DATA_ENCODING);
        StartElement element = checkStartElementPreconditions();

        FloatDataEncoding.Builder floatDataEncoding = null;

        Integer sizeInBits = null;
        if (hasAttribute(ATTR_SIZE_IN_BITS, element)) {
            sizeInBits = readIntAttribute(ATTR_SIZE_IN_BITS, element);
        }
        ByteOrder byteOrder = readByteOrder();
        // encoding attribute
        String value = readAttribute("encoding", element, null);
        Encoding enc = null;
        if (value != null) {
            if ("IEEE754_1985".equalsIgnoreCase(value)) {
                // ok, this encoding is the default
            } else if ("MILSTD_1750A".equalsIgnoreCase(value)) {
                enc = Encoding.MILSTD_1750A;
            } else if ("STRING".equalsIgnoreCase(value)) {
                // Not in XTCE, but allowed by Yamcs
                enc = Encoding.STRING;
            } else {
                throwException("Unknown encoding '" + value + "'");
            }
        }
        floatDataEncoding = new FloatDataEncoding.Builder()
                .setSizeInBits(sizeInBits)
                .setByteOrder(byteOrder)
                .setFloatEncoding(enc);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_DEFAULT_CALIBRATOR)) {
                floatDataEncoding.setDefaultCalibrator(readCalibrator(spaceSystem));
            } else if (isStartElementWithName(ELEM_CONTEXT_CALIBRATOR_LIST)) {
                floatDataEncoding.setContextCalibratorList(readContextCalibratorList(spaceSystem));
            } else if (isStartElementWithName(ELEM_STRING_DATA_ENCODING)) {
                // Not in XTCE, but allowed by Yamcs
                floatDataEncoding.setStringEncoding(readStringDataEncoding(spaceSystem).build());
            } else if (isEndElementWithName(ELEM_FLOAT_DATA_ENCODING)) {
                return floatDataEncoding;
            } else {
                logUnknown();
            }
        }
    }

    private List<NumericContextAlarm> readNumericContextAlarmList(SpaceSystem spaceSystem)
            throws IllegalStateException, XMLStreamException {
        log.trace(ELEM_CONTEXT_ALARM_LIST);
        List<NumericContextAlarm> contextAlarmList = new ArrayList<>();
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_CONTEXT_ALARM)) {
                contextAlarmList.add(readNumericContextAlarm(spaceSystem));
            } else if (isEndElementWithName(ELEM_CONTEXT_ALARM_LIST)) {
                return contextAlarmList;
            }
        }
    }

    private NumericAlarm readDefaultAlarm() throws XMLStreamException {
        NumericAlarm na = new NumericAlarm();
        readAlarmAttributes(na);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isEndElementWithName(ELEM_DEFAULT_ALARM)) {
                return na;
            } else if (xmlEvent.getEventType() == XMLStreamConstants.START_ELEMENT) {
                readNumericAlarmElement(na);
            }
        }
    }

    private NumericContextAlarm readNumericContextAlarm(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_CONTEXT_ALARM);
        NumericContextAlarm nca = new NumericContextAlarm();
        readAlarmAttributes(nca);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(ELEM_CONTEXT_MATCH)) {
                nca.setContextMatch(readMatchCriteria(spaceSystem, null));
            } else if (xmlEvent.getEventType() == XMLStreamConstants.START_ELEMENT) {
                readNumericAlarmElement(nca);
            } else if (isEndElementWithName(ELEM_CONTEXT_ALARM)) {
                return nca;
            } else {
                logUnknown();
            }
        }
    }

    private void readNumericAlarmElement(NumericAlarm numericAlarm) throws XMLStreamException {
        if (isStartElementWithName(ELEM_STATIC_ALARM_RANGES)) {
            numericAlarm.setStaticAlarmRanges(readAlarmRanges());
        }
    }

    private AlarmRanges readAlarmRanges() throws XMLStreamException {
        String tag = xmlEvent.asStartElement().getName().getLocalPart();
        AlarmRanges ar = new AlarmRanges();
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName("WatchRange")) {
                ar.addWatchRange(readFloatRange());
            } else if (isStartElementWithName("WarningRange")) {
                ar.addWarningRange(readFloatRange());
            } else if (isStartElementWithName("DistressRange")) {
                ar.addDistressRange(readFloatRange());
            } else if (isStartElementWithName("CriticalRange")) {
                ar.addCriticalRange(readFloatRange());
            } else if (isStartElementWithName("SevereRange")) {
                ar.addSevereRange(readFloatRange());
            } else if (isEndElementWithName(tag)) {
                return ar;
            }
        }
    }

    private DoubleRange readFloatRange() throws XMLStreamException {
        StartElement element = xmlEvent.asStartElement();
        double minExclusive = readDoubleAttribute("minExclusive", element, Double.NaN);
        double maxExclusive = readDoubleAttribute("maxExclusive", element, Double.NaN);
        double minInclusive = readDoubleAttribute("minInclusive", element, Double.NaN);
        double maxInclusive = readDoubleAttribute("maxInclusive", element, Double.NaN);

        return DoubleRange.fromXtceComplement(minExclusive, maxExclusive, minInclusive, maxInclusive);
    }

    private FloatValidRange readFloatValidRange() throws XMLStreamException {
        StartElement element = xmlEvent.asStartElement();
        FloatValidRange fvr = new FloatValidRange(readFloatRange());
        boolean calib = readBooleanAttribute("validRangeAppliesToCalibrated", element, true);
        fvr.setValidRangeAppliesToCalibrated(calib);
        return fvr;
    }

    private IntegerRange readIntegerRange(boolean signed) throws XMLStreamException {
        StartElement element = xmlEvent.asStartElement();
        long minInclusive = readLongAttribute("minInclusive", element, signed ? Long.MIN_VALUE : 0);
        long maxInclusive = readLongAttribute("maxInclusive", element, Long.MAX_VALUE);
        return new IntegerRange(minInclusive, maxInclusive);
    }

    private IntegerValidRange readIntegerValidRange(boolean signed) throws XMLStreamException {
        StartElement element = xmlEvent.asStartElement();
        IntegerValidRange ivr = new IntegerValidRange(readIntegerRange(signed));
        boolean calib = readBooleanAttribute("validRangeAppliesToCalibrated", element, true);
        ivr.setValidRangeAppliesToCalibrated(calib);

        return ivr;
    }

    private IntegerValidRange readIntegerValidRangeSet(boolean signed) throws XMLStreamException {
        log.trace(ELEM_VALID_RANGE_SET);
        StartElement element = xmlEvent.asStartElement();
        boolean calib = readBooleanAttribute("validRangeAppliesToCalibrated", element, true);

        IntegerValidRange ivr = null;
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(ELEM_VALID_RANGE)) {
                if (ivr != null) {
                    throw new XMLStreamException("Only one ValidRange supported. ", xmlEvent.getLocation());
                }
                ivr = readIntegerValidRange(signed);
            } else if (isEndElementWithName(ELEM_VALID_RANGE_SET)) {
                if (ivr == null) {
                    throw new XMLStreamException("No ValidRange supecified ", xmlEvent.getLocation());
                }
                ivr.setValidRangeAppliesToCalibrated(calib);
                return ivr;
            }
        }
    }

    private FloatValidRange readFloatValidRangeSet() throws XMLStreamException {
        log.trace(ELEM_VALID_RANGE_SET);
        StartElement element = xmlEvent.asStartElement();
        boolean calib = readBooleanAttribute("validRangeAppliesToCalibrated", element, true);

        FloatValidRange fvr = null;
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(ELEM_VALID_RANGE)) {
                if (fvr != null) {
                    throw new XMLStreamException("Only one ValidRange supported. ", xmlEvent.getLocation());
                }
                fvr = readFloatValidRange();
            } else if (isEndElementWithName(ELEM_VALID_RANGE_SET)) {
                if (fvr == null) {
                    throw new XMLStreamException("No ValidRange specified ", xmlEvent.getLocation());
                }
                fvr.setValidRangeAppliesToCalibrated(calib);
                return fvr;
            }
        }
    }

    private NumberFormatType readToString() throws XMLStreamException {
        log.trace(ELEM_TO_STRING);
        NumberFormatType numberFormat = null;
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(ELEM_NUMBER_FORMAT)) {
                if (numberFormat != null) {
                    throw new XMLStreamException("Only one NumberFormat supported", xmlEvent.getLocation());
                }
                numberFormat = readNumberFormat();
            } else if (isEndElementWithName(ELEM_TO_STRING)) {
                if (numberFormat == null) {
                    throw new XMLStreamException("No NumberFormat specified", xmlEvent.getLocation());
                }
                return numberFormat;
            }
        }
    }

    private NumberFormatType readNumberFormat() throws XMLStreamException {
        log.trace(ELEM_NUMBER_FORMAT);
        StartElement element = xmlEvent.asStartElement();
        NumberFormatType format = new NumberFormatType();
        String numberBase = readAttribute("numberBase", element, "Decimal");
        format.setNumberBase(RadixType.valueOf(numberBase.toUpperCase()));
        int minimumFractionDigits = readIntAttribute("minimumFractionDigits", element, 0);
        format.setMinimumFractionDigits(minimumFractionDigits);

        String value = readAttribute("maximumFractionDigits", element, null);
        if (value != null) {
            format.setMaximumFractionDigits(Integer.parseInt(value));
        }

        int minimumIntegerDigits = readIntAttribute("minimumIntegerDigits", element, 1);
        format.setMinimumIntegerDigits(minimumIntegerDigits);

        value = readAttribute("maximumIntegerDigits", element, null);
        if (value != null) {
            format.setMaximumIntegerDigits(Integer.parseInt(value));
        }

        String negativeSuffix = readAttribute("negativeSuffix", element, "");
        format.setNegativeSuffix(negativeSuffix.isEmpty() ? null : negativeSuffix);
        String positiveSuffix = readAttribute("positiveSuffix", element, "");
        format.setPositiveSuffix(positiveSuffix.isEmpty() ? null : positiveSuffix);
        String negativePrefix = readAttribute("negativePrefix", element, "-");
        format.setNegativePrefix(negativePrefix.isEmpty() ? null : negativePrefix);
        String positivePrefix = readAttribute("positivePrefix", element, "");
        format.setPositivePrefix(positivePrefix.isEmpty() ? null : positivePrefix);

        boolean showThousandsGrouping = readBooleanAttribute("showThousandsGrouping", element, false);
        format.setShowThousandsGrouping(showThousandsGrouping);

        String notation = readAttribute("notation", element, "normal");
        format.setNotation(FloatingPointNotationType.valueOf(notation.toUpperCase()));

        return format;
    }

    private void readAlarmAttributes(AlarmType alarm) {
        String value = readAttribute("minViolations", xmlEvent.asStartElement(), null);
        if (value != null) {
            int minViolations = Integer.parseInt(value);
            alarm.setMinViolations(minViolations);
        }
    }

    private IncompleteType readBinaryParameterType(SpaceSystem spaceSystem)
            throws IllegalStateException, XMLStreamException {
        log.trace(ELEM_BINARY_PARAMETER_TYPE);
        StartElement element = checkStartElementPreconditions();

        BinaryParameterType.Builder typeBuilder = new BinaryParameterType.Builder();
        IncompleteType incompleteType = new IncompleteType(spaceSystem, typeBuilder);

        readParameterBaseTypeAttributes(spaceSystem, element, incompleteType);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isNamedItemProperty()) {
                readNamedItemProperty(typeBuilder);
            } else if (isStartElementWithName(ELEM_UNIT_SET)) {
                typeBuilder.addAllUnits(readUnitSet());
            } else if (isStartElementWithName(ELEM_BINARY_DATA_ENCODING)) {
                typeBuilder.setEncoding(readBinaryDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_STRING_DATA_ENCODING)) {
                typeBuilder.setEncoding(readStringDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_INTEGER_DATA_ENCODING)) {
                typeBuilder.setEncoding(readIntegerDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_FLOAT_DATA_ENCODING)) {
                throw new XMLStreamException("Encoding " + xmlEvent.asStartElement().getName().getLocalPart()
                        + " not supported for binary parameter", xmlEvent.getLocation());
            } else if (isEndElementWithName(ELEM_BINARY_PARAMETER_TYPE)) {
                return incompleteType;
            } else {
                logUnknown();
            }
        }
    }

    private BinaryDataEncoding.Builder readBinaryDataEncoding(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_BINARY_DATA_ENCODING);
        checkStartElementPreconditions();
        String tag = xmlEvent.asStartElement().getName().getLocalPart();

        BinaryDataEncoding.Builder binaryDataEncoding = new BinaryDataEncoding.Builder();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_SIZE_IN_BITS)) {
                IntegerValue v = readIntegerValue(spaceSystem);
                if (v instanceof FixedIntegerValue) {
                    binaryDataEncoding.setSizeInBits((int) ((FixedIntegerValue) v).getValue());
                } else if (v instanceof DynamicIntegerValue) {
                    if (v != IGNORED_DYNAMIC_VALUE) {
                        binaryDataEncoding.setDynamicSize(((DynamicIntegerValue) v));
                        binaryDataEncoding.setType(BinaryDataEncoding.Type.DYNAMIC);
                    }
                } else {
                    throwException("Only FixedIntegerValue supported for sizeInBits");
                }
            } else if (isStartElementWithName("FromBinaryTransformAlgorithm")) {
                binaryDataEncoding.setFromBinaryTransformAlgorithm(readCustomAlgorithm(spaceSystem, null));
            } else if (isStartElementWithName("ToBinaryTransformAlgorithm")) {
                binaryDataEncoding.setToBinaryTransformAlgorithm(readCustomAlgorithm(spaceSystem, null));
            } else if (isEndElementWithName(tag)) {
                return binaryDataEncoding;
            }
        }
    }

    private void throwException(String msg) throws XMLStreamException {
        throw new XMLStreamException(msg, xmlEvent.getLocation());
    }

    private int readIntegerValue() throws XMLStreamException {
        checkStartElementPreconditions();
        String tag = xmlEvent.asStartElement().getName().getLocalPart();

        int sizeInBits = 0;

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (xmlEvent.isCharacters()) {
                sizeInBits = getIntegerCharacter();
            } else if (isEndElementWithName(tag)) {
                return sizeInBits;
            }
        }
    }

    int getIntegerCharacter() throws XMLStreamException {
        if (xmlEvent.isCharacters()) {
            String value = xmlEvent.asCharacters().getData();
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new XMLStreamException("Cannot parse integer '" + value + "'", xmlEvent.getLocation());
            }
        } else {
            throw new IllegalStateException();
        }
    }

    private IncompleteType readStringParameterType(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_INTEGER_PARAMETER_TYPE);
        StartElement element = checkStartElementPreconditions();

        // name attribute
        StringParameterType.Builder typeBuilder = new StringParameterType.Builder();
        IncompleteType incompleteType = new IncompleteType(spaceSystem, typeBuilder);

        readParameterBaseTypeAttributes(spaceSystem, element, incompleteType);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_UNIT_SET)) {
                typeBuilder.addAllUnits(readUnitSet());
            } else if (isStartElementWithName(ELEM_STRING_DATA_ENCODING)) {
                typeBuilder.setEncoding(readStringDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_BINARY_DATA_ENCODING)) {
                typeBuilder.setEncoding(readBinaryDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_CONTEXT_ALARM_LIST)) {
                skipXtceSection(ELEM_CONTEXT_ALARM_LIST);
            } else if (isStartElementWithName(ELEM_INTEGER_DATA_ENCODING)
                    || isStartElementWithName(ELEM_FLOAT_DATA_ENCODING)) {
                throw new XMLStreamException("Encoding " + xmlEvent.asStartElement().getName().getLocalPart()
                        + " not supported for string parameter", xmlEvent.getLocation());
            } else if (isEndElementWithName(ELEM_STRING_PARAMETER_TYPE)) {
                return incompleteType;
            } else {
                logUnknown();
            }
        }
    }

    private StringDataEncoding.Builder readStringDataEncoding(SpaceSystem spaceSystem) throws XMLStreamException {
        checkStartElementPreconditions();

        StringDataEncoding.Builder stringDataEncoding = new StringDataEncoding.Builder();
        String encoding = readAttribute("encoding", xmlEvent.asStartElement(), null);
        if (encoding != null) {
            stringDataEncoding.setEncoding(encoding);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(ELEM_SIZE_IN_BITS)) {
                readStringSizeInBits(spaceSystem, stringDataEncoding);
            } else if (isStartElementWithName(ELEM_VARIABLE)) {
                readVariableStringSize(spaceSystem, stringDataEncoding);
            } else if (isEndElementWithName(ELEM_STRING_DATA_ENCODING)) {
                if (stringDataEncoding.getSizeType() == null) {
                    throw new XMLStreamException(
                            ELEM_SIZE_IN_BITS + " or " + ELEM_VARIABLE + " not specified for the StringDataEncoding",
                            xmlEvent.getLocation());
                }
                return stringDataEncoding;
            }
        }
    }

    private void readStringSizeInBits(SpaceSystem spaceSystem, StringDataEncoding.Builder stringDataEncoding)
            throws XMLStreamException {
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(ELEM_FIXED)) {
                IntegerValue v = readIntegerValue(spaceSystem);
                if (v instanceof FixedIntegerValue) {
                    stringDataEncoding.setSizeType(SizeType.FIXED);
                    stringDataEncoding.setSizeInBits((int) ((FixedIntegerValue) v).getValue());
                } else {
                    throwException("Only FixedValue supported for string size in bits");
                }
            } else if (isStartElementWithName(ELEM_TERMINATION_CHAR)) {
                stringDataEncoding.setSizeType(SizeType.TERMINATION_CHAR);
                byte[] x = readHexBinary();
                if (x == null || x.length != 1) {
                    throwException("Terminated strings have to have the size of the termination character of 1");
                }
                stringDataEncoding.setTerminationChar(x[0]);
            } else if (isStartElementWithName(ELEM_LEADING_SIZE)) {
                stringDataEncoding.setSizeType(SizeType.LEADING_SIZE);
                int sizeInBits = readIntAttribute("sizeInBitsOfSizeTag", xmlEvent.asStartElement(), 16);
                stringDataEncoding.setSizeInBitsOfSizeTag(sizeInBits);
            } else if (isEndElementWithName(ELEM_SIZE_IN_BITS)) {
                return;
            }
        }
    }

    private void readVariableStringSize(SpaceSystem spaceSystem, StringDataEncoding.Builder stringDataEncoding)
            throws XMLStreamException {
        checkStartElementPreconditions();
        int maxSizeInBits = readIntAttribute("maxSizeInBits", xmlEvent.asStartElement());
        stringDataEncoding.setMaxSizeInBits(maxSizeInBits);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(ELEM_DYNAMIC_VALUE)) {
                DynamicIntegerValue div = readDynamicValue(spaceSystem);
                if (div != IGNORED_DYNAMIC_VALUE) {
                    stringDataEncoding.setSizeType(SizeType.FIXED);
                    stringDataEncoding.setDynamicBufferSize(div);
                }
            } else if (isStartElementWithName(ELEM_TERMINATION_CHAR)) {
                stringDataEncoding.setSizeType(SizeType.TERMINATION_CHAR);
                byte[] x = readHexBinary();
                if (x == null || x.length != 1) {
                    throwException("Terminated strings have to have the size of the termination character of 1");
                }
                stringDataEncoding.setTerminationChar(x[0]);
            } else if (isStartElementWithName(ELEM_LEADING_SIZE)) {
                stringDataEncoding.setSizeType(SizeType.LEADING_SIZE);
                int sizeInBits = readIntAttribute("sizeInBitsOfSizeTag", xmlEvent.asStartElement(), 16);
                stringDataEncoding.setSizeInBitsOfSizeTag(sizeInBits);
            } else if (isEndElementWithName(ELEM_VARIABLE)) {
                return;
            }
        }
    }

    private byte[] readHexBinary() throws XMLStreamException {
        checkStartElementPreconditions();
        String tag = xmlEvent.asStartElement().getName().getLocalPart();
        byte[] b = null;

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (xmlEvent.isCharacters()) {
                b = HexUtils.unhex(xmlEvent.asCharacters().getData());
            } else if (isEndElementWithName(tag)) {
                return b;
            }
        }
    }

    private IncompleteType readIntegerParameterType(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_INTEGER_PARAMETER_TYPE);

        StartElement element = checkStartElementPreconditions();

        IntegerParameterType.Builder typeBuilder = new IntegerParameterType.Builder();
        IncompleteType incompleteType = new IncompleteType(spaceSystem, typeBuilder);
        readParameterBaseTypeAttributes(spaceSystem, element, incompleteType);

        int sizeInBits = readIntAttribute(ATTR_SIZE_IN_BITS, element, 32);
        typeBuilder.setSizeInBits(sizeInBits);

        String value = readAttribute("signed", element, null);
        if (value != null) {
            boolean signed = Boolean.parseBoolean(value);
            typeBuilder.setSigned(signed);
        }
        value = readAttribute(ATTR_INITIAL_VALUE, element, null);
        if (value != null) {
            typeBuilder.setInitialValue(value);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (readBaseTypeProperties(typeBuilder)) {
                continue;
            } else if (isStartElementWithName(ELEM_INTEGER_DATA_ENCODING)) {
                typeBuilder.setEncoding(readIntegerDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_FLOAT_DATA_ENCODING)) {
                typeBuilder.setEncoding(readFloatDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_STRING_DATA_ENCODING)) {
                typeBuilder.setEncoding(readStringDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_BINARY_DATA_ENCODING)) {
                throw new XMLStreamException("Cannot use a binary data encoding for integer parameters");
            } else if (isStartElementWithName(ELEM_DEFAULT_ALARM)) {
                typeBuilder.setDefaultAlarm(readDefaultAlarm());
            } else if (isStartElementWithName(ELEM_CONTEXT_ALARM_LIST)) {
                typeBuilder.setNumericContextAlarmList(readNumericContextAlarmList(spaceSystem));
            } else if (isStartElementWithName(ELEM_VALID_RANGE)) {
                typeBuilder.setValidRange(readIntegerValidRange(typeBuilder.isSigned()));
            } else if (isStartElementWithName(ELEM_TO_STRING)) {
                typeBuilder.setNumberFormat(readToString());
            } else if (isEndElementWithName(ELEM_INTEGER_PARAMETER_TYPE)) {
                return incompleteType;
            } else {
                logUnknown();
            }
        }
    }

    private IntegerDataEncoding.Builder readIntegerDataEncoding(SpaceSystem spaceSystem) throws IllegalStateException,
            XMLStreamException {
        log.trace(ELEM_INTEGER_DATA_ENCODING);
        StartElement element = checkStartElementPreconditions();

        IntegerDataEncoding.Builder integerDataEncoding = null;

        Integer sizeInBits = null;
        if (hasAttribute(ATTR_SIZE_IN_BITS, element)) {
            sizeInBits = readIntAttribute(ATTR_SIZE_IN_BITS, element);
            if (sizeInBits < 0 || sizeInBits > 64) {
                throw new XMLStreamException(
                        "Invalid sizeInBits " + sizeInBits
                                + " specified for integer data encoding. Supported are between 0 and 64.",
                        xmlEvent.getLocation());
            }
        }
        ByteOrder byteOrder = readByteOrder();
        integerDataEncoding = new IntegerDataEncoding.Builder()
                .setSizeInBits(sizeInBits)
                .setByteOrder(byteOrder);

        // encoding attribute
        String value = readAttribute("encoding", element, null);
        if (value != null) {
            if ("unsigned".equalsIgnoreCase(value)) {
                integerDataEncoding.setEncoding(IntegerDataEncoding.Encoding.UNSIGNED);
            } else if ("signMagnitude".equalsIgnoreCase(value)) {
                integerDataEncoding.setEncoding(IntegerDataEncoding.Encoding.SIGN_MAGNITUDE);
            } else if ("twosComplement".equalsIgnoreCase(value)) {
                integerDataEncoding.setEncoding(IntegerDataEncoding.Encoding.TWOS_COMPLEMENT);
            } else if ("twosCompliment".equalsIgnoreCase(value)) {
                // Tolerate this misspelling. It was part of XTCE v1.0 and v1.1 (fixed in v1.2)
                integerDataEncoding.setEncoding(IntegerDataEncoding.Encoding.TWOS_COMPLEMENT);
            } else if ("onesComplement".equalsIgnoreCase(value)) {
                integerDataEncoding.setEncoding(IntegerDataEncoding.Encoding.ONES_COMPLEMENT);
            } else if ("onesCompliment".equalsIgnoreCase(value)) {
                // Tolerate this misspelling. It was part of XTCE v1.0 and v1.1 (fixed in v1.2)
                integerDataEncoding.setEncoding(IntegerDataEncoding.Encoding.ONES_COMPLEMENT);
            } else if ("string".equalsIgnoreCase(value)) {
                integerDataEncoding.setEncoding(IntegerDataEncoding.Encoding.STRING);
            } else {
                throwException("Unsupported encoding '" + value + "'");
            }
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_DEFAULT_CALIBRATOR)) {
                integerDataEncoding.setDefaultCalibrator(readCalibrator(spaceSystem));
            } else if (isStartElementWithName(ELEM_CONTEXT_CALIBRATOR_LIST)) {
                integerDataEncoding.setContextCalibratorList(readContextCalibratorList(spaceSystem));
            } else if (isStartElementWithName(ELEM_STRING_DATA_ENCODING)) {
                // Not in XTCE, but allowed by Yamcs
                integerDataEncoding.setStringEncoding(readStringDataEncoding(spaceSystem).build());
            } else if (isEndElementWithName(ELEM_INTEGER_DATA_ENCODING)) {
                return integerDataEncoding;
            } else {
                logUnknown();
            }
        }
    }

    private ByteOrder readByteOrder() throws XMLStreamException {
        String byteOrderStr = readAttribute("byteOrder", xmlEvent.asStartElement(), null);
        if (byteOrderStr == null) {
            return null;
        }

        ByteOrder byteOrder = ByteOrder.BIG_ENDIAN;
        if ("mostSignificantByteFirst".equals(byteOrderStr)) {
            byteOrder = ByteOrder.BIG_ENDIAN;
        } else if ("leastSignificantByteFirst".equals(byteOrderStr)) {
            byteOrder = ByteOrder.LITTLE_ENDIAN;
        } else {
            throw new XMLStreamException(
                    "Unsupported byteOrder '" + byteOrderStr
                            + "' specified for integer data encoding. Supported are mostSignificantByteFirst or leastSignificantByteFirst.",
                    xmlEvent.getLocation());
        }
        return byteOrder;
    }

    private List<ContextCalibrator> readContextCalibratorList(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_CONTEXT_CALIBRATOR_LIST);
        checkStartElementPreconditions();

        List<ContextCalibrator> clist = new ArrayList<>();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(ELEM_CONTEXT_CALIBRATOR)) {
                clist.add(readContextCalibrator(spaceSystem));
            } else if (isEndElementWithName(ELEM_CONTEXT_CALIBRATOR_LIST)) {
                return clist;
            } else {
                logUnknown();
            }
        }
    }

    private Calibrator readCalibrator(SpaceSystem spaceSystem) throws IllegalStateException, XMLStreamException {
        log.trace(ELEM_DEFAULT_CALIBRATOR);
        checkStartElementPreconditions();
        String tag = xmlEvent.asStartElement().getName().getLocalPart();
        Calibrator calibrator = null;

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_POLYNOMIAL_CALIBRATOR)) {
                calibrator = readPolynomialCalibrator();
            } else if (isStartElementWithName(ELEM_MATH_OPERATION_CALIBRATOR)) {
                calibrator = (Calibrator) readMathOperation(spaceSystem, null);
            } else if (isStartElementWithName(ELEM_SPLINE_CALIBRATOR)) {
                calibrator = readSplineCalibrator();
            } else if (isEndElementWithName(tag)) {
                return calibrator;
            }
        }
    }

    // reads a math operation either as part of an algorithm (when algo is not null)
    // or as part of a calibration
    private MathOperation readMathOperation(SpaceSystem spaceSystem, MathAlgorithm algo) throws XMLStreamException {
        String tag = xmlEvent.asStartElement().getName().getLocalPart();
        List<MathOperation.Element> list = new ArrayList<>();

        String refName = null;
        if (algo != null) {
            refName = readMandatoryAttribute("outputParameterRef", xmlEvent.asStartElement());
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(ELEM_VALUE_OPERAND)) {
                list.add(new MathOperation.Element(readDouble()));
            } else if (isStartElementWithName("ThisParameterOperand")) {
                if (algo != null) {
                    throw new XMLStreamException("Cannot reference 'ThisParameter' in algorithms.",
                            xmlEvent.getLocation());
                }
                skipToTheEnd("ThisParameterOperand");
                list.add(new MathOperation.Element());
            } else if (isStartElementWithName("ParameterInstanceRefOperand")) {
                if (algo == null) {
                    throw new XMLStreamException("Cannot reference other paramters in calibrations."
                            + " Use 'ThisParameterOperand' to refer to the parameter to be calibrated ",
                            xmlEvent.getLocation());
                }
                ParameterInstanceRef pref = readParameterInstanceRef(spaceSystem, null);
                pref.setRelativeTo(InstanceRelativeTo.PACKET_START_ACROSS_PACKETS);
                algo.addInput(new InputParameter(pref));
                list.add(new MathOperation.Element(pref));
            } else if (isStartElementWithName("Operator")) {
                list.add(new MathOperation.Element(readMathOperator()));
            } else if (isStartElementWithName(ELEM_TRIGGER_SET)) {
                if (algo == null) {
                    throw new XMLStreamException("Cannot specify a trigger set for a calibration");
                }
                algo.setTriggerSet(readTriggerSet(spaceSystem));
            } else if (isEndElementWithName(tag)) {
                break;
            }
        }

        if (algo != null) {

            TriggeredMathOperation trigMathOp = new TriggeredMathOperation(list);
            NameReference nr = new NameReference(refName, Type.PARAMETER).addResolvedAction(nd -> {
                algo.addOutput(new OutputParameter((Parameter) nd));
            });
            spaceSystem.addUnresolvedReference(nr);
            algo.setMathOperation(trigMathOp);
            return trigMathOp;
        } else {
            return new MathOperationCalibrator(list);
        }
    }

    private TriggerSetType readTriggerSet(SpaceSystem spaceSystem) throws XMLStreamException {
        String tag = xmlEvent.asStartElement().getName().getLocalPart();
        TriggerSetType triggerSet = new TriggerSetType();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName("OnParameterUpdateTrigger")) {
                triggerSet.addOnParameterUpdateTrigger(readOnParameterUpdateTrigger(spaceSystem));
            } else if (isStartElementWithName("OnContainerUpdateTrigger")) {
                throw new XMLStreamException("OnContainerUpdateTrigger not implemented", xmlEvent.getLocation());
            } else if (isStartElementWithName("OnPeriodicRateTrigger")) {
                triggerSet.addOnPeriodicRateTrigger(readOnPeriodicRateTrigger(spaceSystem));
            } else if (isEndElementWithName(tag)) {
                return triggerSet;
            }
        }
    }

    private OnParameterUpdateTrigger readOnParameterUpdateTrigger(SpaceSystem spaceSystem) throws XMLStreamException {
        String refName = null;
        refName = readMandatoryAttribute(ATTR_PARAMETER_REF, xmlEvent.asStartElement());
        OnParameterUpdateTrigger trigger = new OnParameterUpdateTrigger();
        NameReference nr = new NameReference(refName, Type.PARAMETER).addResolvedAction(nd -> {
            trigger.setParameter((Parameter) nd);
        });
        spaceSystem.addUnresolvedReference(nr);
        return trigger;
    }

    private OnPeriodicRateTrigger readOnPeriodicRateTrigger(SpaceSystem spaceSystem) throws XMLStreamException {
        double d = readDoubleAttribute("fireRateInSeconds", xmlEvent.asStartElement());
        OnPeriodicRateTrigger trigger = new OnPeriodicRateTrigger((long) (1000 * d));
        return trigger;
    }

    private double parseDouble(String value) throws XMLStreamException {
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            throw new XMLStreamException("Cannot parse double value '" + value + "'", xmlEvent.getLocation());
        }
    }

    private MathOperator readMathOperator() throws XMLStreamException {
        String tag = xmlEvent.asStartElement().getName().getLocalPart();
        MathOperator m = null;
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (xmlEvent.isCharacters()) {
                m = MathOperator.fromXtceName(xmlEvent.asCharacters().getData());
            } else if (isEndElementWithName(tag)) {
                return m;
            }
        }
    }

    private ContextCalibrator readContextCalibrator(SpaceSystem spaceSystem)
            throws IllegalStateException, XMLStreamException {
        log.trace(ELEM_CONTEXT_CALIBRATOR);
        checkStartElementPreconditions();

        MatchCriteria context = null;
        Calibrator calibrator = null;
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_CONTEXT_MATCH)) {
                context = readMatchCriteria(spaceSystem, null);
            } else if (isStartElementWithName(ELEM_CALIBRATOR)) {
                calibrator = readCalibrator(spaceSystem);
            } else if (isEndElementWithName(ELEM_CONTEXT_CALIBRATOR)) {
                if (context == null) {
                    throw new XMLStreamException("Invalid context calibrator, no context specified");
                }
                if (calibrator == null) {
                    throw new XMLStreamException("Invalid context calibrator, no calibrator specified",
                            xmlEvent.getLocation());
                }
                return new ContextCalibrator(context, calibrator);
            }
        }
    }

    /**
     * Instantiate the SplineCalibrator element.
     * 
     * @return
     * @throws XMLStreamException
     */
    private Calibrator readSplineCalibrator() throws XMLStreamException {
        log.trace(ELEM_SPLINE_CALIBRATOR);
        checkStartElementPreconditions();

        ArrayList<SplinePoint> splinePoints = new ArrayList<>();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_SPLINE_POINT)) {
                splinePoints.add(readSplinePoint());
            } else if (isEndElementWithName(ELEM_SPLINE_CALIBRATOR)) {
                return new SplineCalibrator(splinePoints);
            }
        }
    }

    /**
     * Instantiate SplinePoint element. This element has two required attributes: raw, calibrated
     * 
     * @return
     * @throws XMLStreamException
     */
    private SplinePoint readSplinePoint() throws XMLStreamException {
        log.trace(ELEM_SPLINE_POINT);
        checkStartElementPreconditions();

        double raw = readDoubleAttribute("raw", xmlEvent.asStartElement());
        double calibrated = readDoubleAttribute("calibrated", xmlEvent.asStartElement());

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isEndElementWithName(ELEM_SPLINE_POINT)) {
                return new SplinePoint(raw, calibrated);
            } else {
                logUnknown();
            }
        }
    }

    private Calibrator readPolynomialCalibrator() throws XMLStreamException {
        log.trace(ELEM_POLYNOMIAL_CALIBRATOR);
        checkStartElementPreconditions();

        int maxExponent = 0;
        HashMap<Integer, Double> polynome = new HashMap<>();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(ELEM_TERM)) {
                XtceTerm term = readTerm();
                if (term.getExponent() > maxExponent) {
                    maxExponent = term.getExponent();
                }
                polynome.put(term.getExponent(), term.getCoefficient());
            } else if (isEndElementWithName(ELEM_POLYNOMIAL_CALIBRATOR)) {
                double[] coefficients = new double[maxExponent + 1];
                for (Map.Entry<Integer, Double> entry : polynome.entrySet()) {
                    coefficients[entry.getKey()] = entry.getValue();
                }
                return new PolynomialCalibrator(coefficients);
            } else {
                logUnknown();
            }
        }
    }

    private XtceTerm readTerm() throws XMLStreamException {
        log.trace(ELEM_TERM);
        checkStartElementPreconditions();

        int exponent = readIntAttribute("exponent", xmlEvent.asStartElement());
        double coefficient = readDoubleAttribute("coefficient", xmlEvent.asStartElement());

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isEndElementWithName(ELEM_TERM)) {
                return new XtceTerm(exponent, coefficient);
            }
        }
    }

    private List<UnitType> readUnitSet() throws IllegalStateException, XMLStreamException {
        log.trace(ELEM_UNIT_SET);

        List<UnitType> units = new ArrayList<>();
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_UNIT)) {
                UnitType u = readUnit();
                if (u != null) {
                    units.add(u);
                }
            } else if (isEndElementWithName(ELEM_UNIT_SET)) {
                return units;
            } else {
                logUnknown();
            }
        }
    }

    private Double readDouble() throws XMLStreamException {
        String tag = xmlEvent.asStartElement().getName().getLocalPart();
        Double d = null;
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (xmlEvent.isCharacters()) {
                d = parseDouble(xmlEvent.asCharacters().getData());
            } else if (isEndElementWithName(tag)) {
                return d;
            }
        }
    }

    private UnitType readUnit() throws XMLStreamException {
        log.trace(ELEM_UNIT);
        checkStartElementPreconditions();

        StartElement element = xmlEvent.asStartElement();

        double powerValue = readDoubleAttribute("power", element, 1);
        String factorValue = readAttribute("factor", element, null);
        String descriptionValue = readAttribute("description", element, null);
        String unit;

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (xmlEvent.isCharacters()) {
                unit = xmlEvent.asCharacters().getData();
                break;
            } else if (isEndElementWithName(ELEM_UNIT)) {
                return null;
            }
        }

        UnitType unitType = new UnitType(unit);
        unitType.setPower(powerValue);
        if (factorValue != null) {
            unitType.setFactor(factorValue);
        }
        if (descriptionValue != null) {
            unitType.setDescription(descriptionValue);
        }

        return unitType;
    }

    private IncompleteType readEnumeratedParameterType(SpaceSystem spaceSystem) throws XMLStreamException {

        log.trace(ELEM_ENUMERATED_PARAMETER_TYPE);

        StartElement element = checkStartElementPreconditions();

        // name attribute
        EnumeratedParameterType.Builder typeBuilder = new EnumeratedParameterType.Builder();
        IncompleteType incompleteType = new IncompleteType(spaceSystem, typeBuilder);
        readParameterBaseTypeAttributes(spaceSystem, element, incompleteType);

        // initialValue attribute
        String value = readAttribute(ATTR_INITIAL_VALUE, element, null);
        if (value != null) {
            typeBuilder.setInitialValue(value);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (readBaseTypeProperties(typeBuilder)) {
                continue;
            } else if (isStartElementWithName(ELEM_INTEGER_DATA_ENCODING)) {
                typeBuilder.setEncoding(readIntegerDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_FLOAT_DATA_ENCODING)) {
                typeBuilder.setEncoding(readFloatDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_STRING_DATA_ENCODING)) {
                typeBuilder.setEncoding(readStringDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_BINARY_DATA_ENCODING)) {
                typeBuilder.setEncoding(readBinaryDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_ENUMERATION_LIST)) {
                readEnumerationList(typeBuilder);
            } else if (isStartElementWithName(ELEM_DEFAULT_ALARM)) {
                typeBuilder.setDefaultAlarm(readEnumerationAlarm(typeBuilder));
            } else if (isStartElementWithName(ELEM_CONTEXT_ALARM_LIST)) {
                typeBuilder.setContextAlarmList(readEnumerationContextAlarmList(spaceSystem, typeBuilder));
            } else if (isEndElementWithName(ELEM_ENUMERATED_PARAMETER_TYPE)) {
                return incompleteType;
            } else {
                logUnknown();
            }
        }
    }

    private List<EnumerationContextAlarm> readEnumerationContextAlarmList(SpaceSystem spaceSystem,
            EnumeratedDataType.Builder<?> enumParamType) throws XMLStreamException {
        log.trace(ELEM_CONTEXT_ALARM_LIST);
        List<EnumerationContextAlarm> contextAlarmList = new ArrayList<>();
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_CONTEXT_ALARM)) {
                contextAlarmList.add(readEnumerationContextAlarm(spaceSystem, enumParamType));
            } else if (isEndElementWithName(ELEM_CONTEXT_ALARM_LIST)) {
                return contextAlarmList;
            } else {
                logUnknown();
            }
        }
    }

    private EnumerationContextAlarm readEnumerationContextAlarm(SpaceSystem spaceSystem,
            EnumeratedDataType.Builder<?> enumParamType) throws XMLStreamException {
        log.trace(ELEM_CONTEXT_ALARM);
        EnumerationContextAlarm eca = new EnumerationContextAlarm();
        readAlarmAttributes(eca);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(ELEM_CONTEXT_MATCH)) {
                eca.setContextMatch(readMatchCriteria(spaceSystem, null));
            } else if (xmlEvent.getEventType() == XMLStreamConstants.START_ELEMENT) {
                EnumerationAlarm a = readEnumerationAlarm(enumParamType);
                eca.setAlarmList(a.getAlarmList());

            } else if (isEndElementWithName(ELEM_CONTEXT_ALARM)) {
                return eca;
            } else {
                logUnknown();
            }
        }
    }

    private void readEnumerationList(EnumeratedDataType.Builder<?> enumDataType) throws XMLStreamException {
        log.trace(ELEM_ENUMERATION_LIST);
        checkStartElementPreconditions();

        // initialValue attribute
        String initialValue = readAttribute(ATTR_INITIAL_VALUE, xmlEvent.asStartElement(), null);
        if (initialValue != null) {
            enumDataType.setInitialValue(initialValue);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_ENUMERATION)) {
                readEnumeration(enumDataType);
            } else if (isStartElementWithName(ELEM_RANGE_ENUMERATION)) {
                enumDataType.addEnumerationRange(readRangeEnumeration());
            } else if (isEndElementWithName(ELEM_ENUMERATION_LIST)) {
                return;
            } else {
                logUnknown();
            }
        }
    }

    private EnumerationAlarm readEnumerationAlarm(EnumeratedDataType.Builder<?> enumParamType)
            throws XMLStreamException {
        checkStartElementPreconditions();
        String tag = xmlEvent.asStartElement().getName().getLocalPart();
        EnumerationAlarm alarm = new EnumerationAlarm();

        alarm.setMinViolations(readIntAttribute("minViolations", xmlEvent.asStartElement(), 1));

        var defaultAlarmLevel = AlarmLevels.NORMAL;
        var defaultAlarmLevelString = readAttribute("defaultAlarmLevel", xmlEvent.asStartElement(), null);
        if (defaultAlarmLevelString != null) {
            defaultAlarmLevel = getAlarmLevel(defaultAlarmLevelString);
        }
        alarm.setDefaultAlarmLevel(defaultAlarmLevel);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName("EnumerationAlarm")) {
                String label = readAttribute("enumerationLabel", xmlEvent.asStartElement(), null);
                if (label == null) {
                    label = readAttribute("enumerationValue", xmlEvent.asStartElement(), null); // XTCE 1.1
                }
                if (label == null) {
                    throw new XMLStreamException(fileName + ": error in definition of " + enumParamType.getName()
                            + "EnumerationAlarm: no enumerationLabel specified", xmlEvent.getLocation());
                }
                if (!enumParamType.hasLabel(label)) {
                    throw new XMLStreamException("Reference to invalid enumeration label '" + label + "'");
                }
                AlarmLevels level = getAlarmLevel(readMandatoryAttribute("alarmLevel", xmlEvent.asStartElement()));
                alarm.addAlarm(label, level);
            } else if (isEndElementWithName(tag)) {
                return alarm;
            }
        }
    }

    private AlarmLevels getAlarmLevel(String level) throws XMLStreamException {
        try {
            return AlarmLevels.fromXtce(level);
        } catch (IllegalArgumentException e) {
            String s = Arrays.stream(AlarmLevels.values()).map(al -> al.xtceName)
                    .collect(Collectors.joining(", ", "[", "]"));

            throw new XMLStreamException("Invalid alarm level '" + level + "'; use one of: " + s);
        }
    }

    private ValueEnumerationRange readRangeEnumeration() throws XMLStreamException {
        log.trace(ELEM_RANGE_ENUMERATION);
        checkStartElementPreconditions();

        boolean isMinInclusive = true;
        boolean isMaxInclusive = true;
        double min = 0.0;
        double max = 0.0;

        String value = readAttribute("maxInclusive", xmlEvent.asStartElement(), null);
        if (value != null) {
            max = parseDouble(value);
            isMaxInclusive = true;
        }

        value = readAttribute("minInclusive", xmlEvent.asStartElement(), null);
        if (value != null) {
            isMinInclusive = true;
            min = parseDouble(value);
        }

        value = readAttribute("maxExclusive", xmlEvent.asStartElement(), null);
        if (value != null) {
            max = parseDouble(value);
            isMaxInclusive = false;
        }

        value = readAttribute("minExclusive", xmlEvent.asStartElement(), null);
        if (value != null) {
            isMinInclusive = false;
            min = parseDouble(value);
        }

        value = readAttribute("label", xmlEvent.asStartElement(), null);
        if (value == null) {
            log.error("Attribute label is missing.");
            value = "UNDEF";
        }

        ValueEnumerationRange range = new ValueEnumerationRange(min, max, isMinInclusive,
                isMaxInclusive, value);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isEndElementWithName(ELEM_RANGE_ENUMERATION)) {
                return range;
            } else {
                logUnknown();
            }
        }
    }

    private void readEnumeration(EnumeratedDataType.Builder<?> enumDataType) throws XMLStreamException {
        log.trace(ELEM_ENUMERATION);
        checkStartElementPreconditions();
        StartElement element = xmlEvent.asStartElement();

        long longValue = readLongAttribute("value", element);
        String label = readMandatoryAttribute("label", element);
        String description = readAttribute(ATTR_SHORT_DESCRIPTION, element, null);
        String maxValue = readAttribute("maxValue", element, null);
        if (maxValue != null) {
            double mvd = Double.parseDouble(maxValue);
            ValueEnumerationRange ver = new ValueEnumerationRange(longValue, mvd, true, true, label);
            ver.setDescription(description);
            enumDataType.addEnumerationRange(ver);
        } else {
            ValueEnumeration ve = new ValueEnumeration(longValue, label);
            ve.setDescription(description);
            enumDataType.addEnumerationValue(ve);
        }
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isEndElementWithName(ELEM_ENUMERATION)) {
                return;
            } else {
                logUnknown();
            }
        }
    }

    /**
     * @param spaceSystem
     * 
     */
    private void readParameterSet(SpaceSystem spaceSystem) throws IllegalStateException, XMLStreamException {
        log.trace(ELEM_PARAMETER_SET);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_PARAMETER)) {
                readParameter(spaceSystem); // the parameter is registered inside
            } else if (isStartElementWithName(ELEM_PARAMETER_REF)) {
                skipXtceSection(ELEM_PARAMETER_REF);
            } else if (isEndElementWithName(ELEM_PARAMETER_SET)) {
                return;
            } else {
                logUnknown();
            }
        }
    }

    /**
     * 
     * @return Parameter instance
     * @throws IllegalStateException
     * @throws XMLStreamException
     */
    private Parameter readParameter(SpaceSystem spaceSystem) throws IllegalStateException, XMLStreamException {
        log.trace(ELEM_PARAMETER);
        checkStartElementPreconditions();

        Parameter parameter = null;

        // name
        StartElement element = xmlEvent.asStartElement();
        String value = readMandatoryAttribute(ATTR_NAME, element);
        parameter = new Parameter(value);

        // by default in XTCE all parameters are persistent
        // when reading the properties, the persistence may be disabled for a parameter
        parameter.setPersistent(true);

        String initialValue = readAttribute(ATTR_INITIAL_VALUE, xmlEvent.asStartElement(), null);

        // parameterTypeRef
        value = readMandatoryAttribute("parameterTypeRef", element);
        ParameterType ptype = spaceSystem.getParameterType(value);
        if (ptype != null) {
            parameter.setParameterType(ptype);
            if (initialValue != null) {
                parameter.setInitialValue(ptype.convertType(initialValue));
            }
        } else {
            final Parameter p = parameter;
            NameReference nr = new NameReference(value, Type.PARAMETER_TYPE).addResolvedAction(nd -> {
                ParameterType ptype1 = (ParameterType) nd;
                p.setParameterType(ptype1);
                if (initialValue != null) {
                    p.setInitialValue(ptype1.convertType(initialValue));
                }
            });
            spaceSystem.addUnresolvedReference(nr);
        }

        // shortDescription
        parameter.setShortDescription(readAttribute(ATTR_SHORT_DESCRIPTION, element, null));

        // register the parameter now, because parameter can refer to
        // self in the parameter properties
        spaceSystem.addParameter(parameter);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isNamedItemProperty()) {
                readNamedItemProperty(parameter);
            } else if (isStartElementWithName(ELEM_PARAMETER_PROPERTIES)) {
                readParameterProperties(spaceSystem, parameter);
            } else if (isEndElementWithName(ELEM_PARAMETER)) {
                return parameter;
            } else {
                logUnknown();
            }
        }
    }

    private XtceParameterProperties readParameterProperties(SpaceSystem spaceSystem, Parameter p)
            throws XMLStreamException {
        log.trace(ELEM_PARAMETER_PROPERTIES);
        checkStartElementPreconditions();
        String v = readAttribute("dataSource", xmlEvent.asStartElement(), null);
        if (v != null) {
            try {
                p.setDataSource(DataSource.valueOf(v.toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new XMLStreamException(
                        "invalid dataSource '" + v + "'. Valid values: "
                                + Stream.of(DataSource.values())
                                        .map(ds -> ds.name().toLowerCase())
                                        .collect(Collectors.joining(", ")));
            }

        }
        boolean persistence = readBooleanAttribute("persistence", xmlEvent.asStartElement(), true);
        p.setPersistent(persistence);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_VALIDITY_CONDITION)) {
                readMatchCriteria(spaceSystem, null);
            } else if (isStartElementWithName(ELEM_PHYSICAL_ADDRESS_SET)) {
                skipXtceSection(ELEM_PHYSICAL_ADDRESS_SET);
            } else if (isStartElementWithName(ELEM_SYSTEM_NAME)) {
                skipXtceSection(ELEM_SYSTEM_NAME);
            } else if (isStartElementWithName(ELEM_TIME_ASSOCIATION)) {
                skipXtceSection(ELEM_TIME_ASSOCIATION);
            } else if (isEndElementWithName(ELEM_PARAMETER_PROPERTIES)) {
                return null;
            } else {
                logUnknown();
            }
        }
    }

    private RateInStream readRateInStream(SpaceSystem spaceSystem) throws XMLStreamException {
        checkStartElementPreconditions();

        String basis = readAttribute("basis", xmlEvent.asStartElement(), null);
        if ((basis != null) && !"perSecond".equalsIgnoreCase(basis)) {
            throw new XMLStreamException("Currently unsupported rate in stream basis: " + basis);
        }
        long minInterval = -1;
        long maxInterval = -1;
        String v = readAttribute("minimumValue", xmlEvent.asStartElement(), null);
        if (v != null) {
            maxInterval = (long) (1000 / parseDouble(v));
        }
        v = readAttribute("maximumValue", xmlEvent.asStartElement(), null);
        if (v != null) {
            minInterval = (long) (1000 / parseDouble(v));
        }
        RateInStream ris = new RateInStream(minInterval, maxInterval);

        // read end element
        xmlEvent = xmlEventReader.nextEvent();
        if (!isEndElementWithName(ELEM_DEFAULT_RATE_IN_STREAM)) {
            throw new IllegalStateException(ELEM_DEFAULT_RATE_IN_STREAM + " end element expected");
        }
        return ris;
    }

    // if metaCmd is not null, it means this is referenced from a command verifier or transmission constraint and it can
    // reference command arguments or command history parameters
    private MatchCriteria readMatchCriteria(SpaceSystem spaceSystem, MetaCommand metaCmd) throws XMLStreamException {
        log.trace("MatchCriteria");
        checkStartElementPreconditions();
        String tag = xmlEvent.asStartElement().getName().getLocalPart();
        MatchCriteria criteria = null;

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(ELEM_COMPARISON)) {
                criteria = readComparison(spaceSystem, metaCmd);
            } else if (isStartElementWithName(ELEM_COMPARISON_LIST)) {
                criteria = readComparisonList(spaceSystem, metaCmd);
            } else if (isStartElementWithName(ELEM_BOOLEAN_EXPRESSION)) {
                criteria = readBooleanExpression(spaceSystem, metaCmd);
            } else if (isStartElementWithName(ELEM_CUSTOM_ALGORITHM)) {
                skipXtceSection(ELEM_CUSTOM_ALGORITHM);
            } else if (isEndElementWithName(tag)) {
                return criteria;
            } else {
                logUnknown();
            }
        }
    }

    // if metaCmd is not null, it means this is part of a command verifier or constraint so references to arguments and
    // command history are possible
    private ComparisonList readComparisonList(SpaceSystem spaceSystem, MetaCommand metaCmd) throws XMLStreamException {
        log.trace(ELEM_COMPARISON_LIST);
        checkStartElementPreconditions();

        ComparisonList list = new ComparisonList();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_COMPARISON)) {
                list.addComparison(readComparison(spaceSystem, metaCmd));
            } else if (isEndElementWithName(ELEM_COMPARISON_LIST)) {
                return list;
            } else {
                logUnknown();
            }
        }
    }

    private BooleanExpression readBooleanExpression(SpaceSystem spaceSystem, MetaCommand metaCmd)
            throws XMLStreamException {
        log.trace(ELEM_BOOLEAN_EXPRESSION);
        checkStartElementPreconditions();
        BooleanExpression expr = null;

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(ELEM_CONDITION)) {
                expr = readCondition(spaceSystem, metaCmd);
            } else if (isStartElementWithName(ELEM_AND_CONDITIONS)) {
                expr = readAndCondition(spaceSystem, metaCmd);
            } else if (isStartElementWithName(ELEM_OR_CONDITIONS)) {
                expr = readOrCondition(spaceSystem, metaCmd);
            } else if (isEndElementWithName(ELEM_BOOLEAN_EXPRESSION)) {
                if (expr == null) {
                    throw new XMLStreamException(
                            "BooleanExpression has to contain one of Condition, ANDedCondition or ORedCondition",
                            xmlEvent.getLocation());
                }
                return expr;
            } else {
                logUnknown();
            }
        }
    }

    private ParameterValueChange readParameterValueChange(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_PARAMETER_VALUE_CHANGE);
        checkStartElementPreconditions();
        ParameterValueChange pvc = new ParameterValueChange();
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(ELEM_PARAMETER_REF)) {
                Location xmlLocation = xmlEvent.getLocation();
                CompletableFuture<ParameterInstanceRef> cf = new CompletableFuture<>();
                pvc.setParameterRef(readParameterRef(spaceSystem, cf));
                cf.thenAccept(ref -> {
                    ParameterType ptype = ref.getParameter().getParameterType();
                    if (ref.getMemberPath() != null) {
                        ptype = AggregateTypeUtil.getMemberType(ptype, ref.getMemberPath());
                    }

                    if (!(ptype instanceof NumericParameterType)) {
                        throwException(xmlLocation, "Invalid parameter of type " + ptype
                                + " used in ParameterValuChange verifier; expecting a numeric parameter");
                    }
                });
            } else if (isStartElementWithName(ELEM_CHANGE)) {
                double delta = readDoubleAttribute("value", xmlEvent.asStartElement());
                if (delta == 0) {
                    throwException(xmlEvent.getLocation(), "change value cannot be 0");
                }
                pvc.setDelta(delta);
            } else if (isEndElementWithName(ELEM_PARAMETER_VALUE_CHANGE)) {
                if (pvc.getParameterRef() == null) {
                    throw new XMLStreamException("ParameterValueChange has to contain a reference to a parameter",
                            xmlEvent.getLocation());
                }
                if (pvc.getDelta() == 0) {
                    throw new XMLStreamException("ParameterValueChange has to contain a change value",
                            xmlEvent.getLocation());
                }
                return pvc;
            } else {
                logUnknown();
            }
        }
    }

    // if metaCmd is not null, it means this is part of a command verifier or constraint so references to arguments and
    // command history are possible
    private Condition readCondition(SpaceSystem spaceSystem, MetaCommand metaCmd) throws XMLStreamException {
        log.trace(ELEM_CONDITION);
        checkStartElementPreconditions();

        ParameterOrArgumentRef lValueRef = null, rValueRef = null;
        OperatorType comparisonOperator = null;
        String rvalue = null;

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(ELEM_PARAMETER_INSTANCE_REF)) {
                String paraRef = readMandatoryAttribute(ATTR_PARAMETER_REF, xmlEvent.asStartElement());

                ParameterOrArgumentRef ref = paraRef.startsWith(YAMCS_CMDARG_SPACESYSTEM_NAME)
                        ? readArgumentInstanceRef(spaceSystem, metaCmd)
                        : readParameterInstanceRef(spaceSystem, null);

                if (lValueRef == null) {
                    lValueRef = ref;
                } else {
                    rValueRef = ref;
                }
            } else if (isStartElementWithName(ELEM_ARGUMENT_INSTANCE_REF)) {
                ArgumentInstanceRef ref = readArgumentInstanceRef(spaceSystem, metaCmd);

                if (lValueRef == null) {
                    lValueRef = ref;
                } else {
                    rValueRef = ref;
                }
            } else if (isStartElementWithName(ELEM_COMPARISON_OPERATOR)) {
                comparisonOperator = readComparisonOperator();
            } else if (isStartElementWithName(ELEM_VALUE)) {
                rvalue = readStringBetweenTags(ELEM_VALUE);
            } else if (isEndElementWithName(ELEM_CONDITION)) {
                if (lValueRef == null) {
                    throw new XMLStreamException("Condition without left value", xmlEvent.getLocation());
                }
                if (comparisonOperator == null) {
                    throw new XMLStreamException("Condition without comparison operator", xmlEvent.getLocation());
                }
                Condition cond;
                if (rValueRef != null) {
                    cond = new Condition(comparisonOperator, lValueRef, rValueRef);
                } else if (rvalue != null) {
                    cond = new Condition(comparisonOperator, lValueRef, rvalue);
                } else {
                    throw new XMLStreamException("Condition without right value", xmlEvent.getLocation());
                }
                return cond;
            } else {
                logUnknown();
            }
        }
    }

    private ORedConditions readOrCondition(SpaceSystem spaceSystem, MetaCommand metaCmd) throws XMLStreamException {
        log.trace(ELEM_OR_CONDITIONS);
        checkStartElementPreconditions();
        ORedConditions cond = new ORedConditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(ELEM_CONDITION)) {
                cond.addConditionExpression(readCondition(spaceSystem, metaCmd));
            } else if (isStartElementWithName(ELEM_AND_CONDITIONS)) {
                cond.addConditionExpression(readAndCondition(spaceSystem, metaCmd));
            } else if (isEndElementWithName(ELEM_OR_CONDITIONS)) {
                return cond;
            } else {
                logUnknown();
            }
        }
    }

    private ANDedConditions readAndCondition(SpaceSystem spaceSystem, MetaCommand metaCmd) throws XMLStreamException {
        log.trace(ELEM_AND_CONDITIONS);
        checkStartElementPreconditions();
        ANDedConditions cond = new ANDedConditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(ELEM_CONDITION)) {
                cond.addConditionExpression(readCondition(spaceSystem, metaCmd));
            } else if (isStartElementWithName(ELEM_OR_CONDITIONS)) {
                cond.addConditionExpression(readOrCondition(spaceSystem, metaCmd));
            } else if (isEndElementWithName(ELEM_AND_CONDITIONS)) {
                return cond;
            } else {
                logUnknown();
            }
        }
    }

    private OperatorType readComparisonOperator() throws XMLStreamException {
        Location loc = xmlEvent.getLocation();
        String s = readStringBetweenTags(ELEM_COMPARISON_OPERATOR);
        try {
            return OperatorType.fromSymbol(s);
        } catch (IllegalArgumentException e) {
            throw new XMLStreamException("Unknown operator '" + s + "'", loc);
        }
    }

    /**
     * Reads the definition of the containers
     * 
     * @param spaceSystem
     * 
     * @return
     * @throws IllegalStateException
     * @throws XMLStreamException
     */
    private void readContainerSet(SpaceSystem spaceSystem) throws IllegalStateException, XMLStreamException {
        log.trace(ELEM_CONTAINER_SET);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_SEQUENCE_CONTAINER)) {
                SequenceContainer sc = readSequenceContainer(spaceSystem);

                if (excludedContainers.contains(sc.getName())) {
                    log.debug("Not adding '" + sc.getName() + "' to the SpaceSystem because excluded by configuration");
                } else {
                    spaceSystem.addSequenceContainer(sc);
                }
            } else if (isEndElementWithName(ELEM_CONTAINER_SET)) {
                return;
            } else {
                logUnknown();
            }
        }
    }

    private SequenceContainer readSequenceContainer(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_SEQUENCE_CONTAINER);
        checkStartElementPreconditions();

        String value = readMandatoryAttribute(ATTR_NAME, xmlEvent.asStartElement());
        SequenceContainer seqContainer = new SequenceContainer(value);
        seqContainer.setShortDescription(readAttribute(ATTR_SHORT_DESCRIPTION, xmlEvent.asStartElement(), null));

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isNamedItemProperty()) {
                readNamedItemProperty(seqContainer);
            } else if (isStartElementWithName(ELEM_ENTRY_LIST)) {
                readEntryList(spaceSystem, seqContainer, null);
            } else if (isStartElementWithName(ELEM_BASE_CONTAINER)) {
                readBaseContainer(spaceSystem, seqContainer);
            } else if (isStartElementWithName(ELEM_DEFAULT_RATE_IN_STREAM)) {
                seqContainer.setRateInStream(readRateInStream(spaceSystem));
            } else if (isStartElementWithName(ELEM_BINARY_ENCODING)) {
                BinaryDataEncoding.Builder bde = readBinaryDataEncoding(spaceSystem);
                seqContainer.setSizeInBits(bde.getSizeInBits());
            } else if (isEndElementWithName(ELEM_SEQUENCE_CONTAINER)) {
                return seqContainer;
            } else {
                logUnknown();
            }
        }

    }

    private void readBaseContainer(SpaceSystem spaceSystem, SequenceContainer seqContainer) throws XMLStreamException {
        log.trace(ELEM_BASE_CONTAINER);
        StartElement element = checkStartElementPreconditions();
        String refName = readMandatoryAttribute("containerRef", element);
        if (excludedContainers.contains(refName)) {
            log.debug("adding " + seqContainer.getName()
                    + " to the list of the excluded containers because its parent is excluded");
            excludedContainers.add(seqContainer.getName());
        } else {
            // find base container in the set of already defined containers
            SequenceContainer baseContainer = spaceSystem.getSequenceContainer(refName);
            if (baseContainer != null) {
                seqContainer.setBaseContainer(baseContainer);
            } else { // must come from somewhere else
                final SequenceContainer finalsc = seqContainer;
                NameReference nr = new NameReference(refName, Type.SEQUENCE_CONTAINER)
                        .addResolvedAction(nd -> {
                            finalsc.setBaseContainer((SequenceContainer) nd);
                        });
                spaceSystem.addUnresolvedReference(nr);
            }
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_RESTRICTION_CRITERIA)) {
                MatchCriteria criteria = readMatchCriteria(spaceSystem, null);
                seqContainer.setRestrictionCriteria(criteria);
            } else if (isEndElementWithName(ELEM_BASE_CONTAINER)) {
                return;
            } else {
                logUnknown();
            }
        }
    }

    private void readEntryList(SpaceSystem spaceSystem, Container container, MetaCommand mc)
            throws XMLStreamException {
        log.trace(ELEM_ENTRY_LIST);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            SequenceEntry entry = null;
            if (isStartElementWithName(ELEM_PARAMETER_REF_ENTRY)) {
                entry = readParameterRefEntry(spaceSystem);
            } else if (isStartElementWithName(ELEM_PARAMETER_SEGMENT_REF_ENTRY)) {
                skipXtceSection(ELEM_PARAMETER_SEGMENT_REF_ENTRY);
            } else if (isStartElementWithName(ELEM_CONTAINER_REF_ENTRY)) {
                entry = readContainerRefEntry(spaceSystem);
            } else if (isStartElementWithName(ELEM_CONTAINER_SEGMENT_REF_ENTRY)) {
                skipXtceSection(ELEM_CONTAINER_SEGMENT_REF_ENTRY);
            } else if (isStartElementWithName(ELEM_STREAM_SEGMENT_ENTRY)) {
                skipXtceSection(ELEM_STREAM_SEGMENT_ENTRY);
            } else if (isStartElementWithName(ELEM_INDIRECT_PARAMETER_REF_ENTRY)) {
                entry = readIndirectParameterRefEntry(spaceSystem);
            } else if (isStartElementWithName(ELEM_ARRAY_PARAMETER_REF_ENTRY)) {
                entry = readArrayParameterRefEntry(spaceSystem);
            } else if (isStartElementWithName(ELEM_ARGUMENT_REF_ENTRY)) {
                entry = readArgumentRefEntry(spaceSystem, mc);
            } else if (isStartElementWithName(ELEM_ARRAY_ARGUMENT_REF_ENTRY)) {
                skipXtceSection(ELEM_ARRAY_ARGUMENT_REF_ENTRY);
            } else if (isStartElementWithName(ELEM_FIXED_VALUE_ENTRY)) {
                entry = readFixedValueEntry(spaceSystem);
            } else if (isEndElementWithName(ELEM_ENTRY_LIST)) {
                return;
            } else {
                logUnknown();
            }
            if (entry != null) {
                container.addEntry(entry);
            }
        }

    }

    private SequenceEntry readIndirectParameterRefEntry(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_INDIRECT_PARAMETER_REF_ENTRY);
        checkStartElementPreconditions();

        String aliasNameSpace = readAttribute(ATTR_ALIAS_NAME_SPACE, xmlEvent.asStartElement(), null);

        IndirectParameterRefEntry indirectParameterRefEntry = new IndirectParameterRefEntry(0,
                ReferenceLocationType.PREVIOUS_ENTRY, null, aliasNameSpace);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_LOCATION_IN_CONTAINER_IN_BITS)) {
                readLocationInContainerInBits(indirectParameterRefEntry);
            } else if (isStartElementWithName(ELEM_PARAMETER_INSTANCE)) {
                ParameterInstanceRef ref = readParameterInstanceRef(spaceSystem, null);
                ref.setRelativeTo(InstanceRelativeTo.CURRENT_ENTRY_WITHIN_PACKET);
                indirectParameterRefEntry.setParameterRef(ref);
            } else if (isEndElementWithName(ELEM_INDIRECT_PARAMETER_REF_ENTRY)) {
                if (indirectParameterRefEntry.getParameterRef() == null) {
                    throw new XMLStreamException(ELEM_PARAMETER_INSTANCE + " not specified");
                }
                return indirectParameterRefEntry;
            } else {
                logUnknown();
            }
        }
    }

    private SequenceEntry readArrayParameterRefEntry(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_ARRAY_PARAMETER_REF_ENTRY);
        checkStartElementPreconditions();

        String refName = readMandatoryAttribute(ATTR_PARAMETER_REF, xmlEvent.asStartElement());
        SequenceEntry.ReferenceLocationType locationType = SequenceEntry.ReferenceLocationType.PREVIOUS_ENTRY; // default
        ArrayParameterEntry parameterEntry = new ArrayParameterEntry(0, locationType);

        final ArrayParameterEntry finalpe = parameterEntry;
        makeParameterReference(spaceSystem, refName, (param, path) -> {
            finalpe.setParameter(param);
        });

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_LOCATION_IN_CONTAINER_IN_BITS)) {
                readLocationInContainerInBits(parameterEntry);
            } else if (isStartElementWithName(ELEM_REPEAT_ENTRY)) {
                Repeat r = readRepeatEntry(spaceSystem);
                parameterEntry.setRepeatEntry(r);
            } else if (isStartElementWithName(ELEM_INCLUDE_CONDITION)) {
                parameterEntry.setIncludeCondition(readMatchCriteria(spaceSystem, null));
            } else if (isStartElementWithName(ELEM_DIMENSION_LIST)) {
                parameterEntry.setSize(readDimensionList(spaceSystem));
            } else if (isEndElementWithName(ELEM_ARRAY_PARAMETER_REF_ENTRY)) {
                return parameterEntry;
            } else {
                logUnknown();
            }
        }
    }

    private List<IntegerValue> readDimensionList(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_DIMENSION_LIST);
        checkStartElementPreconditions();
        List<IntegerValue> l = new ArrayList<>();
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(ELEM_DIMENSION)) {
                l.add(readDimension(spaceSystem));
            } else if (isStartElementWithName(ELEM_SIZE)) { // FIXME not in XTCE ?
                l.add(readIntegerValue(spaceSystem));
            } else if (isEndElementWithName(ELEM_DIMENSION_LIST)) {
                return l;
            } else {
                logUnknown();
            }
        }
    }

    // Currently only reads an index range like 0..30 which is interpreted as
    // size: 31.
    private IntegerValue readDimension(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_DIMENSION);
        checkStartElementPreconditions();

        IntegerValue endingIndex = null;
        IntegerValue startingIndex = null;
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(ELEM_STARTING_INDEX)) {
                startingIndex = readIntegerValue(spaceSystem);
                if (!(startingIndex instanceof FixedIntegerValue)
                        || ((FixedIntegerValue) startingIndex).getValue() != 0) {
                    throw new XMLStreamException(
                            "Dimension indexes must be specified with FixedValue starting from 0 (partial array entries not supported)",
                            xmlEvent.getLocation());
                }
            } else if (isStartElementWithName(ELEM_ENDING_INDEX)) {
                endingIndex = readIntegerValue(spaceSystem);
            } else if (isEndElementWithName(ELEM_DIMENSION)) {
                if (endingIndex == null) {
                    throw new XMLStreamException(ELEM_ENDING_INDEX + " not specified");
                }
                if (endingIndex instanceof FixedIntegerValue) {
                    long v = ((FixedIntegerValue) endingIndex).getValue();
                    return new FixedIntegerValue(v + 1);
                } else if (endingIndex instanceof DynamicIntegerValue) {
                    DynamicIntegerValue div = (DynamicIntegerValue) endingIndex;
                    div.setIntercept(div.getIntercept() + 1);
                } else {
                    throw new IllegalArgumentException("Unknown type " + endingIndex.getClass());
                }
                return endingIndex;
            } else {
                logUnknown();
            }
        }
    }

    private SequenceEntry readParameterRefEntry(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_PARAMETER_REF_ENTRY);
        checkStartElementPreconditions();

        String refName = readMandatoryAttribute(ATTR_PARAMETER_REF, xmlEvent.asStartElement());

        SequenceEntry.ReferenceLocationType locationType = SequenceEntry.ReferenceLocationType.PREVIOUS_ENTRY; // default
        ParameterEntry parameterEntry = new ParameterEntry(0, locationType);
        final ParameterEntry finalpe = parameterEntry;
        NameReference nr = new NameReference(refName, Type.PARAMETER).addResolvedAction(nd -> {
            finalpe.setParameter((Parameter) nd);
        });

        spaceSystem.addUnresolvedReference(nr);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_LOCATION_IN_CONTAINER_IN_BITS)) {
                readLocationInContainerInBits(parameterEntry);
            } else if (isStartElementWithName(ELEM_REPEAT_ENTRY)) {
                Repeat r = readRepeatEntry(spaceSystem);
                parameterEntry.setRepeatEntry(r);
            } else if (isStartElementWithName(ELEM_INCLUDE_CONDITION)) {
                parameterEntry.setIncludeCondition(readMatchCriteria(spaceSystem, null));
            } else if (isEndElementWithName(ELEM_PARAMETER_REF_ENTRY)) {
                return parameterEntry;
            } else {
                logUnknown();
            }
        }
    }

    private ContainerEntry readContainerRefEntry(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_CONTAINER_REF_ENTRY);
        checkStartElementPreconditions();

        String refName = readMandatoryAttribute("containerRef", xmlEvent.asStartElement());

        SequenceEntry.ReferenceLocationType locationType = SequenceEntry.ReferenceLocationType.PREVIOUS_ENTRY; // default
        SequenceContainer container = spaceSystem.getSequenceContainer(refName);
        ContainerEntry containerEntry = null;
        if (container != null) {
            containerEntry = new ContainerEntry(0, locationType, container);
        } else {
            containerEntry = new ContainerEntry(0, locationType);
            final ContainerEntry finalce = containerEntry;
            NameReference nr = new NameReference(refName, Type.SEQUENCE_CONTAINER).addResolvedAction(nd -> {
                finalce.setRefContainer((SequenceContainer) nd);
            });
            spaceSystem.addUnresolvedReference(nr);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_LOCATION_IN_CONTAINER_IN_BITS)) {
                readLocationInContainerInBits(containerEntry);
            } else if (isStartElementWithName(ELEM_REPEAT_ENTRY)) {
                Repeat r = readRepeatEntry(spaceSystem);
                containerEntry.setRepeatEntry(r);
            } else if (isStartElementWithName(ELEM_INCLUDE_CONDITION)) {
                containerEntry.setIncludeCondition(readMatchCriteria(spaceSystem, null));
            } else if (isEndElementWithName(ELEM_CONTAINER_REF_ENTRY)) {
                return containerEntry;
            } else {
                logUnknown();
            }
        }
    }

    private Repeat readRepeatEntry(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_REPEAT_ENTRY);
        Repeat r = null;
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_COUNT)) {
                r = new Repeat(readIntegerValue(spaceSystem));
            } else if (isEndElementWithName(ELEM_REPEAT_ENTRY)) {
                return r;
            }
        }
    }

    private IntegerValue readIntegerValue(SpaceSystem spaceSystem) throws XMLStreamException {
        String tag = xmlEvent.asStartElement().getName().getLocalPart();

        checkStartElementPreconditions();
        IntegerValue v = null;

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(ELEM_FIXED_VALUE)) {
                v = new FixedIntegerValue(readIntegerValue());
            } else if (isStartElementWithName(ELEM_DYNAMIC_VALUE)) {
                v = readDynamicValue(spaceSystem);
            } else if (isEndElementWithName(tag)) {
                return v;
            } else {
                logUnknown();
            }
        }
    }

    private DynamicIntegerValue readDynamicValue(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_DYNAMIC_VALUE);

        checkStartElementPreconditions();
        DynamicIntegerValue v = null;

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(ELEM_PARAMETER_INSTANCE_REF)) {
                String paramRef = readMandatoryAttribute(ATTR_PARAMETER_REF, xmlEvent.asStartElement());
                if (YAMCS_IGNORE.equals(paramRef)) {
                    v = IGNORED_DYNAMIC_VALUE;
                } else {
                    v = new DynamicIntegerValue(readParameterInstanceRef(spaceSystem, null));
                }
            } else if (isStartElementWithName(ELEM_ARGUMENT_INSTANCE_REF)) {
                v = new DynamicIntegerValue(readArgumentInstanceRef(spaceSystem, null));
            } else if (isStartElementWithName(ELEM_LINEAR_ADJUSTMENT)) {
                if (v == null) {
                    throw new XMLStreamException(ELEM_PARAMETER_INSTANCE_REF + " or " + ELEM_ARGUMENT_INSTANCE_REF
                            + " has to be specified before " + ELEM_LINEAR_ADJUSTMENT);
                }
                LinearAdjusment ladj = readLinearAdjusment();
                v.setIntercept((long) ladj.getIntercept());
                v.setSlope((long) ladj.getSlope());
            } else if (isEndElementWithName(ELEM_DYNAMIC_VALUE)) {
                if (v == null) {
                    throw new XMLStreamException("No " + ELEM_PARAMETER_INSTANCE_REF + " section found");
                }
                return v;
            } else {
                logUnknown();
            }
        }
    }

    private LinearAdjusment readLinearAdjusment() throws XMLStreamException {
        log.trace(ELEM_LINEAR_ADJUSTMENT);
        StartElement startElement = checkStartElementPreconditions();
        double intercept = readDoubleAttribute("intercept", startElement, 0.0);
        double slope = readDoubleAttribute("slope", startElement, 1.0);
        return new LinearAdjusment(intercept, slope);
    }

    // if resolveCf is not null, it will be called when the parameter reference has been resolved
    private ParameterInstanceRef readParameterInstanceRef(SpaceSystem spaceSystem,
            CompletableFuture<ParameterInstanceRef> resolvedCf)
            throws XMLStreamException {
        log.trace(ELEM_PARAMETER_INSTANCE_REF);

        StartElement startElement = checkStartElementPreconditions();

        String paramRef = readMandatoryAttribute(ATTR_PARAMETER_REF, startElement);
        boolean useCalibrated = readBooleanAttribute("useCalibratedValue", startElement, true);
        int instance = readIntAttribute("instance", startElement, 0);

        final ParameterInstanceRef instanceRef = new ParameterInstanceRef(useCalibrated);
        instanceRef.setInstance(instance);
        instanceRef.setRelativeTo(InstanceRelativeTo.PACKET_START_WITHIN_PACKET);
        makeParameterReference(spaceSystem, paramRef, (para, path) -> {
            instanceRef.setParameter(para);
            instanceRef.setMemberPath(path);

            if (resolvedCf != null) {
                resolvedCf.complete(instanceRef);
            }
        });

        return instanceRef;
    }

    private ArgumentInstanceRef readArgumentInstanceRef(SpaceSystem spaceSystem, MetaCommand metaCmd)
            throws XMLStreamException {

        StartElement startElement = checkStartElementPreconditions();
        String argRef;
        if (ELEM_PARAMETER_INSTANCE_REF.equals(startElement.getName().getLocalPart())
                || ELEM_INPUT_PARAMETER_INSTANCE_REF.equals(startElement.getName().getLocalPart())) {
            // special case: treat /yamcs/cmd/arg/<argName> as argument reference
            // because XTCE does not allow argument references in command verifiers and transmission checks
            argRef = readMandatoryAttribute(ATTR_PARAMETER_REF, startElement)
                    .substring(YAMCS_CMDARG_SPACESYSTEM_NAME.length() + 1);
        } else {
            argRef = readMandatoryAttribute("argumentRef", startElement);
        }
        boolean useCalibrated = readBooleanAttribute("useCalibratedValue", startElement, true);

        ArgumentInstanceRef argInstRef = new ArgumentInstanceRef();
        // If we are not in the context of a metacommand, such as an argument
        // that is used as the size of another argument type, then do not try
        // to resolve the argument. Instead, the name alone will be used to
        // look up the argument in context when needed.
        if (metaCmd == null) {
            argInstRef.setArgument(new Argument(argRef));
        } else {
            ArgumentReference ref = ArgumentReference.getReference(metaCmd,
                    argRef);

            ref.addResolvedAction((arg, path) -> {
                argInstRef.setArgument(arg);
                argInstRef.setMemberPath(path);
                return true;
            });

            if (!ref.isResolved()) {
                spaceSystem.addUnresolvedReference(ref);
            }
        }

        argInstRef.setUseCalibratedValue(useCalibrated);

        return argInstRef;
    }

    // if resolveCf is not null, it will be called when the parameter reference has been resolved
    private ParameterInstanceRef readParameterRef(SpaceSystem spaceSystem,
            CompletableFuture<ParameterInstanceRef> resolvedCf)
            throws XMLStreamException {
        log.trace(ELEM_PARAMETER_REF);

        String paramRef = readMandatoryAttribute(ATTR_PARAMETER_REF, xmlEvent.asStartElement());
        final ParameterInstanceRef instanceRef = new ParameterInstanceRef();

        makeParameterReference(spaceSystem, paramRef, (para, path) -> {
            instanceRef.setParameter(para);
            instanceRef.setMemberPath(path);

            if (resolvedCf != null) {
                resolvedCf.complete(instanceRef);
            }
        });
        return instanceRef;
    }

    private void readLocationInContainerInBits(SequenceEntry entry) throws XMLStreamException {
        log.trace(ELEM_LOCATION_IN_CONTAINER_IN_BITS);
        checkStartElementPreconditions();

        int locationInContainerInBits = 0;

        ReferenceLocationType location;
        String value = readAttribute("referenceLocation", xmlEvent.asStartElement(), "previousEntry");
        if (value.equalsIgnoreCase("previousEntry")) {
            location = ReferenceLocationType.PREVIOUS_ENTRY;
        } else if (value.equalsIgnoreCase("containerStart")) {
            location = ReferenceLocationType.CONTAINER_START;
        } else {
            throw new XMLStreamException("Currently unsupported reference location: " + value);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_FIXED_VALUE)) {
                locationInContainerInBits = readIntegerValue();
            } else if (isStartElementWithName(ELEM_DYNAMIC_VALUE)) {
                skipXtceSection(ELEM_DYNAMIC_VALUE);
            } else if (isStartElementWithName(ELEM_DISCRETE_LOOKUP_LIST)) {
                skipXtceSection(ELEM_DISCRETE_LOOKUP_LIST);
            } else if (isEndElementWithName(ELEM_LOCATION_IN_CONTAINER_IN_BITS)) {
                entry.setLocation(location, locationInContainerInBits);
                return;
            } else {
                logUnknown();
            }
        }
    }

    private Comparison readComparison(SpaceSystem spaceSystem, MetaCommand metaCmd) throws XMLStreamException {
        log.trace(ELEM_COMPARISON);
        checkStartElementPreconditions();

        String ref = readMandatoryAttribute(ATTR_PARAMETER_REF, xmlEvent.asStartElement());

        String value = readAttribute("comparisonOperator", xmlEvent.asStartElement(), null);
        if (value == null) {
            value = "=="; // default value
        }

        OperatorType optype = OperatorType.fromSymbol(value);

        String theValue;
        theValue = readMandatoryAttribute("value", xmlEvent.asStartElement());

        boolean useCalibratedValue = readBooleanAttribute("useCalibratedValue", xmlEvent.asStartElement(), true);
        Comparison comparison;

        if (ref.startsWith(YAMCS_CMDARG_SPACESYSTEM_NAME)) {
            if (metaCmd == null) {
                throw new XtceLoadException(fileName, xmlEvent.getLocation(),
                        "Cannot use reference to command arguments in comparisons not linked to commands");
            }
            String argName = ref.substring(YAMCS_CMDARG_SPACESYSTEM_NAME.length() + 1);
            Argument arg = metaCmd.getArgument(argName);
            if (arg == null) {
                throw new XtceLoadException(fileName, xmlEvent.getLocation(),
                        "No argument named '" + argName + "' for command" + metaCmd.getName());
            }
            ArgumentInstanceRef instanceRef = new ArgumentInstanceRef(arg);
            comparison = new Comparison(instanceRef, theValue, optype);
        } else {
            final ParameterInstanceRef instanceRef = new ParameterInstanceRef(useCalibratedValue);
            comparison = new Comparison(instanceRef, theValue, optype);
            makeParameterReference(spaceSystem, ref, (p, path) -> {
                instanceRef.setParameter(p);
                instanceRef.setMemberPath(path);
                if (!(p instanceof SystemParameter)) {
                    comparison.validateValueType();
                } // else cannot validate system parameters because they don't have a type
            });
        }
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isEndElementWithName(ELEM_COMPARISON)) {
                return comparison;
            } else {
                logUnknown();
            }
        }
    }

    /**
     * Just skips the whole section.
     * 
     * @return
     * @throws IllegalStateException
     * @throws XMLStreamException
     */
    private XtceNotImplemented readMessageSet() throws IllegalStateException, XMLStreamException {
        skipXtceSection(ELEM_MESSAGE_SET);
        return null;
    }

    /**
     * Just skips the whole section.
     * 
     * @return
     * @throws IllegalStateException
     * @throws XMLStreamException
     */
    private XtceNotImplemented readStreamSet() throws IllegalStateException, XMLStreamException {
        skipXtceSection(ELEM_STREAM_SET);
        return null;
    }

    /**
     * Just skips the whole section.
     * 
     * @param spaceSystem
     * 
     * @return
     * @throws IllegalStateException
     * @throws XMLStreamException
     */
    private void readAlgorithmSet(SpaceSystem spaceSystem) throws IllegalStateException, XMLStreamException {
        log.trace(ELEM_ALGORITHM_SET);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            Algorithm algo = null;
            if (isStartElementWithName(ELEM_MATH_ALGORITHM)) {
                algo = readMathAlgorithm(spaceSystem);
            } else if (isStartElementWithName(ELEM_CUSTOM_ALGORITHM)) {
                algo = readCustomAlgorithm(spaceSystem, null);
            } else if (isEndElementWithName(ELEM_ALGORITHM_SET)) {
                return;
            }
            if (algo != null) {
                spaceSystem.addAlgorithm(algo);
            }
        }
    }

    private MathAlgorithm readMathAlgorithm(SpaceSystem spaceSystem) throws IllegalStateException, XMLStreamException {
        checkStartElementPreconditions();

        String value = readMandatoryAttribute(ATTR_NAME, xmlEvent.asStartElement());

        MathAlgorithm algo = new MathAlgorithm(value);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_MATH_OPERATION)) {
                readMathOperation(spaceSystem, algo);
            }
            if (isNamedItemProperty()) {
                readNamedItemProperty(algo);
            } else if (isEndElementWithName(ELEM_MATH_ALGORITHM)) {
                return algo;
            } else {
                logUnknown();
            }
        }
    }

    /**
     * Extraction of the TelemetryMetaData section
     * 
     * @param spaceSystem
     * 
     * @throws XMLStreamException
     */
    private void readCommandMetaData(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_COMMAND_MEATA_DATA);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_PARAMETER_TYPE_SET)) {
                readParameterTypeSet(spaceSystem);
            } else if (isStartElementWithName(ELEM_PARAMETER_SET)) {
                readParameterSet(spaceSystem);
            } else if (isStartElementWithName(ELEM_ARGUMENT_TYPE_SET)) {
                readArgumentTypeSet(spaceSystem);
            } else if (isStartElementWithName(ELEM_META_COMMAND_SET)) {
                readMetaCommandSet(spaceSystem);
            } else if (isStartElementWithName(ELEM_COMMAND_CONTAINER_SET)) {
                readCommandContainerSet(spaceSystem);
            } else if (isStartElementWithName(ELEM_MESSAGE_SET)) {
                readMessageSet();
            } else if (isStartElementWithName(ELEM_ALGORITHM_SET)) {
                readAlgorithmSet(spaceSystem);
            } else if (isEndElementWithName(ELEM_COMMAND_MEATA_DATA)) {
                return;
            } else {
                logUnknown();
            }
        }
    }

    /**
     * @param spaceSystem
     * @throws XMLStreamException
     */
    private void readArgumentTypeSet(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_ARGUMENT_TYPE_SET);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            IncompleteType incompleteType = null;

            if (isStartElementWithName(ELEM_BOOLEAN_ARGUMENT_TYPE)) {
                incompleteType = readBooleanArgumentType(spaceSystem);
            } else if (isStartElementWithName(ELEM_ENUMERATED_ARGUMENT_TYPE)) {
                incompleteType = readEnumeratedArgumentType(spaceSystem);
            } else if (isStartElementWithName(ELEM_FLOAT_ARGUMENT_TYPE)) {
                incompleteType = readFloatArgumentType(spaceSystem);
            } else if (isStartElementWithName(ELEM_INTEGER_ARGUMENT_TYPE)) {
                incompleteType = readIntegerArgumentType(spaceSystem);
            } else if (isStartElementWithName(ELEM_BINARY_ARGUMENT_TYPE)) {
                incompleteType = readBinaryArgumentType(spaceSystem);
            } else if (isStartElementWithName(ELEM_STRING_ARGUMENT_TYPE)) {
                incompleteType = readStringArgumentType(spaceSystem);
            } else if (isStartElementWithName(ELEM_AGGREGATE_ARGUMENT_TYPE)) {
                incompleteType = readAggregateArgumentType(spaceSystem);
            } else if (isStartElementWithName(ELEM_ABSOLUTE_TIME_ARGUMENT_TYPE)) {
                incompleteType = readAbsoluteTimeArgumentType(spaceSystem);
            } else if (isStartElementWithName(ELEM_ARRAY_ARGUMENT_TYPE)) {
                incompleteType = readArrayArgumentType(spaceSystem);
            } else {
                logUnknown();
            }

            if (incompleteType != null) {
                incompleteType.scheduleCompletion();
            }

            if (isEndElementWithName(ELEM_ARGUMENT_TYPE_SET)) {
                return;
            } else {
                logUnknown();
            }
        }
    }

    private IncompleteType readBooleanArgumentType(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_BOOLEAN_ARGUMENT_TYPE);

        StartElement element = checkStartElementPreconditions();

        BooleanArgumentType.Builder typeBuilder = new BooleanArgumentType.Builder();
        IncompleteType incompleteType = new IncompleteType(spaceSystem, typeBuilder);

        readArgumentBaseTypeAttributes(spaceSystem, element, incompleteType);

        String initialValue = readAttribute(ATTR_INITIAL_VALUE, xmlEvent.asStartElement(), null);
        if (initialValue != null) {
            typeBuilder.setInitialValue(initialValue);
        }

        typeBuilder.setOneStringValue(readAttribute("oneStringValue", element, "True"));
        typeBuilder.setZeroStringValue(readAttribute("zeroStringValue", element, "False"));

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (readBaseTypeProperties(typeBuilder)) {
                continue;
            } else if (isStartElementWithName(ELEM_INTEGER_DATA_ENCODING)) {
                typeBuilder.setEncoding(readIntegerDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_FLOAT_DATA_ENCODING)) {
                typeBuilder.setEncoding(readFloatDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_STRING_DATA_ENCODING)) {
                typeBuilder.setEncoding(readStringDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_BINARY_DATA_ENCODING)) {
                typeBuilder.setEncoding(readBinaryDataEncoding(spaceSystem));
            } else if (isEndElementWithName(ELEM_BOOLEAN_ARGUMENT_TYPE)) {
                return incompleteType;
            } else {
                logUnknown();
            }
        }
    }

    private IncompleteType readFloatArgumentType(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_FLOAT_ARGUMENT_TYPE);
        StartElement element = checkStartElementPreconditions();

        FloatArgumentType.Builder typeBuilder = new FloatArgumentType.Builder();
        IncompleteType incompleteType = new IncompleteType(spaceSystem, typeBuilder);

        readArgumentBaseTypeAttributes(spaceSystem, element, incompleteType);

        String value = readAttribute(ATTR_SIZE_IN_BITS, xmlEvent.asStartElement(), null);

        if (value != null) {
            int sizeInBits = Integer.parseInt(value);
            if (sizeInBits != 32 && sizeInBits != 64) {
                throw new XMLStreamException("Float encoding " + sizeInBits + " not supported;"
                        + " Only 32 and 64 bits are supported", xmlEvent.getLocation());
            }
            typeBuilder.setSizeInBits(sizeInBits);
        }

        String initialValue = readAttribute(ATTR_INITIAL_VALUE, xmlEvent.asStartElement(), null);
        if (initialValue != null) {
            typeBuilder.setInitialValue(initialValue);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (readBaseTypeProperties(typeBuilder)) {
                continue;
            } else if (isStartElementWithName(ELEM_INTEGER_DATA_ENCODING)) {
                typeBuilder.setEncoding(readIntegerDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_FLOAT_DATA_ENCODING)) {
                typeBuilder.setEncoding(readFloatDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_STRING_DATA_ENCODING)) {
                typeBuilder.setEncoding(readStringDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_BINARY_DATA_ENCODING)) {
                throw new XMLStreamException("Encoding " + xmlEvent.asStartElement().getName().getLocalPart()
                        + " not supported for float argument", xmlEvent.getLocation());
            } else if (isStartElementWithName(ELEM_VALID_RANGE)) {
                typeBuilder.setValidRange(readFloatValidRange());
            } else if (isStartElementWithName(ELEM_VALID_RANGE_SET)) {
                typeBuilder.setValidRange(readFloatValidRangeSet());
            } else if (isStartElementWithName(ELEM_TO_STRING)) {
                typeBuilder.setNumberFormat(readNumberFormat());
            } else if (isEndElementWithName(ELEM_FLOAT_ARGUMENT_TYPE)) {
                return incompleteType;
            } else {
                logUnknown();
            }
        }
    }

    private IncompleteType readEnumeratedArgumentType(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_ENUMERATED_ARGUMENT_TYPE);
        StartElement element = checkStartElementPreconditions();

        EnumeratedArgumentType.Builder typeBuilder = new EnumeratedArgumentType.Builder();
        IncompleteType incompleteType = new IncompleteType(spaceSystem, typeBuilder);

        readArgumentBaseTypeAttributes(spaceSystem, element, incompleteType);

        String initialValue = readAttribute(ATTR_INITIAL_VALUE, xmlEvent.asStartElement(), null);
        if (initialValue != null) {
            typeBuilder.setInitialValue(initialValue);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (readBaseTypeProperties(typeBuilder)) {
                continue;
            } else if (isStartElementWithName(ELEM_INTEGER_DATA_ENCODING)) {
                typeBuilder.setEncoding(readIntegerDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_FLOAT_DATA_ENCODING)) {
                typeBuilder.setEncoding(readFloatDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_STRING_DATA_ENCODING)) {
                typeBuilder.setEncoding(readStringDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_BINARY_DATA_ENCODING)) {
                typeBuilder.setEncoding(readBinaryDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_ENUMERATION_LIST)) {
                readEnumerationList(typeBuilder);
            } else if (isEndElementWithName(ELEM_ENUMERATED_ARGUMENT_TYPE)) {
                return incompleteType;
            }
        }
    }

    private IncompleteType readAggregateArgumentType(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_AGGREGATE_ARGUMENT_TYPE);

        StartElement element = checkStartElementPreconditions();

        AggregateArgumentType.Builder typeBuilder = new AggregateArgumentType.Builder();
        IncompleteType incompleteType = new IncompleteType(spaceSystem, typeBuilder);

        String name = readMandatoryAttribute(ATTR_NAME, element);
        typeBuilder.setName(name);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(ELEM_MEMBER_LIST)) {
                typeBuilder.addMembers(readMemberList(spaceSystem, false));
            } else if (isEndElementWithName(ELEM_AGGREGATE_ARGUMENT_TYPE)) {
                return incompleteType;
            } else {
                logUnknown();
            }
        }
    }

    private IncompleteType readIntegerArgumentType(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_INTEGER_ARGUMENT_TYPE);
        StartElement element = checkStartElementPreconditions();

        IntegerArgumentType.Builder typeBuilder = new IntegerArgumentType.Builder();
        IncompleteType incompleteType = new IncompleteType(spaceSystem, typeBuilder);

        readArgumentBaseTypeAttributes(spaceSystem, element, incompleteType);

        int sizeInBits = readIntAttribute(ATTR_SIZE_IN_BITS, xmlEvent.asStartElement(), 32);
        typeBuilder.setSizeInBits(sizeInBits);

        String value = readAttribute("signed", xmlEvent.asStartElement(), null);
        if (value != null) {
            boolean signed = Boolean.parseBoolean(value);
            typeBuilder.setSigned(signed);
        }

        String initialValue = readAttribute(ATTR_INITIAL_VALUE, xmlEvent.asStartElement(), null);
        if (initialValue != null) {
            typeBuilder.setInitialValue(initialValue);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (readBaseTypeProperties(typeBuilder)) {
                continue;
            } else if (isStartElementWithName(ELEM_INTEGER_DATA_ENCODING)) {
                typeBuilder.setEncoding(readIntegerDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_FLOAT_DATA_ENCODING)) {
                typeBuilder.setEncoding(readFloatDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_STRING_DATA_ENCODING)) {
                typeBuilder.setEncoding(readStringDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_BINARY_DATA_ENCODING)) {
                throw new XMLStreamException("Cannot use a binary data encoding for integer arguments");
            } else if (isStartElementWithName(ELEM_VALID_RANGE)) {// XTCE 1.1
                typeBuilder.setValidRange(readIntegerValidRange(typeBuilder.isSigned()));
            } else if (isStartElementWithName(ELEM_VALID_RANGE_SET)) {// XTCE 1.2
                typeBuilder.setValidRange(readIntegerValidRangeSet(typeBuilder.isSigned()));
            } else if (isEndElementWithName(ELEM_INTEGER_ARGUMENT_TYPE)) {
                return incompleteType;
            } else {
                logUnknown();
            }
        }
    }

    private IncompleteType readBinaryArgumentType(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_BINARY_ARGUMENT_TYPE);

        StartElement element = checkStartElementPreconditions();

        BinaryArgumentType.Builder typeBuilder = new BinaryArgumentType.Builder();
        IncompleteType incompleteType = new IncompleteType(spaceSystem, typeBuilder);

        readArgumentBaseTypeAttributes(spaceSystem, element, incompleteType);

        String initialValue = readAttribute(ATTR_INITIAL_VALUE, xmlEvent.asStartElement(), null);
        if (initialValue != null) {
            typeBuilder.setInitialValue(initialValue);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (readBaseTypeProperties(typeBuilder)) {
                continue;
            } else if (isStartElementWithName(ELEM_BINARY_DATA_ENCODING)) {
                typeBuilder.setEncoding(readBinaryDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_STRING_DATA_ENCODING)) {
                typeBuilder.setEncoding(readStringDataEncoding(spaceSystem));
            } else if (isEndElementWithName(ELEM_BINARY_ARGUMENT_TYPE)) {
                return incompleteType;
            } else {
                logUnknown();
            }
        }
    }

    private IncompleteType readStringArgumentType(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_STRING_ARGUMENT_TYPE);

        StartElement element = checkStartElementPreconditions();

        StringArgumentType.Builder typeBuilder = new StringArgumentType.Builder();
        IncompleteType incompleteType = new IncompleteType(spaceSystem, typeBuilder);

        readArgumentBaseTypeAttributes(spaceSystem, element, incompleteType);

        String initialValue = readAttribute(ATTR_INITIAL_VALUE, xmlEvent.asStartElement(), null);
        if (initialValue != null) {
            typeBuilder.setInitialValue(initialValue);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (readBaseTypeProperties(typeBuilder)) {
                continue;
            } else if (isStartElementWithName(ELEM_STRING_DATA_ENCODING)) {
                typeBuilder.setEncoding(readStringDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_BINARY_DATA_ENCODING)) {
                typeBuilder.setEncoding(readBinaryDataEncoding(spaceSystem));
            } else if (isStartElementWithName(ELEM_SIZE_RANGE_IN_CHARACTERS)) {
                typeBuilder.setSizeRangeInCharacters(readIntegerRange(false));
            } else if (isStartElementWithName(ELEM_CONTEXT_ALARM_LIST)) {
                skipXtceSection(ELEM_CONTEXT_ALARM_LIST);
            } else if (isStartElementWithName(ELEM_INTEGER_DATA_ENCODING)
                    || isStartElementWithName(ELEM_FLOAT_DATA_ENCODING)) {
                throw new XMLStreamException("Encoding " + xmlEvent.asStartElement().getName().getLocalPart()
                        + " not supported for string argument", xmlEvent.getLocation());
            } else if (isEndElementWithName(ELEM_STRING_ARGUMENT_TYPE)) {
                return incompleteType;
            } else {
                logUnknown();
            }
        }
    }

    private IncompleteType readAbsoluteTimeArgumentType(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_ABSOLUTE_TIME_ARGUMENT_TYPE);
        StartElement element = checkStartElementPreconditions();

        AbsoluteTimeArgumentType.Builder typeBuilder = new AbsoluteTimeArgumentType.Builder();
        IncompleteType incompleteType = new IncompleteType(spaceSystem, typeBuilder);

        readArgumentBaseTypeAttributes(spaceSystem, element, incompleteType);

        String initialValue = readAttribute(ATTR_INITIAL_VALUE, xmlEvent.asStartElement(), null);
        if (initialValue != null) {
            typeBuilder.setInitialValue(initialValue);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (readBaseTypeProperties(typeBuilder)) {
                continue;
            } else if (isStartElementWithName(ELEM_REFERENCE_TIME)) {
                typeBuilder.setReferenceTime(readReferenceTime(spaceSystem));
            } else if (isStartElementWithName(ELEM_ENCODING)) {
                readEncoding(spaceSystem, typeBuilder);
            } else if (isEndElementWithName(ELEM_ABSOLUTE_TIME_ARGUMENT_TYPE)) {

                if (typeBuilder.getReferenceTime() == null) {
                    if (!(typeBuilder.getEncoding() instanceof BinaryDataEncoding.Builder bdb)
                            || bdb.getToBinaryTransformAlgorithm() == null) {
                        throw new XMLStreamException("AbsoluteTimeArgumentType without a reference time not supported "
                                + "(except if it used a BinaryDataEncoding with an algorithm which could produce directly an absolute time)",
                                xmlEvent.getLocation());
                    }
                }
                return incompleteType;
            } else {
                logUnknown();
            }
        }
    }

    private IncompleteType readArrayArgumentType(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_ARRAY_ARGUMENT_TYPE);
        StartElement element = checkStartElementPreconditions();

        ArrayArgumentType.Builder typeBuilder = new ArrayArgumentType.Builder();
        IncompleteType incompleteType = new IncompleteType(spaceSystem, typeBuilder);
        typeBuilder.setName(readMandatoryAttribute(ATTR_NAME, element));
        typeBuilder.setShortDescription(readAttribute(ATTR_SHORT_DESCRIPTION, element, null));

        String refName = readMandatoryAttribute("arrayTypeRef", xmlEvent.asStartElement());
        NameReference nr = new NameReference(refName, Type.ARGUMENT_TYPE).addResolvedAction(nd -> {
            typeBuilder.setElementType((ArgumentType) nd);
        });
        incompleteType.addReference(nr);
        spaceSystem.addUnresolvedReference(nr);

        String initialValue = readAttribute(ATTR_INITIAL_VALUE, xmlEvent.asStartElement(), null);
        if (initialValue != null) {
            typeBuilder.setInitialValue(initialValue);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (readNamedItemProperty(typeBuilder)) {
                continue;
            } else if (isStartElementWithName(ELEM_DIMENSION_LIST)) {
                List<IntegerValue> dimList = readDimensionList(spaceSystem);
                typeBuilder.setSize(dimList);
            } else if (isEndElementWithName(ELEM_ARRAY_ARGUMENT_TYPE)) {
                if (typeBuilder.getSize() == null) {
                    throw new XMLStreamException(ELEM_DIMENSION_LIST + " not defined for the ArrayArgumentType "
                            + typeBuilder.getName());
                }
                return incompleteType;
            } else {
                logUnknown();
            }
        }
    }

    /**
     * Reads the definition of the command containers
     * 
     * @param spaceSystem
     * 
     * @return
     * @throws IllegalStateException
     */
    private void readCommandContainerSet(SpaceSystem spaceSystem) throws XMLStreamException {
        skipXtceSection(ELEM_COMMAND_CONTAINER_SET);
    }

    /**
     * Reads the definition of the metacommand containers
     * 
     * @param spaceSystem
     * 
     * @return
     * @throws IllegalStateException
     */
    private void readMetaCommandSet(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_META_COMMAND_SET);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_META_COMMAND)) {
                MetaCommand mc = readMetaCommand(spaceSystem);
                if (excludedContainers.contains(mc.getName())) {
                    log.debug("Not adding '{}' to the SpaceSystem because excluded by configuration", mc.getName());
                } else {
                    spaceSystem.addMetaCommand(mc);
                }
            } else if (isEndElementWithName(ELEM_META_COMMAND_SET)) {
                return;
            }
        }
    }

    private MetaCommand readMetaCommand(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_META_COMMAND);
        checkStartElementPreconditions();

        MetaCommand mc = null;

        String value = readMandatoryAttribute(ATTR_NAME, xmlEvent.asStartElement());
        mc = new MetaCommand(value);
        mc.setAbstract(readBooleanAttribute("abstract", xmlEvent.asStartElement(), false));
        value = readAttribute(ATTR_SHORT_DESCRIPTION, xmlEvent.asStartElement(), null);
        if (value != null) {
            mc.setShortDescription(value);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isNamedItemProperty()) {
                readNamedItemProperty(mc);
            } else if (isStartElementWithName(ELEM_BASE_META_COMMAND)) {
                readBaseMetaCommand(spaceSystem, mc);
            } else if (isStartElementWithName(ELEM_COMMAND_CONTAINER)) {
                CommandContainer cc = readCommandContainer(spaceSystem, mc);
                mc.setCommandContainer(cc);
                spaceSystem.addCommandContainer(cc);
            } else if (isStartElementWithName(ELEM_ARGUMENT_LIST)) {
                readArgumentList(spaceSystem, mc);
            } else if (isStartElementWithName(ELEM_VERIFIER_SET)) {
                readVerifierSet(spaceSystem, mc);
            } else if (isStartElementWithName(ELEM_DEFAULT_SIGNIFICANCE)) {
                mc.setDefaultSignificance(readSignificance(spaceSystem));
            } else if (isStartElementWithName(ELEM_TRANSMISSION_CONSTRAINT_LIST)) {
                readTransmissionConstraintList(spaceSystem, mc);
            } else if (isEndElementWithName(ELEM_META_COMMAND)) {
                return mc;
            } else {
                logUnknown();
            }
        }
    }

    private void readBaseMetaCommand(SpaceSystem spaceSystem, MetaCommand mc) throws XMLStreamException {
        log.trace(ELEM_BASE_META_COMMAND);
        checkStartElementPreconditions();

        String refName = readMandatoryAttribute("metaCommandRef", xmlEvent.asStartElement());

        if (refName != null) {
            if (excludedContainers.contains(refName)) {
                log.debug("adding {} to the list of the excluded containers because its parent is excluded",
                        mc.getName());
                excludedContainers.add(mc.getName());
            } else {
                // find base container in the set of already defined containers
                MetaCommand baseContainer = spaceSystem.getMetaCommand(refName);
                if (baseContainer != null) {
                    mc.setBaseMetaCommand(baseContainer);
                } else { // must come from somewhere else
                    final MetaCommand finalmc = mc;
                    NameReference nr = new NameReference(refName, Type.META_COMMAND).addResolvedAction(nd -> {
                        finalmc.setBaseMetaCommand((MetaCommand) nd);
                    });
                    spaceSystem.addUnresolvedReference(nr);
                }
            }

            while (true) {
                xmlEvent = xmlEventReader.nextEvent();

                if (isStartElementWithName(ELEM_ARGUMENT_ASSIGNMENT_LIST)) {
                    readArgumentAssignmentList(spaceSystem, mc);
                } else if (isEndElementWithName(ELEM_BASE_META_COMMAND)) {
                    return;
                } else {
                    logUnknown();
                }
            }
        }
    }

    private void readArgumentAssignmentList(SpaceSystem spaceSystem, MetaCommand mc) throws XMLStreamException {
        log.trace(ELEM_ARGUMENT_ASSIGNMENT_LIST);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_ARGUMENT_ASSIGNMENT)) {
                ArgumentAssignment aa = readArgumentAssignment(spaceSystem);
                mc.addArgumentAssignment(aa);
            } else if (isEndElementWithName(ELEM_ARGUMENT_ASSIGNMENT_LIST)) {
                return;
            }
        }

    }

    private ArgumentAssignment readArgumentAssignment(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_ARGUMENT_ASSIGNMENT);
        checkStartElementPreconditions();

        StartElement element = xmlEvent.asStartElement();
        String argumentName = readMandatoryAttribute("argumentName", element);
        String argumentValue = readMandatoryAttribute("argumentValue", element);
        skipToTheEnd(ELEM_ARGUMENT_ASSIGNMENT);
        return new ArgumentAssignment(argumentName, argumentValue);
    }

    private void readArgumentList(SpaceSystem spaceSystem, MetaCommand mc) throws XMLStreamException {
        log.trace(ELEM_ARGUMENT_LIST);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_ARGUMENT)) {
                Argument arg = readArgument(spaceSystem);
                mc.addArgument(arg);
            } else if (isEndElementWithName(ELEM_ARGUMENT_LIST)) {
                return;
            }
        }
    }

    /**
     * 
     * @return Parameter instance
     * @throws IllegalStateException
     * @throws XMLStreamException
     */
    private Argument readArgument(SpaceSystem spaceSystem) throws IllegalStateException, XMLStreamException {
        log.trace(ELEM_ARGUMENT);
        checkStartElementPreconditions();

        Argument arg = null;

        StartElement element = xmlEvent.asStartElement();
        String name = readMandatoryAttribute(ATTR_NAME, element);
        arg = new Argument(name);

        String initialValue = readAttribute(ATTR_INITIAL_VALUE, element, null);

        String argumentTypeRef = readMandatoryAttribute("argumentTypeRef", element);

        final Argument arg1 = arg;
        NameReference nr = new NameReference(argumentTypeRef, Type.ARGUMENT_TYPE)
                .addResolvedAction(nd -> {
                    ArgumentType atype1 = (ArgumentType) nd;
                    if (initialValue != null) {
                        arg1.setInitialValue(atype1.convertType(initialValue));
                    }
                    arg1.setArgumentType(atype1);
                });
        spaceSystem.addUnresolvedReference(nr);

        arg.setShortDescription(readAttribute(ATTR_SHORT_DESCRIPTION, element, null));

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isNamedItemProperty()) {
                readNamedItemProperty(arg);
            } else if (isEndElementWithName(ELEM_ARGUMENT)) {
                return arg;
            } else {
                logUnknown();
            }
        }
    }

    private void readVerifierSet(SpaceSystem spaceSystem, MetaCommand mc) throws XMLStreamException {
        log.trace(ELEM_VERIFIER_SET);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (xmlEvent.isStartElement() && xmlEvent.asStartElement().getName().getLocalPart().endsWith("Verifier")) {
                CommandVerifier cmdVerifier = readVerifier(spaceSystem, mc);
                if (cmdVerifier != null) {
                    mc.addVerifier(cmdVerifier);
                }
            } else if (isEndElementWithName(ELEM_VERIFIER_SET)) {
                return;
            } else {
                logUnknown();
            }
        }
    }

    private CommandVerifier readVerifier(SpaceSystem spaceSystem, MetaCommand metaCmd) throws XMLStreamException {
        StartElement element = checkStartElementPreconditions();

        String tag = element.getName().getLocalPart();
        String stage = readAttribute(ATTR_NAME, element, null);
        String type = tag.substring(0, tag.length() - 8);// strip the "Verifier" suffix;
        if (stage == null) {
            stage = type;
        }
        List<AncillaryData> ancillaryData = Collections.emptyList();
        CommandVerifier cmdVerifier = null;
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(ELEM_CONTAINER_REF)) {
                cmdVerifier = new CommandVerifier(CommandVerifier.Type.CONTAINER, stage);
                readContainerRef(spaceSystem, cmdVerifier);
            } else if (isStartElementWithName(ELEM_CUSTOM_ALGORITHM)) {
                cmdVerifier = new CommandVerifier(CommandVerifier.Type.ALGORITHM, stage);
                CustomAlgorithm algo = readCustomAlgorithm(spaceSystem, metaCmd);
                algo.setScope(Scope.COMMAND_VERIFICATION);
                cmdVerifier.setAlgorithm(algo);
            } else if (isStartElementWithName(ELEM_COMPARISON)) {
                cmdVerifier = new CommandVerifier(CommandVerifier.Type.MATCH_CRITERIA, stage);
                MatchCriteria matchCriteria = readComparison(spaceSystem, metaCmd);
                cmdVerifier.setMatchCriteria(matchCriteria);
            } else if (isStartElementWithName(ELEM_COMPARISON_LIST)) {
                cmdVerifier = new CommandVerifier(CommandVerifier.Type.MATCH_CRITERIA, stage);
                MatchCriteria matchCriteria = readComparisonList(spaceSystem, metaCmd);
                cmdVerifier.setMatchCriteria(matchCriteria);
            } else if (isStartElementWithName(ELEM_BOOLEAN_EXPRESSION)) {
                cmdVerifier = new CommandVerifier(CommandVerifier.Type.MATCH_CRITERIA, stage);
                MatchCriteria matchCriteria = readBooleanExpression(spaceSystem, metaCmd);
                cmdVerifier.setMatchCriteria(matchCriteria);
            } else if (isStartElementWithName(ELEM_PARAMETER_VALUE_CHANGE)) {
                cmdVerifier = new CommandVerifier(CommandVerifier.Type.PARAMETER_VALUE_CHANGE, stage);
                ParameterValueChange paraValueChange = readParameterValueChange(spaceSystem);
                cmdVerifier.setParameterValueChange(paraValueChange);
            } else if (isStartElementWithName(ELEM_CHECK_WINDOW)) {
                if (cmdVerifier == null) {
                    throw new XMLStreamException("CheckWindow specified before the verifier.", xmlEvent.getLocation());
                }
                CheckWindow cw = readCheckWindow(spaceSystem);
                cmdVerifier.setCheckWindow(cw);
            } else if (isStartElementWithName(ELEM_ANCILLARY_DATA_SET)) {
                ancillaryData = readAncillaryDataSet();
            } else if (isStartElementWithName(ELEM_RETURN_PARAM_REF)) {
                if (cmdVerifier == null) {
                    throw new XMLStreamException("ReturnParmRef specified before the verifier.",
                            xmlEvent.getLocation());
                }
                CommandVerifier cmdVerifier1 = cmdVerifier;
                String paramRef = readMandatoryAttribute(ATTR_PARAMETER_REF, xmlEvent.asStartElement());
                makeParameterReference(spaceSystem, paramRef, (param, path) -> {
                    cmdVerifier1.setReturnParameter(param);
                });
            } else if (isEndElementWithName(tag)) {
                if (cmdVerifier != null) {
                    if (cmdVerifier.getCheckWindow() == null) {
                        throw new XMLStreamException("No CheckWindow specified for command verifier",
                                xmlEvent.getLocation());
                    }
                    TerminationAction onSuccess = null;
                    TerminationAction onFail = null;
                    TerminationAction onTimeout = null;
                    if ("Failed".equals(type)) {
                        onSuccess = TerminationAction.FAIL;
                    } else if ("Complete".equals(type)) {
                        onSuccess = TerminationAction.SUCCESS;
                        onFail = TerminationAction.FAIL;
                    } else {
                        onFail = TerminationAction.FAIL;
                    }

                    // Allow overriding the default behaviour through
                    // ancillary data elements
                    for (var item : ancillaryData) {
                        if (item.getName().equals("yamcs.onSuccess")) {
                            if (item.getValue() != null) {
                                onSuccess = TerminationAction.valueOf(item.getValue());
                            } else {
                                onSuccess = null;
                            }
                        } else if (item.getName().equals("yamcs.onFail")) {
                            if (item.getValue() != null) {
                                onFail = TerminationAction.valueOf(item.getValue());
                            } else {
                                onFail = null;
                            }
                        } else if (item.getName().equals("yamcs.onTimeout")) {
                            if (item.getValue() != null) {
                                onTimeout = TerminationAction.valueOf(item.getValue());
                            } else {
                                onTimeout = null;
                            }
                        }
                    }

                    cmdVerifier.setOnSuccess(onSuccess);
                    cmdVerifier.setOnFail(onFail);
                    cmdVerifier.setOnTimeout(onTimeout);
                }
                return cmdVerifier;
            } else {
                logUnknown();
            }
        }
    }

    private void readContainerRef(SpaceSystem spaceSystem, CommandVerifier cmdVerifier)
            throws XMLStreamException {
        String refName = readMandatoryAttribute("containerRef", xmlEvent.asStartElement());
        SequenceContainer container = spaceSystem.getSequenceContainer(refName);
        if (container != null) {
            cmdVerifier.setContainerRef(container);
        } else { // must come from somewhere else
            NameReference nr = new NameReference(refName, Type.SEQUENCE_CONTAINER)
                    .addResolvedAction(nd -> {
                        cmdVerifier.setContainerRef((SequenceContainer) nd);
                    });
            spaceSystem.addUnresolvedReference(nr);
        }
        xmlEvent = xmlEventReader.nextEvent();
        if (!isEndElementWithName(ELEM_CONTAINER_REF)) {
            throw new IllegalStateException(ELEM_CONTAINER_REF + " end element expected");
        }
    }

    private CheckWindow readCheckWindow(SpaceSystem spaceSystem)
            throws XMLStreamException {
        StartElement element = xmlEvent.asStartElement();
        String v = readAttribute("timeToStartChecking", element, null);
        long timeToStartChecking = v == null ? -1 : parseDuration(v);

        long timeToStopChecking = parseDuration(readMandatoryAttribute("timeToStopChecking", element));

        v = readAttribute("timeWindowIsRelativeTo", element, "timeLastVerifierPassed");
        try {
            CheckWindow.TimeWindowIsRelativeToType timeWindowIsRelativeTo = TimeWindowIsRelativeToType.fromXtce(v);
            return new CheckWindow(timeToStartChecking, timeToStopChecking, timeWindowIsRelativeTo);
        } catch (IllegalArgumentException e) {
            throw new XMLStreamException("Invalid value '" + v + "' for timeWindowIsRelativeTo");
        }
    }

    long parseDuration(String v) {
        Duration d;
        try {
            d = DatatypeFactory.newInstance().newDuration(v);
        } catch (DatatypeConfigurationException e) {
            throw new Error(e);
        }
        return d.getTimeInMillis(new Date());
    }

    private CommandContainer readCommandContainer(SpaceSystem spaceSystem, MetaCommand mc)
            throws XMLStreamException {
        log.trace(ELEM_COMMAND_CONTAINER);
        checkStartElementPreconditions();

        CommandContainer cmdContainer = null;

        String value = readMandatoryAttribute(ATTR_NAME, xmlEvent.asStartElement());
        cmdContainer = new CommandContainer(value);

        value = readAttribute(ATTR_SHORT_DESCRIPTION, xmlEvent.asStartElement(), null);
        if (value != null) {
            cmdContainer.setShortDescription(value);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isNamedItemProperty()) {
                readNamedItemProperty(cmdContainer);
            } else if (isStartElementWithName(ELEM_ENTRY_LIST)) {
                readEntryList(spaceSystem, cmdContainer, mc);
            } else if (isStartElementWithName(ELEM_BASE_CONTAINER)) {
                readBaseContainer(spaceSystem, cmdContainer);
            } else if (isStartElementWithName(ELEM_DEFAULT_RATE_IN_STREAM)) {
                cmdContainer.setRateInStream(readRateInStream(spaceSystem));
            } else if (isStartElementWithName(ELEM_BINARY_ENCODING)) {
                BinaryDataEncoding.Builder bde = readBinaryDataEncoding(spaceSystem);
                cmdContainer.setSizeInBits(bde.getSizeInBits());
            } else if (isEndElementWithName(ELEM_COMMAND_CONTAINER)) {
                return cmdContainer;
            } else {
                logUnknown();
            }
        }
    }

    private void readBaseContainer(SpaceSystem spaceSystem, CommandContainer mcContainer)
            throws IllegalStateException, XMLStreamException {
        log.trace(ELEM_BASE_CONTAINER);
        checkStartElementPreconditions();

        String refName = readMandatoryAttribute("containerRef", xmlEvent.asStartElement());
        if (excludedContainers.contains(refName)) {
            log.debug("adding {} to the list of the excluded containers because its parent is excluded",
                    mcContainer.getName());
            excludedContainers.add(mcContainer.getName());
        } else {
            // find base container in the set of already defined containers
            CommandContainer baseContainer = spaceSystem.getCommandContainer(refName);
            if (baseContainer != null) {
                mcContainer.setBaseContainer(baseContainer);
            } else { // must come from somewhere else
                final CommandContainer finalsc = mcContainer;
                NameReference nr = new NameReference(refName, Type.COMMAND_CONTAINER)
                        .addResolvedAction(nd -> {
                            finalsc.setBaseContainer((Container) nd);
                        });
                spaceSystem.addUnresolvedReference(nr);
            }
        }
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_RESTRICTION_CRITERIA)) {
                MatchCriteria criteria = readMatchCriteria(spaceSystem, null);
                mcContainer.setRestrictionCriteria(criteria);
            } else if (isEndElementWithName(ELEM_BASE_CONTAINER)) {
                return;
            } else {
                logUnknown();
            }
        }
    }

    private ArgumentEntry readArgumentRefEntry(SpaceSystem spaceSystem, MetaCommand mc) throws XMLStreamException {
        log.trace(ELEM_ARGUMENT_REF_ENTRY);
        checkStartElementPreconditions();

        String refName = readMandatoryAttribute("argumentRef", xmlEvent.asStartElement());

        Argument arg = mc.getArgument(refName);
        ArgumentEntry argumentEntry = null;
        if (arg == null) {
            throw new XMLStreamException("Undefined argument reference '" + refName + "'", xmlEvent.getLocation());
        }
        argumentEntry = new ArgumentEntry(arg);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_LOCATION_IN_CONTAINER_IN_BITS)) {
                readLocationInContainerInBits(argumentEntry);
            } else if (isStartElementWithName(ELEM_REPEAT_ENTRY)) {
                Repeat r = readRepeatEntry(spaceSystem);
                argumentEntry.setRepeatEntry(r);
            } else if (isStartElementWithName(ELEM_INCLUDE_CONDITION)) {
                skipXtceSection(ELEM_INCLUDE_CONDITION);
            } else if (isEndElementWithName(ELEM_ARGUMENT_REF_ENTRY)) {
                return argumentEntry;
            } else {
                logUnknown();
            }
        }
    }

    private FixedValueEntry readFixedValueEntry(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_FIXED_VALUE_ENTRY);
        checkStartElementPreconditions();
        StartElement startElement = xmlEvent.asStartElement();

        String name = readAttribute(ATTR_NAME, startElement, null);

        String value = readMandatoryAttribute("binaryValue", startElement);
        byte[] binaryValue = HexUtils.unhex(value);

        int sizeInBits = readIntAttribute(ATTR_SIZE_IN_BITS, startElement, binaryValue.length * 8);

        FixedValueEntry fixedValueEntry = new FixedValueEntry(name, binaryValue, sizeInBits);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_LOCATION_IN_CONTAINER_IN_BITS)) {
                readLocationInContainerInBits(fixedValueEntry);
            } else if (isStartElementWithName(ELEM_REPEAT_ENTRY)) {
                Repeat r = readRepeatEntry(spaceSystem);
                fixedValueEntry.setRepeatEntry(r);
            } else if (isStartElementWithName(ELEM_INCLUDE_CONDITION)) {
                skipXtceSection(ELEM_INCLUDE_CONDITION);
            } else if (isEndElementWithName(ELEM_FIXED_VALUE_ENTRY)) {
                return fixedValueEntry;
            } else {
                logUnknown();
            }
        }
    }

    /**
     * builds a custom algorithm
     * <p>
     * If metaCmd is not null, the algorithm is used as part of a command verifier and can have command arguments and
     * command history parameters as inputs
     */
    private CustomAlgorithm readCustomAlgorithm(SpaceSystem spaceSystem, MetaCommand metaCmd)
            throws XMLStreamException {
        checkStartElementPreconditions();
        StartElement startElement = xmlEvent.asStartElement();
        String tag = startElement.getName().getLocalPart();

        String name = readMandatoryAttribute(ATTR_NAME, startElement);

        CustomAlgorithm algo = new CustomAlgorithm(name);
        algo.setShortDescription(readAttribute(ATTR_SHORT_DESCRIPTION, startElement, null));

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isNamedItemProperty()) {
                readNamedItemProperty(algo);
            } else if (isStartElementWithName(ELEM_ALGORITHM_TEXT)) {
                readCustomAlgorithmText(algo);
            } else if (isStartElementWithName(ELEM_TRIGGER_SET)) {
                algo.setTriggerSet(readTriggerSet(spaceSystem));
            } else if (isStartElementWithName(ELEM_OUTPUT_SET)) {
                algo.setOutputSet(readOutputSet(spaceSystem));
            } else if (isStartElementWithName(ELEM_INPUT_SET)) {
                addInputSet(spaceSystem, algo, metaCmd);
            } else if (isEndElementWithName(tag)) {
                return algo;
            } else {
                logUnknown();
            }
        }
    }

    private void readCustomAlgorithmText(CustomAlgorithm algo) throws XMLStreamException {
        checkStartElementPreconditions();
        StartElement startElement = xmlEvent.asStartElement();
        String tag = startElement.getName().getLocalPart();

        String language = readMandatoryAttribute("language", startElement);
        if (!"JavaScript".equals(language) && !"python".equals(language)
                && !"java".equalsIgnoreCase(language)
                && !"java-expression".equalsIgnoreCase(language)) {
            throw new XtceLoadException(fileName, xmlEvent.getLocation(), "Invalid algorithm language '" + language
                    + "'. Supported are 'JavaScript', 'python', 'java' and 'java-expression'");
        }
        algo.setLanguage(language);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (xmlEvent.isCharacters()) {
                algo.setAlgorithmText(xmlEvent.asCharacters().getData());
            } else if (isEndElementWithName(tag)) {
                return;
            }
        }
    }

    private void addInputSet(SpaceSystem spaceSystem, CustomAlgorithm algo, MetaCommand metaCmd)
            throws XMLStreamException {
        checkStartElementPreconditions();
        StartElement startElement = xmlEvent.asStartElement();
        String tag = startElement.getName().getLocalPart();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_INPUT_PARAMETER_INSTANCE_REF)) {
                addInputParameterInstanceRef(spaceSystem, algo, metaCmd);
            } else if (isStartElementWithName(ELEM_INPUT_ARGUMENT_INSTANCE_REF)) {
                if (metaCmd == null) {
                    throw new XtceLoadException(fileName, xmlEvent.getLocation(),
                            "Argument references can only be used in algorithms related to commands");
                }

                addInputArgumentInstanceRef(spaceSystem, algo, metaCmd);
            } else if (isStartElementWithName(ELEM_CONSTANT)) {
                throw new XMLStreamException("Constant input parameters not supported", xmlEvent.getLocation());
            } else if (isEndElementWithName(tag)) {
                return;
            } else {
                logUnknown();
            }
        }
    }

    private void addInputParameterInstanceRef(SpaceSystem spaceSystem, CustomAlgorithm algo, MetaCommand metaCmd)
            throws XMLStreamException {
        log.trace(ELEM_INPUT_PARAMETER_INSTANCE_REF);
        String paramRef = readMandatoryAttribute(ATTR_PARAMETER_REF, xmlEvent.asStartElement());

        String inputName = readAttribute("inputName", xmlEvent.asStartElement(), null);

        InputParameter inputParameter;

        if (paramRef.startsWith(YAMCS_CMDARG_SPACESYSTEM_NAME)) {
            ArgumentInstanceRef argRef = readArgumentInstanceRef(spaceSystem, metaCmd);
            inputParameter = new InputParameter(argRef, inputName);
        } else {
            ParameterInstanceRef instanceRef = readParameterInstanceRef(spaceSystem, null);
            instanceRef.setRelativeTo(InstanceRelativeTo.PACKET_START_ACROSS_PACKETS);
            inputParameter = new InputParameter(instanceRef, inputName);
        }

        List<AncillaryData> adlist = algo.getAncillaryData();
        if (adlist != null) {
            boolean mandatory = algo.getAncillaryData().stream()
                    .anyMatch(ad -> AncillaryData.KEY_ALGO_MANDATORY_INPUT.equalsIgnoreCase(ad.getName())
                            && Objects.equals(ad.getValue(), inputName));
            inputParameter.setMandatory(mandatory);
        }
        algo.addInput(inputParameter);
    }

    private void addInputArgumentInstanceRef(SpaceSystem spaceSystem, CustomAlgorithm algo, MetaCommand metaCmd)
            throws XMLStreamException {
        log.trace(ELEM_INPUT_ARGUMENT_INSTANCE_REF);
        String inputName = readAttribute("inputName", xmlEvent.asStartElement(), null);
        ArgumentInstanceRef argRef = readArgumentInstanceRef(spaceSystem, metaCmd);
        InputParameter inputParameter = new InputParameter(argRef, inputName);
        algo.addInput(inputParameter);
    }

    private List<OutputParameter> readOutputSet(SpaceSystem spaceSystem) throws XMLStreamException {
        checkStartElementPreconditions();
        StartElement startElement = xmlEvent.asStartElement();
        String tag = startElement.getName().getLocalPart();

        List<OutputParameter> result = new ArrayList<>();
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_OUTPUT_PARAMETER_REF)) {
                result.add(readOutputParameterRef(spaceSystem));
            } else if (isEndElementWithName(tag)) {
                return result;
            }
        }
    }

    private Significance readSignificance(SpaceSystem spaceSystem) throws IllegalStateException,
            XMLStreamException {
        log.trace(ELEM_DEFAULT_SIGNIFICANCE);
        checkStartElementPreconditions();

        String reason = readAttribute("reasonForWarning", xmlEvent.asStartElement(), null);

        String conseq = readMandatoryAttribute("consequenceLevel", xmlEvent.asStartElement());
        Levels clevel;
        try {
            clevel = Levels.fromString(conseq.toLowerCase());
        } catch (IllegalArgumentException e) {
            String allowedValues = Arrays.stream(Levels.values()).map(l -> l.xtceAlias())
                    .collect(Collectors.joining(", "));
            throw new XMLStreamException(
                    "Invalid consequence level '" + conseq + "'; allowed values: [" + allowedValues + "]");
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isEndElementWithName(ELEM_DEFAULT_SIGNIFICANCE)) {
                return new Significance(clevel, reason);
            } else {
                logUnknown();
            }
        }
    }

    private void readTransmissionConstraintList(SpaceSystem spaceSystem, MetaCommand metaCmd)
            throws XMLStreamException {
        log.trace(ELEM_TRANSMISSION_CONSTRAINT_LIST);
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(ELEM_TRANSMISSION_CONSTRAINT)) {
                TransmissionConstraint trc = readTransmissionConstraint(spaceSystem, metaCmd);
                metaCmd.addTransmissionConstrain(trc);
            } else if (isEndElementWithName(ELEM_TRANSMISSION_CONSTRAINT_LIST)) {
                return;
            }
        }
    }

    TransmissionConstraint readTransmissionConstraint(SpaceSystem spaceSystem, MetaCommand metaCmd)
            throws XMLStreamException {
        log.trace(ELEM_TRANSMISSION_CONSTRAINT);
        StartElement element = xmlEvent.asStartElement();
        String timeouts = readAttribute("timeOut", element, null);
        long timeout = timeouts == null ? 0 : parseDuration(timeouts);
        MatchCriteria matchCriteria = readMatchCriteria(spaceSystem, metaCmd);
        return new TransmissionConstraint(matchCriteria, timeout);
    }

    private OutputParameter readOutputParameterRef(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(ELEM_OUTPUT_PARAMETER_REF);
        String paramRef = readMandatoryAttribute(ATTR_PARAMETER_REF, xmlEvent.asStartElement());
        String outputName = readAttribute("outputName", xmlEvent.asStartElement(), null);

        OutputParameter outp = new OutputParameter();
        outp.setOutputName(outputName);

        NameReference nr = new NameReference(paramRef, Type.PARAMETER).addResolvedAction(nd -> {
            outp.setParameter((Parameter) nd);
        });
        spaceSystem.addUnresolvedReference(nr);

        return outp;
    }

    /**
     * Increase the skip statistics for the section.
     * 
     * @param xtceSectionName
     *            Name of the skipped section.
     */
    private void addToSkipStatistics(String xtceSectionName) {
        Integer count = xtceSkipStatistics.get(xtceSectionName);
        if (count == null) {
            xtceSkipStatistics.put(xtceSectionName, Integer.valueOf(1));
        } else {
            xtceSkipStatistics.put(xtceSectionName, count + 1);
        }
    }

    /**
     * Write statistics.
     */
    public void writeStatistics() {

        log.info("------------------");
        log.info("Statistics of skipped elements: ");
        for (Map.Entry<String, Integer> entry : xtceSkipStatistics.entrySet()) {
            log.info(">> {} : {} ", entry.getKey(), entry.getValue());
        }
        log.info("------------------");
    }

    /**
     * Skips whole section in the document.
     * 
     * @param sectionName
     *            name of the section to skip
     * @throws XMLStreamException
     * @throws IllegalStateException
     *             Exception on algorithm error
     */
    private void skipXtceSection(String sectionName) throws XMLStreamException {
        log.trace(sectionName);
        checkStartElementPreconditions();

        addToSkipStatistics(sectionName);

        while (true) {
            // just skip whole section, read events until the
            // end element of the section occurs
            try {
                xmlEvent = xmlEventReader.nextEvent();

                if (isStartElementWithName(sectionName)) {
                    // skip wrapped sections with the same name
                    skipXtceSection(sectionName);
                } else if (isEndElementWithName(sectionName)) {
                    log.info("Section <{}> skipped", sectionName);
                    return;
                }
            } catch (NoSuchElementException e) {
                throw new XMLStreamException("End of section unreachable: " + sectionName, xmlEvent.getLocation());
            }
        }
    }

    private void skipToTheEnd(String sectionName) throws XMLStreamException, IllegalStateException {
        log.trace(sectionName);
        checkStartElementPreconditions();
        while (true) {
            // just skip whole section, read events until the
            // end element of the section occurs
            try {
                xmlEvent = xmlEventReader.nextEvent();

                if (isStartElementWithName(sectionName)) {
                    skipXtceSection(sectionName);
                } else if (isEndElementWithName(sectionName)) {
                    return;
                }
            } catch (NoSuchElementException e) {
                throw new XMLStreamException("End of section unreachable: " + sectionName, xmlEvent.getLocation());
            }
        }
    }

    private boolean isNamedItemProperty() {
        if (xmlEvent.getEventType() != XMLStreamConstants.START_ELEMENT) {
            return false;
        }
        String name = xmlEvent.asStartElement().getName().getLocalPart();
        return ELEM_ALIAS_SET.equals(name) || ELEM_LONG_DESCRIPTION.equals(name)
                || ELEM_ANCILLARY_DATA_SET.equals(name);
    }

    void readNamedItemProperty(NameDescription nd) throws XMLStreamException {
        if (isStartElementWithName(ELEM_ALIAS_SET)) {
            nd.setAliasSet(readAliasSet());
        } else if (isStartElementWithName(ELEM_ANCILLARY_DATA_SET)) {
            nd.setAncillaryData(readAncillaryDataSet());
        } else if (isStartElementWithName(ELEM_LONG_DESCRIPTION)) {
            nd.setLongDescription(readStringBetweenTags(ELEM_LONG_DESCRIPTION));
        }
    }

    boolean readNamedItemProperty(NameDescription.Builder<?> nd) throws XMLStreamException {
        if (isStartElementWithName(ELEM_ALIAS_SET)) {
            nd.setAliasSet(readAliasSet());
        } else if (isStartElementWithName(ELEM_ANCILLARY_DATA_SET)) {
            nd.setAncillaryData(readAncillaryDataSet());
        } else if (isStartElementWithName(ELEM_LONG_DESCRIPTION)) {
            nd.setLongDescription(readStringBetweenTags(ELEM_LONG_DESCRIPTION));
        } else {
            return false;
        }
        return true;
    }

    public void setExcludedContainers(Set<String> excludedContainers) {
        this.excludedContainers = excludedContainers;
    }

    private void logUnknown() {
        if (xmlEvent.isStartElement()) {
            StartElement element = xmlEvent.asStartElement();
            log.warn("Skipping unknown tag {} at {}:{}", element.getName().getLocalPart(),
                    element.getLocation().getLineNumber(), element.getLocation().getColumnNumber());
        }
    }

    public static ParameterReference getParameterReference(SpaceSystem spaceSystem, String paramName) {
        ParameterReference paraRef = new ParameterReference(paramName);
        spaceSystem.addUnresolvedReference(paraRef);

        return paraRef;
    }

    public static void makeParameterReference(SpaceSystem spaceSystem, String paramRef,
            ParameterResolvedAction action) {

        getParameterReference(spaceSystem, paramRef).addResolvedAction(action);
    }

    void throwException(Location location, String message) {
        throw new XtceLoadException(fileName, location, message);
    }
}

/**
 * 
 */
package org.yamcs.xtce.xml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.utils.DoubleRange;
import org.yamcs.utils.StringConverter;
import org.yamcs.xtce.AbsoluteTimeParameterType;
import org.yamcs.xtce.AggregateArgumentType;
import org.yamcs.xtce.AggregateParameterType;
import org.yamcs.xtce.AlarmLevels;
import org.yamcs.xtce.AlarmRanges;
import org.yamcs.xtce.AlarmType;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.ArgumentEntry;
import org.yamcs.xtce.ArgumentType;
import org.yamcs.xtce.ArrayParameterEntry;
import org.yamcs.xtce.ArrayParameterType;
import org.yamcs.xtce.BinaryArgumentType;
import org.yamcs.xtce.BinaryDataEncoding;
import org.yamcs.xtce.BinaryParameterType;
import org.yamcs.xtce.BooleanArgumentType;
import org.yamcs.xtce.BooleanParameterType;
import org.yamcs.xtce.Calibrator;
import org.yamcs.xtce.Comparison;
import org.yamcs.xtce.ComparisonList;
import org.yamcs.xtce.Container;
import org.yamcs.xtce.ContainerEntry;
import org.yamcs.xtce.ContextCalibrator;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.DynamicIntegerValue;
import org.yamcs.xtce.EnumeratedArgumentType;
import org.yamcs.xtce.EnumeratedDataType;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.EnumerationAlarm;
import org.yamcs.xtce.EnumerationContextAlarm;
import org.yamcs.xtce.FixedIntegerValue;
import org.yamcs.xtce.FixedValueEntry;
import org.yamcs.xtce.FloatArgumentType;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.FloatDataEncoding.Encoding;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.IntegerValue;
import org.yamcs.xtce.MatchCriteria;
import org.yamcs.xtce.MathAlgorithm;
import org.yamcs.xtce.MathOperation;
import org.yamcs.xtce.MathOperationCalibrator;
import org.yamcs.xtce.MathOperator;
import org.yamcs.xtce.Member;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.CommandContainer;
import org.yamcs.xtce.util.UnresolvedNameReference;
import org.yamcs.xtce.util.UnresolvedParameterReference;
import org.yamcs.xtce.Repeat;
import org.yamcs.xtce.UnitType;
import org.yamcs.xtce.NumericAlarm;
import org.yamcs.xtce.NumericContextAlarm;
import org.yamcs.xtce.OnParameterUpdateTrigger;
import org.yamcs.xtce.OnPeriodicRateTrigger;
import org.yamcs.xtce.OperatorType;
import org.yamcs.xtce.OutputParameter;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterEntry;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.PolynomialCalibrator;
import org.yamcs.xtce.RateInStream;
import org.yamcs.xtce.ReferenceTime;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SequenceEntry;
import org.yamcs.xtce.SequenceEntry.ReferenceLocationType;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.SplineCalibrator;
import org.yamcs.xtce.SplinePoint;
import org.yamcs.xtce.StringArgumentType;
import org.yamcs.xtce.StringDataEncoding;
import org.yamcs.xtce.StringDataEncoding.SizeType;
import org.yamcs.xtce.util.NameReference;
import org.yamcs.xtce.util.NameReference.Type;
import org.yamcs.xtce.StringParameterType;
import org.yamcs.xtce.TimeEpoch;
import org.yamcs.xtce.TriggerSetType;
import org.yamcs.xtce.TriggeredMathOperation;
import org.yamcs.xtce.ValueEnumerationRange;
import org.yamcs.xtce.Header;
import org.yamcs.xtce.InputParameter;
import org.yamcs.xtce.IntegerArgumentType;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartDocument;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

/**
 * This class reads the XTCE XML files. XML document is accessed with the use of
 * the Stax Iterator API.
 * 
 * @author mu
 * 
 */
public class XtceStaxReader {

    // XTCE Schema defined tags, to minimize the mistyping errors

    private static final String XTCE_RelativeTimeParameterType = "RelativeTimeParameterType";
    private static final String XTCE_ARRAY_PARAMETER_TYPE = "ArrayParameterType";
    private static final String XTCE_AGGREGATE_PARAMETER_TYPE = "AggregateParameterType";
    private static final String XTCE_AGGREGATE_ARGUMENT_TYPE = "AggregateArgumentType";

    private static final String XTCE_AuthorSet = "AuthorSet";
    private static final String XTCE_NoteSet = "NoteSet";
    private static final String XTCE_HistorySet = "HistorySet";

    private static final String XTCE_TELEMTRY_META_DATA = "TelemetryMetaData";
    private static final String XTCE_PARAMETER_TYPE_SET = "ParameterTypeSet";
    private static final String XTCE_BOOLEAN_PARAMETER_TYPE = "BooleanParameterType";
    private static final String XTCE_ENUMERATED_PARAMETER_TYPE = "EnumeratedParameterType";
    private static final String XTCE_ENUMERATION_LIST = "EnumerationList";
    private static final String XTCE_ENUMERATION = "Enumeration";
    private static final String XTCE_RANGE_ENUMERATION = "RangeEnumeration";
    private static final String XTCE_STRING_PARAMETER_TYPE = "StringParameterType";
    private static final String XTCE_BINARY_PARAMETER_TYPE = "BinaryParameterType";
    private static final String XTCE_INTEGER_PARAMETER_TYPE = "IntegerParameterType";
    private static final String XTCE_FLOAT_PARAMETER_TYPE = "FloatParameterType";
    private static final String XTCE_SPACE_SYSTEM = "SpaceSystem";
    private static final String XTCE_ALIAS_SET = "AliasSet";
    private static final String XTCE_ALIAS = "Alias";
    private static final String XTCE_LONG_DESCRIPTION = "LongDescription";
    private static final String XTCE_HEADER = "Header";
    private static final String XTCE_ABSOLUTE_TIME_PARAMETER_TYPE = "AbsoluteTimeParameterType";
    private static final String XTCE_PARAMETER_SET = "ParameterSet";
    private static final String XTCE_PARAMETER = "Parameter";
    private static final String XTCE_PARAMETER_REF = "ParameterRef";
    private static final String XTCE_PARAMETER_PROPERTIES = "ParameterProperties";
    private static final String XTCE_VALIDITY_CONDITION = "ValidityCondition";
    private static final String XTCE_COMPARISON_LIST = "ComparisonList";
    private static final String XTCE_COMPARISON = "Comparison";
    private static final String XTCE_BOOLEAN_EXPRESSION = "BooleanExpression";
    private static final String XTCE_CUSTOM_ALGORITHM = "CustomAlgorithm";
    private static final String XTCE_MATH_ALGORITHM = "MathAlgorithm";
    private static final String XTCE_RESTRICTION_CRITERIA = "RestrictionCriteria";
    private static final String XTCE_SYSTEM_NAME = "SystemName";
    private static final String XTCE_PHYSICAL_ADDRESS_SET = "PhysicalAddressSet";
    private static final String XTCE_TIME_ASSOCIATION = "TimeAssociation";
    private static final String XTCE_CONTAINER_SET = "ContainerSet";
    private static final String XTCE_COMMAND_CONTAINER_SET = "CommandContainerSet";
    private static final String XTCE_BASE_CONTAINER = "BaseContainer";
    private static final String XTCE_MESSAGE_SET = "MessageSet";
    private static final String XTCE_STREAM_SET = "StreamSet";
    private static final String XTCE_ALGORITHM_SET = "AlgorithmSet";

    private static final String XTCE_COMMAND_MEATA_DATA = "CommandMetaData";
    private static final String XTCE_SEQUENCE_CONTAINER = "SequenceContainer";
    private static final String XTCE_ENTRY_LIST = "EntryList";
    private static final String XTCE_PARAMETER_REF_ENTRY = "ParameterRefEntry";

    private static final String XTCE_LOCATION_IN_CONTAINER_IN_BITS = "LocationInContainerInBits";
    private static final String XTCE_REPEAT_ENTRY = "RepeatEntry";
    private static final String XTCE_INCLUDE_CONDITION = "IncludeCondition";

    private static final String XTCE_PARAMETER_SEGMENT_REF_ENTRY = "ParameterSegmentRefEntry";
    private static final String XTCE_CONTAINER_REF_ENTRY = "ContainerRefEntry";
    private static final String XTCE_CONTAINER_SEGMENT_REF_ENTRY = "ContainerSegmentRefEntry";
    private static final String XTCE_STREAM_SEGMENT_ENTRY = "StreamSegmentEntry";
    private static final String XTCE_INDIRECT_PARAMETER_REF_ENTRY = "IndirectParameterRefEntry";
    private static final String XTCE_ARRAY_PARAMETER_REF_ENTRY = "ArrayParameterRefEntry";

    private static final String XTCE_UNIT_SET = "UnitSet";
    private static final String XTCE_UNIT = "Unit";
    private static final String XTCE_FLOAT_DATA_ENCODING = "FloatDataEncoding";
    private static final String XTCE_BINARY_DATA_ENCODING = "BinaryDataEncoding";
    private static final String XTCE_SIZE_IN_BITS = "SizeInBits";
    private static final String XTCE_FIXED_VALUE = "FixedValue";
    private static final String XTCE_DYNAMIC_VALUE = "DynamicValue";
    private static final String XTCE_DISCRETE_LOOKUP_LIST = "DiscreteLookupList";
    private static final String XTCE_INTEGER_DATA_ENCODING = "IntegerDataEncoding";
    private static final String XTCE_STRING_DATA_ENCODING = "StringDataEncoding";
    private static final String XTCE_CONTEXT_ALARM_LIST = "ContextAlarmList";
    private static final String XTCE_CONTEXT_ALARM = "ContextAlarm";
    private static final String XTCE_CONTEXT_MATCH = "ContextMatch";
    private static final String XTCE_DEFAULT_CALIBRATOR = "DefaultCalibrator";
    private static final String XTCE_CALIBRATOR = "Calibrator";
    private static final String XTCE_CONTEXT_CALIBRATOR = "ContextCalibrator";
    private static final String XTCE_CONTEXT_CALIBRATOR_LIST = "ContextCalibratorList";
    private static final String XTCE_SPLINE_CALIBRATOR = "SplineCalibrator";
    private static final String XTCE_POLYNOMIAL_CALIBRATOR = "PolynomialCalibrator";
    private static final String XTCE_MATH_OPERATION_CALIBRATOR = "MathOperationCalibrator";
    private static final String XTCE_TERM = "Term";
    private static final String XTCE_SPLINE_POINT = "SplinePoint";
    private static final String XTCE_COUNT = "Count";
    private static final String XTCE_PARAMETER_INSTANCE_REF = "ParameterInstanceRef";
    private static final String XTCE_STATIC_ALARM_RANGES = "StaticAlarmRanges";
    private static final String XTCE_DEFAULT_ALARM = "DefaultAlarm";
    private static final String XTCE_FIXED = "Fixed";
    private static final String XTCE_TERMINATION_CHAR = "TerminationChar";
    private static final String XTCE_LEADING_SIZE = "LeadingSize";
    private static final String XTCE_DEFAULT_RATE_IN_STREAM = "DefaultRateInStream";
    private static final String XTCE_REFERENCE_TIME = "ReferenceTime";
    private static final String XTCE_OFFSET_FROM = "OffsetFrom";
    private static final String XTCE_EPOCH = "Epoch";
    private static final String XTCE_ENCODING = "Encoding";
    private static final String XTCE_ARGUMENT_TYPE_SET = "ArgumentTypeSet";
    private static final String XTCE_META_COMMAND_SET = "MetaCommandSet";
    private static final String XTCE_META_COMMAND = "MetaCommand";
    private static final String XTCE_COMMAND_CONTAINER = "CommandContainer";
    private static final String XTCE_STRING_ARGUMENT_TYPE = "StringArgumentType";
    private static final String XTCE_BINARY_ARGUMENT_TYPE = "BinaryArgumentType";
    private static final String XTCE_INTEGER_ARGUMENT_TYPE = "IntegerArgumentType";
    private static final String XTCE_FLOAT_ARGUMENT_TYPE = "FloatArgumentType";
    private static final String XTCE_BOOLEAN_ARGUMENT_TYPE = "BooleanArgumentType";
    private static final String XTCE_ENUMERATED_ARGUMENT_TYPE = "EnumeratedArgumentType";
    private static final String XTCE_BASE_META_COMMAND = "BaseMetaCommand";
    private static final String XTCE_ARGUMENT_LIST = "ArgumentList";
    private static final String XTCE_ARGUMENT_ASSIGNMENT_LIST = "ArgumentAssignmentList";
    private static final String XTCE_ARGUMENT = "Argument";
    private static final String XTCE_ARGUMENT_REF_ENTRY = "ArgumentRefEntry";
    private static final String XTCE_ARRAY_ARGUMENT_REF_ENTRY = "ArrayArgumentRefEntry";
    private static final String XTCE_FIXED_VALUE_ENTRY = "FixedValueEntry";
    private static final String XTCE_VALUE_OPERAND = "ValueOperand";
    private static final String XTCE_MATH_OPERATION = "MathOperation";
    private static final String XTCE_TRIGGER_SET = "TriggerSet";
    private static final String XTCE_OUTPUT_SET = "OutputSet";
    private static final String XTCE_INPUT_SET = "InputSet";
    private static final String XTCE_INPUT_PARAMETER_INSTANCE_REF = "InputParameterInstanceRef";
    private static final String XTCE_CONSTANT = "Constant";
    private static final String XTCE_OUTPUT_PARAMETER_REF = "OutputParameterRef";
    private static final String XTCE_ALGORITHM_TEXT = "AlgorithmText";
    private static final String XTCE_ARGUMENT_ASSIGNMENT = "ArgumentAssignment";
    private static final String XTCE_MEMBER_LIST = "MemberList";
    private static final String XTCE_MEMBER = "Member";
    private static final String XTCE_DIMENSION_LIST = "DimensionList";
    private static final String XTCE_SIZE = "Size";
    private static final String XTCE_DIMENSION = "Dimension";
    private static final String XTCE_ANCILLARY_DATA_SET = "AncillaryDataSet";
    private static final String XTCE_VALID_RANGE = "ValidRange";
    private static final String XTCE_BINARY_ENCODING = "BinaryEncoding";

    /**
     * Logging subsystem
     */
    private static Logger log = LoggerFactory.getLogger(XtceStaxReader.class);

    /**
     * XML Event reader
     */
    private XMLEventReader xmlEventReader = null;

    /**
     * XML Event
     */
    private XMLEvent xmlEvent = null;

    /**
     * Statistics about the skipped sections. (good for overview about unimplemented features)
     */
    private Map<String, Integer> xtceSkipStatistics = new HashMap<String, Integer>();
    private Set<String> excludedContainers = new HashSet<String>();
    String fileName;

    /**
     * Constructor
     */
    public XtceStaxReader() {
    }

    /**
     * Reading of the XML XTCE file
     * 
     * @param fileName
     * 
     * @return returns the SpaceSystem read from the XML file
     * @throws XMLStreamException
     * @throws IOException
     * 
     */
    public SpaceSystem readXmlDocument(String fileName) throws XMLStreamException, IOException {
        this.fileName = fileName;
        log.info("Parsing XTCE file {}", fileName);
        xmlEventReader = initEventReader(fileName);
        xmlEvent = null;
        SpaceSystem spaceSystem = null;
        try {
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
                } else if (isStartElementWithName(XTCE_SPACE_SYSTEM)) {
                    SpaceSystem ss = readSpaceSystem();
                    spaceSystem.addSpaceSystem(ss);
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
                    this.xmlEventReader.close();
                    this.xmlEventReader = null;
                    throw new IllegalStateException("XML file parsing error");
                }
            }
            log.info("XTCE file parsing finished, loaded: {} parameters, {} tm containers, {} commands", 
                    spaceSystem.getParameterCount(true), spaceSystem.getSequenceContainerCount(true), spaceSystem.getMetaCommandCount(true));
        } catch (IllegalArgumentException e) {
            e.getCause().printStackTrace();
            throw new XMLStreamException(e.getMessage(), xmlEvent.getLocation());
        }
        return spaceSystem;
    }

    /**
     * Method called on start document event. Currently just logs the
     * information contained in the xml preamble of the parsed file.
     * 
     * @param start
     *            Start document event object
     */
    private void onStartDocument(StartDocument start) {
        log.trace("XML version='{} encoding: '{}'", start.getVersion(), start.getCharacterEncodingScheme());
    }

    /**
     * Start of reading at the root of the document. According to the XTCE
     * schema the root element is &lt;SpaceSystem&gt;
     * 
     * @throws XMLStreamException
     */
    private SpaceSystem readSpaceSystem() throws XMLStreamException {
        checkStartElementPreconditions();

        String value = readMandatoryAttribute("name", xmlEvent.asStartElement());
        SpaceSystem spaceSystem = new SpaceSystem(value);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_LONG_DESCRIPTION)) {
                spaceSystem.setLongDescription(readStringBetweenTags(XTCE_LONG_DESCRIPTION));
            } else if (isStartElementWithName(XTCE_ALIAS_SET)) {
                XtceAliasSet aliasSet = readAliasSet();
                spaceSystem.setAliasSet(aliasSet);
            } else if (isStartElementWithName(XTCE_HEADER)) {
                readHeader(spaceSystem);
            } else if (isStartElementWithName(XTCE_TELEMTRY_META_DATA)) {
                readTelemetryMetaData(spaceSystem);
            } else if (isStartElementWithName(XTCE_COMMAND_MEATA_DATA)) {
                readCommandMetaData(spaceSystem);
            } else if (isStartElementWithName(XTCE_SPACE_SYSTEM)) {
                SpaceSystem ss = readSpaceSystem();
                spaceSystem.addSpaceSystem(ss);
            } else if (isEndElementWithName(XTCE_SPACE_SYSTEM)) {
                return spaceSystem;
            } else {
                logUnknown();
            }
        }
    }

    /**
     * Action taken on end of the document event
     */
    private void onEndDocument() {
        try {
            log.trace("End of XML document");
            if (xmlEventReader != null) {
                xmlEventReader.close();
            }
        } catch (XMLStreamException e) {
            // exception thrown by the close method
            e.printStackTrace();
        } finally {
            xmlEventReader = null;
        }
    }

    /**
     * Extraction of the AliasSet section Current implementation does nothing,
     * just skips whole section
     * 
     * @return Set of aliases defined for the object
     * @throws XMLStreamException
     */
    private XtceAliasSet readAliasSet() throws XMLStreamException {
        log.trace(XTCE_ALIAS_SET);
        checkStartElementPreconditions();

        XtceAliasSet xtceAliasSet = new XtceAliasSet();

        while (true) {

            xmlEvent = xmlEventReader.nextEvent();

            // <Alias> sections
            if (isStartElementWithName(XTCE_ALIAS)) {
                readAlias(xtceAliasSet);
            } else if (isEndElementWithName(XTCE_ALIAS_SET)) {
                return xtceAliasSet;
            } else {
                logUnknown();
            }
        }
    }

    /**
     * Extraction of the AliasSet section Current implementation does nothing,
     * just skips whole section
     * 
     * @throws XMLStreamException
     */
    private void readAlias(XtceAliasSet aliasSet) throws XMLStreamException {
        log.trace(XTCE_ALIAS);
        checkStartElementPreconditions();

        String nameSpace = readMandatoryAttribute("nameSpace", xmlEvent.asStartElement());
        nameSpace = nameSpace.intern();
        String alias = readMandatoryAttribute("alias", xmlEvent.asStartElement());

        aliasSet.addAlias(nameSpace, alias);

        // read end element
        xmlEvent = xmlEventReader.nextEvent();
        if (!isEndElementWithName(XTCE_ALIAS)) {
            throw new IllegalStateException(XTCE_ALIAS + " end element expected");
        }
    }

    /**
     * Extraction of the Header section Current implementation does nothing,
     * just skips whole section
     * 
     * @param spaceSystem
     * 
     * @throws XMLStreamException
     */
    private void readHeader(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_HEADER);
        checkStartElementPreconditions();
        Header h = new Header();

        String value = readMandatoryAttribute("version", xmlEvent.asStartElement());
        h.setVersion(value);

        value = readMandatoryAttribute("date", xmlEvent.asStartElement());
        h.setDate(value);
        spaceSystem.setHeader(h);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_AuthorSet)) {
                skipXtceSection(XTCE_AuthorSet);
            } else if (isStartElementWithName(XTCE_NoteSet)) {
                skipXtceSection(XTCE_NoteSet);
            } else if (isStartElementWithName(XTCE_HistorySet)) {
                skipXtceSection(XTCE_HistorySet);
            } else if (isEndElementWithName(XTCE_HEADER)) {
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
        log.trace(XTCE_TELEMTRY_META_DATA);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_PARAMETER_TYPE_SET)) {
                readParameterTypeSet(spaceSystem);
            } else if (isStartElementWithName(XTCE_PARAMETER_SET)) {
                readParameterSet(spaceSystem);
            } else if (isStartElementWithName(XTCE_CONTAINER_SET)) {
                readContainerSet(spaceSystem);
            } else if (isStartElementWithName(XTCE_MESSAGE_SET)) {
                readMessageSet();
            } else if (isStartElementWithName(XTCE_STREAM_SET)) {
                readStreamSet();
            } else if (isStartElementWithName(XTCE_ALGORITHM_SET)) {
                readAlgorithmSet(spaceSystem);
            } else if (isEndElementWithName(XTCE_TELEMTRY_META_DATA)) {
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
        log.trace(XTCE_PARAMETER_TYPE_SET);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            ParameterType parameterType = null;

            if (isStartElementWithName(XTCE_BOOLEAN_PARAMETER_TYPE)) {
                parameterType = readBooleanParameterType(spaceSystem);
            } else if (isStartElementWithName(XTCE_ENUMERATED_PARAMETER_TYPE)) {
                parameterType = readEnumeratedParameterType(spaceSystem);
            } else if (isStartElementWithName(XTCE_FLOAT_PARAMETER_TYPE)) {
                parameterType = readFloatParameterType(spaceSystem);
            } else if (isStartElementWithName(XTCE_INTEGER_PARAMETER_TYPE)) {
                parameterType = readIntegerParameterType(spaceSystem);
            } else if (isStartElementWithName(XTCE_BINARY_PARAMETER_TYPE)) {
                parameterType = readBinaryParameterType(spaceSystem);
            } else if (isStartElementWithName(XTCE_STRING_PARAMETER_TYPE)) {
                parameterType = readStringParameterType(spaceSystem);
            } else if (isStartElementWithName(XTCE_RelativeTimeParameterType)) {
                parameterType = readRelativeTimeParameterType();
            } else if (isStartElementWithName(XTCE_ABSOLUTE_TIME_PARAMETER_TYPE)) {
                parameterType = readAbsoluteTimeParameterType(spaceSystem);
            } else if (isStartElementWithName(XTCE_ARRAY_PARAMETER_TYPE)) {
                parameterType = readArrayParameterType(spaceSystem);
            } else if (isStartElementWithName(XTCE_AGGREGATE_PARAMETER_TYPE)) {
                parameterType = readAggregateParameterType(spaceSystem);
            } else {
                logUnknown();
            }

            if (parameterType != null) {
                spaceSystem.addParameterType(parameterType);
            }

            if (isEndElementWithName(XTCE_PARAMETER_TYPE_SET)) {
                return;
            }
        }
    }

    private BooleanParameterType readBooleanParameterType(SpaceSystem spaceSystem)
            throws IllegalStateException, XMLStreamException {
        log.trace(XTCE_BOOLEAN_PARAMETER_TYPE);
        checkStartElementPreconditions();
        StartElement element = xmlEvent.asStartElement();
        // name attribute
        BooleanParameterType boolParamType = null;
        String name = readMandatoryAttribute("name", element);
        boolParamType = new BooleanParameterType(name);
        boolParamType.setOneStringValue(readAttribute("oneStringValue", element, "True"));
        boolParamType.setZeroStringValue(readAttribute("zeroStringValue", element, "False"));

        // read all parameters

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_LONG_DESCRIPTION)) {
                boolParamType.setLongDescription(readStringBetweenTags(XTCE_LONG_DESCRIPTION));
            } else if (isStartElementWithName(XTCE_UNIT_SET)) {
                boolParamType.addAllUnits(readUnitSet());
            } else if (isStartElementWithName(XTCE_INTEGER_DATA_ENCODING)) {
                boolParamType.setEncoding(readIntegerDataEncoding(spaceSystem));
            } else if (isStartElementWithName(XTCE_FLOAT_DATA_ENCODING)) {
                boolParamType.setEncoding(readFloatDataEncoding(spaceSystem));
            } else if (isStartElementWithName(XTCE_BINARY_DATA_ENCODING)) {
                boolParamType.setEncoding(readBinaryDataEncoding(spaceSystem));
            } else if (isStartElementWithName(XTCE_STRING_DATA_ENCODING)) {
                boolParamType.setEncoding(readStringDataEncoding(spaceSystem));
            } else if (isEndElementWithName(XTCE_BOOLEAN_PARAMETER_TYPE)) {
                return boolParamType;
            } else {
                logUnknown();
            }
        }
    }

    private ParameterType readAggregateParameterType(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_AGGREGATE_PARAMETER_TYPE);
        checkStartElementPreconditions();
        AggregateParameterType ptype = null;

        String name = readMandatoryAttribute("name", xmlEvent.asStartElement());
        ptype = new AggregateParameterType(name);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_MEMBER_LIST)) {
                ptype.addMembers(readMemberList(spaceSystem, true));
            } else if (isStartElementWithName(XTCE_LONG_DESCRIPTION)) {
                ptype.setLongDescription(readStringBetweenTags(XTCE_LONG_DESCRIPTION));
            } else if (isEndElementWithName(XTCE_AGGREGATE_PARAMETER_TYPE)) {
                return ptype;
            } else {
                logUnknown();
            }
        }
    }

    private List<Member> readMemberList(SpaceSystem spaceSystem, boolean paramOrAggreg) throws XMLStreamException {
        log.trace(XTCE_MEMBER_LIST);
        checkStartElementPreconditions();
        List<Member> l = new ArrayList<>();
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_MEMBER)) {
                l.add(readXtceMember(spaceSystem, paramOrAggreg));
            } else if (isEndElementWithName(XTCE_MEMBER_LIST)) {
                return l;
            }
        }
    }

    private Member readXtceMember(SpaceSystem spaceSystem, boolean paramOrAggreg) throws XMLStreamException {
        log.trace(XTCE_MEMBER);
        checkStartElementPreconditions();
        StartElement element = xmlEvent.asStartElement();
        String name = readMandatoryAttribute("name", element);
        Member member = new Member(name);
        member.setShortDescription(readAttribute("shortDescription", element, null));

        String typeRef = readMandatoryAttribute("typeRef", element);

        if (paramOrAggreg) {
            ParameterType ptype = spaceSystem.getParameterType(typeRef);
            if (ptype != null) {
                member.setDataType(ptype);
            } else {
                NameReference nr = new UnresolvedNameReference(typeRef, Type.PARAMETER_TYPE).addResolvedAction(nd -> {
                    member.setDataType((ParameterType) nd);
                    return true;
                });
                spaceSystem.addUnresolvedReference(nr);
            }
        } else {
            ArgumentType atype = spaceSystem.getArgumentType(typeRef);
            if (atype != null) {
                member.setDataType(atype);
            } else {
                NameReference nr = new UnresolvedNameReference(typeRef, Type.ARGUMENT_TYPE).addResolvedAction(nd -> {
                    member.setDataType((ArgumentType) nd);
                    return true;
                });
                spaceSystem.addUnresolvedReference(nr);
            }
        }
        return member;
    }

    private ParameterType readArrayParameterType(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_ARRAY_PARAMETER_TYPE);
        checkStartElementPreconditions();
        StartElement element = xmlEvent.asStartElement();

        String name = readMandatoryAttribute("name", element);
        ArrayParameterType ptype  = new ArrayParameterType(name);

        String value = readMandatoryAttribute("numberOfDimensions", element);
        ptype.setNumberOfDimensions(Integer.valueOf(value));
        
        String refName = readMandatoryAttribute("arrayTypeRef", xmlEvent.asStartElement());

        NameReference nr = new UnresolvedNameReference(refName, Type.PARAMETER_TYPE).addResolvedAction(nd -> {
            ptype.setElementType((ParameterType)nd);
            return true;
        });
        spaceSystem.addUnresolvedReference(nr);
        
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_LONG_DESCRIPTION)) {
                ptype.setLongDescription(readStringBetweenTags(XTCE_LONG_DESCRIPTION));
            } else if (isEndElementWithName(XTCE_ARRAY_PARAMETER_TYPE)) {
                return ptype;
            } else {
                logUnknown();
            }
        }
    }

    private AbsoluteTimeParameterType readAbsoluteTimeParameterType(SpaceSystem spaceSystem)
            throws IllegalStateException,
            XMLStreamException {
        AbsoluteTimeParameterType ptype = null;
        log.trace(XTCE_ABSOLUTE_TIME_PARAMETER_TYPE);
        checkStartElementPreconditions();

        String value = readMandatoryAttribute("name", xmlEvent.asStartElement());
        ptype = new AbsoluteTimeParameterType(value);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_LONG_DESCRIPTION)) {
                ptype.setLongDescription(readStringBetweenTags(XTCE_LONG_DESCRIPTION));
            } else if (isStartElementWithName(XTCE_REFERENCE_TIME)) {
                ptype.setReferenceTime(readReferenceTime(spaceSystem));
            } else if (isStartElementWithName(XTCE_ENCODING)) {
                readEncoding(spaceSystem, ptype);
            } else if (isEndElementWithName(XTCE_ABSOLUTE_TIME_PARAMETER_TYPE)) {
                return ptype;
            } else {
                logUnknown();
            }
        }
    }

    private ParameterType readRelativeTimeParameterType() throws IllegalStateException,
            XMLStreamException {
        skipXtceSection(XTCE_RelativeTimeParameterType);
        return null;
    }

    private ReferenceTime readReferenceTime(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_REFERENCE_TIME);
        checkStartElementPreconditions();

        ReferenceTime referenceTime = null;

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_OFFSET_FROM)) {
                referenceTime = new ReferenceTime(readParameterInstanceRef(spaceSystem));
            } else if (isStartElementWithName(XTCE_EPOCH)) {
                referenceTime = new ReferenceTime(readEpoch());
            } else if (isEndElementWithName(XTCE_REFERENCE_TIME)) {
                return referenceTime;
            } else {
                logUnknown();
            }
        }
    }

    private TimeEpoch readEpoch() throws XMLStreamException {
        log.trace(XTCE_EPOCH);
        String s = readStringBetweenTags(XTCE_EPOCH);
        try {
            return new TimeEpoch(TimeEpoch.CommonEpochs.valueOf(s));
        } catch (IllegalArgumentException e) {
            // TODO validate the date
            return new TimeEpoch(s);
        }
    }

    private void readEncoding(SpaceSystem spaceSystem, AbsoluteTimeParameterType ptype) throws XMLStreamException {
        log.trace(XTCE_ENCODING);
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
        ptype.setScaling(needsScaling, offset, scale);

        DataEncoding dataEncoding = null;
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_INTEGER_DATA_ENCODING)) {
                dataEncoding = readIntegerDataEncoding(spaceSystem);
            } else if (isStartElementWithName(XTCE_FLOAT_DATA_ENCODING)) {
                dataEncoding = readFloatDataEncoding(spaceSystem);
            } else if (isEndElementWithName(XTCE_ENCODING)) {
                ptype.setEncoding(dataEncoding);
                return;
            } else {
                logUnknown();
            }
        }
    }

    private FloatParameterType readFloatParameterType(SpaceSystem spaceSystem)
            throws IllegalStateException, XMLStreamException {
        FloatParameterType floatParamType = null;
        log.trace(XTCE_FLOAT_PARAMETER_TYPE);
        checkStartElementPreconditions();
        StartElement element = xmlEvent.asStartElement();
        // name attribute
        String value = readMandatoryAttribute("name", element);
        floatParamType = new FloatParameterType(value);

        value = readAttribute("sizeInBits", element, null);

        if (value != null) {
            int sizeInBits = Integer.parseInt(value);
            if (sizeInBits != 32 && sizeInBits != 64) {
                throw new XMLStreamException("Float encoding " + sizeInBits + " not supported;"
                        + " Only 32 and 64 bits are supported", xmlEvent.getLocation());
            }
            floatParamType.setSizeInBits(sizeInBits);
        }
        value = readAttribute("initialValue", element, null);
        if (value != null) {
            floatParamType.setInitialValue(value);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_UNIT_SET)) {
                floatParamType.addAllUnits(readUnitSet());
            } else if (isStartElementWithName(XTCE_INTEGER_DATA_ENCODING)) {
                floatParamType.setEncoding(readIntegerDataEncoding(spaceSystem));
            } else if (isStartElementWithName(XTCE_FLOAT_DATA_ENCODING)) {
                floatParamType.setEncoding(readFloatDataEncoding(spaceSystem));
            } else if (isStartElementWithName(XTCE_DEFAULT_ALARM)) {
                floatParamType.setDefaultAlarm(readDefaultAlarm());
            } else if (isStartElementWithName(XTCE_CONTEXT_ALARM_LIST)) {
                floatParamType.setContextAlarmList(readNumericContextAlarmList(spaceSystem));
            } else if (isEndElementWithName(XTCE_FLOAT_PARAMETER_TYPE)) {
                return floatParamType;
            }
        }
    }

    private FloatDataEncoding readFloatDataEncoding(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_FLOAT_DATA_ENCODING);
        checkStartElementPreconditions();

        FloatDataEncoding floatDataEncoding = null;

        // sizeInBits attribute
        int sizeInBits = readIntAttribute("sizeInBits", xmlEvent.asStartElement(), 32); 

        // encoding attribute
        String value = readAttribute("encoding", xmlEvent.asStartElement(), null);
        Encoding enc = Encoding.IEEE754_1985;
        if (value != null) {
            if ("IEEE754_1985".equalsIgnoreCase(value)) {
                // ok, this encoding is the default
            } else if ("MILSTD_1750A".equalsIgnoreCase(value)) {
                enc = Encoding.MILSTD_1750A;
            } else {
                throwException("Unknown encoding '" + value + "'");
            }
        }
        floatDataEncoding = new FloatDataEncoding(sizeInBits, enc);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_DEFAULT_CALIBRATOR)) {
                floatDataEncoding.setDefaultCalibrator(readCalibrator(spaceSystem));
            } else if (isStartElementWithName(XTCE_CONTEXT_CALIBRATOR_LIST)) {
                floatDataEncoding.setContextCalibratorList(readContextCalibratorList(spaceSystem));
            } else if (isEndElementWithName(XTCE_FLOAT_DATA_ENCODING)) {
                return floatDataEncoding;
            } else {
                logUnknown();
            }
        }
    }

    private List<NumericContextAlarm> readNumericContextAlarmList(SpaceSystem spaceSystem)
            throws IllegalStateException, XMLStreamException {
        log.trace(XTCE_CONTEXT_ALARM_LIST);
        List<NumericContextAlarm> contextAlarmList = new ArrayList<NumericContextAlarm>();
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_CONTEXT_ALARM)) {
                contextAlarmList.add(readNumericContextAlarm(spaceSystem));
            } else if (isEndElementWithName(XTCE_CONTEXT_ALARM_LIST)) {
                return contextAlarmList;
            }
        }
    }

    private NumericAlarm readDefaultAlarm() throws XMLStreamException {
        NumericAlarm na = new NumericAlarm();
        readAlarmAttributes(na);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isEndElementWithName(XTCE_DEFAULT_ALARM)) {
                return na;
            } else if (xmlEvent.getEventType() == XMLStreamConstants.START_ELEMENT) {
                readNumericAlarmElement(na);
            }
        }
    }

    private NumericContextAlarm readNumericContextAlarm(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_CONTEXT_ALARM);
        NumericContextAlarm nca = new NumericContextAlarm();
        readAlarmAttributes(nca);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_CONTEXT_MATCH)) {
                nca.setContextMatch(readMatchCriteria(spaceSystem));
            }  else if (xmlEvent.getEventType() == XMLStreamConstants.START_ELEMENT) {
                readNumericAlarmElement(nca);
            } else if (isEndElementWithName(XTCE_CONTEXT_ALARM)) {
                return nca;
            } else {
                logUnknown();
            }
        }
    }

    private void readNumericAlarmElement(NumericAlarm numericAlarm) throws XMLStreamException {
        if (isStartElementWithName(XTCE_STATIC_ALARM_RANGES)) {
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
        StartElement e = xmlEvent.asStartElement();
        double minExclusive = readDoubleAttribute("minExclusive", e, Double.NaN);
        double maxExclusive = readDoubleAttribute("maxExclusive", e, Double.NaN);
        double minInclusive = readDoubleAttribute("minInclusive", e, Double.NaN);
        double maxInclusive = readDoubleAttribute("maxInclusive", e, Double.NaN);

        return DoubleRange.fromXtceComplement(minExclusive, maxExclusive, minInclusive, maxInclusive);
    }

    private void readAlarmAttributes(AlarmType alarm) {
        String value = readAttribute("minViolations", xmlEvent.asStartElement(), null);
        if (value != null) {
            int minViolations = Integer.parseInt(value);
            alarm.setMinViolations(minViolations);
        }
    }

    private BinaryParameterType readBinaryParameterType(SpaceSystem spaceSystem)
            throws IllegalStateException, XMLStreamException {
        log.trace(XTCE_BINARY_PARAMETER_TYPE);
        checkStartElementPreconditions();

        // name attribute
        BinaryParameterType binaryParamType = null;
        String name = readMandatoryAttribute("name", xmlEvent.asStartElement());
        binaryParamType = new BinaryParameterType(name);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_LONG_DESCRIPTION)) {
                binaryParamType.setLongDescription(readStringBetweenTags(XTCE_LONG_DESCRIPTION));
            } else if (isStartElementWithName(XTCE_UNIT_SET)) {
                binaryParamType.addAllUnits(readUnitSet());
            } else if (isStartElementWithName(XTCE_INTEGER_DATA_ENCODING)) {
                binaryParamType.setEncoding(readIntegerDataEncoding(spaceSystem));
            } else if (isStartElementWithName(XTCE_BINARY_DATA_ENCODING)) {
                binaryParamType.setEncoding(readBinaryDataEncoding(spaceSystem));
            } else if (isEndElementWithName(XTCE_BINARY_PARAMETER_TYPE)) {
                return binaryParamType;
            } else {
                logUnknown();
            }
        }
    }

    private DataEncoding readBinaryDataEncoding(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_BINARY_DATA_ENCODING);
        checkStartElementPreconditions();

        BinaryDataEncoding binaryDataEncoding = null;

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_SIZE_IN_BITS)) {
                IntegerValue v = readIntegerValue(spaceSystem);
                if (v instanceof FixedIntegerValue) {
                    binaryDataEncoding = new BinaryDataEncoding((int) ((FixedIntegerValue) v).getValue());
                } else {
                    throwException("Only FixedIntegerValue supported for sizeInBits");
                }
            } else if (isStartElementWithName("FromBinaryTransformAlgorithm")) {
                skipXtceSection("FromBinaryTransformAlgorithm");
            } else if (isStartElementWithName("ToBinaryTransformAlgorithm")) {
                skipXtceSection("ToBinaryTransformAlgorithm");
            } else if (isEndElementWithName(XTCE_BINARY_DATA_ENCODING)) {
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

    private StringParameterType readStringParameterType(SpaceSystem spaceSystem)
            throws IllegalStateException, XMLStreamException {
        log.trace(XTCE_INTEGER_PARAMETER_TYPE);
        checkStartElementPreconditions();
        StringParameterType stringParamType = null;

        // name attribute
        String value = readMandatoryAttribute("name", xmlEvent.asStartElement());
        stringParamType = new StringParameterType(value);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_UNIT_SET)) {
                stringParamType.addAllUnits(readUnitSet());
            } else if (isStartElementWithName(XTCE_STRING_DATA_ENCODING)) {
                stringParamType.setEncoding(readStringDataEncoding(spaceSystem));
            } else if (isStartElementWithName(XTCE_CONTEXT_ALARM_LIST)) {
                skipXtceSection(XTCE_CONTEXT_ALARM_LIST);
            } else if (isEndElementWithName(XTCE_STRING_PARAMETER_TYPE)) {
                return stringParamType;
            }
        }
    }

    private StringDataEncoding readStringDataEncoding(SpaceSystem spaceSystem) throws XMLStreamException {
        checkStartElementPreconditions();

        StringDataEncoding stringDataEncoding = new StringDataEncoding();
        ;

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_SIZE_IN_BITS)) {
                readStringSizeInBits(spaceSystem, stringDataEncoding);
            } else if (isEndElementWithName(XTCE_STRING_DATA_ENCODING)) {
                return stringDataEncoding;
            }
        }
    }

    private void readStringSizeInBits(SpaceSystem spaceSystem, StringDataEncoding stringDataEncoding)
            throws XMLStreamException {
        checkStartElementPreconditions();
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_FIXED)) {
                IntegerValue v = readIntegerValue(spaceSystem);
                if (v instanceof FixedIntegerValue) {
                    stringDataEncoding.setSizeType(SizeType.FIXED);
                    stringDataEncoding.setSizeInBits((int) ((FixedIntegerValue) v).getValue());
                } else {
                    throwException("Only FixedValue supported for string size in bits");
                }
            } else if (isStartElementWithName(XTCE_TERMINATION_CHAR)) {
                stringDataEncoding.setSizeType(SizeType.TERMINATION_CHAR);
                byte[] x = readHexBinary();
                if (x == null || x.length != 1) {
                    throwException("Terminated strings have to have the size of the termination character of 1");
                }
                stringDataEncoding.setTerminationChar(x[0]);
            } else if (isStartElementWithName(XTCE_LEADING_SIZE)) {
                stringDataEncoding.setSizeType(SizeType.LEADING_SIZE);
                int sizeInBits = readIntAttribute("sizeInBitsOfSizeTag", xmlEvent.asStartElement(), 16);
                stringDataEncoding.setSizeInBitsOfSizeTag(sizeInBits);
            } else if (isEndElementWithName(XTCE_SIZE_IN_BITS)) {
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
                b = StringConverter.hexStringToArray(xmlEvent.asCharacters().getData());
            } else if (isEndElementWithName(tag)) {
                return b;
            }
        }
    }

    private IntegerParameterType readIntegerParameterType(SpaceSystem spaceSystem)
            throws IllegalStateException, XMLStreamException {
        IntegerParameterType integerParamType;

        log.trace(XTCE_INTEGER_PARAMETER_TYPE);
        checkStartElementPreconditions();
        StartElement element = xmlEvent.asStartElement();
        // name attribute
        String value = readMandatoryAttribute("name", element);
        integerParamType = new IntegerParameterType(value);

        int sizeInBits = readIntAttribute("sizeInBits", element, 32);
        integerParamType.setSizeInBits(sizeInBits);

        value = readAttribute("signed", element, null);
        if (value != null) {
            boolean signed = Boolean.parseBoolean(value);
            integerParamType.setSigned(signed);
        }
        value = readAttribute("initialValue", element, null);
        if (value != null) {
            integerParamType.setInitialValue(value);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_LONG_DESCRIPTION)) {
                integerParamType.setLongDescription(readStringBetweenTags(XTCE_LONG_DESCRIPTION));
            } else if (isStartElementWithName(XTCE_UNIT_SET)) {
                integerParamType.addAllUnits(readUnitSet());
            } else if (isStartElementWithName(XTCE_INTEGER_DATA_ENCODING)) {
                integerParamType.setEncoding(readIntegerDataEncoding(spaceSystem));
            } else if (isStartElementWithName(XTCE_DEFAULT_ALARM)) {
                integerParamType.setDefaultAlarm(readDefaultAlarm());
            } else if (isStartElementWithName(XTCE_CONTEXT_ALARM_LIST)) {
                integerParamType.setContextAlarmList(readNumericContextAlarmList(spaceSystem));
            } else if (isStartElementWithName(XTCE_VALID_RANGE)) {
                skipXtceSection(XTCE_VALID_RANGE);
            } else if (isEndElementWithName(XTCE_INTEGER_PARAMETER_TYPE)) {
                return integerParamType;
            } else {
                logUnknown();
            }
        }
    }

    private IntegerDataEncoding readIntegerDataEncoding(SpaceSystem spaceSystem) throws IllegalStateException,
            XMLStreamException {
        log.trace(XTCE_INTEGER_DATA_ENCODING);
        checkStartElementPreconditions();

        IntegerDataEncoding integerDataEncoding = null;

        // sizeInBits attribute
        int sizeInBits = readIntAttribute("sizeInBits", xmlEvent.asStartElement(), 8);
        integerDataEncoding = new IntegerDataEncoding(sizeInBits);

        // encoding attribute
        String value = readAttribute("encoding", xmlEvent.asStartElement(), null);
        if (value != null) {
            if ("unsigned".equalsIgnoreCase(value)) {
                integerDataEncoding.setEncoding(IntegerDataEncoding.Encoding.UNSIGNED);
            } else if ("signMagnitude".equalsIgnoreCase(value)) {
                integerDataEncoding.setEncoding(IntegerDataEncoding.Encoding.SIGN_MAGNITUDE);
            } else if ("twosComplement".equalsIgnoreCase(value)) {
                integerDataEncoding.setEncoding(IntegerDataEncoding.Encoding.TWOS_COMPLEMENT);
            } else if ("twosCompliment".equalsIgnoreCase(value)) { // this is for compatibility with CD-MCS/CGS SCOE XML
                // exporter
                integerDataEncoding.setEncoding(IntegerDataEncoding.Encoding.TWOS_COMPLEMENT);
            } else if ("onesComplement".equalsIgnoreCase(value)) {
                integerDataEncoding.setEncoding(IntegerDataEncoding.Encoding.ONES_COMPLEMENT);
            } else {
                throwException("Unsupported encoding '" + value + "'");
            }
        } else {
            // default is unsigned
            integerDataEncoding.setEncoding(IntegerDataEncoding.Encoding.UNSIGNED);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_DEFAULT_CALIBRATOR)) {
                integerDataEncoding.setDefaultCalibrator(readCalibrator(spaceSystem));
            } else if (isStartElementWithName(XTCE_CONTEXT_CALIBRATOR_LIST)) {
                integerDataEncoding.setContextCalibratorList(readContextCalibratorList(spaceSystem));
            } else if (isEndElementWithName(XTCE_INTEGER_DATA_ENCODING)) {
                return integerDataEncoding;
            } else {
                logUnknown();
            }
        }
    }

    private List<ContextCalibrator> readContextCalibratorList(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_CONTEXT_CALIBRATOR_LIST);
        checkStartElementPreconditions();

        List<ContextCalibrator> clist = new ArrayList<>();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_CONTEXT_CALIBRATOR)) {
                clist.add(readContextCalibrator(spaceSystem));
            } else if (isEndElementWithName(XTCE_CONTEXT_CALIBRATOR_LIST)) {
                return clist;
            } else {
                logUnknown();
            }
        }
    }

    private Calibrator readCalibrator(SpaceSystem spaceSystem) throws IllegalStateException, XMLStreamException {
        log.trace(XTCE_DEFAULT_CALIBRATOR);
        checkStartElementPreconditions();
        String tag = xmlEvent.asStartElement().getName().getLocalPart();
        Calibrator calibrator = null;

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_POLYNOMIAL_CALIBRATOR)) {
                calibrator = readPolynomialCalibrator();
            } else if (isStartElementWithName(XTCE_MATH_OPERATION_CALIBRATOR)) {
                calibrator = (Calibrator) readMathOperation(spaceSystem, null);
            } else if (isStartElementWithName(XTCE_SPLINE_CALIBRATOR)) {
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
            if (isStartElementWithName(XTCE_VALUE_OPERAND)) {
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
                ParameterInstanceRef pref = readParameterInstanceRef(spaceSystem);
                algo.addInput(new InputParameter(pref));
                list.add(new MathOperation.Element(pref));
            } else if (isStartElementWithName("Operator")) {
                list.add(new MathOperation.Element(readMathOperator()));
            } else if (isStartElementWithName(XTCE_TRIGGER_SET)) {
                algo.setTriggerSet(readTriggerSet(spaceSystem));
            } else if (isEndElementWithName(tag)) {
                break;
            }
        }

        if (algo != null) {

            TriggeredMathOperation trigMathOp = new TriggeredMathOperation(list);
            NameReference nr = new UnresolvedNameReference(refName, Type.PARAMETER).addResolvedAction(nd -> {
                algo.addOutput(new OutputParameter((Parameter) nd));
                return true;
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
        refName = readMandatoryAttribute("parameterRef", xmlEvent.asStartElement());
        OnParameterUpdateTrigger trigger = new OnParameterUpdateTrigger();
        NameReference nr = new UnresolvedNameReference(refName, Type.PARAMETER).addResolvedAction(nd -> {
            trigger.setParameter((Parameter) nd);
            return true;
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
        log.trace(XTCE_CONTEXT_CALIBRATOR);
        checkStartElementPreconditions();

        MatchCriteria context = null;
        Calibrator calibrator = null;
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_CONTEXT_MATCH)) {
                context = readMatchCriteria(spaceSystem);
            } else if (isStartElementWithName(XTCE_CALIBRATOR)) {
                calibrator = readCalibrator(spaceSystem);
            } else if (isEndElementWithName(XTCE_CONTEXT_CALIBRATOR)) {
                if (context == null) {
                    throw new XMLStreamException("Invalid context calibrator, no context specified");
                }
                if (calibrator == null) {
                    throw new XMLStreamException("Invalid context calibrator, no calibrator specified");
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
        log.trace(XTCE_SPLINE_CALIBRATOR);
        checkStartElementPreconditions();

        ArrayList<SplinePoint> splinePoints = new ArrayList<SplinePoint>();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_SPLINE_POINT)) {
                splinePoints.add(readSplinePoint());
            } else if (isEndElementWithName(XTCE_SPLINE_CALIBRATOR)) {
                return new SplineCalibrator(splinePoints);
            }
        }
    }

    /**
     * Instantiate SplinePoint element.
     * This element has two required attributes: raw, calibrated
     * 
     * @return
     * @throws XMLStreamException
     */
    private SplinePoint readSplinePoint() throws XMLStreamException {
        log.trace(XTCE_SPLINE_POINT);
        checkStartElementPreconditions();

        double raw = readDoubleAttribute("raw", xmlEvent.asStartElement());
        double calibrated = readDoubleAttribute("calibrated", xmlEvent.asStartElement());

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isEndElementWithName(XTCE_SPLINE_POINT)) {
                return new SplinePoint(raw, calibrated);
            } else {
                logUnknown();
            }
        }
    }

    private Calibrator readPolynomialCalibrator() throws XMLStreamException {
        log.trace(XTCE_POLYNOMIAL_CALIBRATOR);
        checkStartElementPreconditions();

        int maxExponent = 0;
        HashMap<Integer, Double> polynome = new HashMap<Integer, Double>();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_TERM)) {
                XtceTerm term = readTerm();
                if (term.getExponent() > maxExponent) {
                    maxExponent = term.getExponent();
                }
                polynome.put(term.getExponent(), term.getCoefficient());
            } else if (isEndElementWithName(XTCE_POLYNOMIAL_CALIBRATOR)) {
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
        log.trace(XTCE_TERM);
        checkStartElementPreconditions();

        int exponent = readIntAttribute("exponent", xmlEvent.asStartElement());
        double coefficient = readDoubleAttribute("coefficient", xmlEvent.asStartElement());

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isEndElementWithName(XTCE_TERM)) {
                return new XtceTerm(exponent, coefficient);
            }
        }
    }

    private List<UnitType> readUnitSet() throws IllegalStateException, XMLStreamException {
        log.trace(XTCE_UNIT_SET);

        List<UnitType> units = new ArrayList<UnitType>();
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_UNIT)) {
                UnitType u = readUnit();
                units.add(u);
            } else if (isEndElementWithName(XTCE_UNIT_SET)) {
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
        log.trace(XTCE_UNIT);
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
            } else if (isEndElementWithName(XTCE_UNIT)) {
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

    private EnumeratedParameterType readEnumeratedParameterType(SpaceSystem spaceSystem)
            throws IllegalStateException, XMLStreamException {
        EnumeratedParameterType enumParamType = null;

        log.trace(XTCE_ENUMERATED_PARAMETER_TYPE);
        checkStartElementPreconditions();

        // name attribute
        String value = readMandatoryAttribute("name", xmlEvent.asStartElement());
        enumParamType = new EnumeratedParameterType(value);

        // defaultValue attribute
        value = readAttribute("defaultValue", xmlEvent.asStartElement(), null);
        if (value != null) {
            enumParamType.setInitialValue(value);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_LONG_DESCRIPTION)) {
                enumParamType.setLongDescription(readStringBetweenTags(XTCE_LONG_DESCRIPTION));
            } else if (isStartElementWithName(XTCE_UNIT_SET)) {
                enumParamType.addAllUnits(readUnitSet());
            } else if (isStartElementWithName(XTCE_INTEGER_DATA_ENCODING)) {
                enumParamType.setEncoding(readIntegerDataEncoding(spaceSystem));
            } else if (isStartElementWithName(XTCE_FLOAT_DATA_ENCODING)) {
                enumParamType.setEncoding(readFloatDataEncoding(spaceSystem));
            } else if (isStartElementWithName(XTCE_STRING_DATA_ENCODING)) {
                enumParamType.setEncoding(readStringDataEncoding(spaceSystem));
            } else if (isStartElementWithName(XTCE_BINARY_DATA_ENCODING)) {
                enumParamType.setEncoding(readBinaryDataEncoding(spaceSystem));
            } else if (isStartElementWithName(XTCE_ENUMERATION_LIST)) {
                readEnumerationList(enumParamType);
            } else if (isStartElementWithName(XTCE_DEFAULT_ALARM)) {
                enumParamType.setDefaultAlarm(readEnumerationAlarm(enumParamType));
            } else if (isStartElementWithName(XTCE_CONTEXT_ALARM_LIST)) {
                enumParamType.setContextAlarmList(readEnumerationContextAlarmList(spaceSystem, enumParamType));
            } else if (isEndElementWithName(XTCE_ENUMERATED_PARAMETER_TYPE)) {
                return enumParamType;
            } else {
                logUnknown();
            }
        }
    }

    private List<EnumerationContextAlarm> readEnumerationContextAlarmList(SpaceSystem spaceSystem,
            EnumeratedParameterType enumParamType) throws XMLStreamException {
        log.trace(XTCE_CONTEXT_ALARM_LIST);
        List<EnumerationContextAlarm> contextAlarmList = new ArrayList<EnumerationContextAlarm>();
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_CONTEXT_ALARM)) {
                contextAlarmList.add(readEnumerationContextAlarm(spaceSystem, enumParamType));
            } else if (isEndElementWithName(XTCE_CONTEXT_ALARM_LIST)) {
                return contextAlarmList;
            } else {
                logUnknown();
            }
        }
    }

    private EnumerationContextAlarm readEnumerationContextAlarm(SpaceSystem spaceSystem,
            EnumeratedParameterType enumParamType) throws XMLStreamException {
        log.trace(XTCE_CONTEXT_ALARM);
        EnumerationContextAlarm eca = new EnumerationContextAlarm();
        readAlarmAttributes(eca);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_CONTEXT_MATCH)) {
                eca.setContextMatch(readMatchCriteria(spaceSystem));
            } else if (xmlEvent.getEventType() == XMLStreamConstants.START_ELEMENT) {
                EnumerationAlarm a = readEnumerationAlarm(enumParamType);
                eca.setAlarmList(a.getAlarmList());
                
            } else if (isEndElementWithName(XTCE_CONTEXT_ALARM)) {
                return eca;
            } else {
                logUnknown();
            }
        }
    }

    private void readEnumerationList(EnumeratedDataType enumDataType) throws XMLStreamException {
        log.trace(XTCE_ENUMERATION_LIST);
        checkStartElementPreconditions();

        // initialValue attribute
        String initialValue = readAttribute("initialValue", xmlEvent.asStartElement(), null);
        if (initialValue != null) {
            enumDataType.setInitialValue(initialValue);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_ENUMERATION)) {
                readEnumeration(enumDataType);
            } else if (isStartElementWithName(XTCE_RANGE_ENUMERATION)) {
                enumDataType.addEnumerationRange(readRangeEnumeration());
            } else if (isEndElementWithName(XTCE_ENUMERATION_LIST)) {
                return;
            } else {
                logUnknown();
            }
        }
    }

    private EnumerationAlarm readEnumerationAlarm(EnumeratedParameterType enumParamType) throws XMLStreamException {
        checkStartElementPreconditions();
        String tag = xmlEvent.asStartElement().getName().getLocalPart();
        EnumerationAlarm alarm = new EnumerationAlarm();

        // initialValue attribute
        alarm.setMinViolations(readIntAttribute("minViolations", xmlEvent.asStartElement(), 1));

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName("EnumerationAlarm")) {
                String label = readAttribute("enumerationLabel", xmlEvent.asStartElement(), null);
                if (label == null) {
                    label = readAttribute("enumerationValue", xmlEvent.asStartElement(), null); // XTCE 1.1
                }
                if (label == null) {
                    throw new XMLStreamException(fileName + ": error in In definition of " + enumParamType.getName()
                            + "EnumerationAlarm: no enumerationLabel specified", xmlEvent.getLocation());
                }
                if (!enumParamType.hasLabel(label)) {
                    throw new XMLStreamException("Reference to invalid enumeration label '" + label + "'");
                }
                AlarmLevels level = getAlarmLevel(readAttribute("alarmLevel", xmlEvent.asStartElement(), null));
                alarm.addAlarm(label, level);
            } else if (isEndElementWithName(tag)) {
                return alarm;
            }
        }
    }

    private AlarmLevels getAlarmLevel(String l) throws XMLStreamException {
        try {
            return AlarmLevels.valueOf(l.toLowerCase());
        } catch (IllegalArgumentException e) {
            throw new XMLStreamException(
                    "Invalid alarm level '" + l + "'; use one of: " + Arrays.toString(AlarmLevels.values()));
        }
    }

    private ValueEnumerationRange readRangeEnumeration() throws XMLStreamException {
        log.trace(XTCE_RANGE_ENUMERATION);
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
            if (isEndElementWithName(XTCE_RANGE_ENUMERATION)) {
                return range;
            } else {
                logUnknown();
            }
        }
    }

    private void readEnumeration(EnumeratedDataType enumDataType) throws XMLStreamException {
        log.trace(XTCE_ENUMERATION);
        checkStartElementPreconditions();
        StartElement element = xmlEvent.asStartElement();

        long longValue = readLongAttribute("value", element);
        String label = readMandatoryAttribute("label", element);
        String maxValue = readAttribute("maxValue", element, null);
        if (maxValue != null) {
            double mvd = Double.parseDouble(maxValue);
            enumDataType.addEnumerationRange(new ValueEnumerationRange(longValue, mvd, true, true, label));
        } else {
            enumDataType.addEnumerationValue(longValue, label);
        }
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isEndElementWithName(XTCE_ENUMERATION)) {
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
        log.trace(XTCE_PARAMETER_SET);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_PARAMETER)) {
                readParameter(spaceSystem); // the parameter is registered inside
            } else if (isStartElementWithName(XTCE_PARAMETER_REF)) {
                skipXtceSection(XTCE_PARAMETER_REF);
            } else if (isEndElementWithName(XTCE_PARAMETER_SET)) {
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
        log.trace(XTCE_PARAMETER);
        checkStartElementPreconditions();

        Parameter parameter = null;

        // name
        StartElement element = xmlEvent.asStartElement();
        String value = readMandatoryAttribute("name", element);
        parameter = new Parameter(value);

        // parameterTypeRef
        value = readMandatoryAttribute("parameterTypeRef", element);
        ParameterType ptype = spaceSystem.getParameterType(value);
        if (ptype != null) {
            parameter.setParameterType(ptype);
        } else {
            final Parameter p = parameter;
            NameReference nr = new UnresolvedNameReference(value, Type.PARAMETER_TYPE).addResolvedAction(nd -> {
                p.setParameterType((ParameterType) nd);
                return true;
            });
            spaceSystem.addUnresolvedReference(nr);
        }

        // shortDescription
        parameter.setShortDescription(readAttribute("shortDescription", element, null));

        // register the parameter now, because parameter can refer to
        // self in the parameter properties
        spaceSystem.addParameter(parameter);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_ALIAS_SET)) {
                parameter.setAliasSet(readAliasSet());
            } else if (isStartElementWithName(XTCE_PARAMETER_PROPERTIES)) {
                readParameterProperties(spaceSystem, parameter);
            } else if (isStartElementWithName(XTCE_LONG_DESCRIPTION)) {
                parameter.setLongDescription(readStringBetweenTags(XTCE_LONG_DESCRIPTION));
            } else if (isStartElementWithName(XTCE_ANCILLARY_DATA_SET)) {
                skipXtceSection(XTCE_ANCILLARY_DATA_SET);
            } else if (isEndElementWithName(XTCE_PARAMETER)) {
                return parameter;
            } else {
                logUnknown();
            }
        }
    }

    private XtceParameterProperties readParameterProperties(SpaceSystem spaceSystem, Parameter p)
            throws XMLStreamException {
        log.trace(XTCE_PARAMETER_PROPERTIES);
        checkStartElementPreconditions();
        String v = readAttribute("dataSource", xmlEvent.asStartElement(), null);
        if (v != null) {
            try {
                p.setDataSource(DataSource.valueOf(v.toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new XMLStreamException(
                        "invalid dataSource '" + v + "'. Valid values: " + DataSource.values());
            }

        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_VALIDITY_CONDITION)) {
                readMatchCriteria(spaceSystem);
            } else if (isStartElementWithName(XTCE_PHYSICAL_ADDRESS_SET)) {
                skipXtceSection(XTCE_PHYSICAL_ADDRESS_SET);
            } else if (isStartElementWithName(XTCE_SYSTEM_NAME)) {
                skipXtceSection(XTCE_SYSTEM_NAME);
            } else if (isStartElementWithName(XTCE_TIME_ASSOCIATION)) {
                skipXtceSection(XTCE_TIME_ASSOCIATION);
            } else if (isEndElementWithName(XTCE_PARAMETER_PROPERTIES)) {
                return null;
            } else {
                logUnknown();
            }
        }
    }

    private String readStringBetweenTags(String tagName) throws XMLStreamException {
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
        if (!isEndElementWithName(XTCE_DEFAULT_RATE_IN_STREAM)) {
            throw new IllegalStateException(XTCE_DEFAULT_RATE_IN_STREAM + " end element expected");
        }
        return ris;
    }

    private MatchCriteria readMatchCriteria(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace("MatchCriteria");
        checkStartElementPreconditions();
        String tag = xmlEvent.asStartElement().getName().getLocalPart();
        MatchCriteria criteria = null;

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_COMPARISON)) {
                criteria = readComparison(spaceSystem);
            } else if (isStartElementWithName(XTCE_COMPARISON_LIST)) {
                criteria = readComparisonList(spaceSystem);
            } else if (isStartElementWithName(XTCE_BOOLEAN_EXPRESSION)) {
                skipXtceSection(XTCE_BOOLEAN_EXPRESSION);
            } else if (isStartElementWithName(XTCE_CUSTOM_ALGORITHM)) {
                skipXtceSection(XTCE_CUSTOM_ALGORITHM);
            } else if (isEndElementWithName(tag)) {
                return criteria;
            } else {
                logUnknown();
            }
        }
    }

    private ComparisonList readComparisonList(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_COMPARISON_LIST);
        checkStartElementPreconditions();

        ComparisonList list = new ComparisonList();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_COMPARISON)) {
                list.addComparison(readComparison(spaceSystem));
            } else if (isEndElementWithName(XTCE_COMPARISON_LIST)) {
                return list;
            } else {
                logUnknown();
            }
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
        log.trace(XTCE_CONTAINER_SET);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_SEQUENCE_CONTAINER)) {
                SequenceContainer sc = readSequenceContainer(spaceSystem);

                if (excludedContainers.contains(sc.getName())) {
                    log.debug("Not adding '" + sc.getName() + "' to the SpaceSystem because excluded by configuration");
                } else {
                    spaceSystem.addSequenceContainer(sc);
                }
                if ((sc.getBaseContainer() == null) && (spaceSystem.getRootSequenceContainer() == null)) {
                    spaceSystem.setRootSequenceContainer(sc);
                }
            } else if (isEndElementWithName(XTCE_CONTAINER_SET)) {
                return;
            } else {
                logUnknown();
            }
        }
    }

    private SequenceContainer readSequenceContainer(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_SEQUENCE_CONTAINER);
        checkStartElementPreconditions();

        String value = readMandatoryAttribute("name", xmlEvent.asStartElement());
        SequenceContainer seqContainer = new SequenceContainer(value);
        seqContainer.setShortDescription(readAttribute("shortDescription", xmlEvent.asStartElement(), null));

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_ALIAS_SET)) {
                seqContainer.setAliasSet(readAliasSet());
            } else if (isStartElementWithName(XTCE_ENTRY_LIST)) {
                readEntryList(spaceSystem, seqContainer, null);
            } else if (isStartElementWithName(XTCE_BASE_CONTAINER)) {
                readBaseContainer(spaceSystem, seqContainer);
            } else if (isStartElementWithName(XTCE_LONG_DESCRIPTION)) {
                seqContainer.setLongDescription(readStringBetweenTags(XTCE_LONG_DESCRIPTION));
            } else if (isStartElementWithName(XTCE_DEFAULT_RATE_IN_STREAM)) {
                seqContainer.setRateInStream(readRateInStream(spaceSystem));
            } else if (isStartElementWithName(XTCE_BINARY_ENCODING)) {
                skipXtceSection(XTCE_BINARY_ENCODING);
            } else if (isEndElementWithName(XTCE_SEQUENCE_CONTAINER)) {
                return seqContainer;
            } else {
                logUnknown();
            }
        }
    }

    private void readBaseContainer(SpaceSystem spaceSystem, SequenceContainer seqContainer)
            throws IllegalStateException, XMLStreamException {
        log.trace(XTCE_BASE_CONTAINER);
        checkStartElementPreconditions();

        String refName = readMandatoryAttribute("containerRef", xmlEvent.asStartElement());
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
                NameReference nr = new UnresolvedNameReference(refName, Type.SEQUENCE_CONTAINER)
                        .addResolvedAction(nd -> {
                            finalsc.setBaseContainer((SequenceContainer) nd);
                            return true;
                        });
                spaceSystem.addUnresolvedReference(nr);
            }
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_RESTRICTION_CRITERIA)) {
                MatchCriteria criteria = readMatchCriteria(spaceSystem);
                seqContainer.setRestrictionCriteria(criteria);
            } else if (isEndElementWithName(XTCE_BASE_CONTAINER)) {
                return;
            } else {
                logUnknown();
            }
        }
    }

    private void readEntryList(SpaceSystem spaceSystem, Container container, MetaCommand mc)
            throws XMLStreamException {
        log.trace(XTCE_ENTRY_LIST);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            SequenceEntry entry = null;
            if (isStartElementWithName(XTCE_PARAMETER_REF_ENTRY)) {
                entry = readParameterRefEntry(spaceSystem);
            } else if (isStartElementWithName(XTCE_PARAMETER_SEGMENT_REF_ENTRY)) {
                skipXtceSection(XTCE_PARAMETER_SEGMENT_REF_ENTRY);
            } else if (isStartElementWithName(XTCE_CONTAINER_REF_ENTRY)) {
                entry = readContainerRefEntry(spaceSystem);
            } else if (isStartElementWithName(XTCE_CONTAINER_SEGMENT_REF_ENTRY)) {
                skipXtceSection(XTCE_CONTAINER_SEGMENT_REF_ENTRY);
            } else if (isStartElementWithName(XTCE_STREAM_SEGMENT_ENTRY)) {
                skipXtceSection(XTCE_STREAM_SEGMENT_ENTRY);
            } else if (isStartElementWithName(XTCE_INDIRECT_PARAMETER_REF_ENTRY)) {
                skipXtceSection(XTCE_INDIRECT_PARAMETER_REF_ENTRY);
            } else if (isStartElementWithName(XTCE_ARRAY_PARAMETER_REF_ENTRY)) {
                entry = readArrayParameterRefEntry(spaceSystem);
            } else if (isStartElementWithName(XTCE_ARGUMENT_REF_ENTRY)) {
                entry = readArgumentRefEntry(spaceSystem, mc);
            } else if (isStartElementWithName(XTCE_ARRAY_ARGUMENT_REF_ENTRY)) {
                skipXtceSection(XTCE_ARRAY_ARGUMENT_REF_ENTRY);
            } else if (isStartElementWithName(XTCE_FIXED_VALUE_ENTRY)) {
                entry = readFixedValueEntry(spaceSystem);
            } else if (isEndElementWithName(XTCE_ENTRY_LIST)) {
                return;
            } else {
                logUnknown();
            }
            if (entry != null) {
                container.addEntry(entry);
            }
        }

    }

    private SequenceEntry readArrayParameterRefEntry(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_ARRAY_PARAMETER_REF_ENTRY);
        checkStartElementPreconditions();

        String refName = readMandatoryAttribute("parameterRef", xmlEvent.asStartElement());

        ArrayParameterEntry parameterEntry = new ArrayParameterEntry();

        final ArrayParameterEntry finalpe = parameterEntry;
        NameReference nr = new UnresolvedNameReference(refName, Type.PARAMETER).addResolvedAction(nd -> {
            finalpe.setParameter((Parameter) nd);
            return true;
        });

        spaceSystem.addUnresolvedReference(nr);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_LOCATION_IN_CONTAINER_IN_BITS)) {
                readLocationInContainerInBits(parameterEntry);
            } else if (isStartElementWithName(XTCE_REPEAT_ENTRY)) {
                Repeat r = readRepeatEntry(spaceSystem);
                parameterEntry.setRepeatEntry(r);
            } else if (isStartElementWithName(XTCE_INCLUDE_CONDITION)) {
                parameterEntry.setIncludeCondition(readMatchCriteria(spaceSystem));
            } else if (isStartElementWithName(XTCE_DIMENSION_LIST)) {
                parameterEntry.setSize(readDimensionList(spaceSystem));
            } else if (isEndElementWithName(XTCE_ARRAY_PARAMETER_REF_ENTRY)) {
                return parameterEntry;
            } else {
                logUnknown();
            }
        }
    }

    private List<IntegerValue> readDimensionList(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_DIMENSION_LIST);
        checkStartElementPreconditions();
        List<IntegerValue> l = new ArrayList<>();
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_DIMENSION)) {
                skipXtceSection(XTCE_DIMENSION);
            } else if (isStartElementWithName(XTCE_SIZE)) {
                l.add(readIntegerValue(spaceSystem));
            } else if (isEndElementWithName(XTCE_DIMENSION_LIST)) {
                return l;
            } else {
                logUnknown();
            }
        }
    }

    private SequenceEntry readParameterRefEntry(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_PARAMETER_REF_ENTRY);
        checkStartElementPreconditions();

        String refName = readMandatoryAttribute("parameterRef", xmlEvent.asStartElement());

        SequenceEntry.ReferenceLocationType locationType = SequenceEntry.ReferenceLocationType.previousEntry; // default
        ParameterEntry parameterEntry = new ParameterEntry(0, locationType);
        final ParameterEntry finalpe = parameterEntry;
        NameReference nr = new UnresolvedNameReference(refName, Type.PARAMETER).addResolvedAction(nd -> {
            finalpe.setParameter((Parameter) nd);
            return true;
        });

        spaceSystem.addUnresolvedReference(nr);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_LOCATION_IN_CONTAINER_IN_BITS)) {
                readLocationInContainerInBits(parameterEntry);
            } else if (isStartElementWithName(XTCE_REPEAT_ENTRY)) {
                Repeat r = readRepeatEntry(spaceSystem);
                parameterEntry.setRepeatEntry(r);
            } else if (isStartElementWithName(XTCE_INCLUDE_CONDITION)) {
                parameterEntry.setIncludeCondition(readMatchCriteria(spaceSystem));
            } else if (isEndElementWithName(XTCE_PARAMETER_REF_ENTRY)) {
                return parameterEntry;
            } else {
                logUnknown();
            }
        }
    }

    private ContainerEntry readContainerRefEntry(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_CONTAINER_REF_ENTRY);
        checkStartElementPreconditions();

        String refName = readMandatoryAttribute("containerRef", xmlEvent.asStartElement());

        SequenceEntry.ReferenceLocationType locationType = SequenceEntry.ReferenceLocationType.previousEntry; // default
        SequenceContainer container = spaceSystem.getSequenceContainer(refName);
        ContainerEntry containerEntry = null;
        if (container != null) {
            containerEntry = new ContainerEntry(0, locationType, container);
        } else {
            containerEntry = new ContainerEntry(0, locationType);
            final ContainerEntry finalce = containerEntry;
            NameReference nr = new UnresolvedNameReference(refName, Type.SEQUENCE_CONTAINER).addResolvedAction(nd -> {
                finalce.setRefContainer((SequenceContainer) nd);
                return true;
            });
            spaceSystem.addUnresolvedReference(nr);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_LOCATION_IN_CONTAINER_IN_BITS)) {
                readLocationInContainerInBits(containerEntry);
            } else if (isStartElementWithName(XTCE_REPEAT_ENTRY)) {
                Repeat r = readRepeatEntry(spaceSystem);
                containerEntry.setRepeatEntry(r);
            } else if (isStartElementWithName(XTCE_INCLUDE_CONDITION)) {
                containerEntry.setIncludeCondition(readMatchCriteria(spaceSystem));
            } else if (isEndElementWithName(XTCE_CONTAINER_REF_ENTRY)) {
                return containerEntry;
            } else {
                logUnknown();
            }
        }
    }

    private Repeat readRepeatEntry(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_REPEAT_ENTRY);
        Repeat r = null;
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_COUNT)) {
                r = new Repeat(readIntegerValue(spaceSystem));
            } else if (isEndElementWithName(XTCE_REPEAT_ENTRY)) {
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
            if (isStartElementWithName(XTCE_FIXED_VALUE)) {
                v = new FixedIntegerValue(readIntegerValue());
            } else if (isStartElementWithName(XTCE_DYNAMIC_VALUE)) {
                v = readDynamicValue(spaceSystem);
            } else if (isEndElementWithName(tag)) {
                return v;
            } else {
                logUnknown();
            }
        }
    }

    private DynamicIntegerValue readDynamicValue(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_DYNAMIC_VALUE);

        checkStartElementPreconditions();
        DynamicIntegerValue v = null;

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_PARAMETER_INSTANCE_REF)) {
                v = new DynamicIntegerValue(readParameterInstanceRef(spaceSystem));
            } else if (isEndElementWithName(XTCE_DYNAMIC_VALUE)) {
                if (v == null) {
                    throw new XMLStreamException("No " + XTCE_PARAMETER_INSTANCE_REF + " section found");
                }
                return v;
            } else {
                logUnknown();
            }
        }
    }

    private ParameterInstanceRef readParameterInstanceRef(SpaceSystem spaceSystem)
            throws XMLStreamException {
        log.trace(XTCE_PARAMETER_INSTANCE_REF);

        String paramRef = readMandatoryAttribute("parameterRef", xmlEvent.asStartElement());
        final ParameterInstanceRef instanceRef = new ParameterInstanceRef(true);

        NameReference nr = new UnresolvedNameReference(paramRef, Type.PARAMETER).addResolvedAction(nd -> {

            instanceRef.setParameter((Parameter) nd);
            return true;
        });

        Parameter parameter = spaceSystem.getParameter(paramRef);
        if (parameter != null) {
            if (!nr.resolved(parameter)) {
                spaceSystem.addUnresolvedReference(nr);
            }
        } else {
            spaceSystem.addUnresolvedReference(nr);
        }
        return instanceRef;
    }

    private void readLocationInContainerInBits(SequenceEntry entry) throws XMLStreamException {
        log.trace(XTCE_LOCATION_IN_CONTAINER_IN_BITS);
        checkStartElementPreconditions();

        int locationInContainerInBits = 0;

        ReferenceLocationType location;
        String value = readAttribute("referenceLocation", xmlEvent.asStartElement(), "previousEntry");
        if (value.equalsIgnoreCase("previousEntry")) {
            location = ReferenceLocationType.previousEntry;
        } else if (value.equalsIgnoreCase("containerStart")) {
            location =  ReferenceLocationType.containerStart;
        } else {
            throw new XMLStreamException("Currently unsupported reference location: " + value);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_FIXED_VALUE)) {
                locationInContainerInBits = readIntegerValue();
            } else if (isStartElementWithName(XTCE_DYNAMIC_VALUE)) {
                skipXtceSection(XTCE_DYNAMIC_VALUE);
            } else if (isStartElementWithName(XTCE_DISCRETE_LOOKUP_LIST)) {
                skipXtceSection(XTCE_DISCRETE_LOOKUP_LIST);
            } else if (isEndElementWithName(XTCE_LOCATION_IN_CONTAINER_IN_BITS)) {
                entry.setLocation(location, locationInContainerInBits);
                return;
            } else {
                logUnknown();
            }
        }
    }

    private Comparison readComparison(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_COMPARISON);
        checkStartElementPreconditions();

        String paramRef = readMandatoryAttribute("parameterRef", xmlEvent.asStartElement());

        String value = readAttribute("comparisonOperator", xmlEvent.asStartElement(), null);
        if (value == null) {
            value = "=="; // default value
        }

        OperatorType optype = Comparison.stringToOperator(value);

        String theValue;
        theValue = readMandatoryAttribute("value", xmlEvent.asStartElement());

        boolean useCalibratedValue = readBooleanAttribute("useCalibratedValue", xmlEvent.asStartElement(), true);

        final ParameterInstanceRef instanceRef = new ParameterInstanceRef(useCalibratedValue);
        Comparison comparison = new Comparison(instanceRef, theValue, optype);

        UnresolvedParameterReference nr = new UnresolvedParameterReference(paramRef).addResolvedAction((nd, path) -> {
            Parameter p = (Parameter) nd;
            instanceRef.setParameter((Parameter) nd);
            instanceRef.setMemberPath(path);
            if (p.getParameterType() == null) {
                return false;
            }
            comparison.resolveValueType();
            return true;
        });

        Parameter parameter = spaceSystem.getParameter(paramRef);

        if (parameter != null) {
            if (!nr.resolved(parameter)) {
                spaceSystem.addUnresolvedReference(nr);
            }
        } else {
            spaceSystem.addUnresolvedReference(nr);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isEndElementWithName(XTCE_COMPARISON)) {
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
        skipXtceSection(XTCE_MESSAGE_SET);
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
        skipXtceSection(XTCE_STREAM_SET);
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
        log.trace(XTCE_ALGORITHM_SET);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_MATH_ALGORITHM)) {
                readMathAlgorithm(spaceSystem);
            } else if (isStartElementWithName(XTCE_CUSTOM_ALGORITHM)) {
                readCustomAlgorithm(spaceSystem);
            } else if (isEndElementWithName(XTCE_ALGORITHM_SET)) {
                return;
            }
        }
    }

    private void readMathAlgorithm(SpaceSystem spaceSystem) throws IllegalStateException, XMLStreamException {
        checkStartElementPreconditions();

        String value = readMandatoryAttribute("name", xmlEvent.asStartElement());

        MathAlgorithm algo = new MathAlgorithm(value);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_MATH_OPERATION)) {
                readMathOperation(spaceSystem, algo);
            } else if (isStartElementWithName(XTCE_ALIAS_SET)) {
                algo.setAliasSet(readAliasSet());
            } else if (isStartElementWithName(XTCE_LONG_DESCRIPTION)) {
                algo.setLongDescription(readStringBetweenTags(XTCE_LONG_DESCRIPTION));
            } else if (isEndElementWithName(XTCE_MATH_ALGORITHM)) {
                spaceSystem.addAlgorithm(algo);
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
    private void readCommandMetaData(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_COMMAND_MEATA_DATA);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_PARAMETER_TYPE_SET)) {
                readParameterTypeSet(spaceSystem);
            } else if (isStartElementWithName(XTCE_PARAMETER_SET)) {
                readParameterSet(spaceSystem);
            } else if (isStartElementWithName(XTCE_ARGUMENT_TYPE_SET)) {
                readArgumentTypeSet(spaceSystem);
            } else if (isStartElementWithName(XTCE_META_COMMAND_SET)) {
                readMetaCommandSet(spaceSystem);
            } else if (isStartElementWithName(XTCE_COMMAND_CONTAINER_SET)) {
                readCommandContainerSet(spaceSystem);
            } else if (isStartElementWithName(XTCE_MESSAGE_SET)) {
                readMessageSet();
            } else if (isStartElementWithName(XTCE_ALGORITHM_SET)) {
                readAlgorithmSet(spaceSystem);
            } else if (isEndElementWithName(XTCE_COMMAND_MEATA_DATA)) {
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
        log.trace(XTCE_ARGUMENT_TYPE_SET);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            ArgumentType argumentType = null;

            if (isStartElementWithName(XTCE_BOOLEAN_ARGUMENT_TYPE)) {
                argumentType = readBooleanArgumentType(spaceSystem);
            } else if (isStartElementWithName(XTCE_ENUMERATED_ARGUMENT_TYPE)) {
                argumentType = readEnumeratedArgumentType(spaceSystem);
            } else if (isStartElementWithName(XTCE_FLOAT_ARGUMENT_TYPE)) {
                argumentType = readFloatArgumentType(spaceSystem);
            } else if (isStartElementWithName(XTCE_INTEGER_ARGUMENT_TYPE)) {
                argumentType = readIntegerArgumentType(spaceSystem);
            } else if (isStartElementWithName(XTCE_BINARY_ARGUMENT_TYPE)) {
                argumentType = readBinaryArgumentType(spaceSystem);
            } else if (isStartElementWithName(XTCE_STRING_ARGUMENT_TYPE)) {
                argumentType = readStringArgumentType(spaceSystem);
            } else if (isStartElementWithName(XTCE_AGGREGATE_ARGUMENT_TYPE)) {
                argumentType = readAggregateArgumentType(spaceSystem);
            }

            if (argumentType != null) {
                spaceSystem.addArgumentType(argumentType);
            }

            if (isEndElementWithName(XTCE_ARGUMENT_TYPE_SET)) {
                return;
            } else {
                logUnknown();
            }
        }
    }

    private BooleanArgumentType readBooleanArgumentType(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_BOOLEAN_ARGUMENT_TYPE);
        checkStartElementPreconditions();
        StartElement element = xmlEvent.asStartElement();
        // name attribute
        String name = readMandatoryAttribute("name", element);
        BooleanArgumentType boolArgType = new BooleanArgumentType(name);
        boolArgType.setOneStringValue(readAttribute("oneStringValue", element, "True"));
        boolArgType.setZeroStringValue(readAttribute("zeroStringValue", element, "False"));

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_UNIT_SET)) {
                boolArgType.addAllUnits(readUnitSet());
            } else if (isStartElementWithName(XTCE_INTEGER_DATA_ENCODING)) {
                boolArgType.setEncoding(readIntegerDataEncoding(spaceSystem));
            } else if (isEndElementWithName(XTCE_BOOLEAN_ARGUMENT_TYPE)) {
                return boolArgType;
            } else {
                logUnknown();
            }
        }
    }

    private FloatArgumentType readFloatArgumentType(SpaceSystem spaceSystem)
            throws IllegalStateException, XMLStreamException {
        FloatArgumentType floatArgType = null;
        log.trace(XTCE_FLOAT_ARGUMENT_TYPE);
        checkStartElementPreconditions();

        // name attribute
        String value = readMandatoryAttribute("name", xmlEvent.asStartElement());
        floatArgType = new FloatArgumentType(value);

        value = readAttribute("sizeInBits", xmlEvent.asStartElement(), null);

        if (value != null) {
            int sizeInBits = Integer.parseInt(value);
            if (sizeInBits != 32 && sizeInBits != 64) {
                throw new XMLStreamException("Float encoding " + sizeInBits + " not supported;"
                        + " Only 32 and 64 bits are supported", xmlEvent.getLocation());
            }
            floatArgType.setSizeInBits(sizeInBits);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_UNIT_SET)) {
                floatArgType.addAllUnits(readUnitSet());
            } else if (isStartElementWithName(XTCE_INTEGER_DATA_ENCODING)) {
                floatArgType.setEncoding(readIntegerDataEncoding(spaceSystem));
            } else if (isStartElementWithName(XTCE_FLOAT_DATA_ENCODING)) {
                floatArgType.setEncoding(readFloatDataEncoding(spaceSystem));
            } else if (isEndElementWithName(XTCE_FLOAT_ARGUMENT_TYPE)) {
                return floatArgType;
            } else {
                logUnknown();
            }
        }
    }

    private EnumeratedArgumentType readEnumeratedArgumentType(SpaceSystem spaceSystem) throws XMLStreamException {
        EnumeratedArgumentType enumArgType = null;

        log.trace(XTCE_ENUMERATED_ARGUMENT_TYPE);
        checkStartElementPreconditions();

        // name attribute
        String value = readMandatoryAttribute("name", xmlEvent.asStartElement());
        enumArgType = new EnumeratedArgumentType(value);

        // defaultValue attribute
        value = readAttribute("defaultValue", xmlEvent.asStartElement(), null);
        if (value != null) {
            enumArgType.setInitialValue(value);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_UNIT_SET)) {
                enumArgType.addAllUnits(readUnitSet());
            } else if (isStartElementWithName(XTCE_INTEGER_DATA_ENCODING)) {
                enumArgType.setEncoding(readIntegerDataEncoding(spaceSystem));
            } else if (isStartElementWithName(XTCE_ENUMERATION_LIST)) {
                readEnumerationList(enumArgType);
            } else if (isEndElementWithName(XTCE_ENUMERATED_ARGUMENT_TYPE)) {
                return enumArgType;
            }
        }
    }

    private ArgumentType readAggregateArgumentType(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_AGGREGATE_ARGUMENT_TYPE);
        checkStartElementPreconditions();
        AggregateArgumentType argtype = null;

        String name = readMandatoryAttribute("name", xmlEvent.asStartElement());
        argtype = new AggregateArgumentType(name);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_MEMBER_LIST)) {
                argtype.addMembers(readMemberList(spaceSystem, false));
            } else if (isEndElementWithName(XTCE_AGGREGATE_ARGUMENT_TYPE)) {
                return argtype;
            } else {
                logUnknown();
            }
        }
    }

    private IntegerArgumentType readIntegerArgumentType(SpaceSystem spaceSystem)
            throws IllegalStateException, XMLStreamException {
        IntegerArgumentType integerArgType;

        log.trace(XTCE_INTEGER_ARGUMENT_TYPE);
        checkStartElementPreconditions();

        // name attribute
        String value = readMandatoryAttribute("name", xmlEvent.asStartElement());
        integerArgType = new IntegerArgumentType(value);

        int sizeInBits = readIntAttribute("sizeInBits", xmlEvent.asStartElement(), 32);
        integerArgType.setSizeInBits(sizeInBits);

        value = readAttribute("signed", xmlEvent.asStartElement(), null);
        if (value != null) {
            boolean signed = Boolean.parseBoolean(value);
            integerArgType.setSigned(signed);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_UNIT_SET)) {
                integerArgType.addAllUnits(readUnitSet());
            } else if (isStartElementWithName(XTCE_INTEGER_DATA_ENCODING)) {
                integerArgType.setEncoding(readIntegerDataEncoding(spaceSystem));
            } else if (isEndElementWithName(XTCE_INTEGER_ARGUMENT_TYPE)) {
                return integerArgType;
            }
        }
    }

    private BinaryArgumentType readBinaryArgumentType(SpaceSystem spaceSystem)
            throws IllegalStateException, XMLStreamException {
        log.trace(XTCE_BINARY_ARGUMENT_TYPE);
        checkStartElementPreconditions();

        // name attribute
        BinaryArgumentType binaryParamType = null;
        String name = readMandatoryAttribute("name", xmlEvent.asStartElement());
        binaryParamType = new BinaryArgumentType(name);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_UNIT_SET)) {
                binaryParamType.addAllUnits(readUnitSet());
            } else if (isStartElementWithName(XTCE_INTEGER_DATA_ENCODING)) {
                binaryParamType.setEncoding(readIntegerDataEncoding(spaceSystem));
            } else if (isStartElementWithName(XTCE_BINARY_DATA_ENCODING)) {
                binaryParamType.setEncoding(readBinaryDataEncoding(spaceSystem));
            } else if (isEndElementWithName(XTCE_BINARY_ARGUMENT_TYPE)) {
                return binaryParamType;
            } else {
                logUnknown();
            }
        }
    }

    private StringArgumentType readStringArgumentType(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_STRING_ARGUMENT_TYPE);
        checkStartElementPreconditions();
        StringArgumentType stringParamType = null;

        // name attribute
        String value = readMandatoryAttribute("name", xmlEvent.asStartElement());
        stringParamType = new StringArgumentType(value);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_UNIT_SET)) {
                stringParamType.addAllUnits(readUnitSet());
            } else if (isStartElementWithName(XTCE_STRING_DATA_ENCODING)) {
                stringParamType.setEncoding(readStringDataEncoding(spaceSystem));
            } else if (isStartElementWithName(XTCE_CONTEXT_ALARM_LIST)) {
                skipXtceSection(XTCE_CONTEXT_ALARM_LIST);
            } else if (isEndElementWithName(XTCE_STRING_ARGUMENT_TYPE)) {
                return stringParamType;
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
        skipXtceSection(XTCE_COMMAND_CONTAINER_SET);
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
        log.trace(XTCE_META_COMMAND_SET);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_META_COMMAND)) {
                MetaCommand mc = readMetaCommand(spaceSystem);
                if (excludedContainers.contains(mc.getName())) {
                    log.debug("Not adding '{}' to the SpaceSystem because excluded by configuration", mc.getName());
                } else {
                    spaceSystem.addMetaCommand(mc);
                }
            } else if (isEndElementWithName(XTCE_META_COMMAND_SET)) {
                return;
            }
        }
    }

    private MetaCommand readMetaCommand(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_META_COMMAND);
        checkStartElementPreconditions();

        MetaCommand mc = null;

        String value = readMandatoryAttribute("name", xmlEvent.asStartElement());
        mc = new MetaCommand(value);

        value = readAttribute("shortDescription", xmlEvent.asStartElement(), null);
        if (value != null) {
            mc.setShortDescription(value);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_ALIAS_SET)) {
                mc.setAliasSet(readAliasSet());
            } else if (isStartElementWithName(XTCE_LONG_DESCRIPTION)) {
                mc.setLongDescription(readStringBetweenTags(XTCE_LONG_DESCRIPTION));
            } else if (isStartElementWithName(XTCE_BASE_META_COMMAND)) {
                readBaseMetaCommand(spaceSystem, mc);
            } else if (isStartElementWithName(XTCE_COMMAND_CONTAINER)) {
                CommandContainer cc = readCommandContainer(spaceSystem, mc);
                mc.setCommandContainer(cc);
                spaceSystem.addCommandContainer(cc);
            } else if (isStartElementWithName(XTCE_ARGUMENT_LIST)) {
                readArgumentList(spaceSystem, mc);
            } else if (isEndElementWithName(XTCE_META_COMMAND)) {
                return mc;
            } else {
                logUnknown();
            }
        }
    }

    private void readBaseMetaCommand(SpaceSystem spaceSystem, MetaCommand mc) throws XMLStreamException {
        log.trace(XTCE_BASE_META_COMMAND);
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
                    NameReference nr = new UnresolvedNameReference(refName, Type.META_COMMAND).addResolvedAction(nd -> {
                        finalmc.setBaseMetaCommand((MetaCommand) nd);
                        return true;
                    });
                    spaceSystem.addUnresolvedReference(nr);
                }
            }

            while (true) {
                xmlEvent = xmlEventReader.nextEvent();

                if (isStartElementWithName(XTCE_ARGUMENT_ASSIGNMENT_LIST)) {
                    readArgumentAssignmentList(spaceSystem, mc);
                } else if (isEndElementWithName(XTCE_BASE_META_COMMAND)) {
                    return;
                } else {
                    logUnknown();
                }
            }
        }
    }

    private void readArgumentAssignmentList(SpaceSystem spaceSystem, MetaCommand mc) throws XMLStreamException {
        log.trace(XTCE_ARGUMENT_ASSIGNMENT_LIST);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_ARGUMENT_ASSIGNMENT)) {
                ArgumentAssignment aa = readArgumentAssignment(spaceSystem);
                mc.addArgumentAssignment(aa);
            } else if (isEndElementWithName(XTCE_ARGUMENT_ASSIGNMENT_LIST)) {
                return;
            }
        }

    }

    private ArgumentAssignment readArgumentAssignment(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_ARGUMENT_ASSIGNMENT);
        checkStartElementPreconditions();

        StartElement element = xmlEvent.asStartElement();
        String argumentName = readMandatoryAttribute("argumentName", element);
        String argumentValue = readMandatoryAttribute("argumentValue", element);
        skipToTheEnd(XTCE_ARGUMENT_ASSIGNMENT);
        return new ArgumentAssignment(argumentName, argumentValue);
    }

    private void readArgumentList(SpaceSystem spaceSystem, MetaCommand mc) throws XMLStreamException {
        log.trace(XTCE_ARGUMENT_LIST);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_ARGUMENT)) {
                Argument arg = readArgument(spaceSystem);
                mc.addArgument(arg);
            } else if (isEndElementWithName(XTCE_ARGUMENT_LIST)) {
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
        log.trace(XTCE_ARGUMENT);
        checkStartElementPreconditions();

        Argument arg = null;

        // name
        StartElement element = xmlEvent.asStartElement();
        String name = readMandatoryAttribute("name", element);
        arg = new Argument(name);

        String initialValue = readAttribute("initialValue", element, null);
        arg.setInitialValue(initialValue);

        String argumentTypeRef = readMandatoryAttribute("argumentTypeRef", element);
        ArgumentType ptype = spaceSystem.getArgumentType(argumentTypeRef);
        if (ptype != null) {
            arg.setArgumentType(ptype);
        } else {
            final Argument a = arg;
            NameReference nr = new UnresolvedNameReference(argumentTypeRef, Type.ARGUMENT_TYPE)
                    .addResolvedAction(nd -> {
                        a.setArgumentType((ArgumentType) nd);
                        return true;
                    });
            spaceSystem.addUnresolvedReference(nr);
        }

        // shortDescription
        arg.setShortDescription(readAttribute("shortDescription", element, null));

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_ALIAS_SET)) {
                arg.setAliasSet(readAliasSet());
            } else if (isStartElementWithName(XTCE_LONG_DESCRIPTION)) {
                arg.setLongDescription(readStringBetweenTags(XTCE_LONG_DESCRIPTION));
            } else if (isEndElementWithName(XTCE_ARGUMENT)) {
                return arg;
            } else {
                logUnknown();
            }
        }
    }

    private CommandContainer readCommandContainer(SpaceSystem spaceSystem, MetaCommand mc)
            throws XMLStreamException {
        log.trace(XTCE_COMMAND_CONTAINER);
        checkStartElementPreconditions();

        CommandContainer cmdContainer = null;

        String value = readMandatoryAttribute("name", xmlEvent.asStartElement());
        cmdContainer = new CommandContainer(value);

        value = readAttribute("shortDescription", xmlEvent.asStartElement(), null);
        if (value != null) {
            cmdContainer.setShortDescription(value);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_ALIAS_SET)) {
                cmdContainer.setAliasSet(readAliasSet());
            } else if (isStartElementWithName(XTCE_ENTRY_LIST)) {
                readEntryList(spaceSystem, cmdContainer, mc);
            } else if (isStartElementWithName(XTCE_BASE_CONTAINER)) {
                readBaseContainer(spaceSystem, cmdContainer);
            } else if (isStartElementWithName(XTCE_LONG_DESCRIPTION)) {
                cmdContainer.setLongDescription(readStringBetweenTags(XTCE_LONG_DESCRIPTION));
            } else if (isStartElementWithName(XTCE_DEFAULT_RATE_IN_STREAM)) {
                cmdContainer.setRateInStream(readRateInStream(spaceSystem));
            } else if (isEndElementWithName(XTCE_COMMAND_CONTAINER)) {
                return cmdContainer;
            } else {
                logUnknown();
            }
        }
    }

    private void readBaseContainer(SpaceSystem spaceSystem, CommandContainer mcContainer)
            throws IllegalStateException, XMLStreamException {
        log.trace(XTCE_BASE_CONTAINER);
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
                NameReference nr = new UnresolvedNameReference(refName, Type.COMMAND_CONTAINER)
                        .addResolvedAction(nd -> {
                            finalsc.setBaseContainer((Container) nd);
                            return true;
                        });
                spaceSystem.addUnresolvedReference(nr);
            }
        }
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_RESTRICTION_CRITERIA)) {
                MatchCriteria criteria = readMatchCriteria(spaceSystem);
                mcContainer.setRestrictionCriteria(criteria);
            } else if (isEndElementWithName(XTCE_BASE_CONTAINER)) {
                return;
            } else {
                logUnknown();
            }
        }
    }

    private ArgumentEntry readArgumentRefEntry(SpaceSystem spaceSystem, MetaCommand mc) throws XMLStreamException {
        log.trace(XTCE_ARGUMENT_REF_ENTRY);
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

            if (isStartElementWithName(XTCE_LOCATION_IN_CONTAINER_IN_BITS)) {
                readLocationInContainerInBits(argumentEntry);
            } else if (isStartElementWithName(XTCE_REPEAT_ENTRY)) {
                Repeat r = readRepeatEntry(spaceSystem);
                argumentEntry.setRepeatEntry(r);
            } else if (isStartElementWithName(XTCE_INCLUDE_CONDITION)) {
                skipXtceSection(XTCE_INCLUDE_CONDITION);
            } else if (isEndElementWithName(XTCE_ARGUMENT_REF_ENTRY)) {
                return argumentEntry;
            } else {
                logUnknown();
            }
        }
    }

    private FixedValueEntry readFixedValueEntry(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_FIXED_VALUE_ENTRY);
        checkStartElementPreconditions();
        StartElement startElement = xmlEvent.asStartElement();

        String name = readAttribute("name", startElement, null);

        String value = readMandatoryAttribute("binaryValue", startElement);
        byte[] binaryValue = StringConverter.hexStringToArray(value);

        int sizeInBits = readIntAttribute("sizeInBits", startElement, binaryValue.length * 8);

        FixedValueEntry fixedValueEntry = new FixedValueEntry(name, binaryValue, sizeInBits);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_LOCATION_IN_CONTAINER_IN_BITS)) {
                readLocationInContainerInBits(fixedValueEntry);
            } else if (isStartElementWithName(XTCE_REPEAT_ENTRY)) {
                Repeat r = readRepeatEntry(spaceSystem);
                fixedValueEntry.setRepeatEntry(r);
            } else if (isStartElementWithName(XTCE_INCLUDE_CONDITION)) {
                skipXtceSection(XTCE_INCLUDE_CONDITION);
            } else if (isEndElementWithName(XTCE_FIXED_VALUE_ENTRY)) {
                return fixedValueEntry;
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
    private void readCustomAlgorithm(SpaceSystem spaceSystem) throws IllegalStateException, XMLStreamException {
        checkStartElementPreconditions();
        StartElement startElement = xmlEvent.asStartElement();
        String tag = startElement.getName().getLocalPart();

        String name = readMandatoryAttribute("name", startElement);

        CustomAlgorithm algo = new CustomAlgorithm(name);
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_ALGORITHM_TEXT)) {
                readCustomAlgorithmText(algo);
            } else if (isStartElementWithName(XTCE_TRIGGER_SET)) {
                algo.setTriggerSet(readTriggerSet(spaceSystem));
            } else if (isStartElementWithName(XTCE_OUTPUT_SET)) {
                algo.setOutputSet(readOutputSet(spaceSystem));
            } else if (isStartElementWithName(XTCE_INPUT_SET)) {
                algo.setInputSet(readInputSet(spaceSystem));
            } else if (isEndElementWithName(tag)) {
                spaceSystem.addAlgorithm(algo);
                return;
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

    private List<InputParameter> readInputSet(SpaceSystem spaceSystem) throws XMLStreamException {
        checkStartElementPreconditions();
        StartElement startElement = xmlEvent.asStartElement();
        String tag = startElement.getName().getLocalPart();

        List<InputParameter> result = new ArrayList<>();
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_INPUT_PARAMETER_INSTANCE_REF)) {
                result.add(readInputParameterInstanceRef(spaceSystem));
            } else if (isStartElementWithName(XTCE_CONSTANT)) {
                throw new XMLStreamException("Constant input parameters not supported", xmlEvent.getLocation());
            } else if (isEndElementWithName(tag)) {
                return result;
            } else {
                logUnknown();
            }
        }
    }

    private InputParameter readInputParameterInstanceRef(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_INPUT_PARAMETER_INSTANCE_REF);
        String paramRef = readMandatoryAttribute("parameterRef", xmlEvent.asStartElement());

        String inputName = readAttribute("inputName", xmlEvent.asStartElement(), null);

        final ParameterInstanceRef instanceRef = new ParameterInstanceRef(true);

        NameReference nr = new UnresolvedNameReference(paramRef, Type.PARAMETER).addResolvedAction(nd -> {
            instanceRef.setParameter((Parameter) nd);
            return true;
        });
        spaceSystem.addUnresolvedReference(nr);

        return new InputParameter(instanceRef, inputName);
    }

    private List<OutputParameter> readOutputSet(SpaceSystem spaceSystem) throws XMLStreamException {
        checkStartElementPreconditions();
        StartElement startElement = xmlEvent.asStartElement();
        String tag = startElement.getName().getLocalPart();

        List<OutputParameter> result = new ArrayList<>();
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_OUTPUT_PARAMETER_REF)) {
                result.add(readOutputParameterRef(spaceSystem));
            } else if (isEndElementWithName(tag)) {
                return result;
            }
        }
    }

    private OutputParameter readOutputParameterRef(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_OUTPUT_PARAMETER_REF);
        String paramRef = readMandatoryAttribute("parameterRef", xmlEvent.asStartElement());
        String outputName = readAttribute("outputName", xmlEvent.asStartElement(), null);

        OutputParameter outp = new OutputParameter();
        outp.setOutputName(outputName);

        NameReference nr = new UnresolvedNameReference(paramRef, Type.PARAMETER).addResolvedAction(nd -> {
            outp.setParameter((Parameter) nd);
            return true;
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
            xtceSkipStatistics.put(xtceSectionName, new Integer(1));
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

    /**
     * 
     * @param filename
     * @return
     * @throws FileNotFoundException
     * @throws XMLStreamException
     */
    private XMLEventReader initEventReader(String filename) throws FileNotFoundException, XMLStreamException {
        InputStream in = new FileInputStream(new File(filename));
        XMLInputFactory factory = XMLInputFactory.newInstance();
        return factory.createXMLEventReader(in);
    }

    /**
     * Examines element for presence of attributes
     * 
     * @param element
     *            Element to be examined, should not be null
     * @return True, if the element contains attributes, otherwise false
     */
    @SuppressWarnings("unused")
    private boolean hasAttributes(StartElement element) {
        if (element == null) {
            log.info("element param is null");
            return false;
        }
        return element.getAttributes().hasNext();
    }

    /**
     * Check if xml element is a start element with particular name
     * 
     * @param localName
     *            Name of the element
     * @return True if element is start element with the given name, otherwise
     *         false
     */
    private boolean isStartElementWithName(String localName) {
        return (xmlEvent.getEventType() == XMLStreamConstants.START_ELEMENT && xmlEvent
                .asStartElement().getName().getLocalPart().equals(localName));
    }

    /**
     * Test if the xmlEvent is of type END_ELEMENT and has particular local
     * name. This test is used to identify the end of section.
     * 
     * @param localName
     *            Local name of the element (we neglect namespace for now)
     * @return True if current xmlEvent is of type END_ELEMENT and has
     *         particular local name, otherwise false
     */
    private boolean isEndElementWithName(String localName) {
        return (xmlEvent.getEventType() == XMLStreamConstants.END_ELEMENT && xmlEvent
                .asEndElement().getName().getLocalPart().equals(localName));
    }

    /**
     * Checks preconditions before the dedicated code for section reading will
     * run
     * 
     * @throws IllegalStateException
     *             If the conditions are not met
     */
    private void checkStartElementPreconditions() throws IllegalStateException {
        if (xmlEvent == null) {
            throw new IllegalStateException("xmlEvent is null");
        }
        if (xmlEvent.getEventType() != XMLStreamConstants.START_ELEMENT) {
            throw new IllegalStateException("xmlEvent type is not start element");
        }
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
    private String readMandatoryAttribute(String attName, StartElement element) throws XMLStreamException {
        Attribute attribute = element.getAttributeByName(new QName(attName));
        if (attribute != null) {
            return attribute.getValue();
        } else {
            throw new XMLStreamException("Mandatory attribute '" + attName + "' not defined");
        }
    }

    private String readAttribute(String attName, StartElement element, String defaultValue) {
        Attribute attribute = element.getAttributeByName(new QName(attName));
        if (attribute != null) {
            return attribute.getValue();
        }
        return defaultValue;
    }

    private int readIntAttribute(String attName, StartElement element, int defaultValue) throws XMLStreamException {
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

    private int readIntAttribute(String attName, StartElement element) throws XMLStreamException {
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

    private long readLongAttribute(String attName, StartElement element) throws XMLStreamException {
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

    private double readDoubleAttribute(String attName, StartElement element) throws XMLStreamException {
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

    private double readDoubleAttribute(String attName, StartElement element, double defaultValue)
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

    private boolean readBooleanAttribute(String attName, StartElement element, boolean defaultValue)
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

    public void setExcludedContainers(Set<String> excludedContainers) {
        this.excludedContainers = excludedContainers;
    }

    private void logUnknown() {
        if (xmlEvent.isStartElement()) {
            StartElement element = xmlEvent.asStartElement();
            log.warn("Skipping unkown tag {} at {}:{}", element.getName().getLocalPart(),
                    element.getLocation().getLineNumber(), element.getLocation().getColumnNumber());
        }
    }

}

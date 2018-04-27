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
import org.yamcs.xtce.AlarmLevels;
import org.yamcs.xtce.AlarmRanges;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.ArgumentEntry;
import org.yamcs.xtce.ArgumentType;
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
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.DynamicIntegerValue;
import org.yamcs.xtce.EnumeratedArgumentType;
import org.yamcs.xtce.EnumeratedDataType;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.EnumerationAlarm;
import org.yamcs.xtce.FixedIntegerValue;
import org.yamcs.xtce.FixedValueEntry;
import org.yamcs.xtce.FloatArgumentType;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.IntegerValue;
import org.yamcs.xtce.MatchCriteria;
import org.yamcs.xtce.MathOperation;
import org.yamcs.xtce.MathOperationCalibrator;
import org.yamcs.xtce.MathOperator;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.CommandContainer;
import org.yamcs.xtce.NameReference;
import org.yamcs.xtce.UnresolvedNameReference;
import org.yamcs.xtce.Repeat;
import org.yamcs.xtce.UnitType;
import org.yamcs.xtce.NameReference.Type;
import org.yamcs.xtce.NumericAlarm;
import org.yamcs.xtce.NumericContextAlarm;
import org.yamcs.xtce.OperatorType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterEntry;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.PolynomialCalibrator;
import org.yamcs.xtce.RateInStream;
import org.yamcs.xtce.ReferenceTime;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SequenceEntry;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.SplineCalibrator;
import org.yamcs.xtce.SplinePoint;
import org.yamcs.xtce.StringArgumentType;
import org.yamcs.xtce.StringDataEncoding;
import org.yamcs.xtce.StringDataEncoding.SizeType;


import org.yamcs.xtce.StringParameterType;
import org.yamcs.xtce.TimeEpoch;
import org.yamcs.xtce.ValueEnumerationRange;
import org.yamcs.xtce.Header;
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

    private static final String  XTCE_RelativeTimeParameterType = "RelativeTimeParameterType";
    private static final String  XTCE_ArrayParameterType        = "ArrayParameterType";
    private static final String  XTCE_AggregateParameterType    = "AggregateParameterType";
    
    private static final String  XTCE_AuthorSet                 = "AuthorSet";
    private static final String  XTCE_NoteSet                   = "NoteSet";
    private static final String  XTCE_HistorySet                = "HistorySet";

    private static final String  XTCE_TELEMTRY_META_DATA            = "TelemetryMetaData";
    private static final String  XTCE_PARAMETER_TYPE_SET            = "ParameterTypeSet";
    private static final String  XTCE_BOOLEAN_PARAMETER_TYPE        = "BooleanParameterType";
    private static final String  XTCE_ENUMERATED_PARAMETER_TYPE     = "EnumeratedParameterType";
    private static final String  XTCE_ENUMERATION_LIST              = "EnumerationList";
    private static final String  XTCE_ENUMERATION                   = "Enumeration";
    private static final String  XTCE_RANGE_ENUMERATION             = "RangeEnumeration";
    private static final String  XTCE_STRING_PARAMETER_TYPE         = "StringParameterType";
    private static final String  XTCE_BINARY_PARAMETER_TYPE         = "BinaryParameterType";
    private static final String  XTCE_INTEGER_PARAMETER_TYPE        = "IntegerParameterType";
    private static final String  XTCE_FLOAT_PARAMETER_TYPE          = "FloatParameterType";
    private static final String  XTCE_SPACE_SYSTEM                  = "SpaceSystem";
    private static final String  XTCE_ALIAS_SET                     = "AliasSet";
    private static final String  XTCE_ALIAS                         = "Alias";
    private static final String  XTCE_LONG_DESCRIPTION              = "LongDescription";
    private static final String  XTCE_HEADER                        = "Header";
    private static final String  XTCE_ABSOLUTE_TIME_PARAMETER_TYPE  = "AbsoluteTimeParameterType";
    private static final String  XTCE_PARAMETER_SET                 = "ParameterSet";
    private static final String  XTCE_PARAMETER                     = "Parameter";
    private static final String  XTCE_PARAMETER_REF                 = "ParameterRef";
    private static final String  XTCE_PARAMETER_PROPERTIES          = "ParameterProperties";
    private static final String  XTCE_VALIDITY_CONDITION            = "ValidityCondition";
    private static final String  XTCE_COMPARISON_LIST               = "ComparisonList";
    private static final String  XTCE_COMPARISON                    = "Comparison";
    private static final String  XTCE_BOOLEAN_EXPRESSION            = "BooleanExpression";
    private static final String  XTCE_CUSTOM_ALGORITHM              = "CustomAlgorithm";
    private static final String  XTCE_MATH_ALGORITHM                = "MathAlgorithm";
    private static final String  XTCE_RESTRICTION_CRITERIA          = "RestrictionCriteria";
    private static final String  XTCE_SYSTEM_NAME                   = "SystemName";
    private static final String  XTCE_PHYSICAL_ADDRESS_SET          = "PhysicalAddressSet";
    private static final String  XTCE_TIME_ASSOCIATION              = "TimeAssociation";
    private static final String  XTCE_CONTAINER_SET                 = "ContainerSet";
    private static final String  XTCE_COMMAND_CONTAINER_SET         = "CommandContainerSet";
    private static final String  XTCE_BASE_CONTAINER                = "BaseContainer";
    private static final String  XTCE_MESSAGE_SET                   = "MessageSet";
    private static final String  XTCE_STREAM_SET                    = "StreamSet";
    private static final String  XTCE_ALGORITHM_SET                 = "AlgorithmSet";

    private static final String  XTCE_COMMAND_MEATA_DATA            = "CommandMetaData";
    private static final String  XTCE_SEQUENCE_CONTAINER            = "SequenceContainer";
    private static final String  XTCE_ENTRY_LIST                    = "EntryList";
    private static final String  XTCE_PARAMETER_REF_ENTRY           = "ParameterRefEntry";

    private static final String  XTCE_LOCATION_IN_CONTAINER_IN_BITS = "LocationInContainerInBits";
    private static final String  XTCE_REPEAT_ENTRY                  = "RepeatEntry";
    private static final String  XTCE_INCLUDE_CONDITION             = "IncludeCondition";

    private static final String  XTCE_PARAMETER_SEGMENT_REF_ENTRY   = "ParameterSegmentRefEntry";
    private static final String  XTCE_CONTAINER_REF_ENTRY           = "ContainerRefEntry";
    private static final String  XTCE_CONTAINER_SEGMENT_REF_ENTRY   = "ContainerSegmentRefEntry";
    private static final String  XTCE_STREAM_SEGMENT_ENTRY          = "StreamSegmentEntry";
    private static final String  XTCE_INDIRECT_PARAMETER_REF_ENTRY  = "IndirectParameterRefEntry";
    private static final String  XTCE_ARRAY_PARAMETER_REF_ENTRY     = "ArrayParameterRefEntry";

    private static final String  XTCE_UNIT_SET                      = "UnitSet";
    private static final String  XTCE_UNIT                          = "Unit";
    private static final String  XTCE_FLOAT_DATA_ENCODING           = "FloatDataEncoding";
    private static final String  XTCE_BINARY_DATA_ENCODING          = "BinaryDataEncoding";
    private static final String  XTCE_SIZE_IN_BITS                  = "SizeInBits";
    private static final String  XTCE_FIXED_VALUE                   = "FixedValue";
    private static final String  XTCE_DYNAMIC_VALUE                 = "DynamicValue";
    private static final String  XTCE_DISCRETE_LOOKUP_LIST          = "DiscreteLookupList";
    private static final String  XTCE_INTEGER_DATA_ENCODING         = "IntegerDataEncoding";
    private static final String  XTCE_STRING_DATA_ENCODING          = "StringDataEncoding";
    private static final String  XTCE_CONTEXT_ALARM_LIST            = "ContextAlarmList";
    private static final String  XTCE_CONTEXT_ALARM                 = "ContextAlarm";
    private static final String  XTCE_CONTEXT_MATCH                 = "ContextMatch";
    private static final String  XTCE_DEFAULT_CALIBRATOR            = "DefaultCalibrator";
    private static final String  XTCE_CALIBRATOR                    = "Calibrator";
    private static final String  XTCE_CONTEXT_CALIBRATOR            = "ContextCalibrator";
    private static final String  XTCE_CONTEXT_CALIBRATOR_LIST       = "ContextCalibratorList";
    private static final String  XTCE_SPLINE_CALIBRATOR             = "SplineCalibrator";
    private static final String  XTCE_POLYNOMIAL_CALIBRATOR         = "PolynomialCalibrator";
    private static final String  XTCE_MATH_OPERATION_CALIBRATOR     = "MathOperationCalibrator";
    private static final String  XTCE_TERM                          = "Term";
    private static final String  XTCE_SPLINE_POINT                  = "SplinePoint";
    private static final String  XTCE_COUNT                         = "Count";
    private static final String  XTCE_INTEGER_VALUE                 = "IntegerValue";
    private static final String  XTCE_PARAMETER_INSTANCE_REF        = "ParameterInstanceRef";
    private static final String  XTCE_STATIC_ALARM_RANGES           = "StaticAlarmRanges";
    private static final String  XTCE_DEFAULT_ALARM                 = "DefaultAlarm";
    private static final String  XTCE_FIXED                         = "Fixed";
    private static final String  XTCE_TERMINATION_CHAR              = "TerminationChar";
    private static final String  XTCE_LEADING_SIZE                  = "LeadingSize";
    private static final String  XTCE_DEFAULT_RATE_IN_STREAM        = "DefaultRateInStream";
    private static final String  XTCE_REFERENCE_TIME                = "ReferenceTime";
    private static final String  XTCE_OFFSET_FROM                   = "OffsetFrom";
    private static final String  XTCE_EPOCH                         = "Epoch";
    private static final String  XTCE_ENCODING                      = "Encoding";
    private static final String  XTCE_ARGUMENT_TYPE_SET             = "ArgumentTypeSet";
    private static final String  XTCE_META_COMMAND_SET              = "MetaCommandSet";
    private static final String  XTCE_META_COMMAND                  = "MetaCommand";
    private static final String  XTCE_COMMAND_CONTAINER             = "CommandContainer";
    private static final String  XTCE_STRING_ARGUMENT_TYPE          = "StringArgumentType";
    private static final String  XTCE_BINARY_ARGUMENT_TYPE          = "BinaryArgumentType";
    private static final String  XTCE_INTEGER_ARGUMENT_TYPE         = "IntegerArgumentType";
    private static final String  XTCE_FLOAT_ARGUMENT_TYPE           = "FloatArgumentType";
    private static final String  XTCE_BOOLEAN_ARGUMENT_TYPE         = "BooleanArgumentType";
    private static final String  XTCE_ENUMERATED_ARGUMENT_TYPE      = "EnumeratedArgumentType";
    private static final String  XTCE_BASE_META_COMMAND             = "BaseMetaCommand";
    private static final String  XTCE_ARGUMENT_LIST                 = "ArgumentList";
    private static final String  XTCE_ARGUMENT_ASSIGNMENT_LIST      = "ArgumentAssignmentList";
    private static final String  XTCE_ARGUMENT                      = "Argument";
    private static final String  XTCE_ARGUMENT_REF_ENTRY            = "ArgumentRefEntry";
    private static final String  XTCE_ARRAY_ARGUMENT_REF_ENTRY      = "ArrayArgumentRefEntry";
    private static final String  XTCE_FIXED_VALUE_ENTRY             = "FixedValueEntry";
    private static final String  XTCE_VALUE_OPERAND                 = "ValueOperand";
    /**
     * Logging subsystem
     */
    private static Logger                log                            = LoggerFactory.getLogger(XtceStaxReader.class);

    /**
     * XML Event reader
     */
    private XMLEventReader       xmlEventReader                 = null;

    /**
     * XML Event
     */
    private XMLEvent             xmlEvent                       = null;

    /**
     * Statistics about the skipped sections. (good for overview about unimplemented features)
     */
    private Map<String, Integer> xtceSkipStatistics             = new HashMap<String, Integer>();
    private Set<String> excludedContainers = new HashSet<String>();
    String fileName;
    /**
     * Constructor
     */
    public XtceStaxReader() {
    }


    /**
     * Reading of the XML XTCE file
     * @param fileName 
     * 
     * @return returns the SpaceSystem read from the XML file
     * @throws XMLStreamException 
     * @throws IOException 
     * 
     */
    public SpaceSystem readXmlDocument(String fileName) throws XMLStreamException, IOException  {
        this.fileName = fileName;
        xmlEventReader = initEventReader(fileName);
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
        log.info("XTCE file parsing finished successfully");
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
        log.trace("XML version=\"" + start.getVersion() + "\" encoding: \""
                + start.getCharacterEncodingScheme() + "\"");
    }

    /**
     * Start of reading at the root of the document. According to the XTCE
     * schema the root element is &lt;SpaceSystem&gt;
     * 
     * @throws XMLStreamException
     */
    private SpaceSystem readSpaceSystem() throws XMLStreamException {
        checkStartElementPreconditions();

        String value = readAttribute("name", xmlEvent.asStartElement());
        SpaceSystem spaceSystem = new SpaceSystem(value);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_ALIAS_SET)) {
                XtceAliasSet aliasSet=readXtceAliasSet();
                spaceSystem.setAliasSet(aliasSet);
            } else if (isStartElementWithName(XTCE_HEADER)) {
                readXtceHeader(spaceSystem);
            } else if (isStartElementWithName(XTCE_TELEMTRY_META_DATA)) {
                readXtceTelemetryMetaData(spaceSystem);
            } else if (isStartElementWithName(XTCE_COMMAND_MEATA_DATA)) {
                readXtceCommandMetaData(spaceSystem);
            } else if (isStartElementWithName(XTCE_SPACE_SYSTEM)) {
                SpaceSystem ss= readSpaceSystem();
                spaceSystem.addSpaceSystem(ss);
            } else if (isEndElementWithName(XTCE_SPACE_SYSTEM)) {
                return spaceSystem;
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
    private XtceAliasSet readXtceAliasSet() throws XMLStreamException {
        log.trace(XTCE_ALIAS_SET);
        checkStartElementPreconditions();

        XtceAliasSet xtceAliasSet = new XtceAliasSet();

        while (true) {

            xmlEvent = xmlEventReader.nextEvent();

            // <Alias> sections
            if (isStartElementWithName(XTCE_ALIAS)) {
                readXtceAlias(xtceAliasSet);
            } else if (isEndElementWithName(XTCE_ALIAS_SET)) {
                return xtceAliasSet;
            }
        }
    }

    /**
     * Extraction of the AliasSet section Current implementation does nothing,
     * just skips whole section
     * 
     * @throws XMLStreamException
     */
    private void readXtceAlias(XtceAliasSet aliasSet) throws XMLStreamException {
        log.trace(XTCE_ALIAS);
        checkStartElementPreconditions();

        String nameSpace = readAttribute("nameSpace", xmlEvent.asStartElement());
        if (nameSpace == null) {
            throw new XMLStreamException("Namespace attribute is missing");
        }
        nameSpace = nameSpace.intern();
        String alias = readAttribute("alias", xmlEvent.asStartElement());
        if (alias == null) {
            throw new XMLStreamException("Alias attribute is missing");
        }

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
     * @param spaceSystem 
     * 
     * @throws XMLStreamException
     */
    private void readXtceHeader(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_HEADER);
        checkStartElementPreconditions();
        Header h=new Header();

        String value = readAttribute("version", xmlEvent.asStartElement());
        h.setVersion(value);

        value = readAttribute("date", xmlEvent.asStartElement());
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
            }
        }
    }

    /**
     * Extraction of the TelemetryMetaData section
     * @param spaceSystem 
     * 
     * @throws XMLStreamException
     */
    private void readXtceTelemetryMetaData(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_TELEMTRY_META_DATA);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_PARAMETER_TYPE_SET)) {
                readXtceParameterTypeSet(spaceSystem);
            } else if (isStartElementWithName(XTCE_PARAMETER_SET)) {
                readXtceParameterSet(spaceSystem);
            } else if (isStartElementWithName(XTCE_CONTAINER_SET)) {
                readXtceContainerSet(spaceSystem);
            } else if (isStartElementWithName(XTCE_MESSAGE_SET)) {
                readXtceMessageSet();
            } else if (isStartElementWithName(XTCE_STREAM_SET)) {
                readXtceStreamSet();
            } else if (isStartElementWithName(XTCE_ALGORITHM_SET)) {
                readXtceAlgorithmSet(spaceSystem);
            } else if (isEndElementWithName(XTCE_TELEMTRY_META_DATA)) {
                return;
            }
        }
    }

    /**
     * @param spaceSystem 
     * @throws XMLStreamException
     */
    private void readXtceParameterTypeSet(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_PARAMETER_TYPE_SET);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            ParameterType parameterType = null;

            if (isStartElementWithName(XTCE_BOOLEAN_PARAMETER_TYPE)) {
                parameterType = readXtceBooleanParameterType(spaceSystem);
            } else if (isStartElementWithName(XTCE_ENUMERATED_PARAMETER_TYPE)) {
                parameterType = readXtceEnumeratedParameterType(spaceSystem);
            } else if (isStartElementWithName(XTCE_FLOAT_PARAMETER_TYPE)) {
                parameterType = readXtceFloatParameterType(spaceSystem);
            } else if (isStartElementWithName(XTCE_INTEGER_PARAMETER_TYPE)) {
                parameterType = readXtceIntegerParameterType(spaceSystem);
            } else if (isStartElementWithName(XTCE_BINARY_PARAMETER_TYPE)) {
                parameterType = readXtceBinaryParameterType(spaceSystem);
            } else if (isStartElementWithName(XTCE_STRING_PARAMETER_TYPE)) {
                parameterType = readXtceStringParameterType(spaceSystem);
            } else if (isStartElementWithName(XTCE_RelativeTimeParameterType)) {
                parameterType = readXtceRelativeTimeParameterType();
            } else if (isStartElementWithName(XTCE_ABSOLUTE_TIME_PARAMETER_TYPE)) {
                parameterType = readXtceAbsoluteTimeParameterType(spaceSystem);
            } else if (isStartElementWithName(XTCE_ArrayParameterType)) {
                parameterType = readXtceArrayParameterType();
            } else if (isStartElementWithName(XTCE_AggregateParameterType)) {
                parameterType = readXtceAggregateParameterType();
            }

            if (parameterType != null) {
                spaceSystem.addParameterType(parameterType);
            }

            if (isEndElementWithName(XTCE_PARAMETER_TYPE_SET)) {
                return;
            }
        }
    }

    private BooleanParameterType readXtceBooleanParameterType(SpaceSystem spaceSystem) throws IllegalStateException, XMLStreamException {
        log.trace(XTCE_BOOLEAN_PARAMETER_TYPE);
        checkStartElementPreconditions();

        // name attribute
        BooleanParameterType boolParamType = null;
        String name = readAttribute("name", xmlEvent.asStartElement());
        if (name != null) {
            boolParamType = new BooleanParameterType(name);
        } else {
            throw new XMLStreamException("Unnamed boolean parameter type");
        }

        // read all parameters

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_UNIT_SET)) {
                boolParamType.addAllUnits(readXtceUnitSet());               
            } else if (isStartElementWithName(XTCE_INTEGER_DATA_ENCODING)) {
                boolParamType.setEncoding(readXtceIntegerDataEncoding(spaceSystem));
            } else if (isEndElementWithName(XTCE_BOOLEAN_PARAMETER_TYPE)) {
                return boolParamType;
            }
        }
    }

    private ParameterType readXtceAggregateParameterType() throws IllegalStateException,
    XMLStreamException {
        skipXtceSection(XTCE_AggregateParameterType);
        return null;
    }

    private ParameterType readXtceArrayParameterType() throws IllegalStateException,
    XMLStreamException {
        skipXtceSection(XTCE_ArrayParameterType);
        return null;
    }

    private AbsoluteTimeParameterType readXtceAbsoluteTimeParameterType(SpaceSystem spaceSystem) throws IllegalStateException,
    XMLStreamException {
        AbsoluteTimeParameterType ptype = null;
        log.trace(XTCE_ABSOLUTE_TIME_PARAMETER_TYPE);
        checkStartElementPreconditions();
        
        String value = readAttribute("name", xmlEvent.asStartElement());
        if (value != null) {
            ptype = new AbsoluteTimeParameterType(value);
        } else {
            throw new XMLStreamException("Unnamed absolute time parameter type");
        }
      
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_REFERENCE_TIME)) {
                ptype.setReferenceTime(readXtceReferenceTime(spaceSystem));
            } else if (isStartElementWithName(XTCE_ENCODING)) {
                readXtceEncoding(spaceSystem, ptype);
            } else if (isEndElementWithName(XTCE_ABSOLUTE_TIME_PARAMETER_TYPE)) {
                return ptype;
            }
        }
    }

    
    private ParameterType readXtceRelativeTimeParameterType() throws IllegalStateException,
    XMLStreamException {
        skipXtceSection(XTCE_RelativeTimeParameterType);
        return null;
    }

    private ReferenceTime readXtceReferenceTime(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_REFERENCE_TIME);
        checkStartElementPreconditions();
        
        ReferenceTime referenceTime = new ReferenceTime();
        
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_OFFSET_FROM)) {
                referenceTime.setOffsetFrom(readXtceParameterInstanceRef(spaceSystem));
            } else if (isStartElementWithName(XTCE_EPOCH)) {
                referenceTime.setEpoch(readXtceEpoch());
            } else if (isEndElementWithName(XTCE_REFERENCE_TIME)) {
                return referenceTime;
            }
        }
    }
    private TimeEpoch readXtceEpoch() throws XMLStreamException {
        log.trace(XTCE_EPOCH);
        String s = readStringBetweenTags(XTCE_EPOCH);
        try {
            return new TimeEpoch(TimeEpoch.CommonEpochs.valueOf(s));
        } catch (IllegalArgumentException e) {
            //TODO validate the date
            return new TimeEpoch(s);
        }
    }
    
    private void readXtceEncoding(SpaceSystem spaceSystem, AbsoluteTimeParameterType ptype) throws XMLStreamException {
        log.trace(XTCE_ENCODING);
        checkStartElementPreconditions();
        // name attribute
        String units = readAttribute("units", xmlEvent.asStartElement());
        if ((units != null) && (!"seconds".equals(units))) {
            throw new XMLStreamException("Unsupported unit types '"+units+"' for time encoding. Only seconds (with scaling) supported");
        } 
        boolean needsScaling = false;
        double offset = 0d;
        double scale = 1d;
        String offsets = readAttribute("offset", xmlEvent.asStartElement());
        if(offsets!=null) {
            needsScaling = true;
            offset = Double.parseDouble(offsets);
        }
        String scales = readAttribute("scale", xmlEvent.asStartElement());
        if(scales!=null) {
            needsScaling = true;
            scale = Double.parseDouble(scales);
        }
        ptype.setScaling(needsScaling, offset, scale);
        
        DataEncoding dataEncoding = null;
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
             if (isStartElementWithName(XTCE_INTEGER_DATA_ENCODING)) {
                 dataEncoding = readXtceIntegerDataEncoding(spaceSystem);
            } else if (isStartElementWithName(XTCE_FLOAT_DATA_ENCODING)) {
                dataEncoding = readXtceFloatDataEncoding(spaceSystem);
            } else if (isEndElementWithName(XTCE_ENCODING)) {
                ptype.setEncoding(dataEncoding);
                return;
            }
        }
    }
    
    private FloatParameterType readXtceFloatParameterType(SpaceSystem spaceSystem) throws IllegalStateException,  XMLStreamException {
        FloatParameterType floatParamType = null;
        log.trace(XTCE_FLOAT_PARAMETER_TYPE);
        checkStartElementPreconditions();

        // name attribute
        String value = readAttribute("name", xmlEvent.asStartElement());
        if (value != null) {
            floatParamType = new FloatParameterType(value);
        } else {
            throw new XMLStreamException("Unnamed float parameter type");
        }

        value = readAttribute("sizeInBits", xmlEvent.asStartElement());
        
        if (value != null) {
            int sizeInBits = Integer.parseInt(value);
            if(sizeInBits!=32 && sizeInBits!=64){
                throw new XMLStreamException("Float encoding "+sizeInBits+" not supported; Only 32 and 64 bits are supported");
            }
            floatParamType.setSizeInBits(sizeInBits);
        } 
        
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_UNIT_SET)) {
                floatParamType.addAllUnits(readXtceUnitSet());
            } else if (isStartElementWithName(XTCE_INTEGER_DATA_ENCODING)) {
                floatParamType.setEncoding(readXtceIntegerDataEncoding(spaceSystem));
            } else if (isStartElementWithName(XTCE_FLOAT_DATA_ENCODING)) {
                floatParamType.setEncoding(readXtceFloatDataEncoding(spaceSystem));
            } else if (isStartElementWithName(XTCE_DEFAULT_ALARM)) {
                floatParamType.setDefaultAlarm(readDefaultAlarm());
            } else if (isStartElementWithName(XTCE_CONTEXT_ALARM_LIST)) {
                floatParamType.setContextAlarmList(readNumericContextAlarmList(spaceSystem));                
            } else if (isEndElementWithName(XTCE_FLOAT_PARAMETER_TYPE)) {
                return floatParamType;
            }
        }
    }

    private FloatDataEncoding readXtceFloatDataEncoding(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_FLOAT_DATA_ENCODING);
        checkStartElementPreconditions();

        FloatDataEncoding floatDataEncoding = null;
        String name = "";

        // sizeInBits attribute
        String value = readAttribute("sizeInBits", xmlEvent.asStartElement());
        if (value != null) {
            floatDataEncoding = new FloatDataEncoding(Integer.parseInt(value));
        } else {
            // default value is 32
            floatDataEncoding = new FloatDataEncoding(32);
        }

        // encoding attribute
        value = readAttribute("encoding", xmlEvent.asStartElement());
        if (value != null) {
            if ("IEEE754_1985".equalsIgnoreCase(value)) {
                // ok, this encoding is supported by the class implicitly
            } else if ("MILSTD_1750A".equalsIgnoreCase(value)) {
                log.error("Encoding MILSTD_1750A is not currently supported.");
                throw new XMLStreamException("Encoding MILSTD_1750A is not currently supported.");
            } else {
                throw new XMLStreamException();
            }
        } else {
            // default is IEEE754_1985
            // this encoding is supported by the class implicitly
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_DEFAULT_CALIBRATOR)) {
                floatDataEncoding.setDefaultCalibrator(readCalibrator(spaceSystem));
            } else if (isStartElementWithName(XTCE_CONTEXT_CALIBRATOR_LIST)) {
                floatDataEncoding.setContextCalibratorList(readContextCalibratorList(spaceSystem));
            } else if (isEndElementWithName(XTCE_FLOAT_DATA_ENCODING)) {
                return floatDataEncoding;
            }
        }
    }
        
    private List<NumericContextAlarm> readNumericContextAlarmList(SpaceSystem spaceSystem) throws IllegalStateException, XMLStreamException  {
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
            } else if(xmlEvent.getEventType() == XMLStreamConstants.START_ELEMENT){
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
            } else if (isEndElementWithName(XTCE_CONTEXT_ALARM)) {
                return nca;
            } else if(xmlEvent.getEventType() == XMLStreamConstants.START_ELEMENT){
                readNumericAlarmElement(nca);
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
            }  else if (isStartElementWithName("WarningRange")) {
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


    private DoubleRange readFloatRange() {
        StartElement e = xmlEvent.asStartElement();
        double minExclusive = Double.NaN;
        double maxExclusive = Double.NaN;
        double minInclusive = Double.NaN;
        double maxInclusive = Double.NaN;
        
        String value = readAttribute("minExclusive", e);        
        if (value != null) {
            minExclusive = Double.parseDouble(value);            
        } 
        
        value = readAttribute("maxExclusive", e);        
        if (value != null) {
            maxExclusive = Double.parseDouble(value);            
        } 
        value = readAttribute("minInclusive", e);        
        if (value != null) {
            minInclusive = Double.parseDouble(value);            
        } 
        
        value = readAttribute("maxInclusive", e);        
        if (value != null) {
            maxInclusive = Double.parseDouble(value);            
        } 
       
        
        return DoubleRange.fromXtceComplement(minExclusive, maxExclusive, minInclusive, maxInclusive);
    }


    private void readAlarmAttributes(NumericAlarm numericAlarm) {
        String value = readAttribute("minViolations", xmlEvent.asStartElement());
        if (value != null) {
            int minViolations = Integer.parseInt(value);
            numericAlarm.setMinViolations(minViolations);
        }
    }
    
    
    private BinaryParameterType readXtceBinaryParameterType(SpaceSystem spaceSystem) throws IllegalStateException, XMLStreamException {
        log.trace(XTCE_BINARY_PARAMETER_TYPE);
        checkStartElementPreconditions();

        // name attribute
        BinaryParameterType binaryParamType = null;
        String name = readAttribute("name", xmlEvent.asStartElement());
        if (name != null) {
            binaryParamType = new BinaryParameterType(name);
        } else {
            throw new XMLStreamException("Unnamed binary parameter type");
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_UNIT_SET)) {
                binaryParamType.addAllUnits(readXtceUnitSet());
            } else if (isStartElementWithName(XTCE_INTEGER_DATA_ENCODING)) {
                binaryParamType.setEncoding(readXtceIntegerDataEncoding(spaceSystem));
            } else if (isStartElementWithName(XTCE_BINARY_DATA_ENCODING)) {
                binaryParamType.setEncoding(readXtceBinaryDataEncoding(spaceSystem));
            } else if (isEndElementWithName(XTCE_BINARY_PARAMETER_TYPE)) {
                return binaryParamType;
            }
        }
    }

    private DataEncoding readXtceBinaryDataEncoding(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_BINARY_DATA_ENCODING);
        checkStartElementPreconditions();

        BinaryDataEncoding binaryDataEncoding = null;
        String name = "";

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_SIZE_IN_BITS)) {
                IntegerValue v = readXtceIntegerValue(spaceSystem);
                if(v instanceof FixedIntegerValue) {
                    binaryDataEncoding = new BinaryDataEncoding((int)((FixedIntegerValue) v).getValue());
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
        throw new XMLStreamException(msg+" at "+xmlEvent.getLocation().getLineNumber()+": "+xmlEvent.getLocation().getColumnNumber());
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
            } catch(NumberFormatException e) {
                throw new XMLStreamException("Cannot parse integer '"+value+"' at "+xmlEvent.getLocation().getLineNumber()+":"+xmlEvent.getLocation().getColumnNumber());
            }
        } else {
            throw new IllegalStateException();
        }
    }
    private StringParameterType readXtceStringParameterType(SpaceSystem spaceSystem) throws IllegalStateException, XMLStreamException {
        log.trace(XTCE_INTEGER_PARAMETER_TYPE);
        checkStartElementPreconditions();
        StringParameterType stringParamType = null;
        
        // name attribute
        String value = readAttribute("name", xmlEvent.asStartElement());
        if (value != null) {
            stringParamType = new StringParameterType(value);
        } else {
            throw new XMLStreamException("Unnamed string parameter type");
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_UNIT_SET)) {
                stringParamType.addAllUnits(readXtceUnitSet());
            } else if (isStartElementWithName(XTCE_STRING_DATA_ENCODING)) {
                stringParamType.setEncoding(readXtceStringDataEncoding(spaceSystem));
            } else if (isStartElementWithName(XTCE_CONTEXT_ALARM_LIST)) {
                skipXtceSection(XTCE_CONTEXT_ALARM_LIST);
            } else if (isEndElementWithName(XTCE_STRING_PARAMETER_TYPE)) {
                return stringParamType;
            }
        }
    }
    

    private StringDataEncoding readXtceStringDataEncoding(SpaceSystem spaceSystem) throws XMLStreamException {
        checkStartElementPreconditions();

        StringDataEncoding stringDataEncoding = new StringDataEncoding();;
      
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_SIZE_IN_BITS)) {
                readStringSizeInBits(spaceSystem, stringDataEncoding);
            } else if (isEndElementWithName(XTCE_STRING_DATA_ENCODING)) {
                return stringDataEncoding;
            }
        }
    }


    private void readStringSizeInBits(SpaceSystem spaceSystem, StringDataEncoding stringDataEncoding) throws XMLStreamException {
        checkStartElementPreconditions();
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_FIXED)) {
                IntegerValue v = readXtceIntegerValue(spaceSystem);
                if(v instanceof FixedIntegerValue) {
                    stringDataEncoding.setSizeType(SizeType.FIXED);
                    stringDataEncoding.setSizeInBits((int)((FixedIntegerValue) v).getValue());
                } else {
                    throwException("Only FixedValue supported for string size in bits");
                }
             } else if (isStartElementWithName(XTCE_TERMINATION_CHAR)) {
                stringDataEncoding.setSizeType(SizeType.TERMINATION_CHAR);
                byte[] x = readHexBinary();
                if(x==null || x.length!=1) {
                    throw new XMLStreamException("Terminated strings have to have the size of the termination character of 1");
                }
                stringDataEncoding.setTerminationChar(x[0]);
            } else if (isStartElementWithName(XTCE_LEADING_SIZE)) {
                stringDataEncoding.setSizeType(SizeType.LEADING_SIZE);
                String value = readAttribute("sizeInBitsOfSizeTag", xmlEvent.asStartElement());
                stringDataEncoding.setSizeInBitsOfSizeTag(Integer.valueOf(value));
            } else if (isEndElementWithName(XTCE_SIZE_IN_BITS)) {
                return;
            }
        }
    }


    private byte[] readHexBinary() throws XMLStreamException {
        checkStartElementPreconditions();
        String tag = xmlEvent.asStartElement().getName().getLocalPart();
        byte[] b=null;

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (xmlEvent.isCharacters()) {
                b = StringConverter.hexStringToArray(xmlEvent.asCharacters().getData());
            } else if (isEndElementWithName(tag)) {
                return b;
            }
        }
    }


    private IntegerParameterType readXtceIntegerParameterType(SpaceSystem spaceSystem) throws IllegalStateException, XMLStreamException {
        IntegerParameterType integerParamType;

        log.trace(XTCE_INTEGER_PARAMETER_TYPE);
        checkStartElementPreconditions();

        // name attribute
        String value = readAttribute("name", xmlEvent.asStartElement());
        if (value != null) {
            integerParamType = new IntegerParameterType(value);
        } else {
            throw new XMLStreamException("Unnamed integer parameter type");
        }

        value = readAttribute("sizeInBits", xmlEvent.asStartElement());
        if (value != null) {
            int sizeInBits = Integer.parseInt(value);
            integerParamType.setSizeInBits(sizeInBits);
        }
        value = readAttribute("signed", xmlEvent.asStartElement());
        if (value != null) {
            boolean signed = Boolean.parseBoolean(value);
            integerParamType. setSigned(signed);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_UNIT_SET)) {
                integerParamType.addAllUnits(readXtceUnitSet());
            } else if (isStartElementWithName(XTCE_INTEGER_DATA_ENCODING)) {
                integerParamType.setEncoding(readXtceIntegerDataEncoding(spaceSystem));
            } else if (isStartElementWithName(XTCE_DEFAULT_ALARM)) {
                integerParamType.setDefaultAlarm(readDefaultAlarm());             
            } else if (isStartElementWithName(XTCE_CONTEXT_ALARM_LIST)) {
                integerParamType.setContextAlarmList(readNumericContextAlarmList(spaceSystem));
            } else if (isEndElementWithName(XTCE_INTEGER_PARAMETER_TYPE)) {
                return integerParamType;
            }
        }
    }

    private IntegerDataEncoding readXtceIntegerDataEncoding(SpaceSystem spaceSystem) throws IllegalStateException,
    XMLStreamException {
        log.trace(XTCE_INTEGER_DATA_ENCODING);
        checkStartElementPreconditions();

        IntegerDataEncoding integerDataEncoding = null;

        // sizeInBits attribute
        String value = readAttribute("sizeInBits", xmlEvent.asStartElement());
        if (value != null) {
            integerDataEncoding = new IntegerDataEncoding(Integer.parseInt(value));
        } else {
            // default value is 8
            integerDataEncoding = new IntegerDataEncoding(8);
        }

        // encoding attribute
        value = readAttribute("encoding", xmlEvent.asStartElement());
        if (value != null) {
            if ("unsigned".equalsIgnoreCase(value)) {
                integerDataEncoding.setEncoding(IntegerDataEncoding.Encoding.UNSIGNED);
            } else if ("signMagnitude".equalsIgnoreCase(value)) {
                integerDataEncoding.setEncoding(IntegerDataEncoding.Encoding.SIGN_MAGNITUDE);
            } else if ("twosComplement".equalsIgnoreCase(value)) {
                integerDataEncoding.setEncoding(IntegerDataEncoding.Encoding.TWOS_COMPLEMENT);
            } else if ("twosCompliment".equalsIgnoreCase(value)) { //this is for compatibility with CD-MCS/CGS SCOE XML exporter
                integerDataEncoding.setEncoding(IntegerDataEncoding.Encoding.TWOS_COMPLEMENT);
            } else if ("onesComplement".equalsIgnoreCase(value)) {
                integerDataEncoding.setEncoding(IntegerDataEncoding.Encoding.ONES_COMPLEMENT);
            } else {
                throwException("Unsupported encoding '"+value+"'");
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
                calibrator = readXtcePolynomialCalibrator();
            } else if (isStartElementWithName(XTCE_MATH_OPERATION_CALIBRATOR)) {
                calibrator = (Calibrator) readMathOperation(spaceSystem, true);
            } else if (isStartElementWithName(XTCE_SPLINE_CALIBRATOR)) {
                calibrator = readXtceSplineCalibrator();
            } else if (isEndElementWithName(tag)) {
                return calibrator;
            }
        }
    }

    private MathOperation readMathOperation(SpaceSystem spaceSystem, boolean calibrator) throws XMLStreamException {

        String tag = xmlEvent.asStartElement().getName().getLocalPart();
        MathOperation mathOp;
        
        List<MathOperation.Element> list = new ArrayList<>();
        AlarmRanges ar = new AlarmRanges();
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_VALUE_OPERAND)) {
                list.add(new MathOperation.Element(readDouble()));
            }  else if (isStartElementWithName("ThisParameterOperand")) {
                skipQuietly("ThisParameterOperand");
                list.add(new MathOperation.Element());
            } else if (isStartElementWithName("ParameterInstanceRefOperand")) {
                list.add(new MathOperation.Element(readXtceParameterInstanceRef(spaceSystem)));
            } else if (isStartElementWithName("Operator")) {
                list.add(new MathOperation.Element(readMathOperator()));
            } else if (isEndElementWithName(tag)) {
                if(calibrator) {
                    mathOp =new MathOperationCalibrator(list);
                } else {
                    mathOp = new MathOperation(list);
                }
                return mathOp;
            }
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


    private ContextCalibrator readContextCalibrator(SpaceSystem spaceSystem) throws IllegalStateException, XMLStreamException {
        log.trace(XTCE_CONTEXT_CALIBRATOR);
        checkStartElementPreconditions();

        MatchCriteria context = null; 
        Calibrator calibrator = null; 
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_CONTEXT_MATCH)) {
                context = readMatchCriteria(spaceSystem);
            } else if (isStartElementWithName(XTCE_CALIBRATOR)) {
                calibrator=readCalibrator(spaceSystem);
            } else if (isEndElementWithName(XTCE_CONTEXT_CALIBRATOR)) {
                if(context==null) {
                    throw new XMLStreamException("Invalid context calibrator, no context specified");
                }
                if(calibrator==null) {
                    throw new XMLStreamException("Invalid context calibrator, no calibrator specified");
                }
                return new ContextCalibrator(context, calibrator);
            }
        }
    }


    /**
     * Instantiate the SplineCalibrator element.
     * @return
     * @throws XMLStreamException
     */
    private Calibrator readXtceSplineCalibrator() throws XMLStreamException {
        log.trace(XTCE_SPLINE_CALIBRATOR);
        checkStartElementPreconditions();

        ArrayList<SplinePoint> splinePoints = new ArrayList<SplinePoint>();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_SPLINE_POINT)) {
                splinePoints.add(readXtceSplinePoint());
            } else if (isEndElementWithName(XTCE_SPLINE_CALIBRATOR)) {
                return new SplineCalibrator(splinePoints);
            }
        }
    }

    /**
     * Instantiate SplinePoint element.
     * This element has two required attributes: raw, calibrated
     * @return
     * @throws XMLStreamException 
     */
    private SplinePoint readXtceSplinePoint() throws XMLStreamException {
        log.trace(XTCE_SPLINE_POINT);
        checkStartElementPreconditions();

        double raw = 0.0d;
        double calibrated = 0.0d;

        String attributeValue = readAttribute("raw", xmlEvent.asStartElement());
        if (attributeValue != null) {
            raw = Double.parseDouble(attributeValue);
        } else {
            throw new XMLStreamException();
        }

        attributeValue = readAttribute("calibrated", xmlEvent.asStartElement());
        if (attributeValue != null) {
            calibrated = Double.parseDouble(attributeValue);
        } else {
            throw new XMLStreamException();
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isEndElementWithName(XTCE_SPLINE_POINT)) {
                return new SplinePoint(raw, calibrated);
            }
        }
    }

    private Calibrator readXtcePolynomialCalibrator() throws XMLStreamException {
        log.trace(XTCE_POLYNOMIAL_CALIBRATOR);
        checkStartElementPreconditions();

        int maxExponent = 0;
        HashMap<Integer, Double> polynome = new HashMap<Integer, Double>();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_TERM)) {
                XtceTerm term = readXtceTerm();
                if (term.getExponent() > maxExponent) {
                    maxExponent = term.getExponent();
                }
                polynome.put(term.getExponent(), term.getCoefficient());
            } else if (isEndElementWithName(XTCE_POLYNOMIAL_CALIBRATOR)) {
                double[] coefficients = new double[maxExponent + 1];
                for (Map.Entry<Integer, Double> entry:polynome.entrySet()) {
                    coefficients[entry.getKey()] = entry.getValue();
                }
                return new PolynomialCalibrator(coefficients);
            }
        }
    }

    private XtceTerm readXtceTerm() throws XMLStreamException {
        log.trace(XTCE_TERM);
        checkStartElementPreconditions();

        int exponent = 0;
        double coefficient = 0.0;

        String value = readAttribute("coefficient", xmlEvent.asStartElement());
        if (value != null) {
            coefficient = Double.parseDouble(value);
        } else {
            throw new XMLStreamException();
        }

        value = readAttribute("exponent", xmlEvent.asStartElement());
        if (value != null) {
            exponent = (int) Double.parseDouble(value);
        } else {
            throw new XMLStreamException();
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isEndElementWithName(XTCE_TERM)) {
                return new XtceTerm(exponent, coefficient);
            }
        }
    }

    private List<UnitType> readXtceUnitSet() throws IllegalStateException, XMLStreamException {
        log.trace(XTCE_UNIT_SET);

        List<UnitType> units = new ArrayList<UnitType>();
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_UNIT)) {
                UnitType u = readXtceUnit();
                units.add(u);
            } else if (isEndElementWithName(XTCE_UNIT_SET)) {
                return units;
            }
        }
    }

    private Double readDouble() throws XMLStreamException {
        String tag = xmlEvent.asStartElement().getName().getLocalPart();
        Double d = null;
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (xmlEvent.isCharacters()) {
                d = Double.parseDouble(xmlEvent.asCharacters().getData());
            } else if (isEndElementWithName(tag)) {
                return d;
            }
        }
    }

    private UnitType readXtceUnit() throws XMLStreamException {
        log.trace(XTCE_UNIT);
        checkStartElementPreconditions();


        StartElement element = xmlEvent.asStartElement();

        String powerValue = readAttribute("power", element);
        String factorValue = readAttribute("factor", element);
        String descriptionValue = readAttribute("description", element);
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
        if ( powerValue != null ) {
            unitType.setPower( Double.parseDouble(powerValue));
        }
        if ( factorValue != null ) {
            unitType.setFactor( factorValue );
        }
        if ( descriptionValue != null ) {
            unitType.setDescription( descriptionValue );
        }

        return unitType;
    }

    private EnumeratedParameterType readXtceEnumeratedParameterType(SpaceSystem spaceSystem) throws IllegalStateException, XMLStreamException {
        EnumeratedParameterType enumParamType = null;

        log.trace(XTCE_ENUMERATED_PARAMETER_TYPE);
        checkStartElementPreconditions();

        // name attribute
        String value = readAttribute("name", xmlEvent.asStartElement());
        if (value != null) {
            enumParamType = new EnumeratedParameterType(value);
        } else {
            throw new XMLStreamException();
        }

        // defaultValue attribute
        value = readAttribute("defaultValue", xmlEvent.asStartElement());
        if (value != null) {
            enumParamType.setInitialValue(value);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_UNIT_SET)) {
                enumParamType.addAllUnits(readXtceUnitSet());               
            } else if (isStartElementWithName(XTCE_INTEGER_DATA_ENCODING)) {
                enumParamType.setEncoding(readXtceIntegerDataEncoding(spaceSystem));
            } else if (isStartElementWithName(XTCE_ENUMERATION_LIST)) {
                readXtceEnumerationList(enumParamType);
            } else if (isStartElementWithName(XTCE_DEFAULT_ALARM)) {
                enumParamType.setDefaultAlarm(readXtceEnumerationAlarm(enumParamType));
            } else if (isEndElementWithName(XTCE_ENUMERATED_PARAMETER_TYPE)) {
                return enumParamType;
            }
        }
    }

    private void readXtceEnumerationList(EnumeratedDataType enumDataType) throws XMLStreamException {
        log.trace(XTCE_ENUMERATION_LIST);
        checkStartElementPreconditions();

        // initialValue attribute
        String initialValue = readAttribute("initialValue", xmlEvent.asStartElement());
        if (initialValue != null) {
            enumDataType.setInitialValue(initialValue);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_ENUMERATION)) {
                readXtceEnumeration(enumDataType);
            } else if (isStartElementWithName(XTCE_RANGE_ENUMERATION)) {
                enumDataType.addEnumerationRange(readXtceRangeEnumeration());
            } else if (isEndElementWithName(XTCE_ENUMERATION_LIST)) {
                return;
            }
        }
    }
    
    private EnumerationAlarm readXtceEnumerationAlarm(EnumeratedParameterType enumParamType) throws XMLStreamException {
        checkStartElementPreconditions();
        String tag = xmlEvent.asStartElement().getName().getLocalPart();
        EnumerationAlarm alarm = new EnumerationAlarm();
        
        // initialValue attribute
        String v = readAttribute("minViolations", xmlEvent.asStartElement());
        if (v != null) {
            alarm.setMinViolations(Integer.parseInt(v));
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName("EnumerationAlarm")) {
                String label = readAttribute("enumerationLabel", xmlEvent.asStartElement());
                if(label==null) {
                    label = readAttribute("enumerationValue", xmlEvent.asStartElement()); //XTCE 1.1
                }
                if(label==null) {
                    throw new XMLStreamException(fileName+": error in In definition of "+enumParamType.getName()+"EnumerationAlarm: no enumerationLabel specified", xmlEvent.getLocation());
                }
                if(!enumParamType.hasLabel(label)) {
                    throw new XMLStreamException("Reference to invalid enumeration label '"+label+"'");
                }
                AlarmLevels level = getAlarmLevel(readAttribute("alarmLevel", xmlEvent.asStartElement()));
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
            throw new XMLStreamException("Invalid alarm level '"+l+"'; use one of: "+Arrays.toString(AlarmLevels.values()));
        }
    }
    

    private ValueEnumerationRange readXtceRangeEnumeration() throws XMLStreamException {
        log.trace(XTCE_RANGE_ENUMERATION);
        checkStartElementPreconditions();

        boolean isMinInclusive = true;
        boolean isMaxInclusive = true;
        double min = 0.0;
        double max = 0.0;

        String value = readAttribute("maxInclusive", xmlEvent.asStartElement());
        if (value != null) {
            max = Double.parseDouble(value);
            isMaxInclusive = true;
        }

        value = readAttribute("minInclusive", xmlEvent.asStartElement());
        if (value != null) {
            isMinInclusive = true;
            min = Double.parseDouble(value);
        }

        value = readAttribute("maxExclusive", xmlEvent.asStartElement());
        if (value != null) {
            max = Double.parseDouble(value);
            isMaxInclusive = false;
        }

        value = readAttribute("minExclusive", xmlEvent.asStartElement());
        if (value != null) {
            isMinInclusive = false;
            min = Double.parseDouble(value);
        }

        value = readAttribute("label", xmlEvent.asStartElement());
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
            }
        }
    }

    private void readXtceEnumeration(EnumeratedDataType enumDataType) throws XMLStreamException {
        log.trace(XTCE_ENUMERATION);
        checkStartElementPreconditions();

        long longValue = 0;
        String value = readAttribute("value", xmlEvent.asStartElement());
        if (value != null) {
            longValue = Long.parseLong(value);
        } else {
            throw new XMLStreamException();
        }

        value = readAttribute("label", xmlEvent.asStartElement());
        if (value == null) {
            throw new XMLStreamException();
        }

        enumDataType.addEnumerationValue(longValue, value);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isEndElementWithName(XTCE_ENUMERATION)) {
                return;
            }
        }
    }

    /**
     * @param spaceSystem 
     * 
     */
    private void readXtceParameterSet(SpaceSystem spaceSystem) throws IllegalStateException, XMLStreamException {
        log.trace(XTCE_PARAMETER_SET);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_PARAMETER)) {
                readXtceParameter(spaceSystem); // the parameter is registered inside
            } else if (isStartElementWithName(XTCE_PARAMETER_REF)) {
                readXtceParameterRef();
            } else if (isEndElementWithName(XTCE_PARAMETER_SET)) {
                return;
            }
        }
    }

    
    /**
     * @return
     * @throws IllegalStateException
     * @throws XMLStreamException
     */
    private XtceNotImplemented readXtceParameterRef() throws IllegalStateException, XMLStreamException {
        skipXtceSection(XTCE_PARAMETER_REF);
        return null;
    }

    /**
     * 
     * @return Parameter instance
     * @throws IllegalStateException
     * @throws XMLStreamException
     */
    private Parameter readXtceParameter(SpaceSystem spaceSystem) throws IllegalStateException, XMLStreamException {
        log.trace(XTCE_PARAMETER);
        checkStartElementPreconditions();

        Parameter parameter = null;

        // name
        StartElement element = xmlEvent.asStartElement();
        String value = readAttribute("name", element);
        if (value != null) {
            parameter = new Parameter(value);
        } else {
            throw new XMLStreamException("Missing name for the parameter");
        }

        // parameterTypeRef
        value = readAttribute("parameterTypeRef", element);
        if (value != null) {
            ParameterType ptype = spaceSystem.getParameterType(value);
            if(ptype!=null) {
                parameter.setParameterType(ptype);
            } else {
                final Parameter p=parameter;
                NameReference nr=new UnresolvedNameReference(value, Type.PARAMETER_TYPE).addResolvedAction( nd -> {
                        p.setParameterType((ParameterType) nd);
                        return true;
                });
                spaceSystem.addUnresolvedReference(nr);
            }
        } else {
            throw new XMLStreamException("Cannot find parameterTypeRef in element: "+element);
        }

        // shortDescription
        value = readAttribute("shortDescription", element);
        parameter.setShortDescription(value);

        // register the parameter now, because parameter can refer to
        // self in the parameter properties
        spaceSystem.addParameter(parameter);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_ALIAS_SET)) {
                parameter.setAliasSet(readXtceAliasSet());
            } else if (isStartElementWithName(XTCE_PARAMETER_PROPERTIES)) {
                readXtceParameterProperties(spaceSystem, parameter);
            } else if (isStartElementWithName(XTCE_LONG_DESCRIPTION)) {
                parameter.setLongDescription(readStringBetweenTags(XTCE_LONG_DESCRIPTION));
            } else if (isEndElementWithName(XTCE_PARAMETER)) {
                return parameter;
            }
        }
    }

    private XtceParameterProperties readXtceParameterProperties(SpaceSystem spaceSystem, Parameter p) throws XMLStreamException {
        log.trace(XTCE_PARAMETER_PROPERTIES);
        checkStartElementPreconditions();
        String v = readAttribute("dataSource", xmlEvent.asStartElement());
        if(v!=null) {
            try {
                p.setDataSource(DataSource.valueOf(v.toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new XMLStreamException("invalid dataSource '"+v+"'. Valid values: "+Arrays.toString(DataSource.values()));
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
            }
        }
    }

    private String readStringBetweenTags(String tagName) throws XMLStreamException {
        checkStartElementPreconditions();

        StringBuilder longDescr = new StringBuilder();
        while(true) {
            xmlEvent = xmlEventReader.nextEvent();
            if(isEndElementWithName(tagName)) {
                break;
            }
            if(!xmlEvent.isCharacters()) {
                throw new IllegalStateException(tagName + " characters or end element expected but instead got "+xmlEvent);
            }
            longDescr.append(xmlEvent.asCharacters().getData());
        }

        return longDescr.toString();
    }

    private RateInStream readXtceRateInStream(SpaceSystem spaceSystem) throws XMLStreamException {
        checkStartElementPreconditions();

        String basis = readAttribute("basis", xmlEvent.asStartElement());
        if((basis!=null) && !"perSecond".equalsIgnoreCase(basis)) {
            throw new XMLStreamException("Currently unsupported rate in stream basis: " + basis);
        }
        long minInterval = -1;
        long maxInterval = -1;
        String v= readAttribute("minimumValue", xmlEvent.asStartElement());
        if(v!=null) {
            maxInterval = (long) (1000/Double.parseDouble(v));
        }
        v= readAttribute("maximumValue", xmlEvent.asStartElement());
        if(v!=null) {
            minInterval = (long) (1000/Double.parseDouble(v));
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
                criteria = readXtceComparison(spaceSystem);
            } else if (isStartElementWithName(XTCE_COMPARISON_LIST)) {
                criteria = readXtceComparisonList(spaceSystem);
            } else if (isStartElementWithName(XTCE_BOOLEAN_EXPRESSION)) {
                skipXtceSection(XTCE_BOOLEAN_EXPRESSION);
            } else if (isStartElementWithName(XTCE_CUSTOM_ALGORITHM)) {
                skipXtceSection(XTCE_CUSTOM_ALGORITHM);
            } else if (isEndElementWithName(tag)) {
                return criteria;
            }
        }
    }

    private ComparisonList readXtceComparisonList(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_COMPARISON_LIST);
        checkStartElementPreconditions();

        ComparisonList list = new ComparisonList();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_COMPARISON)) {
                list.addComparison(readXtceComparison(spaceSystem));
            } else if (isEndElementWithName(XTCE_COMPARISON_LIST)) {
                return list;
            }
        }
    }

    /**
     * Reads the definition of the containers
     * @param spaceSystem 
     * 
     * @return
     * @throws IllegalStateException
     * @throws XMLStreamException
     */
    private void readXtceContainerSet(SpaceSystem spaceSystem) throws IllegalStateException, XMLStreamException {
        log.trace(XTCE_CONTAINER_SET);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_SEQUENCE_CONTAINER)) {
                SequenceContainer sc = readXtceSequenceContainer(spaceSystem);
                
                if(excludedContainers.contains(sc.getName())) {
                    log.debug("Not adding '"+sc.getName()+"' to the SpaceSystem because excluded by configuration");
                } else {
                    spaceSystem.addSequenceContainer(sc);
                }
                if((sc.getBaseContainer()==null) && (spaceSystem.getRootSequenceContainer()==null)) {
                    spaceSystem.setRootSequenceContainer(sc);
                }
            } else if (isEndElementWithName(XTCE_CONTAINER_SET)) {
                return;
            }
        }
    }

    private SequenceContainer readXtceSequenceContainer(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_SEQUENCE_CONTAINER);
        checkStartElementPreconditions();

        SequenceContainer seqContainer = null;

        String value = readAttribute("name", xmlEvent.asStartElement());
        if (value != null) {
            seqContainer = new SequenceContainer(value);
        } else {
            throw new XMLStreamException("Name is missing for container");
        }

        value = readAttribute("shortDescription", xmlEvent.asStartElement());
        if (value != null) {
            seqContainer.setShortDescription(value);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_ALIAS_SET)) {
                seqContainer.setAliasSet(readXtceAliasSet());
            } else if (isStartElementWithName(XTCE_ENTRY_LIST)) {
                readXtceEntryList(spaceSystem, seqContainer, null);
            } else if (isStartElementWithName(XTCE_BASE_CONTAINER)) {
                readXtceBaseContainer(spaceSystem, seqContainer);
            } else if (isStartElementWithName(XTCE_LONG_DESCRIPTION)) {
                seqContainer.setLongDescription(readStringBetweenTags(XTCE_LONG_DESCRIPTION));
            } else if (isStartElementWithName(XTCE_DEFAULT_RATE_IN_STREAM)) {
                seqContainer.setRateInStream(readXtceRateInStream(spaceSystem));
            }  else if (isEndElementWithName(XTCE_SEQUENCE_CONTAINER)) {
                return seqContainer;
            }
        }
    }

    private void readXtceBaseContainer(SpaceSystem spaceSystem, SequenceContainer seqContainer)  throws IllegalStateException, XMLStreamException {
        log.trace(XTCE_BASE_CONTAINER);
        checkStartElementPreconditions();

        String refName = readAttribute("containerRef", xmlEvent.asStartElement());
        if (refName != null) {
            if(excludedContainers.contains(refName)) {
                log.debug("adding "+seqContainer.getName()+" to the list of the excluded containers because its parent is excluded");
                excludedContainers.add(seqContainer.getName());
            } else {
                // find base container in the set of already defined containers
                SequenceContainer baseContainer = spaceSystem.getSequenceContainer(refName);
                if (baseContainer != null) {
                    seqContainer.setBaseContainer(baseContainer);
                } else { //must come from somewhere else
                    final SequenceContainer finalsc = seqContainer;
                    NameReference nr = new UnresolvedNameReference(refName, Type.SEQUENCE_CONTAINTER).addResolvedAction( nd -> {
                        finalsc.setBaseContainer((SequenceContainer) nd);
                        return true;
                    });
                    spaceSystem.addUnresolvedReference(nr);
                }
            }
        } else {
            throw new XMLStreamException("Reference on base container is missing");
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_RESTRICTION_CRITERIA)) {
                MatchCriteria criteria = readMatchCriteria(spaceSystem);
                seqContainer.setRestrictionCriteria(criteria);
            } else if (isEndElementWithName(XTCE_BASE_CONTAINER)) {
                return;
            }
        }
    }

    private void readXtceEntryList(SpaceSystem spaceSystem, Container container, MetaCommand mc) throws XMLStreamException {
        log.trace(XTCE_ENTRY_LIST);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            SequenceEntry entry = null;
            if (isStartElementWithName(XTCE_PARAMETER_REF_ENTRY)) {
                entry = readXtceParameterRefEntry(spaceSystem);
            } else if (isStartElementWithName(XTCE_PARAMETER_SEGMENT_REF_ENTRY)) {
                skipXtceSection(XTCE_PARAMETER_SEGMENT_REF_ENTRY);
            } else if (isStartElementWithName(XTCE_CONTAINER_REF_ENTRY)) {
                entry = readXtceConteinerRefEntry(spaceSystem);                 
            } else if (isStartElementWithName(XTCE_CONTAINER_SEGMENT_REF_ENTRY)) {
                skipXtceSection(XTCE_CONTAINER_SEGMENT_REF_ENTRY);
            } else if (isStartElementWithName(XTCE_STREAM_SEGMENT_ENTRY)) {
                skipXtceSection(XTCE_STREAM_SEGMENT_ENTRY);
            } else if (isStartElementWithName(XTCE_INDIRECT_PARAMETER_REF_ENTRY)) {
                skipXtceSection(XTCE_INDIRECT_PARAMETER_REF_ENTRY);
            } else if (isStartElementWithName(XTCE_ARRAY_PARAMETER_REF_ENTRY)) {
                skipXtceSection(XTCE_ARRAY_PARAMETER_REF_ENTRY);
            } else if (isStartElementWithName(XTCE_ARGUMENT_REF_ENTRY)) {
                entry = readXtceArgumentRefEntry(spaceSystem, mc);
            } else if (isStartElementWithName(XTCE_ARRAY_ARGUMENT_REF_ENTRY)) {
                skipXtceSection(XTCE_ARRAY_ARGUMENT_REF_ENTRY);
            } else if (isStartElementWithName(XTCE_FIXED_VALUE_ENTRY)) {
                entry = readXtceFixedValueEntry(spaceSystem);                 
            } else if (isEndElementWithName(XTCE_ENTRY_LIST)) {
                return;
            }
            if(entry!=null) {
                entry.setContainer(container);
                container.addEntry(entry);
            }
        }

    }


    private SequenceEntry readXtceParameterRefEntry(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_PARAMETER_REF_ENTRY);
        checkStartElementPreconditions();

        String refName = readAttribute("parameterRef", xmlEvent.asStartElement());
        if (refName == null) {
            throw new XMLStreamException("Reference to parameter is missing");
        }

        SequenceEntry.ReferenceLocationType locationType = SequenceEntry.ReferenceLocationType.previousEntry; //default
        Parameter parameter = spaceSystem.getParameter(refName);
        ParameterEntry parameterEntry=null;
        if (parameter != null) {
            parameterEntry = new ParameterEntry(-1, null, 0, locationType, parameter);
        } else {
            parameterEntry = new ParameterEntry(-1, null, 0, locationType);
            final ParameterEntry finalpe=parameterEntry;
            NameReference nr=new UnresolvedNameReference(refName, Type.PARAMETER_TYPE).addResolvedAction( nd -> {
                finalpe.setParameter((Parameter) nd);
                return true;
            });
            spaceSystem.addUnresolvedReference(nr);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_LOCATION_IN_CONTAINER_IN_BITS)) {
                readXtceLocationInContainerInBits(parameterEntry);
            } else if (isStartElementWithName(XTCE_REPEAT_ENTRY)) {
                Repeat r = readXtceRepeatEntry(spaceSystem);
                parameterEntry.setRepeatEntry(r);
            } else if (isStartElementWithName(XTCE_INCLUDE_CONDITION)) {
                parameterEntry.setIncludeCondition(readMatchCriteria(spaceSystem));
            } else if (isEndElementWithName(XTCE_PARAMETER_REF_ENTRY)) {
                return parameterEntry;
            }
        }
    }

    

    private ContainerEntry readXtceConteinerRefEntry(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_CONTAINER_REF_ENTRY);
        checkStartElementPreconditions();

        String refName = readAttribute("containerRef", xmlEvent.asStartElement());
        if (refName == null) {
            throw new XMLStreamException("Reference to container is missing");
        }

        SequenceEntry.ReferenceLocationType locationType = SequenceEntry.ReferenceLocationType.previousEntry; //default
        SequenceContainer container = spaceSystem.getSequenceContainer(refName);
        ContainerEntry containerEntry=null;
        if (container != null) {
            containerEntry = new ContainerEntry(-1, null, 0, locationType, container);
        } else {
            containerEntry = new ContainerEntry(-1, null, 0, locationType);
            final ContainerEntry finalce = containerEntry;
            NameReference nr = new UnresolvedNameReference(refName, Type.SEQUENCE_CONTAINTER).addResolvedAction( nd -> {
                finalce.setRefContainer((SequenceContainer) nd);
                return true;
            });
            spaceSystem.addUnresolvedReference(nr);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_LOCATION_IN_CONTAINER_IN_BITS)) {
                readXtceLocationInContainerInBits(containerEntry);
            } else if (isStartElementWithName(XTCE_REPEAT_ENTRY)) {
                Repeat r = readXtceRepeatEntry(spaceSystem);
                containerEntry.setRepeatEntry(r);
            } else if (isStartElementWithName(XTCE_INCLUDE_CONDITION)) {
                containerEntry.setIncludeCondition(readMatchCriteria(spaceSystem));
            } else if (isEndElementWithName(XTCE_CONTAINER_REF_ENTRY)) {
                return containerEntry;
            }
        }
    }
    
    
    private Repeat readXtceRepeatEntry(SpaceSystem spaceSystem) throws  XMLStreamException {
        log.trace(XTCE_REPEAT_ENTRY);
        Repeat r = new Repeat();
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_COUNT)) {
                r.setCount(readXtceIntegerValue(spaceSystem));
            } else if (isStartElementWithName("FromBinaryTransformAlgorithm")) {
                skipXtceSection("FromBinaryTransformAlgorithm");
            } else if (isStartElementWithName("ToBinaryTransformAlgorithm")) {
                skipXtceSection("ToBinaryTransformAlgorithm");
            } else if (isEndElementWithName(XTCE_REPEAT_ENTRY)) {
                return r;
            }
        }
    }



    private IntegerValue readXtceIntegerValue(SpaceSystem spaceSystem) throws XMLStreamException {
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
            }
        }
    }


    private DynamicIntegerValue readDynamicValue(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_DYNAMIC_VALUE);

        checkStartElementPreconditions();
        DynamicIntegerValue v = new DynamicIntegerValue();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_PARAMETER_INSTANCE_REF)) {
                ParameterInstanceRef pir = readXtceParameterInstanceRef(spaceSystem);
                v.setParameterInstanceRef(pir);
            } else if (isEndElementWithName(XTCE_DYNAMIC_VALUE)) {
                return v;
            }
        }
    }


    private ParameterInstanceRef readXtceParameterInstanceRef(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_PARAMETER_INSTANCE_REF);
        String paramRef = readAttribute("parameterRef", xmlEvent.asStartElement());
        if(paramRef==null) {
            throw new XMLStreamException("Reference to parameter is missing");
        }

        final ParameterInstanceRef instanceRef = new ParameterInstanceRef(true);

        NameReference nr=new UnresolvedNameReference(paramRef, Type.PARAMETER).addResolvedAction( nd -> {
            instanceRef.setParameter((Parameter) nd);
            return true;
        });

        Parameter parameter = spaceSystem.getParameter(paramRef);
        if(parameter!=null) {
            if(!nr.resolved(parameter)) {
                spaceSystem.addUnresolvedReference(nr);
            }
        } else {
            spaceSystem.addUnresolvedReference(nr);
        }

        return instanceRef;
    }


    private void readXtceLocationInContainerInBits(SequenceEntry entry) throws XMLStreamException {
        log.trace(XTCE_LOCATION_IN_CONTAINER_IN_BITS);
        checkStartElementPreconditions();

        int locationInContainerInBits = 0;

        String value = readAttribute("referenceLocation", xmlEvent.asStartElement());
        if (value == null) {
            value = "previousEntry"; // default
        }

        if (value.equalsIgnoreCase("previousEntry")) {
            entry.setReferenceLocation(SequenceEntry.ReferenceLocationType.previousEntry);
        } else if (value.equalsIgnoreCase("containerStart")) {
            entry.setReferenceLocation(SequenceEntry.ReferenceLocationType.containerStart);
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
                entry.setLocationInContainerInBits(locationInContainerInBits);
                return;
            }
        }
    }

   
    private Comparison readXtceComparison(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_COMPARISON);
        checkStartElementPreconditions();


        String paramRef = readAttribute("parameterRef", xmlEvent.asStartElement());
        if(paramRef==null) {
            throw new XMLStreamException("Reference to parameter is missing");
        }

        String value = readAttribute("comparisonOperator", xmlEvent.asStartElement());
        if (value == null) {
            value = "=="; // default value
        }

        OperatorType optype = Comparison.stringToOperator(value);

        String theValue;
        theValue = readAttribute("value", xmlEvent.asStartElement());
        if (theValue == null) {
            throw new XMLStreamException("Value for comparison is missing");
        }

        boolean useCalibratedValue = true; // default value
        value = readAttribute("useCalibratedValue", xmlEvent.asStartElement());
        if (value != null) {
            useCalibratedValue = value.equalsIgnoreCase("true");
        }


        final ParameterInstanceRef instanceRef = new ParameterInstanceRef(useCalibratedValue);
        Comparison comparison = new Comparison(instanceRef, theValue, optype);
        
        NameReference nr = new UnresolvedNameReference(paramRef, Type.PARAMETER).addResolvedAction( nd -> {
                Parameter p=(Parameter)nd;
                instanceRef.setParameter((Parameter) nd);                                
                if(p.getParameterType()==null) {
                    return false;
                }
                comparison.resolveValueType();
                return true;
            });


        Parameter parameter = spaceSystem.getParameter(paramRef);
        if(parameter!=null) {
            if(!nr.resolved(parameter)) {
                spaceSystem.addUnresolvedReference(nr);
            }
        } else {
            spaceSystem.addUnresolvedReference(nr);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isEndElementWithName(XTCE_COMPARISON)) {
                return comparison;
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
    private XtceNotImplemented readXtceMessageSet() throws IllegalStateException,
    XMLStreamException {
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
    private XtceNotImplemented readXtceStreamSet() throws IllegalStateException, XMLStreamException {
        skipXtceSection(XTCE_STREAM_SET);
        return null;
    }

    /**
     * Just skips the whole section.
     * @param spaceSystem 
     * 
     * @return
     * @throws IllegalStateException
     * @throws XMLStreamException
     */
    private void readXtceAlgorithmSet(SpaceSystem spaceSystem) throws IllegalStateException, XMLStreamException {
        log.trace(XTCE_ALGORITHM_SET);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_MATH_ALGORITHM)) {
                readXtceMathAlgorithm(); 
            } else if (isStartElementWithName(XTCE_CUSTOM_ALGORITHM)) {
                readXtceCustomAlgorithm();
            } else if (isEndElementWithName(XTCE_ALGORITHM_SET)) {
                return;
            }
        }
    }
    
    private XtceNotImplemented readXtceMathAlgorithm() throws IllegalStateException, XMLStreamException {
        skipXtceSection(XTCE_MATH_ALGORITHM);
        return null;
    }
    
    /**
     * Extraction of the TelemetryMetaData section 
     * @param spaceSystem 
     * 
     * @throws XMLStreamException
     */
    private void readXtceCommandMetaData(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_COMMAND_MEATA_DATA);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_PARAMETER_TYPE_SET)) {
                readXtceParameterTypeSet(spaceSystem);
            } else if (isStartElementWithName(XTCE_PARAMETER_SET)) {
                readXtceParameterSet(spaceSystem);
            } else if (isStartElementWithName(XTCE_ARGUMENT_TYPE_SET)) {
                readXtceArgumentTypeSet(spaceSystem);
            } else if (isStartElementWithName(XTCE_META_COMMAND_SET)) {
                readXtceMetaCommandSet(spaceSystem);
            } else if (isStartElementWithName(XTCE_COMMAND_CONTAINER_SET)) {
                readXtceCommandContainerSet(spaceSystem);
            } else if (isStartElementWithName(XTCE_MESSAGE_SET)) {
                readXtceMessageSet();
            } else if (isStartElementWithName(XTCE_ALGORITHM_SET)) {
                readXtceAlgorithmSet(spaceSystem);
            } else if (isEndElementWithName(XTCE_COMMAND_MEATA_DATA)) {
                return;
            }
        }
    }

    
    /**
     * @param spaceSystem 
     * @throws XMLStreamException
     */
    private void readXtceArgumentTypeSet(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_ARGUMENT_TYPE_SET);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            ArgumentType argumentType = null;

            if (isStartElementWithName(XTCE_BOOLEAN_ARGUMENT_TYPE)) {
                argumentType = readXtceBooleanArgumentType(spaceSystem);
            } else if (isStartElementWithName(XTCE_ENUMERATED_ARGUMENT_TYPE)) {
                argumentType = readXtceEnumeratedArgumentType(spaceSystem);
            } else if (isStartElementWithName(XTCE_FLOAT_ARGUMENT_TYPE)) {
                argumentType = readXtceFloatArgumentType(spaceSystem);
            } else if (isStartElementWithName(XTCE_INTEGER_ARGUMENT_TYPE)) {
                argumentType = readXtceIntegerArgumentType(spaceSystem);
            } else if (isStartElementWithName(XTCE_BINARY_ARGUMENT_TYPE)) {
                argumentType = readXtceBinaryArgumentType(spaceSystem);
            } else if (isStartElementWithName(XTCE_STRING_ARGUMENT_TYPE)) {
                argumentType = readXtceStringArgumentType(spaceSystem);
            } 
            if(argumentType!=null) {
                spaceSystem.addArgumentType(argumentType);
            }

            if (isEndElementWithName(XTCE_ARGUMENT_TYPE_SET)) {
                return;
            }
        }
    }

    private BooleanArgumentType readXtceBooleanArgumentType(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_BOOLEAN_ARGUMENT_TYPE);
        checkStartElementPreconditions();

        // name attribute
        BooleanArgumentType boolArgType = null;
        String name = readAttribute("name", xmlEvent.asStartElement());
        if (name != null) {
            boolArgType = new BooleanArgumentType(name);
        } else {
            throw new XMLStreamException("Unnamed boolean argument type");
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_UNIT_SET)) {
                boolArgType.addAllUnits(readXtceUnitSet());               
            } else if (isStartElementWithName(XTCE_INTEGER_DATA_ENCODING)) {
                boolArgType.setEncoding(readXtceIntegerDataEncoding(spaceSystem));
            } else if (isEndElementWithName(XTCE_BOOLEAN_ARGUMENT_TYPE)) {
                return boolArgType;
            }
        }
    }
    
    private FloatArgumentType readXtceFloatArgumentType(SpaceSystem spaceSystem) throws IllegalStateException,  XMLStreamException {
        FloatArgumentType floatArgType = null;
        log.trace(XTCE_FLOAT_ARGUMENT_TYPE);
        checkStartElementPreconditions();

        // name attribute
        String value = readAttribute("name", xmlEvent.asStartElement());
        if (value != null) {
            floatArgType = new FloatArgumentType(value);
        } else {
            throw new XMLStreamException("Unnamed float argument type");
        }

        value = readAttribute("sizeInBits", xmlEvent.asStartElement());
        
        if (value != null) {
            int sizeInBits = Integer.parseInt(value);
            if(sizeInBits!=32 && sizeInBits!=64){
                throw new XMLStreamException("Float encoding "+sizeInBits+" not supported; Only 32 and 64 bits are supported");
            }
            floatArgType.setSizeInBits(sizeInBits);
        } 
        
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_UNIT_SET)) {
                floatArgType.addAllUnits(readXtceUnitSet());
            } else if (isStartElementWithName(XTCE_INTEGER_DATA_ENCODING)) {
                floatArgType.setEncoding(readXtceIntegerDataEncoding(spaceSystem));
            } else if (isStartElementWithName(XTCE_FLOAT_DATA_ENCODING)) {
                floatArgType.setEncoding(readXtceFloatDataEncoding(spaceSystem));
            } else if (isEndElementWithName(XTCE_FLOAT_ARGUMENT_TYPE)) {
                return floatArgType;
            }
        }
    }
    private EnumeratedArgumentType readXtceEnumeratedArgumentType(SpaceSystem spaceSystem) throws XMLStreamException {
        EnumeratedArgumentType enumArgType = null;

        log.trace(XTCE_ENUMERATED_ARGUMENT_TYPE);
        checkStartElementPreconditions();

        // name attribute
        String value = readAttribute("name", xmlEvent.asStartElement());
        if (value != null) {
            enumArgType = new EnumeratedArgumentType(value);
        } else {
            throw new XMLStreamException();
        }

        // defaultValue attribute
        value = readAttribute("defaultValue", xmlEvent.asStartElement());
        if (value != null) {
            enumArgType.setInitialValue(value);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_UNIT_SET)) {
                enumArgType.addAllUnits(readXtceUnitSet());               
            } else if (isStartElementWithName(XTCE_INTEGER_DATA_ENCODING)) {
                enumArgType.setEncoding(readXtceIntegerDataEncoding(spaceSystem));
            } else if (isStartElementWithName(XTCE_ENUMERATION_LIST)) {
                readXtceEnumerationList(enumArgType);
            } else if (isEndElementWithName(XTCE_ENUMERATED_ARGUMENT_TYPE)) {
                return enumArgType;
            }
        }
    }
    
    private IntegerArgumentType readXtceIntegerArgumentType(SpaceSystem spaceSystem) throws IllegalStateException, XMLStreamException {
        IntegerArgumentType integerParamType;

        log.trace(XTCE_INTEGER_ARGUMENT_TYPE);
        checkStartElementPreconditions();

        // name attribute
        String value = readAttribute("name", xmlEvent.asStartElement());
        if (value != null) {
            integerParamType = new IntegerArgumentType(value);
        } else {
            throw new XMLStreamException("Unnamed integer parameter type");
        }

        value = readAttribute("sizeInBits", xmlEvent.asStartElement());
        if (value != null) {
            int sizeInBits = Integer.parseInt(value);
            integerParamType.setSizeInBits(sizeInBits);
        }
        value = readAttribute("signed", xmlEvent.asStartElement());
        if (value != null) {
            boolean signed = Boolean.parseBoolean(value);
            integerParamType. setSigned(signed);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_UNIT_SET)) {
                integerParamType.addAllUnits(readXtceUnitSet());
            } else if (isStartElementWithName(XTCE_INTEGER_DATA_ENCODING)) {
                integerParamType.setEncoding(readXtceIntegerDataEncoding(spaceSystem));
            } else if (isEndElementWithName(XTCE_INTEGER_ARGUMENT_TYPE)) {
                return integerParamType;
            }
        }
    }

    private BinaryArgumentType readXtceBinaryArgumentType(SpaceSystem spaceSystem) throws IllegalStateException, XMLStreamException {
        log.trace(XTCE_BINARY_ARGUMENT_TYPE);
        checkStartElementPreconditions();

        // name attribute
        BinaryArgumentType binaryParamType = null;
        String name = readAttribute("name", xmlEvent.asStartElement());
        if (name != null) {
            binaryParamType = new BinaryArgumentType(name);
        } else {
            throw new XMLStreamException("Unnamed binary parameter type");
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_UNIT_SET)) {
                binaryParamType.addAllUnits(readXtceUnitSet());
            } else if (isStartElementWithName(XTCE_INTEGER_DATA_ENCODING)) {
                binaryParamType.setEncoding(readXtceIntegerDataEncoding(spaceSystem));
            } else if (isStartElementWithName(XTCE_BINARY_DATA_ENCODING)) {
                binaryParamType.setEncoding(readXtceBinaryDataEncoding(spaceSystem));
            } else if (isEndElementWithName(XTCE_BINARY_ARGUMENT_TYPE)) {
                return binaryParamType;
            }
        }
    }

    
    private StringArgumentType readXtceStringArgumentType(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_INTEGER_ARGUMENT_TYPE);
        checkStartElementPreconditions();
        StringArgumentType stringParamType = null;
        
        // name attribute
        String value = readAttribute("name", xmlEvent.asStartElement());
        if (value != null) {
            stringParamType = new StringArgumentType(value);
        } else {
            throw new XMLStreamException("Unnamed string parameter type");
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_UNIT_SET)) {
                stringParamType.addAllUnits(readXtceUnitSet());
            } else if (isStartElementWithName(XTCE_STRING_DATA_ENCODING)) {
                stringParamType.setEncoding(readXtceStringDataEncoding(spaceSystem));
            } else if (isStartElementWithName(XTCE_CONTEXT_ALARM_LIST)) {
                skipXtceSection(XTCE_CONTEXT_ALARM_LIST);
            } else if (isEndElementWithName(XTCE_INTEGER_ARGUMENT_TYPE)) {
                return stringParamType;
            }
        }
    }
    /**
     * Reads the definition of the command containers
     * @param spaceSystem 
     * 
     * @return
     * @throws IllegalStateException
     */
    private void readXtceCommandContainerSet(SpaceSystem spaceSystem) throws XMLStreamException {
        skipXtceSection(XTCE_COMMAND_CONTAINER_SET);
    }

    
    /**
     * Reads the definition of the metacommand containers
     * @param spaceSystem 
     * 
     * @return
     * @throws IllegalStateException
     */
    private void readXtceMetaCommandSet(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_META_COMMAND_SET);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_META_COMMAND)) {
                MetaCommand mc = readXtceMetaCommand(spaceSystem);
                if(excludedContainers.contains(mc.getName())) {
                    log.debug("Not adding '"+mc.getName()+"' to the SpaceSystem because excluded by configuration");
                } else {
                    spaceSystem.addMetaCommand(mc);
                }
            } else if (isEndElementWithName(XTCE_META_COMMAND_SET)) {
                return;
            }
        }
    }
    
    
    private MetaCommand readXtceMetaCommand(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_META_COMMAND);
        checkStartElementPreconditions();

        MetaCommand mc = null;

        String value = readAttribute("name", xmlEvent.asStartElement());
        if (value != null) {
            mc = new MetaCommand(value);
        } else {
            throw new XMLStreamException("Name is missing for meta command");
        }

        value = readAttribute("shortDescription", xmlEvent.asStartElement());
        if (value != null) {
            mc.setShortDescription(value);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();
            if (isStartElementWithName(XTCE_ALIAS_SET)) {
                mc.setAliasSet(readXtceAliasSet());
            } else if (isStartElementWithName(XTCE_LONG_DESCRIPTION)) {
                mc.setLongDescription(readStringBetweenTags(XTCE_LONG_DESCRIPTION));
            } else if (isStartElementWithName(XTCE_BASE_META_COMMAND)) {
                readXtceBaseMetaCommand(spaceSystem, mc);
            } else if (isStartElementWithName(XTCE_COMMAND_CONTAINER)) {
                CommandContainer cc = readXtceCommandContainer(spaceSystem, mc);
                mc.setCommandContainer(cc);
                spaceSystem.addCommandContainer(cc);
            } else if (isStartElementWithName(XTCE_ARGUMENT_LIST)) {
                readXtceArgumentList(spaceSystem, mc);
            } else if (isEndElementWithName(XTCE_META_COMMAND)) {
                return mc;
            }
        }
    }
  
    private void readXtceBaseMetaCommand(SpaceSystem spaceSystem, MetaCommand mc)  throws IllegalStateException, XMLStreamException {
        log.trace(XTCE_BASE_META_COMMAND);
        checkStartElementPreconditions();

        String refName = readAttribute("metaCommandRef", xmlEvent.asStartElement());
        if (refName != null) {
            if(excludedContainers.contains(refName)) {
                log.debug("adding "+mc.getName()+" to the list of the excluded containers because its parent is excluded");
                excludedContainers.add(mc.getName());
            } else {
                // find base container in the set of already defined containers
                MetaCommand baseContainer = spaceSystem.getMetaCommand(refName);
                if (baseContainer != null) {
                    mc.setBaseMetaCommand(baseContainer);
                } else { //must come from somewhere else
                    final MetaCommand finalmc = mc;
                    NameReference nr = new UnresolvedNameReference(refName, Type.META_COMMAND).addResolvedAction( nd -> {
                        finalmc.setBaseMetaCommand((MetaCommand) nd);
                        return true;
                    });
                    spaceSystem.addUnresolvedReference(nr);
                }
            }
        } else {
            throw new XMLStreamException("For "+mc.getName()+": reference to base meta command is missing");
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_ARGUMENT_ASSIGNMENT_LIST)) {
                readArgumentAssignmentList(spaceSystem, mc);
            } else if (isEndElementWithName(XTCE_BASE_META_COMMAND)) {
                return;
            }
        }
    }
    
    private void readArgumentAssignmentList(SpaceSystem spaceSystem, MetaCommand mc) {
        log.trace(XTCE_ARGUMENT_LIST);
        checkStartElementPreconditions();
        
    }
    private void readXtceArgumentList(SpaceSystem spaceSystem, MetaCommand mc) throws XMLStreamException {
        log.trace(XTCE_ARGUMENT_LIST);
        checkStartElementPreconditions();

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_ARGUMENT)) {
                Argument arg = readXtceArgument(spaceSystem);
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
    private Argument readXtceArgument(SpaceSystem spaceSystem) throws IllegalStateException, XMLStreamException {
        log.trace(XTCE_ARGUMENT);
        checkStartElementPreconditions();

        Argument arg = null;

        // name
        StartElement element = xmlEvent.asStartElement();
        String value = readAttribute("name", element);
        if (value != null) {
            arg = new Argument(value);
        } else {
            throw new XMLStreamException("Missing name for the argument");
        }

        value = readAttribute("argumentTypeRef", element);
        if (value != null) {
            ArgumentType ptype = spaceSystem.getArgumentType(value);
            if(ptype!=null) {
                arg.setArgumentType(ptype);
            } else {
                final Argument a = arg;
                NameReference nr=new UnresolvedNameReference(value, Type.ARGUMENT_TYPE).addResolvedAction( nd -> {
                        a.setArgumentType((ArgumentType) nd);
                        return true;
                });
                spaceSystem.addUnresolvedReference(nr);
            }
        } else {
            throw new XMLStreamException("Cannot find argumentTypeRef in element: "+element);
        }

        // shortDescription
        value = readAttribute("shortDescription", element);
        arg.setShortDescription(value);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_ALIAS_SET)) {
                arg.setAliasSet(readXtceAliasSet());
            } else if (isStartElementWithName(XTCE_LONG_DESCRIPTION)) {
                arg.setLongDescription(readStringBetweenTags(XTCE_LONG_DESCRIPTION));
            } else if (isEndElementWithName(XTCE_ARGUMENT)) {
                return arg;
            }
        }
    }

    private CommandContainer readXtceCommandContainer(SpaceSystem spaceSystem, MetaCommand mc) throws XMLStreamException {
        log.trace(XTCE_COMMAND_CONTAINER);
        checkStartElementPreconditions();

        CommandContainer cmdContainer = null;

        String value = readAttribute("name", xmlEvent.asStartElement());
        if (value != null) {
            cmdContainer = new CommandContainer(value);
        } else {
            throw new XMLStreamException("Name is missing for container");
        }

        value = readAttribute("shortDescription", xmlEvent.asStartElement());
        if (value != null) {
            cmdContainer.setShortDescription(value);
        }

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_ALIAS_SET)) {
                cmdContainer.setAliasSet(readXtceAliasSet());
            } else if (isStartElementWithName(XTCE_ENTRY_LIST)) {
                readXtceEntryList(spaceSystem, cmdContainer, mc);
            } else if (isStartElementWithName(XTCE_BASE_CONTAINER)) {
                readXtceBaseContainer(spaceSystem, cmdContainer);
            } else if (isStartElementWithName(XTCE_LONG_DESCRIPTION)) {
                cmdContainer.setLongDescription(readStringBetweenTags(XTCE_LONG_DESCRIPTION));
            } else if (isStartElementWithName(XTCE_DEFAULT_RATE_IN_STREAM)) {
                cmdContainer.setRateInStream(readXtceRateInStream(spaceSystem));
            }  else if (isEndElementWithName(XTCE_COMMAND_CONTAINER)) {
                return cmdContainer;
            }
        }
    }
    

    private void readXtceBaseContainer(SpaceSystem spaceSystem, CommandContainer mcContainer)  throws IllegalStateException, XMLStreamException {
        log.trace(XTCE_BASE_CONTAINER);
        checkStartElementPreconditions();

        String refName = readAttribute("containerRef", xmlEvent.asStartElement());
        if (refName != null) {
            if(excludedContainers.contains(refName)) {
                log.debug("adding "+mcContainer.getName()+" to the list of the excluded containers because its parent is excluded");
                excludedContainers.add(mcContainer.getName());
            } else {
                // find base container in the set of already defined containers
                CommandContainer baseContainer = spaceSystem.getCommandContainer(refName);
                if (baseContainer != null) {
                    mcContainer.setBaseContainer(baseContainer);
                } else { //must come from somewhere else
                    final CommandContainer finalsc = mcContainer;
                    NameReference nr = new UnresolvedNameReference(refName, Type.COMMAND_CONTAINER).addResolvedAction( nd -> {
                        finalsc.setBaseContainer((Container)nd);
                        return true;
                    });
                    spaceSystem.addUnresolvedReference(nr);
                }
            }
        } else {
            throw new XMLStreamException("Reference on base container is missing");
        }
        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_RESTRICTION_CRITERIA)) {
                MatchCriteria criteria = readMatchCriteria(spaceSystem);
                mcContainer.setRestrictionCriteria(criteria);
            } else if (isEndElementWithName(XTCE_BASE_CONTAINER)) {
                return;
            }
        }
    }


    private ArgumentEntry readXtceArgumentRefEntry(SpaceSystem spaceSystem, MetaCommand mc) throws XMLStreamException {
        log.trace(XTCE_ARGUMENT_REF_ENTRY);
        checkStartElementPreconditions();

        String refName = readAttribute("argumentRef", xmlEvent.asStartElement());
        if (refName == null) {
            throw new XMLStreamException("Reference to argument is missing");
        }

        Argument arg = mc.getArgument(refName);
        ArgumentEntry argumentEntry=null;
        if (arg == null) {
            throw new XMLStreamException("Undefined argument reference '"+refName+"'");
        }
        argumentEntry = new ArgumentEntry(arg);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_LOCATION_IN_CONTAINER_IN_BITS)) {
                readXtceLocationInContainerInBits(argumentEntry);
            } else if (isStartElementWithName(XTCE_REPEAT_ENTRY)) {
                Repeat r = readXtceRepeatEntry(spaceSystem);
                argumentEntry.setRepeatEntry(r);
            } else if (isStartElementWithName(XTCE_INCLUDE_CONDITION)) {
                skipXtceSection(XTCE_INCLUDE_CONDITION);
            } else if (isEndElementWithName(XTCE_ARGUMENT_REF_ENTRY)) {
                return argumentEntry;
            }
        }
    }

    private FixedValueEntry readXtceFixedValueEntry(SpaceSystem spaceSystem) throws XMLStreamException {
        log.trace(XTCE_FIXED_VALUE_ENTRY);
        checkStartElementPreconditions();
        StartElement startElement = xmlEvent.asStartElement();

        String name = readAttribute("name", startElement);
        
        String value = readAttribute("binaryValue", startElement);
        if (value == null) {
            throw new XMLStreamException("binaryValue for fixed entry is missing");
        }
        byte[] binaryValue = StringConverter.hexStringToArray(value);
        
        value = readAttribute("sizeInBits", startElement);
        int sizeInBits = binaryValue.length*8;
        if (value != null) {
            sizeInBits = Integer.parseInt(value);
        }
        FixedValueEntry fixedValueEntry = new FixedValueEntry(name, binaryValue, sizeInBits);

        while (true) {
            xmlEvent = xmlEventReader.nextEvent();

            if (isStartElementWithName(XTCE_LOCATION_IN_CONTAINER_IN_BITS)) {
                readXtceLocationInContainerInBits(fixedValueEntry);
            } else if (isStartElementWithName(XTCE_REPEAT_ENTRY)) {
                Repeat r = readXtceRepeatEntry(spaceSystem);
                fixedValueEntry.setRepeatEntry(r);
            } else if (isStartElementWithName(XTCE_INCLUDE_CONDITION)) {
                skipXtceSection(XTCE_INCLUDE_CONDITION);
            } else if (isEndElementWithName(XTCE_FIXED_VALUE_ENTRY)) {
                return fixedValueEntry;
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
    private XtceNotImplemented readXtceCustomAlgorithm() throws IllegalStateException, XMLStreamException {
        skipXtceSection(XTCE_CUSTOM_ALGORITHM);
        return null;
    }
    /**
     * Increase the skip statistics for the section.
     * @param xtceSectionName Name of the skipped section.
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
            log.info(">> " + entry.getKey() + ": " + entry.getValue());
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
    private void skipXtceSection(String sectionName) throws XMLStreamException, IllegalStateException {
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
                    log.info("Section <" + sectionName + "> skipped");
                    return;
                }
            } catch (NoSuchElementException e) {
                throw new IllegalStateException("End of section unreachable: " + sectionName);
            }
        }
    }
    
    private void skipQuietly(String sectionName) throws XMLStreamException, IllegalStateException {
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
                throw new IllegalStateException("End of section unreachable: " + sectionName);
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
     * @throws IllegalStateException If the conditions are not met
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
     */
    private String readAttribute(String attName, StartElement element) {
        Attribute attribute = element.getAttributeByName(new QName(attName));
        if (attribute != null) {
            return attribute.getValue();
        }
        return null;
    }

    /**
     * Test method. 
     * @param args XTCE document to load.
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

        XtceStaxReader reader = new XtceStaxReader();

        if (args.length == 1) {
            reader.readXmlDocument(args[0]);
            reader.writeStatistics();
        } else {
            System.out.println("Wrong arguments, exactly one argument allowed");
        }

    }


    public void setExcludedContainers(Set<String> excludedContainers) {
        this.excludedContainers = excludedContainers;
    }
}

package org.yamcs.mdb;

import static org.yamcs.xtce.xml.Constants.ATTR_ENCODING;
import static org.yamcs.xtce.xml.Constants.ATTR_INITIAL_VALUE;
import static org.yamcs.xtce.xml.Constants.ATTR_PARAMETER_REF;
import static org.yamcs.xtce.xml.Constants.ATTR_SIZE_IN_BITS;
import static org.yamcs.xtce.xml.Constants.ELEM_COMPARISON_OPERATOR;
import static org.yamcs.xtce.xml.Constants.ELEM_CONDITION;
import static org.yamcs.xtce.xml.Constants.ELEM_ENCODING;
import static org.yamcs.xtce.xml.Constants.ELEM_FLOAT_DATA_ENCODING;
import static org.yamcs.xtce.xml.Constants.ELEM_PARAMETER_INSTANCE_REF;
import static org.yamcs.xtce.xml.Constants.ELEM_PARAMETER_REF;
import static org.yamcs.xtce.xml.Constants.ELEM_PARAMETER_VALUE_CHANGE;
import static org.yamcs.xtce.xml.Constants.ELEM_SIZE_IN_BITS;
import static org.yamcs.xtce.xml.Constants.ELEM_VALUE;
import static org.yamcs.xtce.xml.Constants.ELEM_VARIABLE;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Predicate;

import javax.xml.XMLConstants;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.yamcs.logging.Log;
import org.yamcs.utils.StringConverter;
import org.yamcs.xtce.ANDedConditions;
import org.yamcs.xtce.AbsoluteTimeArgumentType;
import org.yamcs.xtce.AbsoluteTimeParameterType;
import org.yamcs.xtce.AggregateArgumentType;
import org.yamcs.xtce.AggregateDataType;
import org.yamcs.xtce.AggregateParameterType;
import org.yamcs.xtce.AlarmRanges;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.AncillaryData;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.ArgumentEntry;
import org.yamcs.xtce.ArgumentInstanceRef;
import org.yamcs.xtce.ArgumentType;
import org.yamcs.xtce.ArrayParameterEntry;
import org.yamcs.xtce.ArrayParameterType;
import org.yamcs.xtce.BinaryArgumentType;
import org.yamcs.xtce.BinaryDataEncoding;
import org.yamcs.xtce.BinaryParameterType;
import org.yamcs.xtce.BooleanArgumentType;
import org.yamcs.xtce.BooleanExpression;
import org.yamcs.xtce.BooleanParameterType;
import org.yamcs.xtce.Calibrator;
import org.yamcs.xtce.CheckWindow;
import org.yamcs.xtce.CheckWindow.TimeWindowIsRelativeToType;
import org.yamcs.xtce.CommandContainer;
import org.yamcs.xtce.CommandVerifier;
import org.yamcs.xtce.Comparison;
import org.yamcs.xtce.ComparisonList;
import org.yamcs.xtce.Condition;
import org.yamcs.xtce.ContainerEntry;
import org.yamcs.xtce.ContextCalibrator;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.DataType;
import org.yamcs.xtce.DynamicIntegerValue;
import org.yamcs.xtce.EnumeratedArgumentType;
import org.yamcs.xtce.EnumeratedDataType;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.EnumerationAlarm.EnumerationAlarmItem;
import org.yamcs.xtce.EnumerationContextAlarm;
import org.yamcs.xtce.FixedIntegerValue;
import org.yamcs.xtce.FixedValueEntry;
import org.yamcs.xtce.FloatArgumentType;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.FloatValidRange;
import org.yamcs.xtce.Header;
import org.yamcs.xtce.History;
import org.yamcs.xtce.InputParameter;
import org.yamcs.xtce.IntegerArgumentType;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.IntegerValidRange;
import org.yamcs.xtce.IntegerValue;
import org.yamcs.xtce.MatchCriteria;
import org.yamcs.xtce.MathAlgorithm;
import org.yamcs.xtce.MathOperation;
import org.yamcs.xtce.MathOperation.ElementType;
import org.yamcs.xtce.MathOperationCalibrator;
import org.yamcs.xtce.Member;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.NameDescription;
import org.yamcs.xtce.NumericAlarm;
import org.yamcs.xtce.NumericContextAlarm;
import org.yamcs.xtce.NumericDataEncoding;
import org.yamcs.xtce.ORedConditions;
import org.yamcs.xtce.OnParameterUpdateTrigger;
import org.yamcs.xtce.OnPeriodicRateTrigger;
import org.yamcs.xtce.OperatorType;
import org.yamcs.xtce.OutputParameter;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterEntry;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.ParameterOrArgumentRef;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.ParameterValueChange;
import org.yamcs.xtce.PathElement;
import org.yamcs.xtce.PolynomialCalibrator;
import org.yamcs.xtce.ReferenceTime;
import org.yamcs.xtce.Repeat;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SequenceEntry;
import org.yamcs.xtce.SequenceEntry.ReferenceLocationType;
import org.yamcs.xtce.Significance;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.SplineCalibrator;
import org.yamcs.xtce.SplinePoint;
import org.yamcs.xtce.StringArgumentType;
import org.yamcs.xtce.StringDataEncoding;
import org.yamcs.xtce.StringDataEncoding.SizeType;
import org.yamcs.xtce.StringParameterType;
import org.yamcs.xtce.TimeEpoch;
import org.yamcs.xtce.TransmissionConstraint;
import org.yamcs.xtce.TriggerSetType;
import org.yamcs.xtce.UnitType;
import org.yamcs.xtce.ValueEnumeration;
import org.yamcs.xtce.ValueEnumerationRange;
import org.yamcs.xtce.util.DoubleRange;
import org.yamcs.xtce.xml.XtceStaxReader;

/**
 * 
 * Experimental export of Mission Database to XTCE.
 * 
 * 
 * @author nm
 *
 */
public class XtceAssembler {

    private static final String NS_XTCE_V1_2 = "http://www.omg.org/spec/XTCE/20180204";
    private static final Log log = new Log(XtceAssembler.class);
    private static final List<String> XTCE_VERIFIER_STAGES = Arrays.asList(
            "TransferredToRange", "SentFromRange", "Received", "Accepted", "Queued", "Execution", "Complete", "Failed");

    boolean emitYamcsNamespace = false;
    private SpaceSystem currentSpaceSystem;
    final DatatypeFactory dataTypeFactory;

    Mdb mdb;

    public XtceAssembler() {
        try {
            dataTypeFactory = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    public final String toXtce(Mdb mdb) {
        return toXtce(mdb, "/", fqn -> true);
    }

    /**
     * Convert the mission database to XTCE starting at the specified top container and saving only filtered containers.
     * <p>
     * The filter will be called with the Fully Qualified Name of each container under the top and if it returns true,
     * the specified container will be saved.
     * <p>
     * Note that in the resulting file (if the top is not the root) the containers will have their qualified name
     * stripped by the top name. In addition there might be references to objects from SpaceSystems that are not part of
     * the export.
     * 
     * 
     * @param mdb
     * @param topSpaceSystem
     *            the fully qualified name of the space system where the export should start from. If the space system
     *            does not exist, a {@link IllegalArgumentException} will be thrown.
     * @param filter
     * @return
     */
    public final String toXtce(Mdb mdb, String topSpaceSystem, Predicate<String> filter) {
        this.mdb = mdb;
        try {
            String unindentedXML;
            try (Writer writer = new StringWriter()) {
                XMLOutputFactory factory = XMLOutputFactory.newInstance();
                XMLStreamWriter xmlWriter = factory.createXMLStreamWriter(writer);
                xmlWriter.writeStartDocument();
                SpaceSystem top = mdb.getSpaceSystem(topSpaceSystem);
                if (top == null) {
                    throw new IllegalArgumentException("Unknown space system '" + topSpaceSystem + "'");
                }
                writeSpaceSystem(xmlWriter, top, true, filter);
                xmlWriter.writeEndDocument();
                xmlWriter.close();
                unindentedXML = writer.toString();
            }

            try (Reader reader = new StringReader(unindentedXML); Writer writer = new StringWriter()) {
                TransformerFactory transformerFactory = TransformerFactory.newInstance();

                // Sonarqube suggestion to protect Java XML Parsers from XXE attack
                // see https://rules.sonarsource.com/java/RSPEC-2755
                transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");

                Transformer transformer = transformerFactory.newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

                // Without this seemingly unnecessary property, the Java XML implementation
                // will put the XML declaration and the root tag on the same line (missing newline).
                transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, "no");

                StreamSource source = new StreamSource(reader);
                StreamResult result = new StreamResult(writer);
                transformer.transform(source, result);
                return writer.toString();
            }
        } catch (IOException | XMLStreamException | TransformerException e) {
            throw new Error(e);
        }
    }

    private void writeSpaceSystem(XMLStreamWriter doc, SpaceSystem spaceSystem, boolean emitNamespace,
            Predicate<String> filter)
            throws XMLStreamException {

        if (!filter.test(spaceSystem.getQualifiedName())) {
            log.debug("Skipping {}", spaceSystem.getQualifiedName());
            return;
        }
        this.currentSpaceSystem = spaceSystem;

        doc.writeStartElement("SpaceSystem");
        if (emitNamespace) {
            doc.writeDefaultNamespace(NS_XTCE_V1_2);
        }
        writeNameDescription(doc, spaceSystem);

        Header header = spaceSystem.getHeader();
        if (header != null) {
            writeHeader(doc, header);
        }

        doc.writeStartElement("TelemetryMetaData");
        if (!spaceSystem.getParameterTypes().isEmpty()) {
            doc.writeStartElement("ParameterTypeSet");
            for (ParameterType ptype : spaceSystem.getParameterTypes()) {
                writeParameterType(doc, ptype);
            }
            doc.writeEndElement();
        }
        if (!spaceSystem.getParameters().isEmpty()) {
            doc.writeStartElement("ParameterSet");
            for (Parameter parameter : spaceSystem.getParameters()) {
                if (parameter.getParameterType() == null) {
                    continue;
                }
                writeParameter(doc, parameter);
            }
            doc.writeEndElement();
        }
        if (!spaceSystem.getSequenceContainers().isEmpty()) {
            doc.writeStartElement("ContainerSet");
            for (SequenceContainer seq : spaceSystem.getSequenceContainers()) {
                writeSequenceContainer(doc, seq);
            }
            doc.writeEndElement();
        }
        if (!spaceSystem.getAlgorithms().isEmpty()) {
            doc.writeStartElement("AlgorithmSet");
            for (Algorithm algo : spaceSystem.getAlgorithms()) {
                if (algo instanceof MathAlgorithm) {
                    writeMathAlgorithm(doc, (MathAlgorithm) algo);
                } else {
                    writeCustomAlgorithm(doc, (CustomAlgorithm) algo, "CustomAlgorithm", false);
                }
            }
            doc.writeEndElement();
        }
        doc.writeEndElement();// TelemetryMetaData

        if (!spaceSystem.getMetaCommands().isEmpty()) {
            doc.writeStartElement("CommandMetaData");

            if (!spaceSystem.getArgumentTypes().isEmpty()) {
                doc.writeStartElement("ArgumentTypeSet");
                for (ArgumentType atype : spaceSystem.getArgumentTypes()) {
                    writeArgumentType(doc, atype);
                }
                doc.writeEndElement();
            }

            doc.writeStartElement("MetaCommandSet");
            for (MetaCommand command : spaceSystem.getMetaCommands()) {
                writeMetaCommand(doc, command);
            }

            doc.writeEndElement();// MetaCommandSet
            doc.writeEndElement();// CommandMetaData
        }

        for (SpaceSystem sub : spaceSystem.getSubSystems()) {
            if (!emitYamcsNamespace && Mdb.YAMCS_SPACESYSTEM_NAME.equals(sub.getQualifiedName())) {
                continue;
            }
            writeSpaceSystem(doc, sub, false, filter);
        }

        doc.writeEndElement();
    }

    private static void writeHeader(XMLStreamWriter doc, Header header) throws XMLStreamException {
        doc.writeStartElement("Header");
        doc.writeAttribute("validationStatus", "Unknown"); // Required attribute
        if (header.getVersion() != null) {
            doc.writeAttribute("version", header.getVersion());
        }
        if (header.getDate() != null) {
            doc.writeAttribute("date", header.getDate());
        }
        if (!header.getHistoryList().isEmpty()) {
            doc.writeStartElement("HistorySet");
            for (History history : header.getHistoryList()) {
                doc.writeStartElement("History");
                if (history.getDate() != null && !history.getDate().isEmpty()) {
                    doc.writeCharacters(history.getDate());
                    doc.writeCharacters(": ");
                }
                doc.writeCharacters(history.getMessage());

                doc.writeEndElement();
            }
            doc.writeEndElement();
        }
        doc.writeEndElement();
    }

    private void writeParameter(XMLStreamWriter doc, Parameter parameter) throws XMLStreamException {
        doc.writeStartElement("Parameter");

        ParameterType ptype = parameter.getParameterType();
        writeNameReferenceAttribute(doc, "parameterTypeRef", (NameDescription) ptype); // Required attribute

        if (parameter.getInitialValue() != null) {
            doc.writeAttribute(ATTR_INITIAL_VALUE, parameter.getInitialValue().toString());
        }
        writeNameDescription(doc, parameter);
        if (hasNonDefaultProperties(parameter)) {
            doc.writeStartElement("ParameterProperties");
            if (parameter.getDataSource() != DataSource.TELEMETERED) {
                doc.writeAttribute("dataSource", parameter.getDataSource().name().toLowerCase());
            }
            if (!parameter.isPersistent()) {
                doc.writeAttribute("persistence", "false");
            }
            doc.writeEndElement();// ParameterProperties
        }
        doc.writeEndElement();// Parameter
    }

    boolean hasNonDefaultProperties(Parameter p) {
        return p.getDataSource() != DataSource.TELEMETERED || !p.isPersistent();
    }

    private void writeParameterType(XMLStreamWriter doc, ParameterType ptype) throws XMLStreamException {
        if (ptype instanceof StringParameterType) {
            writeStringParameterType(doc, (StringParameterType) ptype);
        } else if (ptype instanceof IntegerParameterType) {
            writeIntegerParameterType(doc, (IntegerParameterType) ptype);
        } else if (ptype instanceof AggregateParameterType) {
            writeAggregateParameterType(doc, (AggregateParameterType) ptype);
        } else if (ptype instanceof FloatParameterType) {
            writeFloatParameterType(doc, (FloatParameterType) ptype);
        } else if (ptype instanceof BooleanParameterType) {
            writeBooleanParameterType(doc, (BooleanParameterType) ptype);
        } else if (ptype instanceof EnumeratedParameterType) {
            writeEnumeratedParameterType(doc, (EnumeratedParameterType) ptype);
        } else if (ptype instanceof AbsoluteTimeParameterType) {
            writeAbsoluteTimeParameterType(doc, (AbsoluteTimeParameterType) ptype);
        } else if (ptype instanceof ArrayParameterType) {
            writeArrayParameterType(doc, (ArrayParameterType) ptype);
        } else if (ptype instanceof BinaryParameterType) {
            writeBinaryParameterType(doc, (BinaryParameterType) ptype);
        } else {
            log.warn("Unexpected parameter type " + ptype.getClass());
        }
    }

    private void writeEnumeratedParameterType(XMLStreamWriter doc, EnumeratedParameterType ptype)
            throws XMLStreamException {
        doc.writeStartElement("EnumeratedParameterType");

        writeEnumeratedDataType(doc, ptype);

        if (ptype.getDefaultAlarm() != null) {
            doc.writeStartElement("DefaultAlarm");
            writeEnumerationAlarm(doc, ptype.getDefaultAlarm().getAlarmList());
            doc.writeEndElement();// DefaultAlarm
        }
        if (ptype.getContextAlarmList() != null) {
            doc.writeStartElement("ContextAlarmList");
            for (EnumerationContextAlarm eca : ptype.getContextAlarmList()) {
                doc.writeStartElement("ContextAlarm");

                writeEnumerationAlarm(doc, eca.getAlarmList());

                doc.writeStartElement("ContextMatch");
                writeMatchCriteria(doc, eca.getContextMatch());
                doc.writeEndElement();// ContextMatch

                doc.writeEndElement();// ContextAlarm
            }
            doc.writeEndElement();// ContextAlarmList
        }
        doc.writeEndElement();
    }

    private static void writeAggregateParameterType(XMLStreamWriter doc, AggregateParameterType ptype)
            throws XMLStreamException {
        doc.writeStartElement("AggregateParameterType");

        writeAggregateDataType(doc, ptype);

        doc.writeEndElement();
    }

    private static void writeAggregateDataType(XMLStreamWriter doc, AggregateDataType type) throws XMLStreamException {
        writeNameDescription(doc, type);

        doc.writeStartElement("MemberList");
        for (Member member : type.getMemberList()) {
            DataType mtype = member.getType();
            doc.writeStartElement("Member");
            writeNameDescription(doc, member);
            doc.writeAttribute("typeRef", mtype.getName());
            if (member.getInitialValue() != null) {
                doc.writeAttribute(ATTR_INITIAL_VALUE, member.getInitialValue().toString());
            }
            doc.writeEndElement();
        }
        doc.writeEndElement();
    }

    private void writeIntegerParameterType(XMLStreamWriter doc, IntegerParameterType ptype)
            throws XMLStreamException {
        doc.writeStartElement("IntegerParameterType");
        if (ptype.getSizeInBits() != 32) {
            doc.writeAttribute(ATTR_SIZE_IN_BITS, Integer.toString(ptype.getSizeInBits()));
        }
        if (!ptype.isSigned()) {
            doc.writeAttribute("signed", "false");
        }
        if (ptype.getInitialValue() != null) {
            doc.writeAttribute(ATTR_INITIAL_VALUE, ptype.getInitialValue().toString());
        }
        writeNameDescription(doc, ptype);
        if (ptype.getValidRange() != null) {
            IntegerValidRange range = ptype.getValidRange();
            doc.writeStartElement("ValidRange");
            doc.writeAttribute("minInclusive", String.valueOf(range.getMinInclusive()));
            doc.writeAttribute("maxInclusive", String.valueOf(range.getMaxInclusive()));
            if (!range.isValidRangeAppliesToCalibrated()) {
                doc.writeAttribute("validRangeAppliesToCalibrated", "false");
            }
            doc.writeEndElement();
        }
        writeUnitSet(doc, ptype.getUnitSet());

        if (ptype.getEncoding() != null) {
            writeDataEncoding(doc, ptype.getEncoding());
        }
        doc.writeEndElement();
    }

    private void writeFloatParameterType(XMLStreamWriter doc, FloatParameterType ptype)
            throws XMLStreamException {
        doc.writeStartElement("FloatParameterType");
        doc.writeAttribute(ATTR_SIZE_IN_BITS, Integer.toString(ptype.getSizeInBits()));
        if (ptype.getInitialValue() != null) {
            doc.writeAttribute(ATTR_INITIAL_VALUE, ptype.getInitialValue().toString());
        }
        writeNameDescription(doc, ptype);
        if (ptype.getValidRange() != null) {
            FloatValidRange range = ptype.getValidRange();
            writeRange(doc, "ValidRange", range, range.isValidRangeAppliesToCalibrated() ? null : Boolean.FALSE);
        }
        writeUnitSet(doc, ptype.getUnitSet());

        if (ptype.getEncoding() != null) {
            writeDataEncoding(doc, ptype.getEncoding());
        }
        NumericAlarm alarm = ptype.getDefaultAlarm();
        if (alarm != null) {
            doc.writeStartElement("DefaultAlarm");
            if (alarm.getMinViolations() != 1) {
                doc.writeAttribute("minViolations", Integer.toString(alarm.getMinViolations()));
            }
            writeNumericAlarm(doc, alarm);
            doc.writeEndElement();
        }

        if (ptype.getContextAlarmList() != null) {
            doc.writeStartElement("ContextAlarmList");
            for (NumericContextAlarm nca : ptype.getContextAlarmList()) {
                doc.writeStartElement("ContextAlarm");
                writeNumericAlarm(doc, nca);
                doc.writeStartElement("ContextMatch");
                writeMatchCriteria(doc, nca.getContextMatch());
                doc.writeEndElement();// ContextMatch
                doc.writeEndElement();// ContextAlarm

            }
            doc.writeEndElement();// ContextAlarmList
        }
        doc.writeEndElement();// FloatParameterType
    }

    private static void writeRange(XMLStreamWriter doc, String elementName, DoubleRange range)
            throws XMLStreamException {
        writeRange(doc, elementName, range, null);
    }

    private static void writeRange(XMLStreamWriter doc, String elementName, DoubleRange range,
            Boolean appliesToCalibrated)
            throws XMLStreamException {
        if (range == null) {
            return;
        }
        doc.writeStartElement(elementName);
        if (range.isMinInclusive()) {
            doc.writeAttribute("minInclusive", String.valueOf(range.getMin()));
        } else {
            doc.writeAttribute("minExclusive", String.valueOf(range.getMin()));
        }
        if (range.isMaxInclusive()) {
            doc.writeAttribute("maxInclusive", String.valueOf(range.getMax()));
        } else {
            doc.writeAttribute("maxExclusive", String.valueOf(range.getMax()));
        }
        if (appliesToCalibrated != null) {
            doc.writeAttribute("validRangeAppliesToCalibrated", appliesToCalibrated.toString());
        }
        doc.writeEndElement();
    }

    private static void writeNumericAlarm(XMLStreamWriter doc, NumericAlarm na) throws XMLStreamException {
        doc.writeStartElement("StaticAlarmRanges");
        AlarmRanges ar = na.getStaticAlarmRanges();

        writeRange(doc, "WatchRange", ar.getWatchRange());
        writeRange(doc, "WarningRange", ar.getWarningRange());
        writeRange(doc, "DistressRange", ar.getDistressRange());
        writeRange(doc, "CriticalRange", ar.getCriticalRange());
        writeRange(doc, "SevereRange", ar.getSevereRange());

        doc.writeEndElement();
    }

    private static void writeEnumerationAlarm(XMLStreamWriter doc, List<EnumerationAlarmItem> list)
            throws XMLStreamException {
        doc.writeStartElement("EnumerationAlarmList");
        for (EnumerationAlarmItem eai : list) {
            doc.writeStartElement("EnumerationAlarm");
            doc.writeAttribute("enumerationLabel", eai.getEnumerationLabel());
            doc.writeAttribute("alarmLevel", eai.getAlarmLevel().xtceName);
            doc.writeEndElement();// EnumerationAlarm
        }

        doc.writeEndElement();// EnumerationAlarmList
    }

    private void writeBooleanParameterType(XMLStreamWriter doc, BooleanParameterType ptype)
            throws XMLStreamException {
        doc.writeStartElement("BooleanParameterType");
        if (ptype.getInitialValue() != null) {
            if (ptype.getInitialValue()) {
                doc.writeAttribute(ATTR_INITIAL_VALUE, ptype.getOneStringValue());
            } else {
                doc.writeAttribute(ATTR_INITIAL_VALUE, ptype.getZeroStringValue());
            }
        }
        doc.writeAttribute("oneStringValue", ptype.getOneStringValue());
        doc.writeAttribute("zeroStringValue", ptype.getZeroStringValue());
        writeNameDescription(doc, ptype);
        writeUnitSet(doc, ptype.getUnitSet());

        if (ptype.getEncoding() != null) {
            writeDataEncoding(doc, ptype.getEncoding());
        }

        doc.writeEndElement();
    }

    private void writeAbsoluteTimeParameterType(XMLStreamWriter doc, AbsoluteTimeParameterType ptype)
            throws XMLStreamException {
        doc.writeStartElement("AbsoluteTimeParameterType");
        if (ptype.getInitialValue() != null) {
            doc.writeAttribute(ATTR_INITIAL_VALUE, ptype.getInitialValue().toString());
        }
        writeNameDescription(doc, ptype);

        doc.writeStartElement(ELEM_ENCODING);
        if (ptype.getScale() != 1) {
            doc.writeAttribute("scale", Double.toString(ptype.getScale()));
        }
        if (ptype.getOffset() != 0) {
            doc.writeAttribute("offset", Double.toString(ptype.getOffset()));
        }

        if (ptype.getEncoding() != null) {
            writeDataEncoding(doc, ptype.getEncoding());
        }
        doc.writeEndElement();

        ReferenceTime referenceTime = ptype.getReferenceTime();
        if (referenceTime != null) {
            writeReferenceTime(doc, referenceTime);
        }
        doc.writeEndElement();
    }

    private void writeReferenceTime(XMLStreamWriter doc, ReferenceTime referenceTime) throws XMLStreamException {
        doc.writeStartElement("ReferenceTime");
        if (referenceTime.getOffsetFrom() != null) {
            writeParameterInstanceRef(doc, "OffsetFrom", referenceTime.getOffsetFrom());
        } else if (referenceTime.getEpoch() != null) {
            doc.writeStartElement("Epoch");
            TimeEpoch te = referenceTime.getEpoch();
            if (te.getCommonEpoch() != null) {
                doc.writeCharacters(te.getCommonEpoch().name());
            } else {
                doc.writeCharacters(te.getDateTime());
            }
            doc.writeEndElement();
        }
        doc.writeEndElement();
    }

    private void writeArrayParameterType(XMLStreamWriter doc, ArrayParameterType ptype)
            throws XMLStreamException {
        doc.writeStartElement("ArrayParameterType");
        writeNameReferenceAttribute(doc, "arrayTypeRef", (NameDescription) ptype.getElementType());
        if (ptype.getSize() == null) {
            doc.writeAttribute("numberOfDimensions", Integer.toString(ptype.getNumberOfDimensions()));
        }
        writeNameDescription(doc, ptype);
        if (ptype.getSize() != null) {
            writeDimensionList(doc, ptype.getSize());
        }
        doc.writeEndElement();
    }

    private void writeBinaryParameterType(XMLStreamWriter doc, BinaryParameterType ptype)
            throws XMLStreamException {
        doc.writeStartElement("BinaryParameterType");
        if (ptype.getInitialValue() != null) {
            doc.writeAttribute(ATTR_INITIAL_VALUE, ptype.getInitialValue().toString());
        }
        writeNameDescription(doc, ptype);

        if (ptype.getEncoding() != null) {
            writeDataEncoding(doc, ptype.getEncoding());
        }
        doc.writeEndElement();
    }

    private void writeArgumentType(XMLStreamWriter doc, ArgumentType atype) throws XMLStreamException {
        if (atype instanceof StringArgumentType) {
            writeStringArgumentType(doc, (StringArgumentType) atype);
        } else if (atype instanceof IntegerArgumentType) {
            writeIntegerArgumentType(doc, (IntegerArgumentType) atype);
        } else if (atype instanceof FloatArgumentType) {
            writeFloatArgumentType(doc, (FloatArgumentType) atype);
        } else if (atype instanceof BooleanArgumentType) {
            writeBooleanArgumentType(doc, (BooleanArgumentType) atype);
        } else if (atype instanceof EnumeratedArgumentType) {
            writeEnumeratedArgumentType(doc, (EnumeratedArgumentType) atype);
        } else if (atype instanceof AggregateArgumentType) {
            writeAggregateArgumentType(doc, (AggregateArgumentType) atype);
        } else if (atype instanceof BinaryArgumentType) {
            writeBinaryArgumentType(doc, (BinaryArgumentType) atype);
        } else if (atype instanceof AbsoluteTimeArgumentType) {
            writeAbsoluteTimeArgumentType(doc, (AbsoluteTimeArgumentType) atype);
        } else {
            log.warn("Unexpected argument type " + atype.getClass());
        }
    }

    private void writeStringArgumentType(XMLStreamWriter doc, StringArgumentType atype)
            throws XMLStreamException {
        doc.writeStartElement("StringArgumentType");
        writeNameDescription(doc, atype);
        writeUnitSet(doc, atype.getUnitSet());
        if (atype.getEncoding() != null) {
            writeDataEncoding(doc, atype.getEncoding());
        }
        doc.writeEndElement();
    }

    private void writeEnumeratedArgumentType(XMLStreamWriter doc, EnumeratedArgumentType atype)
            throws XMLStreamException {
        doc.writeStartElement("EnumeratedArgumentType");
        writeEnumeratedDataType(doc, atype);
        doc.writeEndElement();
    }

    private void writeEnumeratedDataType(XMLStreamWriter doc, EnumeratedDataType type) throws XMLStreamException {
        if (type.getInitialValue() != null) {
            doc.writeAttribute(ATTR_INITIAL_VALUE, type.getInitialValue());
        }
        writeNameDescription(doc, type);
        writeUnitSet(doc, type.getUnitSet());
        if (type.getEncoding() != null) {
            writeDataEncoding(doc, type.getEncoding());
        }

        doc.writeStartElement("EnumerationList");
        for (ValueEnumeration valueEnumeration : type.getValueEnumerationList()) {
            doc.writeStartElement("Enumeration");
            doc.writeAttribute("label", valueEnumeration.getLabel());
            doc.writeAttribute("value", Long.toString(valueEnumeration.getValue()));
            String description = valueEnumeration.getDescription();
            if (description != null) {
                doc.writeAttribute("shortDescription", description);
            }
            doc.writeEndElement();
        }
        for (ValueEnumerationRange ver : type.getValueEnumerationRangeList()) {
            doc.writeStartElement("Enumeration");
            doc.writeAttribute("label", ver.getLabel());
            doc.writeAttribute("value", Long.toString((long) ver.getMin()));
            doc.writeAttribute("maxValue", Long.toString((long) ver.getMax()));
            doc.writeEndElement();
        }
        doc.writeEndElement(); // EnumerationList
    }

    private void writeIntegerArgumentType(XMLStreamWriter doc, IntegerArgumentType atype)
            throws XMLStreamException {
        doc.writeStartElement("IntegerArgumentType");
        doc.writeAttribute(ATTR_SIZE_IN_BITS, Integer.toString(atype.getSizeInBits()));
        doc.writeAttribute("signed", atype.isSigned() ? "true" : "false");
        if (atype.getInitialValue() != null) {
            doc.writeAttribute(ATTR_INITIAL_VALUE, atype.getInitialValue().toString());
        }
        writeNameDescription(doc, atype);
        writeUnitSet(doc, atype.getUnitSet());

        if (atype.getEncoding() != null) {
            writeDataEncoding(doc, atype.getEncoding());
        }

        if (atype.getValidRange() != null) {
            doc.writeStartElement("ValidRangeSet");
            IntegerValidRange range = atype.getValidRange();

            if (!range.isValidRangeAppliesToCalibrated()) {
                doc.writeAttribute("validRangeAppliesToCalibrated", "false");
            }

            doc.writeStartElement("ValidRange");
            doc.writeAttribute("minInclusive", String.valueOf(range.getMinInclusive()));
            doc.writeAttribute("maxInclusive", String.valueOf(range.getMaxInclusive()));
            doc.writeEndElement(); // ValidRange

            doc.writeEndElement(); // ValidRangeSet
        }

        doc.writeEndElement();
    }

    private void writeFloatArgumentType(XMLStreamWriter doc, FloatArgumentType atype)
            throws XMLStreamException {
        doc.writeStartElement("FloatArgumentType");
        doc.writeAttribute(ATTR_SIZE_IN_BITS, Integer.toString(atype.getSizeInBits()));
        if (atype.getInitialValue() != null) {
            doc.writeAttribute(ATTR_INITIAL_VALUE, atype.getInitialValue().toString());
        }
        writeNameDescription(doc, atype);
        if (atype.getValidRange() != null) {
            FloatValidRange range = atype.getValidRange();
            doc.writeStartElement("ValidRange");
            if (range.isMinInclusive()) {
                doc.writeAttribute("minInclusive", String.valueOf(range.getMin()));
            } else {
                doc.writeAttribute("minExclusive", String.valueOf(range.getMin()));
            }
            if (range.isMaxInclusive()) {
                doc.writeAttribute("maxInclusive", String.valueOf(range.getMax()));
            } else {
                doc.writeAttribute("maxExclusive", String.valueOf(range.getMax()));
            }

            doc.writeAttribute("validRangeAppliesToCalibrated", "true");
            doc.writeEndElement();
        }
        writeUnitSet(doc, atype.getUnitSet());

        if (atype.getEncoding() != null) {
            writeDataEncoding(doc, atype.getEncoding());
        }

        doc.writeEndElement();
    }

    private void writeBooleanArgumentType(XMLStreamWriter doc, BooleanArgumentType atype)
            throws XMLStreamException {
        doc.writeStartElement("BooleanArgumentType");
        if (atype.getInitialValue() != null) {
            if (atype.getInitialValue()) {
                doc.writeAttribute(ATTR_INITIAL_VALUE, atype.getOneStringValue());
            } else {
                doc.writeAttribute(ATTR_INITIAL_VALUE, atype.getZeroStringValue());
            }
        }
        doc.writeAttribute("oneStringValue", atype.getOneStringValue());
        doc.writeAttribute("zeroStringValue", atype.getZeroStringValue());
        writeNameDescription(doc, atype);
        writeUnitSet(doc, atype.getUnitSet());

        if (atype.getEncoding() != null) {
            writeDataEncoding(doc, atype.getEncoding());
        }
        doc.writeEndElement();
    }

    private void writeBinaryArgumentType(XMLStreamWriter doc, BinaryArgumentType atype)
            throws XMLStreamException {
        doc.writeStartElement("BinaryArgumentType");
        if (atype.getInitialValue() != null) {
            doc.writeAttribute(ATTR_INITIAL_VALUE, StringConverter.arrayToHexString(atype.getInitialValue()));
        }
        writeNameDescription(doc, atype);
        writeUnitSet(doc, atype.getUnitSet());

        if (atype.getEncoding() != null) {
            writeDataEncoding(doc, atype.getEncoding());
        }

        doc.writeEndElement();
    }

    private void writeAbsoluteTimeArgumentType(XMLStreamWriter doc, AbsoluteTimeArgumentType atype)
            throws XMLStreamException {
        doc.writeStartElement("AbsoluteTimeArgumentType");
        if (atype.getInitialValue() != null) {
            doc.writeAttribute(ATTR_INITIAL_VALUE, atype.getInitialValue());
        }
        writeNameDescription(doc, atype);

        writeUnitSet(doc, atype.getUnitSet());
        if (atype.getEncoding() != null) {
            doc.writeStartElement(ELEM_ENCODING);
            if (atype.getScale() != 1) {
                doc.writeAttribute("scale", Double.toString(atype.getScale()));
            }
            if (atype.getOffset() != 0) {
                doc.writeAttribute("offset", Double.toString(atype.getScale()));
            }
            writeDataEncoding(doc, atype.getEncoding());
            doc.writeEndElement();// Encoding
        }

        writeReferenceTime(doc, atype.getReferenceTime());
        doc.writeEndElement();// AbsoluteTimeArgumentType
    }

    private static void writeAggregateArgumentType(XMLStreamWriter doc, AggregateArgumentType atype)
            throws XMLStreamException {
        doc.writeStartElement("AggregateArgumentType");
        writeAggregateDataType(doc, atype);
        doc.writeEndElement();
    }

    private void writeDimensionList(XMLStreamWriter doc, List<IntegerValue> size) throws XMLStreamException {
        doc.writeStartElement("DimensionList");
        for (IntegerValue iv : size) {
            /*
             * doc.writeStartElement("Dimension");
             * writeIntegerValue(doc, "StartingIndex", new FixedIntegerValue(0));
             * writeIntegerValue(doc, "EndingIndex", iv-1);
             * doc.writeEndElement();
             */

            // the code above is commented out because we do not have a way to compute the EndIndex as size-1 for
            // DynamicValues.
            // The Size element is not part of XSD but it is supported by Yamcs
            writeIntegerValue(doc, "Size", iv);
        }

        doc.writeEndElement();
    }

    private void writeIntegerValue(XMLStreamWriter doc, String elementName, IntegerValue iv)
            throws XMLStreamException {
        doc.writeStartElement(elementName);
        if (iv instanceof FixedIntegerValue) {
            doc.writeStartElement("FixedValue");
            FixedIntegerValue fiv = (FixedIntegerValue) iv;
            doc.writeCharacters(Long.toString(fiv.getValue()));
            doc.writeEndElement();
        } else if (iv instanceof DynamicIntegerValue) {
            doc.writeStartElement("DynamicValue");
            DynamicIntegerValue div = (DynamicIntegerValue) iv;
            writeParameterInstanceRef(doc, ELEM_PARAMETER_INSTANCE_REF, div.getParameterInstanceRef());
            doc.writeEndElement();
        }

        doc.writeEndElement();

    }

    private void writeNameReferenceAttribute(XMLStreamWriter doc, String attrName, NameDescription name)
            throws XMLStreamException {
        doc.writeAttribute(attrName, getNameReference(name));
    }

    private void writeParameterInstanceRef(XMLStreamWriter doc, String elementName, ParameterInstanceRef pinstRef)
            throws XMLStreamException {
        doc.writeStartElement(elementName);
        doc.writeAttribute(ATTR_PARAMETER_REF, getNameReference(pinstRef));
        if (pinstRef.getInstance() != 0) {
            doc.writeAttribute("instance", Integer.toString(pinstRef.getInstance()));
        }
        if (!pinstRef.useCalibratedValue()) {
            doc.writeAttribute("useCalibratedValue", "false");
        }
        doc.writeEndElement();
    }

    // write a parameter reference as an argument reference
    private void writeParameterInstanceRef(XMLStreamWriter doc, String elementName, ArgumentInstanceRef pref)
            throws XMLStreamException {
        doc.writeStartElement(elementName);
        doc.writeAttribute(ATTR_PARAMETER_REF, Mdb.YAMCS_CMDARG_SPACESYSTEM_NAME + "/" + pref.getName());
        if (!pref.useCalibratedValue()) {
            doc.writeAttribute("useCalibratedValue", "false");
        }
        doc.writeEndElement();
    }

    private void writeDataEncoding(XMLStreamWriter doc, DataEncoding encoding) throws XMLStreamException {
        if (encoding instanceof IntegerDataEncoding) {
            writeIntegerDataEncoding(doc, (IntegerDataEncoding) encoding);
        } else if (encoding instanceof FloatDataEncoding) {
            writeFloatDataEncoding(doc, (FloatDataEncoding) encoding);
        } else if (encoding instanceof StringDataEncoding) {
            writeStringDataEncoding(doc, (StringDataEncoding) encoding);
        } else if (encoding instanceof BinaryDataEncoding) {
            writeBinaryDataEncoding(doc, (BinaryDataEncoding) encoding);
        } else {
            log.warn("Unexpected data encoding " + encoding.getClass());
        }
    }

    private void writeIntegerDataEncoding(XMLStreamWriter doc, IntegerDataEncoding encoding)
            throws XMLStreamException {
        doc.writeStartElement("IntegerDataEncoding");

        if (encoding.getByteOrder() != ByteOrder.BIG_ENDIAN) {
            doc.writeAttribute("byteOrder", "leastSignificantByteFirst");
        }

        switch (encoding.getEncoding()) {
        case ONES_COMPLEMENT:
            doc.writeAttribute(ATTR_ENCODING, "onesComplement");
            break;
        case SIGN_MAGNITUDE:
            doc.writeAttribute(ATTR_ENCODING, "signMagnitude");
            break;
        case TWOS_COMPLEMENT:
            doc.writeAttribute(ATTR_ENCODING, "twosComplement");
            break;
        case UNSIGNED:
            doc.writeAttribute(ATTR_ENCODING, "unsigned");
            break;
        default:
            log.warn("Unexpected encoding " + encoding);
        }

        doc.writeAttribute(ATTR_SIZE_IN_BITS, Integer.toString(encoding.getSizeInBits()));
        writeNumericDataEncodingCommonProps(doc, encoding);

        doc.writeEndElement();// IntegerDataEncoding
    }

    private void writeNumericDataEncodingCommonProps(XMLStreamWriter doc, NumericDataEncoding encoding)
            throws XMLStreamException {
        if (encoding.getDefaultCalibrator() != null) {
            doc.writeStartElement("DefaultCalibrator");
            writeCalibrator(doc, encoding.getDefaultCalibrator());
            doc.writeEndElement();// DefaultCalibrator
        }

        if (encoding.getContextCalibratorList() != null) {
            doc.writeStartElement("ContextCalibratorList");
            for (ContextCalibrator cc : encoding.getContextCalibratorList()) {
                doc.writeStartElement("ContextCalibrator");

                doc.writeStartElement("ContextMatch");
                writeMatchCriteria(doc, cc.getContextMatch());
                doc.writeEndElement();// ContextMatch

                doc.writeStartElement("Calibrator");
                writeCalibrator(doc, cc.getCalibrator());
                doc.writeEndElement();// Calibrator

                doc.writeEndElement();// ContextCalibrator
            }
            doc.writeEndElement();// ContextCalibratorList
        }
    }

    private void writeCalibrator(XMLStreamWriter doc, Calibrator calibrator) throws XMLStreamException {
        if (calibrator instanceof PolynomialCalibrator) {
            doc.writeStartElement("PolynomialCalibrator");
            double[] coefficients = ((PolynomialCalibrator) calibrator).getCoefficients();
            for (int i = 0; i < coefficients.length; i++) {
                doc.writeStartElement("Term");
                doc.writeAttribute("exponent", Integer.toString(i));
                doc.writeAttribute("coefficient", Double.toString(coefficients[i]));
                doc.writeEndElement();// Term
            }
            doc.writeEndElement();// PolynomialCalibrator
        } else if (calibrator instanceof SplineCalibrator) {
            doc.writeStartElement("SplineCalibrator");
            // doc.writeAttribute("order", "1");
            for (SplinePoint sp : ((SplineCalibrator) calibrator).getPoints()) {
                doc.writeStartElement("SplinePoint");
                doc.writeAttribute("raw", Double.toString(sp.getRaw()));
                doc.writeAttribute("calibrated", Double.toString(sp.getCalibrated()));
                doc.writeEndElement();// SplinePoint
            }
            doc.writeEndElement();
        } else if (calibrator instanceof MathOperationCalibrator) {
            doc.writeStartElement("MathOperationCalibrator");
            writeMathOperation(doc, (MathOperationCalibrator) calibrator);
            doc.writeEndElement();// MathOperationCalibrator
        } else {
            log.error("Unsupported calibrator  type " + calibrator.getClass());
        }
    }

    private void writeMathOperation(XMLStreamWriter doc, MathOperation mathOp) throws XMLStreamException {
        for (MathOperation.Element me : mathOp.getElementList()) {
            doc.writeStartElement(me.getType().xtceName());
            if (me.getParameterInstanceRef() != null) {
                doc.writeAttribute(ATTR_PARAMETER_REF, getNameReference(me.getParameterInstanceRef()));
            }
            if (me.getType() == ElementType.OPERATOR || me.getType() == ElementType.VALUE_OPERAND) {
                doc.writeCharacters(me.toString());
            }
            doc.writeEndElement();
        }
    }

    private void writeFloatDataEncoding(XMLStreamWriter doc, FloatDataEncoding encoding)
            throws XMLStreamException {
        doc.writeStartElement(ELEM_FLOAT_DATA_ENCODING);
        doc.writeAttribute(ATTR_ENCODING, encoding.getEncoding().name());
        doc.writeAttribute(ATTR_SIZE_IN_BITS, Integer.toString(encoding.getSizeInBits()));

        if (encoding.getByteOrder() != ByteOrder.BIG_ENDIAN) {
            doc.writeAttribute("byteOrder", "leastSignificantByteFirst");
        }

        writeNumericDataEncodingCommonProps(doc, encoding);
        doc.writeEndElement();
    }

    private static void writeStringDataEncoding(XMLStreamWriter doc, StringDataEncoding encoding)
            throws XMLStreamException {
        doc.writeStartElement("StringDataEncoding");
        if (!"UTF-8".equals(encoding.getEncoding())) {
            doc.writeAttribute(ATTR_ENCODING, encoding.getEncoding());
        }

        if (encoding.getSizeInBits() > 0) {
            doc.writeStartElement(ELEM_SIZE_IN_BITS);
            doc.writeStartElement("Fixed");
            doc.writeStartElement("FixedValue");
            doc.writeCharacters(Integer.toString(encoding.getSizeInBits()));
            doc.writeEndElement();
            doc.writeEndElement();

            if (encoding.getSizeType() == SizeType.LEADING_SIZE) {
                doc.writeStartElement("LeadingSize");
                doc.writeAttribute("sizeInBitsOfSizeTag", Integer.toString(encoding.getSizeInBitsOfSizeTag()));
                doc.writeEndElement();
            } else if (encoding.getSizeType() == SizeType.TERMINATION_CHAR) {
                doc.writeStartElement("TerminationChar");
                doc.writeCharacters(Integer.toHexString(encoding.getTerminationChar()));
                doc.writeEndElement();
            }
            doc.writeEndElement();// SizeInBits
        } else {
            doc.writeStartElement(ELEM_VARIABLE);

            // This attribute is required
            long maxSizeInBits = encoding.getMaxSizeInBytes() * 8L;
            if (maxSizeInBits > 0) {
                doc.writeAttribute("maxSizeInBits", Long.toString(maxSizeInBits));
            } else {
                doc.writeAttribute("maxSizeInBits", "20000"); // Just set it high.
            }

            // Required element
            doc.writeStartElement("DynamicValue");
            doc.writeStartElement("ParameterInstanceRef");
            var dynamicIntegerValue = encoding.getDynamicBufferSize();
            if (dynamicIntegerValue != null) {
                doc.writeAttribute("parameterRef", dynamicIntegerValue.getParameterInstanceRef().getName());
            } else {
                doc.writeAttribute("parameterRef", XtceStaxReader.IGNORED_DYNAMIC_VALUE
                        .getParameterInstanceRef().getParameter().getName());
            }
            doc.writeEndElement(); // ParameterInstanceRef
            doc.writeEndElement(); // DynamicValue

            if (encoding.getSizeType() == SizeType.LEADING_SIZE) {
                doc.writeStartElement("LeadingSize");
                doc.writeAttribute("sizeInBitsOfSizeTag", Integer.toString(encoding.getSizeInBitsOfSizeTag()));
                doc.writeEndElement();
            } else if (encoding.getSizeType() == SizeType.TERMINATION_CHAR) {
                doc.writeStartElement("TerminationChar");

                // hexBinary requires multiples of 2 hex digits
                var hex = Integer.toHexString(encoding.getTerminationChar());
                if (hex.length() % 2 == 0) {
                    doc.writeCharacters(hex);
                } else {
                    doc.writeCharacters("0" + hex);
                }

                doc.writeEndElement();
            }

            doc.writeEndElement(); // Variable
        }

        doc.writeEndElement();// StringDataEncoding
    }

    private void writeBinaryDataEncoding(XMLStreamWriter doc, BinaryDataEncoding encoding)
            throws XMLStreamException {
        doc.writeStartElement("BinaryDataEncoding");

        doc.writeStartElement(ELEM_SIZE_IN_BITS);
        doc.writeStartElement("FixedValue");
        doc.writeCharacters(Integer.toString(encoding.getSizeInBits()));
        doc.writeEndElement();
        doc.writeEndElement();// SizeInBits

        if (encoding.getFromBinaryTransformAlgorithm() != null) {
            writeCustomAlgorithm(doc, (CustomAlgorithm) encoding.getFromBinaryTransformAlgorithm(),
                    "FromBinaryTransformAlgorithm", true);
        }
        if (encoding.getToBinaryTransformAlgorithm() != null) {
            writeCustomAlgorithm(doc, (CustomAlgorithm) encoding.getToBinaryTransformAlgorithm(),
                    "ToBinaryTransformAlgorithm", true);
        }

        doc.writeEndElement();// BinaryDataEncoding
    }

    private void writeStringParameterType(XMLStreamWriter doc, StringParameterType ptype)
            throws XMLStreamException {
        doc.writeStartElement("StringParameterType");
        writeNameDescription(doc, ptype);
        writeUnitSet(doc, ptype.getUnitSet());
        if (ptype.getEncoding() != null) {
            writeDataEncoding(doc, ptype.getEncoding());
        }
        doc.writeEndElement();
    }

    private static void writeNameDescription(XMLStreamWriter doc, NameDescription nameDescription)
            throws XMLStreamException {
        doc.writeAttribute("name", nameDescription.getName());
        if (nameDescription.getShortDescription() != null) {
            doc.writeAttribute("shortDescription", nameDescription.getShortDescription());
        }
        if (nameDescription.getLongDescription() != null) {
            doc.writeStartElement("LongDescription");
            doc.writeCharacters(nameDescription.getLongDescription());
            doc.writeEndElement();
        }
        if (nameDescription.getAliasSet().size() > 0) {
            doc.writeStartElement("AliasSet");
            for (Entry<String, String> alias : nameDescription.getAliasSet().getAliases().entrySet()) {
                doc.writeStartElement("Alias");
                doc.writeAttribute("nameSpace", alias.getKey());
                doc.writeAttribute("alias", alias.getValue());
                doc.writeEndElement();
            }
            doc.writeEndElement();
        }
        List<AncillaryData> l = nameDescription.getAncillaryData();
        if (l != null) {
            writeAncillaryData(doc, l);
        }
    }

    private static void writeUnitSet(XMLStreamWriter doc, List<UnitType> unitSet) throws XMLStreamException {
        doc.writeStartElement("UnitSet");
        for (UnitType unitType : unitSet) {
            doc.writeStartElement("Unit");
            if (unitType.getPower() != 1) {
                doc.writeAttribute("power", Double.toString(unitType.getPower()));
            }
            if (!unitType.getFactor().equals("1")) {
                doc.writeAttribute("factor", unitType.getFactor());
            }
            if (unitType.getDescription() != null) {
                doc.writeAttribute("description", unitType.getDescription());
            }
            doc.writeCharacters(unitType.getUnit());
            doc.writeEndElement();
        }
        doc.writeEndElement();
    }

    private void writeMetaCommand(XMLStreamWriter doc, MetaCommand command) throws XMLStreamException {
        doc.writeStartElement("MetaCommand");
        if (command.isAbstract()) {
            doc.writeAttribute("abstract", "true");
        }
        writeNameDescription(doc, command);
        if (command.getBaseMetaCommand() != null) {
            doc.writeStartElement("BaseMetaCommand");
            doc.writeAttribute("metaCommandRef", getNameReference(command.getBaseMetaCommand()));

            if (command.getArgumentAssignmentList() != null) {
                doc.writeStartElement("ArgumentAssignmentList");
                for (ArgumentAssignment aa : command.getArgumentAssignmentList()) {
                    writeArgumentAssignemnt(doc, aa);
                }
                doc.writeEndElement();
            }
            doc.writeEndElement();// BaseMetaCommand
        }
        if (command.getArgumentList() != null && !command.getArgumentList().isEmpty()) {
            doc.writeStartElement("ArgumentList");
            for (Argument arg : command.getArgumentList()) {
                writeArgument(doc, arg);
            }
            doc.writeEndElement();
        }
        if (command.getCommandContainer() != null) {
            writeCommandContainer(doc, command.getCommandContainer());
        }
        if (command.hasTransmissionConstraints()) {
            doc.writeStartElement("TransmissionConstraintList");
            for (TransmissionConstraint constraint : command.getTransmissionConstraintList()) {
                writeTransmissionConstraint(doc, constraint);
            }
            doc.writeEndElement();
        }
        if (command.getDefaultSignificance() != null) {
            writeSignificance(doc, command.getDefaultSignificance(), "DefaultSignificance");
        }

        if (command.hasCommandVerifiers()) {
            doc.writeStartElement("VerifierSet");
            for (CommandVerifier verifier : command.getCommandVerifiers()) {
                writeCommandVerifier(doc, verifier);
            }
            doc.writeEndElement();
        }
        doc.writeEndElement();// MetaCommand
    }

    private void writeSignificance(XMLStreamWriter doc, Significance significance, String elementName)
            throws XMLStreamException {
        doc.writeStartElement(elementName);
        if (significance.getReasonForWarning() != null) {
            doc.writeAttribute("reasonForWarning", significance.getReasonForWarning());
        }
        doc.writeAttribute("consequenceLevel", significance.getConsequenceLevel().xtceAlias());
        doc.writeEndElement();
    }

    private void writeArgumentAssignemnt(XMLStreamWriter doc, ArgumentAssignment aa) throws XMLStreamException {
        doc.writeStartElement("ArgumentAssignment");
        doc.writeAttribute("argumentName", aa.getArgumentName());
        doc.writeAttribute("argumentValue", aa.getArgumentValue());
        doc.writeEndElement();
    }

    private void writeArgument(XMLStreamWriter doc, Argument argument) throws XMLStreamException {
        doc.writeStartElement("Argument");
        writeNameReferenceAttribute(doc, "argumentTypeRef", (NameDescription) argument.getArgumentType());
        writeNameDescription(doc, argument);
        if (argument.getInitialValue() != null) {
            doc.writeAttribute(ATTR_INITIAL_VALUE, argument.getArgumentType().toString(argument.getInitialValue()));
        }
        doc.writeEndElement();
    }

    private void writeSequenceContainer(XMLStreamWriter doc, SequenceContainer container) throws XMLStreamException {
        doc.writeStartElement("SequenceContainer");
        writeNameDescription(doc, container);

        doc.writeStartElement("EntryList");

        for (SequenceEntry entry : container.getEntryList()) {
            writeSequenceEntry(doc, entry);
        }
        doc.writeEndElement();// EntryList

        if (container.getBaseContainer() != null) {
            doc.writeStartElement("BaseContainer");
            doc.writeAttribute("containerRef", getNameReference(container.getBaseContainer()));

            if (container.getRestrictionCriteria() != null) {
                doc.writeStartElement("RestrictionCriteria");
                writeMatchCriteria(doc, container.getRestrictionCriteria());
                doc.writeEndElement();// RestrictionCriteria
            }
            doc.writeEndElement();// BaseContainer
        }

        doc.writeEndElement();// SequenceContainer
    }

    private void writeMathAlgorithm(XMLStreamWriter doc, MathAlgorithm algorithm)
            throws XMLStreamException {
        doc.writeStartElement("MathAlgorithm");
        writeNameDescription(doc, algorithm);

        doc.writeStartElement("MathOperation");
        OutputParameter outp = algorithm.getOutputList().get(0);
        doc.writeAttribute("outputParameterRef", getNameReference(outp.getParameter()));
        writeMathOperation(doc, algorithm.getOperation());
        if (algorithm.getTriggerSet() != null) {
            writeTriggerSet(doc, algorithm.getTriggerSet());
        }
        doc.writeEndElement();// MathOperation
        doc.writeEndElement();// MathAlgorithm
    }

    private void writeCustomAlgorithm(XMLStreamWriter doc, CustomAlgorithm algorithm, String elementName,
            boolean inputOnly)
            throws XMLStreamException {
        doc.writeStartElement(elementName);
        writeNameDescription(doc, algorithm);
        if (algorithm.getAlgorithmText() != null) {
            doc.writeStartElement("AlgorithmText");
            doc.writeAttribute("language", algorithm.getLanguage());
            doc.writeCharacters(algorithm.getAlgorithmText());
            doc.writeEndElement();
        }

        if (!algorithm.getInputList().isEmpty()) {
            doc.writeStartElement("InputSet");
            for (InputParameter inp : algorithm.getInputList()) {
                doc.writeStartElement("InputParameterInstanceRef");
                ParameterInstanceRef pref = inp.getParameterInstance();
                if (pref != null) {
                    doc.writeAttribute(ATTR_PARAMETER_REF, getNameReference(pref));
                } else {// TODO - this should be written as part of an InputArgumentInstanceRef but only such a section
                        // is valid in XTCE
                    ArgumentInstanceRef aref = inp.getArgumentRef();
                    doc.writeAttribute(ATTR_PARAMETER_REF, Mdb.YAMCS_CMDARG_SPACESYSTEM_NAME + "/" + aref.getName());
                }
                if (inp.getInputName() != null) {
                    doc.writeAttribute("inputName", inp.getInputName());
                }
                doc.writeEndElement();
            }
            doc.writeEndElement();// InputSet
        }

        // Some CustomAlgorithm uses in XTCE only allow an inputset (e.g. verifiers)
        if (!inputOnly) {
            if (!algorithm.getOutputList().isEmpty()) {
                doc.writeStartElement("OutputSet");
                for (OutputParameter outp : algorithm.getOutputList()) {
                    doc.writeStartElement("OutputParameterRef");
                    doc.writeAttribute(ATTR_PARAMETER_REF, getNameReference(outp.getParameter()));
                    if (outp.getOutputName() != null) {
                        doc.writeAttribute("outputName", outp.getOutputName());
                    }
                    doc.writeEndElement();
                }
                doc.writeEndElement();// OutputSet
            }
            if (algorithm.getTriggerSet() != null) {
                writeTriggerSet(doc, algorithm.getTriggerSet());
            }
        }
        doc.writeEndElement();// elementName
    }

    private void writeTriggerSet(XMLStreamWriter doc, TriggerSetType triggerSet) throws XMLStreamException {
        doc.writeStartElement("TriggerSet");
        for (OnParameterUpdateTrigger trigger : triggerSet.getOnParameterUpdateTriggers()) {
            doc.writeStartElement("OnParameterUpdateTrigger");
            doc.writeAttribute(ATTR_PARAMETER_REF, getNameReference(trigger.getParameter()));
            doc.writeEndElement();
        }
        for (OnPeriodicRateTrigger trigger : triggerSet.getOnPeriodicRateTriggers()) {
            doc.writeStartElement("OnPeriodicRateTrigger");
            doc.writeAttribute("fireRateInSeconds", Double.toString(trigger.getFireRate() / 1000.0));
            doc.writeEndElement();
        }
        doc.writeEndElement();// TriggerSet
    }

    private static void writeAncillaryData(XMLStreamWriter doc, List<AncillaryData> l) throws XMLStreamException {
        doc.writeStartElement("AncillaryDataSet");
        for (AncillaryData ad : l) {
            doc.writeStartElement("AncillaryData");
            writeAttributeIfNotNull(doc, "name", ad.getName());
            writeAttributeIfNotNull(doc, "mimeType", ad.getMimeType());
            if (ad.getHref() != null) {
                doc.writeAttribute("href", ad.getHref().toString());
            }
            writeCharactersIfNotNull(doc, ad.getValue());
            doc.writeEndElement();
        }
        doc.writeEndElement();
    }

    private void writeSequenceEntry(XMLStreamWriter doc, SequenceEntry entry) throws XMLStreamException {
        if (entry instanceof ArrayParameterEntry) {
            doc.writeStartElement("ArrayParameterRefEntry");
            doc.writeAttribute(ATTR_PARAMETER_REF, getNameReference(((ArrayParameterEntry) entry).getParameter()));
        } else if (entry instanceof ParameterEntry) {
            doc.writeStartElement("ParameterRefEntry");
            doc.writeAttribute(ATTR_PARAMETER_REF, getNameReference(((ParameterEntry) entry).getParameter()));
        } else if (entry instanceof ContainerEntry) {
            doc.writeStartElement("ContainerRefEntry");
            doc.writeAttribute("containerRef", getNameReference(((ContainerEntry) entry).getContainer()));
        } else if (entry instanceof ArgumentEntry) {
            doc.writeStartElement("ArgumentRefEntry");
            doc.writeAttribute("argumentRef", ((ArgumentEntry) entry).getArgument().getName());
        } else if (entry instanceof FixedValueEntry) {
            doc.writeStartElement("FixedValueEntry");
            FixedValueEntry fve = (FixedValueEntry) entry;
            if (fve.getName() != null) {
                doc.writeAttribute("name", fve.getName());
            }
            doc.writeAttribute("binaryValue", StringConverter.arrayToHexString(fve.getBinaryValue()));
            doc.writeAttribute(ATTR_SIZE_IN_BITS, Integer.toString(fve.getSizeInBits()));
        } else {
            log.error("Unknown sequence entry type " + entry.getClass() + " used for " + entry);
            return;
        }
        if (entry.getReferenceLocation() != ReferenceLocationType.PREVIOUS_ENTRY
                || entry.getLocationInContainerInBits() != 0) {
            doc.writeStartElement("LocationInContainerInBits");
            doc.writeAttribute("referenceLocation", entry.getReferenceLocation().xtceName());
            doc.writeStartElement("FixedValue");
            doc.writeCharacters(Integer.toString(entry.getLocationInContainerInBits()));
            doc.writeEndElement();// FixedValue
            doc.writeEndElement();// LocationInContainerInBits
        }
        if (entry.getRepeatEntry() != null) {
            doc.writeStartElement("RepeatEntry");
            writeRepeat(doc, entry.getRepeatEntry());
            doc.writeEndElement();
        }

        if (entry.getIncludeCondition() != null) {
            doc.writeStartElement("IncludeCondition");
            writeMatchCriteria(doc, entry.getIncludeCondition());
            doc.writeEndElement();
        }

        if (entry instanceof ArrayParameterEntry) {
            List<IntegerValue> dim = ((ArrayParameterEntry) entry).getSize();
            if (dim != null) {
                writeDimensionList(doc, dim);
            }
        }

        doc.writeEndElement();// *RefEntry
    }

    private void writeRepeat(XMLStreamWriter doc, Repeat repeat) throws XMLStreamException {
        writeIntegerValue(doc, "Count", repeat.getCount());
        if (repeat.getOffsetSizeInBits() != 0) {
            doc.writeStartElement("Offset");
            doc.writeStartElement("FixedValue");
            doc.writeCharacters(Integer.toString(repeat.getOffsetSizeInBits()));
            doc.writeEndElement();
            doc.writeEndElement();
        }
    }

    private void writeMatchCriteria(XMLStreamWriter doc, MatchCriteria mc) throws XMLStreamException {
        if (mc instanceof Comparison) {
            writeComparison(doc, (Comparison) mc);
        } else if (mc instanceof ComparisonList) {
            writeComparisonList(doc, (ComparisonList) mc);
        } else if (mc instanceof BooleanExpression) {
            doc.writeStartElement("BooleanExpression");
            writeBooleanExpression(doc, (BooleanExpression) mc);
            doc.writeEndElement();
        }
    }

    private void writeComparisonList(XMLStreamWriter doc, ComparisonList comparisonList) throws XMLStreamException {
        doc.writeStartElement("ComparisonList");
        for (Comparison c : comparisonList.getComparisonList()) {
            writeComparison(doc, c);
        }
        doc.writeEndElement();
    }

    private void writeComparison(XMLStreamWriter doc, Comparison comparison) throws XMLStreamException {
        doc.writeStartElement("Comparison");
        ParameterOrArgumentRef ref = comparison.getRef();
        if (ref instanceof ParameterInstanceRef) {
            ParameterInstanceRef pref = (ParameterInstanceRef) ref;
            doc.writeAttribute(ATTR_PARAMETER_REF, getNameReference(pref));
            if (pref.getInstance() != 0) {
                doc.writeAttribute("instance", Integer.toString(pref.getInstance()));
            }
        } else {
            doc.writeAttribute(ATTR_PARAMETER_REF, Mdb.YAMCS_CMDARG_SPACESYSTEM_NAME + "/" + ref.getName());
        }

        boolean ucv = comparison.getRef().useCalibratedValue();
        if (!ucv) {
            doc.writeAttribute("useCalibratedValue", "false");
        }
        doc.writeAttribute("value", comparison.getStringValue());
        if (comparison.getComparisonOperator() != OperatorType.EQUALITY) {
            doc.writeAttribute("comparisonOperator", comparison.getComparisonOperator().getSymbol());
        }
        doc.writeEndElement();
    }

    private void writeBooleanExpression(XMLStreamWriter doc, BooleanExpression boolExpr) throws XMLStreamException {
        if (boolExpr instanceof Condition) {
            writeCondition(doc, (Condition) boolExpr);
        } else if (boolExpr instanceof ANDedConditions) {
            writeANDedCondition(doc, (ANDedConditions) boolExpr);
        } else if (boolExpr instanceof ORedConditions) {
            writeORedCondition(doc, (ORedConditions) boolExpr);
        }
    }

    private void writeCondition(XMLStreamWriter doc, Condition condition) throws XMLStreamException {
        doc.writeStartElement(ELEM_CONDITION);
        ParameterOrArgumentRef ref = condition.getLeftRef();
        if (ref instanceof ParameterInstanceRef) {
            writeParameterInstanceRef(doc, ELEM_PARAMETER_INSTANCE_REF, (ParameterInstanceRef) ref);
        } else {
            writeParameterInstanceRef(doc, ELEM_PARAMETER_INSTANCE_REF, (ArgumentInstanceRef) ref);
        }

        doc.writeStartElement(ELEM_COMPARISON_OPERATOR);
        doc.writeCharacters(condition.getComparisonOperator().getSymbol());
        doc.writeEndElement();

        ref = condition.getRightRef();
        if (ref instanceof ParameterInstanceRef) {
            writeParameterInstanceRef(doc, ELEM_PARAMETER_INSTANCE_REF, (ParameterInstanceRef) ref);
        } else if (ref instanceof ArgumentInstanceRef) {
            writeParameterInstanceRef(doc, ELEM_PARAMETER_INSTANCE_REF, (ArgumentInstanceRef) ref);
        } else {
            doc.writeStartElement(ELEM_VALUE);
            doc.writeCharacters(condition.getRightValue());
            doc.writeEndElement();
        }
        doc.writeEndElement();
    }

    private void writeANDedCondition(XMLStreamWriter doc, ANDedConditions condition) throws XMLStreamException {
        doc.writeStartElement("ANDedConditions");
        for (BooleanExpression boolExpr : condition.getExpressionList()) {
            writeBooleanExpression(doc, boolExpr);
        }
        doc.writeEndElement();
    }

    private void writeORedCondition(XMLStreamWriter doc, ORedConditions condition) throws XMLStreamException {
        doc.writeStartElement("ORedConditions");
        for (BooleanExpression boolExpr : condition.getExpressionList()) {
            writeBooleanExpression(doc, boolExpr);
        }
        doc.writeEndElement();
    }

    private void writeCommandContainer(XMLStreamWriter doc, CommandContainer container) throws XMLStreamException {
        doc.writeStartElement("CommandContainer");
        doc.writeAttribute("name", container.getName());
        doc.writeStartElement("EntryList");

        for (SequenceEntry entry : container.getEntryList()) {
            writeSequenceEntry(doc, entry);
        }
        doc.writeEndElement();// EntryList

        if (container.getBaseContainer() != null) {
            doc.writeStartElement("BaseContainer");
            doc.writeAttribute("containerRef", getNameReference(container.getBaseContainer()));

            if (container.getRestrictionCriteria() != null) {
                doc.writeStartElement("RestrictionCriteria");
                writeMatchCriteria(doc, container.getRestrictionCriteria());
                doc.writeEndElement();// RestrictionCriteria
            }
            doc.writeEndElement();// BaseContainer
        }

        doc.writeEndElement();// CommandContainer
    }

    private void writeTransmissionConstraint(XMLStreamWriter doc, TransmissionConstraint constraint)
            throws XMLStreamException {
        doc.writeStartElement("TransmissionConstraint");
        if (constraint.getTimeout() > 0) {
            Duration d = dataTypeFactory.newDuration(constraint.getTimeout());
            doc.writeAttribute("timeOut", d.toString());
        }
        writeMatchCriteria(doc, constraint.getMatchCriteria());
        doc.writeEndElement();// TransmissionConstraint
    }

    private void writeCommandVerifier(XMLStreamWriter doc, CommandVerifier verifier) throws XMLStreamException {
        if (!XTCE_VERIFIER_STAGES.contains(verifier.getStage())) {
            log.warn("The verifier stage '{}' cannot be mapped to XTCE. Allowed XTCE stages: {}", verifier.getStage(),
                    XTCE_VERIFIER_STAGES);
            return;
        }
        doc.writeStartElement(verifier.getStage() + "Verifier");

        var ancillaryData = new ArrayList<AncillaryData>();
        ancillaryData.add(new AncillaryData("yamcs.onSuccess",
                verifier.getOnSuccess() != null ? verifier.getOnSuccess().name() : null));
        ancillaryData.add(new AncillaryData("yamcs.onFail",
                verifier.getOnFail() != null ? verifier.getOnFail().name() : null));
        ancillaryData.add(new AncillaryData("yamcs.onTimeout",
                verifier.getOnTimeout() != null ? verifier.getOnTimeout().name() : null));
        writeAncillaryData(doc, ancillaryData);

        switch (verifier.getType()) {
        case MATCH_CRITERIA:
            writeMatchCriteria(doc, verifier.getMatchCriteria());
            break;
        case CONTAINER:
            doc.writeStartElement("ContainerRef");
            doc.writeAttribute("containerRef", getNameReference(verifier.getContainerRef()));
            doc.writeEndElement();
            break;
        case ALGORITHM:
            writeCustomAlgorithm(doc, (CustomAlgorithm) verifier.getAlgorithm(), "CustomAlgorithm", true);
            break;
        case PARAMETER_VALUE_CHANGE:
            ParameterValueChange pvc = verifier.getParameterValueChange();
            doc.writeStartElement(ELEM_PARAMETER_VALUE_CHANGE);
            writeParameterInstanceRef(doc, ELEM_PARAMETER_REF, pvc.getParameterRef());
            doc.writeStartElement("Change");
            doc.writeAttribute("value", Double.toString(pvc.getDelta()));
            doc.writeEndElement();
            doc.writeEndElement();// ParameterValueChange
            break;
        }

        writeCheckWindow(doc, verifier.getCheckWindow());
        if (verifier.getReturnParameter() != null) {
            doc.writeStartElement("ReturnParmRef");
            doc.writeAttribute(ATTR_PARAMETER_REF, getNameReference(verifier.getReturnParameter()));
            doc.writeEndElement();
        }
        doc.writeEndElement();// verifier name (stage)
    }

    private void writeCheckWindow(XMLStreamWriter doc, CheckWindow cw) throws XMLStreamException {
        doc.writeStartElement("CheckWindow");

        if (cw.getTimeToStartChecking() >= 0) {
            doc.writeAttribute("timeToStartChecking",
                    dataTypeFactory.newDuration(cw.getTimeToStartChecking()).toString());
        }

        doc.writeAttribute("timeToStopChecking",
                dataTypeFactory.newDuration(cw.getTimeToStopChecking()).toString());

        if (cw.getTimeWindowIsRelativeTo() != TimeWindowIsRelativeToType.LAST_VERIFIER) {
            doc.writeAttribute("timeWindowIsRelativeTo", cw.getTimeWindowIsRelativeTo().toXtce());
        }
        doc.writeEndElement();// CheckWindow
    }

    private String getNameReference(ParameterInstanceRef pinstRef) {
        StringBuilder sb = new StringBuilder();
        sb.append(getNameReference(pinstRef.getParameter()));
        if (pinstRef.getMemberPath() != null) {
            for (PathElement pe : pinstRef.getMemberPath()) {
                sb.append(".").append(pe.getName());
            }
        }
        return sb.toString();
    }

    // converts the nd qualified name to a relative reference to the current subsystem
    // if the reference is to the "/yamcs" then an absolute reference is provided instead
    private String getNameReference(NameDescription nd) {
        String ndqn = nd.getQualifiedName();
        if (ndqn == null) { // happens for arguments
            return nd.getName();
        }

        String ssname = currentSpaceSystem.getQualifiedName();

        if (ndqn.startsWith(ssname + "/")) {
            return ndqn.substring(ssname.length() + 1);
        } else if (ndqn.startsWith(Mdb.YAMCS_SPACESYSTEM_NAME)) {
            return ndqn;
        } else {
            String[] pe1 = currentSpaceSystem.getQualifiedName().split("/");
            String[] pe2 = nd.getSubsystemName().split("/");
            if (!pe1[1].equals(pe2[1])) {
                return ndqn;
            }
            int k = 0;
            for (k = 0; k < Math.min(pe1.length, pe2.length); k++) {
                if (!pe1[k].equals(pe2[k])) {
                    break;
                }
            }
            StringBuilder sb = new StringBuilder();
            for (int i = k; i < pe1.length; i++) {
                sb.append("../");
            }
            for (int i = k; i < pe2.length; i++) {
                sb.append(pe2[i]).append("/");
            }
            sb.append(nd.getName());
            return sb.toString();
        }
    }

    private static void writeAttributeIfNotNull(XMLStreamWriter doc, String name, String value)
            throws XMLStreamException {
        if (value != null) {
            doc.writeAttribute(name, value);
        }
    }

    private static void writeCharactersIfNotNull(XMLStreamWriter doc, String text) throws XMLStreamException {
        if (text != null) {
            doc.writeCharacters(text);
        }
    }

}

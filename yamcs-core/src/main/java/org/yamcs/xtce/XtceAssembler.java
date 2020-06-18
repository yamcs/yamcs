package org.yamcs.xtce;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Predicate;

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
import org.yamcs.utils.DoubleRange;
import org.yamcs.utils.StringConverter;
import org.yamcs.xtce.AggregateParameterType;
import org.yamcs.xtce.BooleanParameterType;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.FloatValidRange;
import org.yamcs.xtce.Header;
import org.yamcs.xtce.History;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.IntegerValidRange;
import org.yamcs.xtce.Member;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.NameDescription;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.SequenceEntry.ReferenceLocationType;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.StringDataEncoding;
import org.yamcs.xtce.StringDataEncoding.SizeType;
import org.yamcs.xtce.StringParameterType;
import org.yamcs.xtce.UnitType;
import org.yamcs.xtce.ValueEnumeration;
import org.yamcs.xtce.EnumerationAlarm.EnumerationAlarmItem;

/**
 * 
 * Experimental export of Mission Database to XTCE.
 * 
 * 
 * @author nm
 *
 */
public class XtceAssembler {

    private static final String NS_XTCE_V1_1 = "http://www.omg.org/space/xtce";
    private static final Log log = new Log(XtceAssembler.class);
    boolean emitYamcsNamespace = false;
    private SpaceSystem currentSpaceSystem;

    XtceDb xtceDb;

    public final String toXtce(XtceDb xtceDb) {
        return toXtce(xtceDb, "/", fqn -> true);
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
     * @param xtceDb
     * @param topSpaceSystem
     *            the fully qualified name of the space system where the export should start from. If the space system
     *            does not exist, a {@link IllegalArgumentException} will be thrown.
     * @param filter
     * @return
     */
    public final String toXtce(XtceDb xtceDb, String topSpaceSystem, Predicate<String> filter) {
        this.xtceDb = xtceDb;
        try {
            String unindentedXML;
            try (Writer writer = new StringWriter()) {
                XMLOutputFactory factory = XMLOutputFactory.newInstance();
                XMLStreamWriter xmlWriter = factory.createXMLStreamWriter(writer);
                xmlWriter.writeStartDocument();
                SpaceSystem top = xtceDb.getSpaceSystem(topSpaceSystem);
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
                Transformer transformer = transformerFactory.newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

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
            doc.writeDefaultNamespace(NS_XTCE_V1_1);
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
            if (!emitYamcsNamespace && XtceDb.YAMCS_SPACESYSTEM_NAME.equals(sub.getQualifiedName())) {
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
            doc.writeAttribute("initialValue", parameter.getInitialValue().toString());
        }
        writeNameDescription(doc, parameter);
        if (hasNonDefaultProperties(parameter)) {
            doc.writeStartElement("ParameterProperties");
            if (parameter.getDataSource() != DataSource.TELEMETERED) {
                doc.writeAttribute("dataSource", parameter.getDataSource().name().toLowerCase());
            }
            doc.writeEndElement();// ParameterProperties
        }
        doc.writeEndElement();// Parameter
    }

    boolean hasNonDefaultProperties(Parameter p) {
        return p.getDataSource() != DataSource.TELEMETERED;
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
            doc.writeStartElement("Member");
            writeNameDescription(doc, member);
            doc.writeAttribute("typeRef", member.getType().getName());
            doc.writeEndElement();
        }
        doc.writeEndElement();
    }

    private void writeIntegerParameterType(XMLStreamWriter doc, IntegerParameterType ptype)
            throws XMLStreamException {
        doc.writeStartElement("IntegerParameterType");
        if (ptype.getSizeInBits() != 32) {
            doc.writeAttribute("sizeInBits", Integer.toString(ptype.getSizeInBits()));
        }
        if (!ptype.isSigned()) {
            doc.writeAttribute("signed", "false");
        }
        if (ptype.getInitialValue() != null) {
            doc.writeAttribute("initialValue", ptype.getInitialValue().toString());
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
        doc.writeAttribute("sizeInBits", Integer.toString(ptype.getSizeInBits()));
        if (ptype.getInitialValue() != null) {
            doc.writeAttribute("initialValue", ptype.getInitialValue().toString());
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

        writeRange(doc, "WatchRange", ar.watchRange);
        writeRange(doc, "WarningRange", ar.warningRange);
        writeRange(doc, "DistressRange", ar.distressRange);
        writeRange(doc, "CriticalRange", ar.criticalRange);
        writeRange(doc, "SevereRange", ar.severeRange);

        doc.writeEndElement();
    }

    private static void writeEnumerationAlarm(XMLStreamWriter doc, List<EnumerationAlarmItem> list)
            throws XMLStreamException {
        doc.writeStartElement("EnumerationAlarmList");
        for (EnumerationAlarmItem eai : list) {
            doc.writeStartElement("EnumerationAlarm");
            doc.writeAttribute("enumerationLabel", eai.getEnumerationLabel());
            doc.writeAttribute("alarmLevel", eai.getAlarmLevel().name());
            doc.writeEndElement();// EnumerationAlarm
        }

        doc.writeEndElement();// EnumerationAlarmList
    }

    private void writeBooleanParameterType(XMLStreamWriter doc, BooleanParameterType ptype)
            throws XMLStreamException {
        doc.writeStartElement("BooleanParameterType");
        if (ptype.getInitialValue() != null) {
            if (ptype.getInitialValue()) {
                doc.writeAttribute("initialValue", ptype.getOneStringValue());
            } else {
                doc.writeAttribute("initialValue", ptype.getZeroStringValue());
            }
        }
        doc.writeAttribute("oneStringValue", ptype.getOneStringValue());
        doc.writeAttribute("zeroStringValue", ptype.getZeroStringValue());
        writeNameDescription(doc, ptype);
        writeUnitSet(doc, ptype.getUnitSet());

        DataEncoding encoding = ptype.getEncoding();
        if (encoding != null) {
            writeDataEncoding(doc, encoding);
        }

        doc.writeEndElement();
    }

    private void writeAbsoluteTimeParameterType(XMLStreamWriter doc, AbsoluteTimeParameterType ptype)
            throws XMLStreamException {
        doc.writeStartElement("AbsoluteTimeParameterType");
        if (ptype.getInitialValue() != null) {
            doc.writeAttribute("initialValue", ptype.getInitialValue().toString());
        }
        writeNameDescription(doc, ptype);
        writeUnitSet(doc, ptype.getUnitSet());

        doc.writeStartElement("Encoding");
        if (ptype.getScale() != 1) {
            doc.writeAttribute("scale", Double.toString(ptype.getScale()));
        }
        if (ptype.getOffset() != 0) {
            doc.writeAttribute("offset", Double.toString(ptype.getScale()));
        }

        writeDataEncoding(doc, ptype.getEncoding());
        doc.writeEndElement();

        writeReferenceTime(doc, ptype.getReferenceTime());
        doc.writeEndElement();
    }

    private void writeReferenceTime(XMLStreamWriter doc, ReferenceTime referenceTime) throws XMLStreamException {
        doc.writeStartElement("ReferenceTime");
        if (referenceTime.getOffsetFrom() != null) {
            writeParameterInstanceRef(doc, "OffsetFrom", referenceTime.getOffsetFrom());
        } else if (referenceTime.getEpoch() != null) {
            doc.writeStartElement("Epoch");
            TimeEpoch te = referenceTime.getEpoch();
            if(te.getCommonEpoch()!=null) {
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
            doc.writeAttribute("initialValue", ptype.getInitialValue().toString());
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
        } else {
            log.warn("Unexpected argument type " + atype.getClass());
        }
    }

    private void writeStringArgumentType(XMLStreamWriter doc, StringArgumentType atype)
            throws XMLStreamException {
        doc.writeStartElement("StringArgumentType");
        writeNameDescription(doc, atype);
        writeUnitSet(doc, atype.getUnitSet());
        writeDataEncoding(doc, atype.getEncoding());
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
            doc.writeAttribute("initialValue", type.getInitialValue());
        }
        writeNameDescription(doc, type);
        doc.writeStartElement("EnumerationList");
        for (ValueEnumeration valueEnumeration : type.getValueEnumerationList()) {
            doc.writeStartElement("Enumeration");
            doc.writeAttribute("label", valueEnumeration.getLabel());
            doc.writeAttribute("value", Long.toString(valueEnumeration.getValue()));
            doc.writeEndElement();
        }
        for (ValueEnumerationRange ver : type.getValueEnumerationRangeList()) {
            doc.writeStartElement("Enumeration");
            doc.writeAttribute("label", ver.getLabel());
            doc.writeAttribute("value", Long.toString((long) ver.min));
            doc.writeAttribute("maxValue", Long.toString((long) ver.max));
            doc.writeEndElement();
        }

        doc.writeEndElement();
        writeUnitSet(doc, type.getUnitSet());
        if (type.getEncoding() != null) {
            writeDataEncoding(doc, type.getEncoding());
        }
    }

    private void writeIntegerArgumentType(XMLStreamWriter doc, IntegerArgumentType atype)
            throws XMLStreamException {
        doc.writeStartElement("IntegerArgumentType");
        doc.writeAttribute("sizeInBits", Integer.toString(atype.getSizeInBits()));
        doc.writeAttribute("signed", atype.isSigned() ? "true" : "false");
        if (atype.getInitialValue() != null) {
            doc.writeAttribute("initialValue", atype.getInitialValue().toString());
        }
        writeNameDescription(doc, atype);
        if (atype.getValidRange() != null) {
            IntegerValidRange range = atype.getValidRange();
            doc.writeStartElement("ValidRange");
            doc.writeAttribute("minInclusive", String.valueOf(range.getMinInclusive()));
            doc.writeAttribute("maxInclusive", String.valueOf(range.getMaxInclusive()));
            if (!range.isValidRangeAppliesToCalibrated()) {
                doc.writeAttribute("validRangeAppliesToCalibrated", "false");
            }
            doc.writeEndElement();
        }
        writeUnitSet(doc, atype.getUnitSet());

        DataEncoding encoding = atype.getEncoding();
        writeDataEncoding(doc, encoding);

        doc.writeEndElement();
    }

    private void writeFloatArgumentType(XMLStreamWriter doc, FloatArgumentType atype)
            throws XMLStreamException {
        doc.writeStartElement("FloatArgumentType");
        doc.writeAttribute("sizeInBits", Integer.toString(atype.getSizeInBits()));
        if (atype.getInitialValue() != null) {
            doc.writeAttribute("initialValue", atype.getInitialValue().toString());
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

        DataEncoding encoding = atype.getEncoding();
        writeDataEncoding(doc, encoding);

        doc.writeEndElement();
    }

    private void writeBooleanArgumentType(XMLStreamWriter doc, BooleanArgumentType ptype)
            throws XMLStreamException {
        doc.writeStartElement("BooleanArgumentType");
        if (ptype.getInitialValue() != null) {
            if (ptype.getInitialValue()) {
                doc.writeAttribute("initialValue", ptype.getOneStringValue());
            } else {
                doc.writeAttribute("initialValue", ptype.getZeroStringValue());
            }
        }
        doc.writeAttribute("oneStringValue", ptype.getOneStringValue());
        doc.writeAttribute("zeroStringValue", ptype.getZeroStringValue());
        writeNameDescription(doc, ptype);
        writeUnitSet(doc, ptype.getUnitSet());

        DataEncoding encoding = ptype.getEncoding();
        writeDataEncoding(doc, encoding);

        doc.writeEndElement();
    }

    private void writeBinaryArgumentType(XMLStreamWriter doc, BinaryArgumentType ptype)
            throws XMLStreamException {
        doc.writeStartElement("BinaryArgumentType");
        if (ptype.getInitialValue() != null) {
            doc.writeAttribute("initialValue", StringConverter.arrayToHexString(ptype.getInitialValue()));
        }
        writeNameDescription(doc, ptype);
        writeUnitSet(doc, ptype.getUnitSet());

        DataEncoding encoding = ptype.getEncoding();
        writeDataEncoding(doc, encoding);

        doc.writeEndElement();
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
            writeParameterInstanceRef(doc, "ParameterInstanceRef", div.getParameterInstnaceRef());
            doc.writeEndElement();
        }

        doc.writeEndElement();

    }

    private static void writeNameReferenceAttribute(XMLStreamWriter doc, String attrName, NameDescription name)
            throws XMLStreamException {
        doc.writeAttribute(attrName, name.getName());
    }

    private void writeParameterInstanceRef(XMLStreamWriter doc, String elementName, ParameterInstanceRef pinstRef)
            throws XMLStreamException {
        doc.writeStartElement(elementName);
        doc.writeAttribute("parameterRef", getNameReference(pinstRef));
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
            doc.writeAttribute("encoding", "onesComplement");
            break;
        case SIGN_MAGNITUDE:
            doc.writeAttribute("encoding", "signMagnitude");
            break;
        case TWOS_COMPLEMENT:
            doc.writeAttribute("encoding", "twosComplement");
            break;
        case UNSIGNED:
            doc.writeAttribute("encoding", "unsigned");
            break;
        default:
            log.warn("Unexpected encoding " + encoding);
        }

        doc.writeAttribute("sizeInBits", Integer.toString(encoding.getSizeInBits()));
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

    private static void writeCalibrator(XMLStreamWriter doc, Calibrator calibrator) throws XMLStreamException {
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
            for (MathOperation.Element me : ((MathOperationCalibrator) calibrator).getElementList()) {
                doc.writeStartElement(me.getType().name());
                doc.writeCharacters(me.toString());
                doc.writeEndElement();
            }
            doc.writeEndElement();// MathOperationCalibrator
        } else {
            log.error("Unsupported calibrator  type " + calibrator.getClass());
        }
    }

    private void writeFloatDataEncoding(XMLStreamWriter doc, FloatDataEncoding encoding)
            throws XMLStreamException {
        doc.writeStartElement("FloatDataEncoding");
        doc.writeAttribute("encoding", encoding.getEncoding().name());
        doc.writeAttribute("sizeInBits", Integer.toString(encoding.getSizeInBits()));

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
            doc.writeAttribute("encoding", encoding.getEncoding());
        }

        doc.writeStartElement("SizeInBits");
        if (encoding.getSizeInBits() > 0) {
            doc.writeStartElement("Fixed");
            doc.writeStartElement("FixedValue");
            doc.writeCharacters(Integer.toString(encoding.getSizeInBits()));
            doc.writeEndElement();
            doc.writeEndElement();
        }

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
        doc.writeEndElement();// StringDataEncoding
    }

    private static void writeBinaryDataEncoding(XMLStreamWriter doc, BinaryDataEncoding encoding)
            throws XMLStreamException {
        doc.writeStartElement("BinaryDataEncoding");

        doc.writeStartElement("SizeInBits");
        if (encoding.getSizeInBits() > 0) {
            doc.writeStartElement("FixedValue");
            doc.writeCharacters(Integer.toString(encoding.getSizeInBits()));
            doc.writeEndElement();
        }

        doc.writeEndElement();// SizeInBits
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
    }

    private static void writeUnitSet(XMLStreamWriter doc, List<UnitType> unitSet) throws XMLStreamException {
        if (unitSet.isEmpty()) {
            return;
        }
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
        if (command.getArgumentList() != null) {
            doc.writeStartElement("ArgumentList");
            for (Argument arg : command.getArgumentList()) {
                writeArgument(doc, arg);
            }
            doc.writeEndElement();
        }
        if (command.getCommandContainer() != null) {
            writeCommandContainer(doc, command.getCommandContainer());
        }
        doc.writeEndElement();
    }

    private void writeArgumentAssignemnt(XMLStreamWriter doc, ArgumentAssignment aa) throws XMLStreamException {
        doc.writeStartElement("ArgumentAssignment");
        doc.writeAttribute("argumentName", aa.getArgumentName());
        doc.writeAttribute("argumentValue", aa.getArgumentValue());
        doc.writeEndElement();
    }

    private static void writeArgument(XMLStreamWriter doc, Argument argument) throws XMLStreamException {
        doc.writeStartElement("Argument");
        writeNameReferenceAttribute(doc, "argumentTypeRef", (NameDescription) argument.getArgumentType());
        writeNameDescription(doc, argument);
        if (argument.getInitialValue() != null) {
            doc.writeAttribute("initialValue", argument.getArgumentType().toString(argument.getInitialValue()));
        }
        doc.writeEndElement();
    }

    private void writeSequenceContainer(XMLStreamWriter doc, SequenceContainer container) throws XMLStreamException {
        doc.writeStartElement("SequenceContainer");
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

        doc.writeEndElement();// SequenceContainer
    }

    private void writeSequenceEntry(XMLStreamWriter doc, SequenceEntry entry) throws XMLStreamException {
        if (entry instanceof ArrayParameterEntry) {
            doc.writeStartElement("ArrayParameterRefEntry");
            doc.writeAttribute("parameterRef", getNameReference(((ArrayParameterEntry) entry).getParameter()));
        } else if (entry instanceof ParameterEntry) {
            doc.writeStartElement("ParameterRefEntry");
            doc.writeAttribute("parameterRef", getNameReference(((ParameterEntry) entry).getParameter()));
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
            doc.writeAttribute("sizeInBits", Integer.toString(fve.getSizeInBits()));
        } else {
            log.error("Unknown sequence entry type " + entry.getClass() + " used for " + entry);
            return;
        }
        if (entry.getReferenceLocation() != ReferenceLocationType.previousEntry
                || entry.getLocationInContainerInBits() != 0) {
            doc.writeStartElement("LocationInContainerInBits");
            doc.writeAttribute("referenceLocation", entry.getReferenceLocation().name());
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
            // TODO
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

        doc.writeAttribute("parameterRef", getNameReference(comparison.getParameterInstanceRef()));
        boolean ucv = comparison.getParameterInstanceRef().useCalibratedValue();
        if (!ucv) {
            doc.writeAttribute("useCalibratedValue", "false");
        }
        doc.writeAttribute("value", comparison.getStringValue());
        if (comparison.getComparisonOperator() != OperatorType.EQUALITY) {
            doc.writeAttribute("comparisonOperator", OperatorType.operatorToString(comparison.getComparisonOperator()));
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

    private String getNameReference(ParameterInstanceRef pinstRef) {
        StringBuilder sb = new StringBuilder();
        sb.append(getNameReference(pinstRef.getParameter()));
        if (pinstRef.getMemberPath() != null) {
            for (PathElement pe : pinstRef.getMemberPath()) {
                sb.append("/").append(pe.getName());
            }
        }
        return sb.toString();
    }

    private String getNameReference(NameDescription nd) {
        String ssname = currentSpaceSystem.getQualifiedName();
        if (nd.getQualifiedName().startsWith(ssname)) {
            return nd.getQualifiedName().substring(ssname.length() + 1);
        } else {
            String[] pe1 = currentSpaceSystem.getQualifiedName().split("/");
            String[] pe2 = nd.getSubsystemName().split("/");
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
}

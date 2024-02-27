package org.yamcs.parameter;

import java.io.IOException;
import java.util.Optional;
import java.util.OptionalInt;

import org.yamcs.http.YamcsEncoded;
import org.yamcs.protobuf.Mdb.AlarmLevelType;
import org.yamcs.protobuf.Mdb.AlarmRange;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.util.DoubleRange;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Timestamp;
import com.google.protobuf.WireFormat;

public class ParameterValue extends BasicParameterValue {
    // the definition of the parameter may be null if we do not have a reference to an XtceDB object
    // this could happen if the ParameterValue is extracted from the ParameterArchive
    private Parameter def;
    private final String paramFqn;

    private long acquisitionTime = TimeEncoding.INVALID_INSTANT;

    /**
     * Creates a parameter value for a parameter
     * 
     * @param def
     *            the parameter definition
     */
    public ParameterValue(Parameter def) {
        this.def = def;
        paramFqn = def.getQualifiedName();
    }

    public ParameterValue(String fqn) {
        this.def = null;
        this.paramFqn = fqn;
    }

    // copy constructor - copies all the fields in a shallow mode
    public ParameterValue(ParameterValue pv) {
        super(pv);
        this.def = pv.def;
        this.paramFqn = pv.paramFqn;
        this.acquisitionTime = pv.acquisitionTime;
    }

    public void setAcquisitionTime(long instant) {
        acquisitionTime = instant;
    }

    public void setParameter(Parameter p) {
        this.def = p;
    }

    /**
     * Retrieve the parameter definition for this parameter value
     * 
     * @return parameter definition
     */
    public Parameter getParameter() {
        return def;
    }

    public String getParameterQualifiedName() {
        return paramFqn;
    }

    /**
     * @deprecated use {@link #getParameterQualifiedName()}
     * @return
     */
    @Deprecated
    public String getParameterQualifiedNamed() {
        return paramFqn;
    }

    public long getAcquisitionTime() {
        return acquisitionTime;
    }

    public org.yamcs.protobuf.Pvalue.ParameterValue toGpb() {
        NamedObjectId id = NamedObjectId.newBuilder().setName(getParameterQualifiedName()).build();
        return toProtobufParameterValue(Optional.of(id), OptionalInt.empty());
    }

    public org.yamcs.protobuf.Pvalue.ParameterValue toGpb(NamedObjectId id) {
        Optional<NamedObjectId> optionalId = Optional.ofNullable(id);
        return toProtobufParameterValue(optionalId, OptionalInt.empty());
    }

    public org.yamcs.protobuf.Pvalue.ParameterValue toGpb(int numericId) {
        return toProtobufParameterValue(Optional.empty(), OptionalInt.of(numericId));
    }

    /**
     * Convert a PV to a ProtobufPV
     * 
     * @param id
     *            - the parameter identifier
     * @return the created ProtobufPV
     */
    public org.yamcs.protobuf.Pvalue.ParameterValue toProtobufParameterValue(Optional<NamedObjectId> id,
            OptionalInt numericId) {

        org.yamcs.protobuf.Pvalue.ParameterValue.Builder gpvb = org.yamcs.protobuf.Pvalue.ParameterValue.newBuilder()
                .setAcquisitionStatus(getAcquisitionStatus());
        if (generationTime != TimeEncoding.INVALID_INSTANT) {
            gpvb.setGenerationTime(TimeEncoding.toProtobufTimestamp(generationTime));
        }

        if (id.isPresent()) {
            gpvb.setId(id.get());
        }
        if (numericId.isPresent()) {
            gpvb.setNumericId(numericId.getAsInt());
        }

        if (acquisitionTime != TimeEncoding.INVALID_INSTANT) {
            gpvb.setAcquisitionTime(TimeEncoding.toProtobufTimestamp(acquisitionTime));
        }
        if (engValue != null) {
            gpvb.setEngValue(ValueUtility.toGbp(engValue));
        }
        if (getMonitoringResult() != null) {
            gpvb.setMonitoringResult(getMonitoringResult());
        }
        if (getRangeCondition() != null) {
            gpvb.setRangeCondition(getRangeCondition());
        }

        long expireMillis = status.getExpireMills();
        if (expireMillis >= 0) {
            gpvb.setExpireMillis(expireMillis);
        }

        if (getWatchRange() != null) {
            gpvb.addAlarmRange(toGpbAlarmRange(AlarmLevelType.WATCH, getWatchRange()));
        }
        if (getWarningRange() != null) {
            gpvb.addAlarmRange(toGpbAlarmRange(AlarmLevelType.WARNING, getWarningRange()));
        }
        if (getDistressRange() != null) {
            gpvb.addAlarmRange(toGpbAlarmRange(AlarmLevelType.DISTRESS, getDistressRange()));
        }
        if (getCriticalRange() != null) {
            gpvb.addAlarmRange(toGpbAlarmRange(AlarmLevelType.CRITICAL, getCriticalRange()));
        }
        if (getSevereRange() != null) {
            gpvb.addAlarmRange(toGpbAlarmRange(AlarmLevelType.SEVERE, getSevereRange()));
        }

        if (rawValue != null) {
            gpvb.setRawValue(ValueUtility.toGbp(rawValue));
        }
        return gpvb.build();
    }

    public boolean hasAcquisitionTime() {
        return acquisitionTime != TimeEncoding.INVALID_INSTANT;
    }

    /**
     * Verifies if the parameter value is expired at a given timestamp. Returns false if the expireMillis is not set.
     * 
     * @param now
     * @return true if the parameter is expired at the timestamp now.
     */
    public boolean isExpired(long now) {
        long expireMillis = status.getExpireMills();
        return (expireMillis > 0) && (acquisitionTime + expireMillis < now);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("name: ");
        if (def != null) {
            sb.append(def.getName());
        } else {
            sb.append(paramFqn);
        }
        sb.append(" genTime: {").append(TimeEncoding.toString(generationTime)).append("}");
        if (rawValue != null) {
            sb.append(" rawValue: {").append(rawValue.toString()).append("}");
        }
        if (engValue != null) {
            sb.append(" engValue: {").append(engValue.toString()).append("}");
        }
        return sb.toString();
    }

    /***** protobuf conversion methods *******/

    int memoizedSize = -1;
    static final int RAWVALUE_FN = org.yamcs.protobuf.Pvalue.ParameterValue.RAWVALUE_FIELD_NUMBER;
    static final int ENGVALUE_FN = org.yamcs.protobuf.Pvalue.ParameterValue.ENGVALUE_FIELD_NUMBER;
    static final int NUMERICID_FN = org.yamcs.protobuf.Pvalue.ParameterValue.NUMERICID_FIELD_NUMBER;
    static final int ACQUISITIONTIME_FN = org.yamcs.protobuf.Pvalue.ParameterValue.ACQUISITIONTIME_FIELD_NUMBER;
    static final int GENERATIONTIME_FN = org.yamcs.protobuf.Pvalue.ParameterValue.GENERATIONTIME_FIELD_NUMBER;
    static final int ACQUISITIONSTATUS_FN = org.yamcs.protobuf.Pvalue.ParameterValue.ACQUISITIONSTATUS_FIELD_NUMBER;
    static final int MONITORINGRESULT_FN = org.yamcs.protobuf.Pvalue.ParameterValue.MONITORINGRESULT_FIELD_NUMBER;
    static final int RANGECONDITION_FN = org.yamcs.protobuf.Pvalue.ParameterValue.RANGECONDITION_FIELD_NUMBER;
    static final int ALARMRANGE_FN = org.yamcs.protobuf.Pvalue.ParameterValue.ALARMRANGE_FIELD_NUMBER;
    static final int EXPIREMILLIS_FN = org.yamcs.protobuf.Pvalue.ParameterValue.EXPIREMILLIS_FIELD_NUMBER;

    public int getSerializedSize() {
        int size = memoizedSize;

        if (size == -1) {
            size = 0;

            if (rawValue != null) {
                size += YamcsEncoded.computeMessageSize(RAWVALUE_FN, rawValue);
            }

            if (engValue != null) {
                size += YamcsEncoded.computeMessageSize(ENGVALUE_FN, engValue);
            }
            /*
            if (acquisitionTime != TimeEncoding.INVALID_INSTANT) {
                Timestamp t = TimeEncoding.toProtobufTimestamp(acquisitionTime);
                size += CodedOutputStream.computeMessageSize(ACQUISITIONTIME_FN, t);
            }
            
            if (generationTime != TimeEncoding.INVALID_INSTANT) {
                Timestamp t = TimeEncoding.toProtobufTimestamp(generationTime);
                size += CodedOutputStream.computeMessageSize(GENERATIONTIME_FN, t);
            }
            */

            if (acquisitionTime != TimeEncoding.INVALID_INSTANT) {
                size += CodedOutputStream.computeInt64Size(ACQUISITIONTIME_FN, status.getExpireMills());
            }

            if (generationTime != TimeEncoding.INVALID_INSTANT) {
                size += CodedOutputStream.computeInt64Size(GENERATIONTIME_FN, status.getExpireMills());
            }

            if (status.getAcquisitionStatus() != null) {
                size += CodedOutputStream.computeEnumSize(ACQUISITIONSTATUS_FN,
                        status.getAcquisitionStatus().getNumber());
            }

            if (status.getMonitoringResult() != null) {
                size += CodedOutputStream.computeEnumSize(MONITORINGRESULT_FN,
                        status.getMonitoringResult().getNumber());
            }

            if (status.getRangeCondition() != null) {
                size += CodedOutputStream.computeEnumSize(RANGECONDITION_FN, status.getRangeCondition().getNumber());
            }

            size += doubleRangeSerializedSize(AlarmLevelType.WATCH, status.getWatchRange());
            size += doubleRangeSerializedSize(AlarmLevelType.WARNING, status.getWarningRange());
            size += doubleRangeSerializedSize(AlarmLevelType.DISTRESS, status.getDistressRange());
            size += doubleRangeSerializedSize(AlarmLevelType.SEVERE, status.getSevereRange());
            size += doubleRangeSerializedSize(AlarmLevelType.CRITICAL, status.getCriticalRange());

            if (status.getExpireMills() > 0) {
                size += CodedOutputStream.computeInt64Size(EXPIREMILLIS_FN, status.getExpireMills());
            }
            memoizedSize = size;
        }

        return size;
    }

    public void writeTo(CodedOutputStream output) throws IOException {

        if (rawValue != null) {
            YamcsEncoded.writeMessage(output, RAWVALUE_FN, rawValue);
        }

        if (engValue != null) {
            YamcsEncoded.writeMessage(output, ENGVALUE_FN, engValue);
        }

        if (acquisitionTime != TimeEncoding.INVALID_INSTANT) {
            output.writeSInt64(ACQUISITIONTIME_FN, acquisitionTime);
        }

        if (generationTime != TimeEncoding.INVALID_INSTANT) {
            output.writeSInt64(GENERATIONTIME_FN, acquisitionTime);
        }

        /*
        if (acquisitionTime != TimeEncoding.INVALID_INSTANT) {
            Timestamp t = TimeEncoding.toProtobufTimestamp(acquisitionTime);
            output.writeMessage(ACQUISITIONTIME_FN, t);
        }
        
        if (generationTime != TimeEncoding.INVALID_INSTANT) {
            Timestamp t = TimeEncoding.toProtobufTimestamp(generationTime);
            output.writeMessage(GENERATIONTIME_FN, t);
        }
        */

        if (status.getAcquisitionStatus() != null) {
            output.writeEnum(ACQUISITIONSTATUS_FN, status.getAcquisitionStatus().getNumber());
        }

        if (status.getMonitoringResult() != null) {
            output.writeEnum(MONITORINGRESULT_FN, status.getMonitoringResult().getNumber());
        }

        if (status.getRangeCondition() != null) {
            output.writeEnum(RANGECONDITION_FN, status.getRangeCondition().getNumber());
        }
        doubleRangeWriteTo(output, AlarmLevelType.WATCH, status.getWatchRange());
        doubleRangeWriteTo(output, AlarmLevelType.WARNING, status.getWarningRange());
        doubleRangeWriteTo(output, AlarmLevelType.DISTRESS, status.getDistressRange());
        doubleRangeWriteTo(output, AlarmLevelType.SEVERE, status.getSevereRange());
        doubleRangeWriteTo(output, AlarmLevelType.CRITICAL, status.getCriticalRange());

        if (status.getExpireMills() > 0) {
            output.writeUInt64(EXPIREMILLIS_FN, status.getExpireMills());
        }
    }

    private int doubleRangeSerializedSize(AlarmLevelType level, DoubleRange dr) {
        if (dr == null) {
            return 0;
        }

        int dataSize = doubleRangeSerializedDataSize(level, dr);

        return CodedOutputStream.computeTagSize(ALARMRANGE_FN) + YamcsEncoded.computeLengthDelimitedFieldSize(dataSize);

    }

    private int doubleRangeSerializedDataSize(AlarmLevelType level, DoubleRange dr) {

        int size = CodedOutputStream.computeEnumSize(AlarmRange.LEVEL_FIELD_NUMBER, level.getNumber());
        double min = dr.getMin();
        if (Double.isFinite(min)) {
            if (dr.isMinInclusive()) {
                size += CodedOutputStream.computeDoubleSize(AlarmRange.MININCLUSIVE_FIELD_NUMBER, min);
            } else {
                size += CodedOutputStream.computeDoubleSize(AlarmRange.MINEXCLUSIVE_FIELD_NUMBER, min);
            }
        }
        double max = dr.getMax();

        if (Double.isFinite(max)) {
            if (dr.isMaxInclusive()) {
                size += CodedOutputStream.computeDoubleSize(AlarmRange.MAXINCLUSIVE_FIELD_NUMBER, max);
            } else {
                size += CodedOutputStream.computeDoubleSize(AlarmRange.MAXEXCLUSIVE_FIELD_NUMBER, max);
            }
        }

        return size;
    }

    private void doubleRangeWriteTo(CodedOutputStream output, AlarmLevelType level, DoubleRange dr) throws IOException {
        if (dr == null) {
            return;
        }

        int dataSize = doubleRangeSerializedDataSize(level, dr);

        output.writeTag(ALARMRANGE_FN, WireFormat.WIRETYPE_LENGTH_DELIMITED);
        output.writeUInt32NoTag(dataSize);
        output.writeEnum(AlarmRange.LEVEL_FIELD_NUMBER, level.getNumber());

        double min = dr.getMin();
        if (Double.isFinite(min)) {
            if (dr.isMinInclusive()) {
                output.writeDouble(AlarmRange.MININCLUSIVE_FIELD_NUMBER, min);
            } else {
                output.writeDouble(AlarmRange.MINEXCLUSIVE_FIELD_NUMBER, min);
            }
        }
        double max = dr.getMax();

        if (Double.isFinite(max)) {
            if (dr.isMaxInclusive()) {
                output.writeDouble(AlarmRange.MAXINCLUSIVE_FIELD_NUMBER, max);
            } else {
                output.writeDouble(AlarmRange.MAXEXCLUSIVE_FIELD_NUMBER, max);
            }
        }

    }

}

package org.yamcs.parameter;

import java.util.List;
import java.util.Optional;

import org.yamcs.protobuf.Mdb.AlarmLevelType;
import org.yamcs.protobuf.Mdb.AlarmRange;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Pvalue.RangeCondition;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.DoubleRange;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SequenceEntry;

/**
 * Holds the value of a parameter
 *
 */
public class ParameterValue {

    // the definition of the parameter may be null if we do not have a reference to an XtceDB object
    // this could happen if the ParameterValue is extracted from the ParameterArchive
    private Parameter def;
    private final String paramFqn;

    SequenceEntry entry;
    int absoluteBitOffset, bitSize;

    private Value rawValue;
    private Value engValue;
    private long acquisitionTime = TimeEncoding.INVALID_INSTANT;
    private long generationTime;

    // use this singleton as a default status
    ParameterStatus status = ParameterStatus.NOMINAL;

    /**
     * Creates a parameter value for a parameter which has critical or warning range associated
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
        this.def = pv.def;
        this.paramFqn = pv.paramFqn;
        this.entry = pv.entry;
        this.absoluteBitOffset = pv.absoluteBitOffset;
        this.bitSize = pv.bitSize;
        this.rawValue = pv.rawValue;
        this.engValue = pv.engValue;
        this.acquisitionTime = pv.acquisitionTime;
        this.generationTime = pv.generationTime;
    }

    public int getAbsoluteBitOffset() {
        return absoluteBitOffset;
    }

    public void setAbsoluteBitOffset(int absoluteBitOffset) {
        this.absoluteBitOffset = absoluteBitOffset;
    }

    public int getBitSize() {
        return bitSize;
    }

    public void setBitSize(int bitSize) {
        this.bitSize = bitSize;
    }

    public void setSequenceEntry(SequenceEntry entry) {
        this.entry = entry;
    }

    public SequenceEntry getSequenceEntry() {
        return entry;
    }

    public void setAcquisitionTime(long instant) {
        acquisitionTime = instant;
    }

    public void setGenerationTime(long instant) {
        generationTime = instant;
    }

    public Value getEngValue() {
        return engValue;
    }

    public Value getRawValue() {
        return rawValue;
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

    public String getParameterQualifiedNamed() {
        return paramFqn;
    }

    public long getGenerationTime() {
        return generationTime;
    }

    public long getAcquisitionTime() {
        return acquisitionTime;
    }

    public void setRawValue(Value rv) {
        this.rawValue = rv;
    }

    public void setRawValue(byte[] b) {
        rawValue = new BinaryValue(b);
    }

    public void setRawFloatValue(float f) {
        rawValue = new FloatValue(f);
    }

    public void setRawDoubleValue(double d) {
        rawValue = new DoubleValue(d);
    }

    public void setRawValue(boolean b) {
        rawValue = new BooleanValue(b);
    }

    public void setRawValue(String s) {
        rawValue = new StringValue(s);
    }

    public void setRawSignedInteger(int x) {
        rawValue = new SInt32Value(x);
    }

    public void setRawUnsignedInteger(int x) {
        rawValue = new UInt32Value(x);
    }

    public void setRawSignedLong(long x) {
        rawValue = new SInt64Value(x);
    }

    public void setRawUnsignedLong(long x) {
        rawValue = new UInt64Value(x);
    }

    public void setStringValue(String s) {
        engValue = new StringValue(s);
    }

    public void setBinaryValue(byte[] v) {
        engValue = new BinaryValue(v);
    }

    public void setBooleanValue(boolean b) {
        engValue = new BooleanValue(b);
    }

    public void setDoubleValue(double v) {
        engValue = new DoubleValue(v);
    }

    public void setFloatValue(float v) {
        engValue = new FloatValue(v);
    }

    public void setSignedIntegerValue(int v) {
        engValue = new SInt32Value(v);
    }

    public void setUnsignedIntegerValue(int v) {
        engValue = new UInt32Value(v);
    }

    public void setSignedLongValue(long v) {
        engValue = new SInt64Value(v);
    }

    public void setUnsignedLongValue(long v) {
        engValue = new UInt64Value(v);
    }

    public void setEngineeringValue(Value ev) {
        this.engValue = ev;
    }

    public void setExpireMillis(long em) {
        changeNominalStatus();
        status.setExpireMillis(em);
    }

    public long getExpireMills() {
        return status.getExpireMills();
    }

    public void setEngValue(Value engValue) {
        this.engValue = engValue;
    }

    // *********** parameter status
    private void changeNominalStatus() {
        if (status == ParameterStatus.NOMINAL) {
            status = new ParameterStatus();
        }
    }

    public void setWatchRange(DoubleRange range) {
        changeNominalStatus();
        status.setWatchRange(range);
    }

    public void setWarningRange(DoubleRange range) {
        changeNominalStatus();
        status.setWarningRange(range);
    }

    public void setDistressRange(DoubleRange range) {
        changeNominalStatus();
        status.setDistressRange(range);
    }

    public void setCriticalRange(DoubleRange range) {
        changeNominalStatus();
        status.setCriticalRange(range);
    }

    public void setSevereRange(DoubleRange range) {
        changeNominalStatus();
        status.setSevereRange(range);
    }

    public void setMonitoringResult(MonitoringResult m) {
        changeNominalStatus();
        status.setMonitoringResult(m);
    }

    public void setDeltaMonitoringResult(MonitoringResult m) {
        changeNominalStatus();
        status.setDeltaMonitoringResult(m);
    }

    public void setRangeCondition(RangeCondition rangeCondition) {
        changeNominalStatus();
        status.setRangeCondition(rangeCondition);
    }

    public void setProcessingStatus(boolean p) {
        if (status.getProcessingStatus() != p) {
            changeNominalStatus();
        }
        status.setProcessingStatus(p);
    }

    public void setAcquisitionStatus(AcquisitionStatus a) {
        if (status.getAcquisitionStatus() != a) {
            changeNominalStatus();
        }
        status.setAcquisitionStatus(a);
    }

    public DoubleRange getDistressRange() {
        return status.getDistressRange();
    }

    public DoubleRange getWatchRange() {
        return status.getWatchRange();
    }

    public DoubleRange getCriticalRange() {
        return status.getCriticalRange();
    }

    public DoubleRange getWarningRange() {
        return status.getWarningRange();
    }

    public DoubleRange getSevereRange() {
        return status.getSevereRange();
    }

    public MonitoringResult getMonitoringResult() {
        return status.getMonitoringResult();
    }

    public RangeCondition getRangeCondition() {
        return status.getRangeCondition();
    }

    public AcquisitionStatus getAcquisitionStatus() {
        return status.getAcquisitionStatus();
    }

    public boolean getProcessingStatus() {
        return status.getProcessingStatus();
    }

    public MonitoringResult getDeltaMonitoringResult() {
        return status.getDeltaMonitoringResult();
    }

    public ParameterStatus getStatus() {
        return status;
    }

    public void setStatus(ParameterStatus parameterStatus) {
        this.status = parameterStatus;
    }

    /**
     * Convert a PV to a ProtobufPV
     * 
     * @param id
     *            - the parameter identifier
     * @param withUtc
     *            - if true - set the UTC string times
     * @return the created ProtobufPV
     */
    public org.yamcs.protobuf.Pvalue.ParameterValue toProtobufParameterValue(Optional<NamedObjectId> id,
            boolean withUtc) {

        org.yamcs.protobuf.Pvalue.ParameterValue.Builder gpvb = org.yamcs.protobuf.Pvalue.ParameterValue.newBuilder()
                .setAcquisitionStatus(getAcquisitionStatus())
                .setGenerationTime(getGenerationTime())
                .setProcessingStatus(getProcessingStatus());
        if (id.isPresent()) {
            gpvb.setId(id.get());
        }

        if (acquisitionTime != TimeEncoding.INVALID_INSTANT) {
            gpvb.setAcquisitionTime(acquisitionTime);
            if (withUtc) {
                gpvb.setAcquisitionTimeUTC(TimeEncoding.toString(getAcquisitionTime()));
            }
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
        if (withUtc) {
            gpvb.setGenerationTimeUTC(TimeEncoding.toString(getGenerationTime()));
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

    public org.yamcs.protobuf.Pvalue.ParameterValue toGpb(NamedObjectId id) {
        Optional<NamedObjectId> optionalId = Optional.ofNullable(id);
        return toProtobufParameterValue(optionalId, true);
    }

    public static AlarmRange toGpbAlarmRange(AlarmLevelType gpbLevel, DoubleRange floatRange) {
        AlarmRange.Builder rangeb = AlarmRange.newBuilder();
        rangeb.setLevel(gpbLevel);
        double min = floatRange.getMin();
        if (Double.isFinite(min)) { // floatRange represents the IN_LIMIT range, that's why we invert the inclusive and
                                    // exclusive
            if (floatRange.isMinInclusive()) {
                rangeb.setMinInclusive(min);
            } else {
                rangeb.setMinExclusive(min);
            }
        }
        double max = floatRange.getMax();
        if (Double.isFinite(max)) {
            if (floatRange.isMaxInclusive()) {
                rangeb.setMaxInclusive(max);
            } else {
                rangeb.setMaxExclusive(max);
            }
        }
        return rangeb.build();
    }

    public static ParameterValue fromGpb(String fqn, org.yamcs.protobuf.Pvalue.ParameterValue gpv) {
        ParameterValue pv = new ParameterValue(fqn);
        copyTo(gpv, pv);
        return pv;
    }

    public static ParameterValue fromGpb(Parameter pdef, org.yamcs.protobuf.Pvalue.ParameterValue gpv) {
        ParameterValue pv = new ParameterValue(pdef);
        copyTo(gpv, pv);
        return pv;
    }

    private static void copyTo(org.yamcs.protobuf.Pvalue.ParameterValue gpv, ParameterValue pv) {
        pv.setAcquisitionStatus(gpv.getAcquisitionStatus());
        if (gpv.hasEngValue()) {
            pv.setEngineeringValue(ValueUtility.fromGpb(gpv.getEngValue()));
        }

        if (gpv.hasAcquisitionTime()) {
            pv.setAcquisitionTime(gpv.getAcquisitionTime());
        }

        if (gpv.hasExpireMillis()) {
            pv.setExpireMillis(gpv.getExpireMillis());
        }

        if (gpv.hasGenerationTime()) {
            pv.setGenerationTime(gpv.getGenerationTime());
        }
        if (gpv.hasMonitoringResult()) {
            pv.setMonitoringResult(gpv.getMonitoringResult());
        }

        if (gpv.hasRangeCondition()) {
            pv.setRangeCondition(gpv.getRangeCondition());
        }

        if (gpv.hasProcessingStatus()) {
            pv.setProcessingStatus(gpv.getProcessingStatus());
        }

        if (gpv.hasRawValue()) {
            pv.setRawValue(ValueUtility.fromGpb(gpv.getRawValue()));
        }
    }

    public void addAlarmRanges(List<AlarmRange> alarmRangeList) {
        for (AlarmRange ar : alarmRangeList) {
            switch (ar.getLevel()) {
            case WATCH:
                setWatchRange(fromGbpAlarmRange(ar));
                break;
            case WARNING:
                setWarningRange(fromGbpAlarmRange(ar));
                break;
            case DISTRESS:
                setDistressRange(fromGbpAlarmRange(ar));
                break;
            case CRITICAL:
                setCriticalRange(fromGbpAlarmRange(ar));
                break;
            case SEVERE:
                setSevereRange(fromGbpAlarmRange(ar));
                break;
            case NORMAL: // never used
            }
        }
    }

    public boolean hasAcquisitionTime() {
        return acquisitionTime != TimeEncoding.INVALID_INSTANT;
    }

    public boolean hasExpirationTime() {
        return status.getExpireMills() >= 0;
    }

    private DoubleRange fromGbpAlarmRange(AlarmRange ar) {
        double min = Double.NEGATIVE_INFINITY;
        double max = Double.POSITIVE_INFINITY;
        boolean minInclusive = false;
        boolean maxInclusive = false;

        if (ar.hasMinInclusive()) {
            min = ar.getMinInclusive();
            minInclusive = true;
        } else if (ar.hasMinExclusive()) {
            min = ar.getMinExclusive();
        }

        if (ar.hasMaxInclusive()) {
            max = ar.getMaxInclusive();
            maxInclusive = true;
        } else if (ar.hasMaxExclusive()) {
            max = ar.getMaxExclusive();
        }
        return new DoubleRange(min, max, minInclusive, maxInclusive);
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
}

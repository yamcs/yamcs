package org.yamcs.parameter;

import java.util.List;

import org.yamcs.protobuf.Mdb.AlarmLevelType;
import org.yamcs.protobuf.Mdb.AlarmRange;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Pvalue.RangeCondition;
import org.yamcs.utils.DoubleRange;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Parameter;

/**
 * Holds the value of a parameter.
 * 
 * This class does not reference any parameter definition or name.
 * It doesn't have an aquisition time for parameters either
 *
 */
public class BasicParameterValue {
    protected Value rawValue;
    protected Value engValue;
    protected long generationTime = TimeEncoding.INVALID_INSTANT;

    // use this singleton as a default status
    ParameterStatus status = ParameterStatus.NOMINAL;

    public BasicParameterValue() {
    }
    
    // copy constructor - copies all the fields in a shallow mode
    public BasicParameterValue(BasicParameterValue pv) {
        this.rawValue = pv.rawValue;
        this.engValue = pv.engValue;
        this.generationTime = pv.generationTime;
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

   
    public long getGenerationTime() {
        return generationTime;
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
            pv.setAcquisitionTime(TimeEncoding.fromProtobufTimestamp(gpv.getAcquisitionTime()));
        }

        if (gpv.hasExpireMillis()) {
            pv.setExpireMillis(gpv.getExpireMillis());
        }

        if (gpv.hasGenerationTime()) {
            pv.setGenerationTime(TimeEncoding.fromProtobufTimestamp(gpv.getGenerationTime()));
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

   

    public boolean hasGenerationTime() {
        return generationTime != TimeEncoding.INVALID_INSTANT;
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

   
}

package org.yamcs.parameter;

import java.util.List;

import org.yamcs.protobuf.Mdb.AlarmLevelType;
import org.yamcs.protobuf.Mdb.AlarmRange;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Pvalue.RangeCondition;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.FloatRange;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterEntry;

/** 
 * Holds the value of a parameter
 * @author mache
 *
 */
public class ParameterValue {
    public MonitoringResult getDeltaMonitoringResult() {
        return deltaMonitoringResult;
    }

    //the definition of the parameter may be null if we do not have a reference to an XtceDB object 
    // this could happen if the ParameterValue is extracted from the ParameterArchive
    private final Parameter def;
    private final String paramFqn;


    ParameterEntry entry;
    int absoluteBitOffset, bitSize;

    private Value rawValue;
    private Value engValue;
    private long acquisitionTime = TimeEncoding.INVALID_INSTANT;
    private long generationTime;
    private long expirationTime = TimeEncoding.INVALID_INSTANT;

    private AcquisitionStatus acquisitionStatus;
    private boolean processingStatus;
    private MonitoringResult monitoringResult;
    private MonitoringResult deltaMonitoringResult;
    private RangeCondition rangeCondition;


    public FloatRange watchRange = null;
    public FloatRange warningRange = null;
    public FloatRange distressRange = null;
    public FloatRange criticalRange = null;
    public FloatRange severeRange = null;


    /**
     * Creates a parameter value for a parameter which has critical or warning range associated
     * @param def the parameter definition
     */
    public ParameterValue(Parameter def) {
        this.def=def;
        paramFqn = def.getQualifiedName();

        setAcquisitionStatus(AcquisitionStatus.ACQUIRED);
        setProcessingStatus(true);
    }
    public ParameterValue(String fqn) {
        this.def = null;
        this.paramFqn = fqn;
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

    public void setParameterEntry(ParameterEntry entry) {
        this.entry = entry;
    }

    public ParameterEntry getParameterEntry() {
        return entry;
    }

    public void setAcquisitionStatus(AcquisitionStatus a) {
        acquisitionStatus=a;
    }

    public void setAcquisitionTime(long instant) {
        acquisitionTime=instant;
    }

    public void setGenerationTime(long instant) {
        generationTime=instant;
    }

    public Value getEngValue() {
        return engValue;
    }

    public Value getRawValue() {
        return rawValue;
    }

    public void setWatchRange(FloatRange range) {
        watchRange=range;
    }

    public FloatRange getWatchRange() {
        return watchRange;
    }

    public void setWarningRange(FloatRange range) {
        warningRange=range;
    }

    public FloatRange getWarningRange() {
        return warningRange;
    }

    public void setDistressRange(FloatRange range) {
        distressRange=range;
    }

    public FloatRange getDistressRange() {
        return distressRange;
    }

    public void setCriticalRange(FloatRange range) {
        criticalRange=range;
    }

    public FloatRange getCriticalRange() {
        return criticalRange;
    }

    public void setSevereRange(FloatRange range) {
        severeRange=range;
    }

    public FloatRange getSevereRange() {
        return severeRange;
    }

    public void setMonitoringResult(MonitoringResult m) {
        monitoringResult=m;
    }

    public void setDeltaMonitoringResult(MonitoringResult m) {
        deltaMonitoringResult=m;
    }

    public void setRangeCondition(RangeCondition rangeCondition) {
        this.rangeCondition = rangeCondition;
    }

    public void setProcessingStatus(boolean p) {
        processingStatus=p;
    }


    /**
     * Retrieve the parameter definition for this parameter value
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

    public MonitoringResult getMonitoringResult() {
        return monitoringResult;
    }

    public RangeCondition getRangeCondition() {
        return rangeCondition;
    }

    public long getAcquisitionTime() {
        return acquisitionTime;
    }

    public AcquisitionStatus getAcquisitionStatus() {
        return acquisitionStatus;
    }

    public boolean getProcessingStatus() {
        return processingStatus;
    }


    public void setRawValue(Value rv) {
        this.rawValue=rv;
    }

    public void setRawValue(byte[] b) {
        rawValue = new BinaryValue(b);
    }

    public void setRawValue(float f) {
        rawValue = new FloatValue(f);
    }

    public void setRawValue(double d) {
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
        this.engValue=ev;
    }

    public void setExpirationTime(long et) {
        this.expirationTime = et;
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    public void setEngValue(Value engValue) {
        this.engValue = engValue;
    }

    public org.yamcs.protobuf.Pvalue.ParameterValue toGpb(NamedObjectId id) {
        org.yamcs.protobuf.Pvalue.ParameterValue.Builder gpvb=org.yamcs.protobuf.Pvalue.ParameterValue.newBuilder()
                .setAcquisitionStatus(getAcquisitionStatus())               
                .setGenerationTime(getGenerationTime())
                .setProcessingStatus(getProcessingStatus());

        if(acquisitionTime!=TimeEncoding.INVALID_INSTANT) {
            gpvb.setAcquisitionTime(acquisitionTime);
        }
        if(engValue!=null) {
            gpvb.setEngValue(ValueUtility.toGbp(engValue));
        }
        if(monitoringResult!=null) {
            gpvb.setMonitoringResult(monitoringResult);
        }
        if(rangeCondition!=null) {
            gpvb.setRangeCondition(rangeCondition);
        }

        // TODO make this optional
        if(acquisitionTime!=TimeEncoding.INVALID_INSTANT) {
            gpvb.setAcquisitionTimeUTC(TimeEncoding.toString(getAcquisitionTime()));
        }
        gpvb.setGenerationTimeUTC(TimeEncoding.toString(getGenerationTime()));

        if(expirationTime!=TimeEncoding.INVALID_INSTANT) {
            gpvb.setExpirationTime(expirationTime);
            gpvb.setExpirationTimeUTC(TimeEncoding.toString(expirationTime));
        }

        // TODO make this optional
        if (watchRange != null)
            gpvb.addAlarmRange(toGpbAlarmRange(AlarmLevelType.WATCH, watchRange));
        if (warningRange != null)
            gpvb.addAlarmRange(toGpbAlarmRange(AlarmLevelType.WARNING, warningRange));
        if (distressRange != null)
            gpvb.addAlarmRange(toGpbAlarmRange(AlarmLevelType.DISTRESS, distressRange));
        if (criticalRange != null)
            gpvb.addAlarmRange(toGpbAlarmRange(AlarmLevelType.CRITICAL, criticalRange));
        if (severeRange!=null)
            gpvb.addAlarmRange(toGpbAlarmRange(AlarmLevelType.SEVERE, severeRange));

        if(id!=null) gpvb.setId(id);
        if(rawValue!=null) gpvb.setRawValue(ValueUtility.toGbp(rawValue));
        return gpvb.build();
    }

    private static AlarmRange toGpbAlarmRange(AlarmLevelType gpbLevel, FloatRange floatRange) {
        AlarmRange.Builder rangeb = AlarmRange.newBuilder();
        rangeb.setLevel(gpbLevel);
        if (Double.isFinite(floatRange.getMinInclusive()))
            rangeb.setMinInclusive(floatRange.getMinInclusive());
        if (Double.isFinite(floatRange.getMaxInclusive()))
            rangeb.setMaxInclusive(floatRange.getMaxInclusive());
        return rangeb.build();
    }

    public static ParameterValue fromGpb(Parameter pdef, org.yamcs.protobuf.Pvalue.ParameterValue gpv) {
        ParameterValue pv=new ParameterValue(pdef);

        pv.setAcquisitionStatus(gpv.getAcquisitionStatus());
        pv.setEngineeringValue(ValueUtility.fromGpb(gpv.getEngValue()));

        if(gpv.hasAcquisitionTime()) {
            pv.setAcquisitionTime(gpv.getAcquisitionTime());
        }

        if(gpv.hasExpirationTime()) {
            pv.setExpirationTime(gpv.getExpirationTime());
        }

        if(gpv.hasGenerationTime()) {
            pv.setGenerationTime(gpv.getGenerationTime());
        }
        if(gpv.hasMonitoringResult())
            pv.setMonitoringResult(gpv.getMonitoringResult());

        if(gpv.hasRangeCondition())
            pv.setRangeCondition(gpv.getRangeCondition());

        pv.setProcessingStatus(gpv.getProcessingStatus());

        if(gpv.hasRawValue()) {
            pv.setRawValue(ValueUtility.fromGpb(gpv.getRawValue()));
        }
        return pv;
    }

    
    public void addAlarmRanges(List<AlarmRange> alarmRangeList) {
        for(AlarmRange ar: alarmRangeList) {
            switch(ar.getLevel()){
                case WATCH:
                    watchRange = fromGbpAlarmRange(ar); 
                    break;
                case WARNING:
                    warningRange = fromGbpAlarmRange(ar);
                    break;
                case DISTRESS:
                    distressRange = fromGbpAlarmRange(ar);
                    break;
                case CRITICAL:
                    criticalRange = fromGbpAlarmRange(ar);
                    break;
                case SEVERE:
                    severeRange = fromGbpAlarmRange(ar);
                    break;
                case NORMAL: //never used
            }
        } 
    }

    public boolean hasAcquisitionTime() {
        return acquisitionTime != TimeEncoding.INVALID_INSTANT;
    }

    private FloatRange fromGbpAlarmRange(AlarmRange ar) {
        double minInclusive = ar.hasMinInclusive()?ar.getMinInclusive():Double.NEGATIVE_INFINITY;
        double maxInclusive = ar.hasMaxInclusive()?ar.getMaxInclusive():Double.POSITIVE_INFINITY;
        return new FloatRange(minInclusive, maxInclusive);
    }
    
    @Override
    public String toString() {
        StringBuilder sb=new StringBuilder();
        sb.append("name: ");
        if(def!=null) {
            sb.append(def.getName());
        } else {
            sb.append(paramFqn);
        }
        if(rawValue!=null) sb.append(" rawValue: {").append(rawValue.toString()).append("}");
        if(engValue!=null) sb.append(" engValue: {").append(engValue.toString()).append("}");
        return sb.toString();
    }
    


}

package org.yamcs.parameter;

import java.util.Optional;
import java.util.OptionalInt;

import org.yamcs.protobuf.Mdb.AlarmLevelType;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Parameter;

public class ParameterValue extends BasicParameterValue {
    // the definition of the parameter may be null if we do not have a reference to an MDB object
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
                .setAcquisitionStatus(getAcquisitionStatus())
                .setGenerationTime(TimeEncoding.toProtobufTimestamp(generationTime));
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
}

package org.yamcs.xtce;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.yamcs.xtce.util.DoubleRange;

public class FloatParameterType extends FloatDataType implements NumericParameterType {
    private static final long serialVersionUID = 2L;
    private NumericAlarm defaultAlarm = null;
    private List<NumericContextAlarm> contextAlarmList = null;

    public FloatParameterType(Builder builder) {
        super(builder);
        this.defaultAlarm = builder.defaultAlarm;
        this.contextAlarmList = builder.contextAlarmList;

        if (builder.baseType instanceof FloatParameterType) {
            FloatParameterType baseType = (FloatParameterType) builder.baseType;
            if (builder.defaultAlarm == null && baseType.defaultAlarm != null) {
                this.defaultAlarm = baseType.defaultAlarm;
            }
            if (builder.contextAlarmList == null && baseType.contextAlarmList != null) {
                this.contextAlarmList = baseType.contextAlarmList;
            }
        }
    }

    /**
     * Creates a shallow copy.
     */
    public FloatParameterType(FloatParameterType t) {
        super(t);
        this.defaultAlarm = t.defaultAlarm;
        this.contextAlarmList = t.contextAlarmList;
    }

    @Override
    public NumericAlarm getDefaultAlarm() {
        return defaultAlarm;
    }

    @Override
    public boolean hasAlarm() {
        return defaultAlarm != null || (contextAlarmList != null && !contextAlarmList.isEmpty());
    }

    @Override
    public Set<Parameter> getDependentParameters() {
        if (contextAlarmList == null) {
            return (encoding != null) ? encoding.getDependentParameters() : Collections.emptySet();
        }

        Set<Parameter> dependentParameters = new HashSet<>();
        dependentParameters.addAll(encoding.getDependentParameters());

        for (NumericContextAlarm nca : contextAlarmList) {
            dependentParameters.addAll(nca.getContextMatch().getDependentParameters());
        }
        return dependentParameters;
    }

    public List<NumericContextAlarm> getContextAlarmList() {
        return contextAlarmList;
    }

    public void setDefaultAlarm(NumericAlarm defaultAlarm) {
        this.defaultAlarm = defaultAlarm;
    }

    @Override
    public Builder toBuilder() {
        return new Builder(this);
    }

    @Override
    public String toString() {
        return "FloatParameterType name:" + name + " sizeInBits:" + sizeInBits + " encoding:" + encoding
                + ((getDefaultAlarm() != null) ? ", defaultAlarm:" + getDefaultAlarm() : "")
                + ((contextAlarmList != null) ? ", contextAlarmList:" + contextAlarmList : "");
    }

    public static class Builder extends FloatDataType.Builder<Builder>
            implements NumericParameterType.Builder<Builder> {
        private NumericAlarm defaultAlarm = null;
        private List<NumericContextAlarm> contextAlarmList = null;

        public Builder() {
        }

        public Builder(FloatParameterType floatParameterType) {
            super(floatParameterType);
            this.defaultAlarm = floatParameterType.defaultAlarm;
            this.contextAlarmList = floatParameterType.contextAlarmList;
        }

        @Override
        public void setContextAlarmList(List<NumericContextAlarm> contextAlarmList) {
            this.contextAlarmList = contextAlarmList;
        }

        public void addContextAlarm(NumericContextAlarm nca) {
            if (contextAlarmList == null) {
                contextAlarmList = new ArrayList<>();
            }
            contextAlarmList.add(nca);
        }

        public void addContextAlarms(Collection<NumericContextAlarm> ncas) {
            if (contextAlarmList == null) {
                contextAlarmList = new ArrayList<>();
            }
            contextAlarmList.addAll(ncas);
        }

        @Override
        public NumericAlarm createOrGetAlarm(MatchCriteria contextMatch) {
            if (contextMatch == null) {
                if (defaultAlarm == null) {
                    defaultAlarm = new NumericAlarm();
                }
                return defaultAlarm;
            } else {
                NumericContextAlarm nca = getNumericContextAlarm(contextMatch);
                if (nca == null) {
                    nca = new NumericContextAlarm();
                    nca.setContextMatch(contextMatch);
                    addContextAlarm(nca);
                }
                return nca;
            }
        }

        public NumericContextAlarm getNumericContextAlarm(MatchCriteria context) {
            if (contextAlarmList == null) {
                return null;
            }
            for (NumericContextAlarm nca : contextAlarmList) {
                if (nca.getContextMatch().equals(context)) {
                    return nca;
                }
            }
            return null;
        }

        /**
         * Adds a new, or unions with an existing range for the specified context and level
         * 
         * @param contextMatch
         *            use {@code null} for the default context
         */
        public void addAlarmRange(MatchCriteria contextMatch, DoubleRange floatRange, AlarmLevels level) {
            getAlarmRanges(contextMatch).addRange(floatRange, level);

        }

        /**
         * Adds a new, or unions with an existing watch range for the specified context
         * 
         * @param contextMatch
         *            use {@code null} for the default context
         */
        public void addWatchAlarmRange(MatchCriteria contextMatch, DoubleRange watchRange) {
            getAlarmRanges(contextMatch).addWatchRange(watchRange);
        }

        /**
         * Adds a new, or unions with an existing warning range for the specified context
         * 
         * @param contextMatch
         *            use {@code null} for the default context
         */
        public void addWarningAlarmRange(MatchCriteria contextMatch, DoubleRange warningRange) {
            getAlarmRanges(contextMatch).addWarningRange(warningRange);
        }

        /**
         * Adds a new, or unions with an existing distress range for the specified context
         * 
         * @param contextMatch
         *            use {@code null} for the default context
         */
        public void addDistressAlarmRange(MatchCriteria contextMatch, DoubleRange distressRange) {
            getAlarmRanges(contextMatch).addDistressRange(distressRange);
        }

        /**
         * Adds a new, or unions with an existing critical range for the specified context
         * 
         * @param contextMatch
         *            use {@code null} for the default context
         */
        public void addCriticalAlarmRange(MatchCriteria contextMatch, DoubleRange criticalRange) {
            getAlarmRanges(contextMatch).addCriticalRange(criticalRange);
        }

        /**
         * Adds a new, or unions with an existing severe range for the specified context
         * 
         * @param contextMatch
         *            use {@code null} for the default context
         */
        public void addSevereAlarmRange(MatchCriteria contextMatch, DoubleRange severeRange) {
            getAlarmRanges(contextMatch).addSevereRange(severeRange);
        }

        private AlarmRanges getAlarmRanges(MatchCriteria contextMatch) {
            NumericAlarm alarm = createOrGetAlarm(contextMatch);
            return alarm.getStaticAlarmRanges();
        }

        public void setDefaultWatchAlarmRange(DoubleRange watchRange) {
            getAlarmRanges(null).watchRange = watchRange;
        }

        public void setDefaultWarningAlarmRange(DoubleRange warningRange) {
            getAlarmRanges(null).warningRange = warningRange;
        }

        public void setDefaultDistressAlarmRange(DoubleRange distressRange) {
            getAlarmRanges(null).distressRange = distressRange;
        }

        public void setDefaultCriticalAlarmRange(DoubleRange criticalRange) {
            getAlarmRanges(null).criticalRange = criticalRange;
        }

        public void setDefaultSevereAlarmRange(DoubleRange severeRange) {
            getAlarmRanges(null).severeRange = severeRange;
        }

        public NumericAlarm getDefaultAlarm() {
            return defaultAlarm;
        }

        @Override
        public FloatParameterType build() {
            return new FloatParameterType(this);
        }

        @Override
        public void setDefaultAlarm(NumericAlarm defaultAlarm) {
            this.defaultAlarm = defaultAlarm;
        }
    }

}

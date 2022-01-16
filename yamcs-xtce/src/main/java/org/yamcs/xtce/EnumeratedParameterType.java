package org.yamcs.xtce;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EnumeratedParameterType extends EnumeratedDataType implements ParameterType {
    private static final long serialVersionUID = 5L;

    private EnumerationAlarm defaultAlarm = null;
    private List<EnumerationContextAlarm> contextAlarmList = null;

    public EnumeratedParameterType(Builder builder) {
        super(builder);
        this.defaultAlarm = builder.defaultAlarm;
        this.contextAlarmList  = builder.contextAlarmList;
        
        
        if (builder.baseType instanceof EnumeratedParameterType) {
            EnumeratedParameterType baseType = (EnumeratedParameterType) builder.baseType;
            if(builder.defaultAlarm == null && baseType.defaultAlarm!=null) {
                this.defaultAlarm = baseType.defaultAlarm;
            }
            if(builder.contextAlarmList == null && baseType.contextAlarmList!=null) {
                this.contextAlarmList = baseType.contextAlarmList;
            }
        }
    }
    
    /**
     * Copy constructor
     * 
     */
    public EnumeratedParameterType(EnumeratedParameterType t) {
        super(t);
        this.defaultAlarm = t.defaultAlarm;
        this.contextAlarmList = t.contextAlarmList;
    }

    @Override
    public boolean hasAlarm() {
        return defaultAlarm != null || (contextAlarmList != null && !contextAlarmList.isEmpty());
    }
    
    /**
     * Override the default alarm from MDB
     * @param enumerationAlarm
     */
    public void setDefaultAlarm(EnumerationAlarm enumerationAlarm) {
        this.defaultAlarm = enumerationAlarm;
    }
    
    @Override
    public Set<Parameter> getDependentParameters() {
        if (contextAlarmList == null) {
            return Collections.emptySet();
        }
        Set<Parameter> dependentParameters = new HashSet<>();
        for (EnumerationContextAlarm eca : contextAlarmList)
            dependentParameters.addAll(eca.getContextMatch().getDependentParameters());
        return dependentParameters;
    }

    public EnumerationAlarm getDefaultAlarm() {
        return defaultAlarm;
    }

  

    public EnumerationContextAlarm getContextAlarm(MatchCriteria contextMatch) {
        if (contextAlarmList == null) {
            return null;
        }
        for (EnumerationContextAlarm eca : contextAlarmList) {
            if (eca.getContextMatch().equals(contextMatch)) {
                return eca;
            }
        }
        return null;
    }

    
  

    public List<EnumerationContextAlarm> getContextAlarmList() {
        return contextAlarmList;
    }


    public String getCalibrationDescription() {
        return "EnumeratedParameterType: " + enumeration;
    }


    public void setContextAlarmList(List<EnumerationContextAlarm> contextAlarmList) {
        this.contextAlarmList = contextAlarmList;
    }


    @Override
    public Builder toBuilder() {
        return new Builder(this);
    }


    @Override
    public String toString() {
        return "EnumeratedParameterType: " + enumerationList + " encoding:" + encoding
                + ((defaultAlarm != null) ? defaultAlarm : "") + ((contextAlarmList != null) ? contextAlarmList : "");
    }
    
    public static class Builder extends EnumeratedDataType.Builder<Builder> implements ParameterType.Builder<Builder> {
        private EnumerationAlarm defaultAlarm = null;
        private List<EnumerationContextAlarm> contextAlarmList = null;

        public Builder() {
            
        }
        
        public Builder(EnumeratedParameterType enumeratedParameterType) {
            super(enumeratedParameterType);
            this.defaultAlarm = enumeratedParameterType.defaultAlarm;
            this.contextAlarmList = enumeratedParameterType.contextAlarmList;
        }

        public void setDefaultAlarm(EnumerationAlarm enumerationAlarm) {
            this.defaultAlarm = enumerationAlarm;
        }
        
        public void addContextAlarm(EnumerationContextAlarm nca) {
            if (contextAlarmList == null) {
                contextAlarmList = new ArrayList<>();
            }
            contextAlarmList.add(nca);
        }
        
        /**
         * Adds a new contextual alarm for the specified value
         * 
         * @param contextMatch
         *            use {@code null} for the default context
         */
        public void addAlarm(MatchCriteria contextMatch, String enumLabel, AlarmLevels level) {
            createOrGetAlarm(contextMatch).addAlarm(enumLabel, level);
        }

        public EnumerationAlarm createOrGetAlarm(MatchCriteria contextMatch) {
            if (contextMatch == null) {
                if (defaultAlarm == null) {
                    defaultAlarm = new EnumerationAlarm();
                }
                return defaultAlarm;
            } else {
                EnumerationContextAlarm eca = getContextAlarm(contextMatch);
                if (eca == null) {
                    eca = new EnumerationContextAlarm();
                    eca.setContextMatch(contextMatch);
                    addContextAlarm(eca);
                }
                return eca;
            }
        }
        
        public EnumerationAlarm getDefaultAlarm() {
            return defaultAlarm;
        }

        public EnumerationContextAlarm getContextAlarm(MatchCriteria contextMatch) {
            if (contextAlarmList == null) {
                return null;
            }
            for (EnumerationContextAlarm eca : contextAlarmList) {
                if (eca.getContextMatch().equals(contextMatch)) {
                    return eca;
                }
            }
            return null;
        }
        
        public void setContextAlarmList(List<EnumerationContextAlarm> contextAlarmList) {
            this.contextAlarmList = contextAlarmList;
        }

        @Override
        public EnumeratedParameterType build() {
            return new EnumeratedParameterType(this);
        }
    }

}

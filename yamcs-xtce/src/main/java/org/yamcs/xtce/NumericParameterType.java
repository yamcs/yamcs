package org.yamcs.xtce;

import java.util.List;

public interface NumericParameterType extends ParameterType {

    public DataEncoding getEncoding();

    public NumericAlarm getDefaultAlarm();
    // public <T extends Builder<T>> Builder<T> toBuilder();

    Builder<?> toBuilder();

    interface Builder<T extends Builder<T>> extends ParameterType.Builder<T> {
        public void setDefaultAlarm(NumericAlarm defaultAlarm);

        /**
         * Sets the contextual alarm list overriding any other contextual alarm if already set.
         * 
         * @param contextAlarmList
         */
        public void setContextAlarmList(List<NumericContextAlarm> contextAlarmList);

        public DataEncoding.Builder<?> getEncoding();

        public T setEncoding(DataEncoding.Builder<?> enc);

        public NumericParameterType build();

        public NumericAlarm createOrGetAlarm(MatchCriteria contextMatch);
    }
}

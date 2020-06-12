package org.yamcs.xtce;

import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.TimeEncoding;

/**
 * Used to contain an absolute time.
 * Contains an absolute (to a known epoch) time.
 * 
 * Use the [ISO 8601] extended format CCYY-MM-DDThh:mm:ss where "CC" represents the century, "YY" the year, "MM" the
 * month and "DD" the day,
 * preceded by an optional leading "-" sign to indicate a negative number. If the sign is omitted, "+" is assumed.
 * The letter "T" is the date/time separator and "hh", "mm", "ss" represent hour, minute and second respectively.
 * Additional digits can be used to increase the precision of fractional seconds if desired i.e. the format ss.ss...
 * with any number of digits after
 * the decimal point is supported.
 * 
 * 
 * @author nm
 *
 */
public abstract class AbsoluteTimeDataType extends BaseTimeDataType {
    private static final long serialVersionUID = 1;

    ReferenceTime referenceTime;

    protected AbsoluteTimeDataType(Builder<?> builder) {
        super(builder);
        this.referenceTime = builder.referenceTime;
    }

    protected AbsoluteTimeDataType(String name) {
        super(name);
    }

    protected AbsoluteTimeDataType(AbsoluteTimeDataType t) {
        super(t);
        this.initialValue = t.initialValue;
        this.referenceTime = t.referenceTime;
    }

    public ReferenceTime getReferenceTime() {
        return referenceTime;
    }

    /**
     * sets the initial value in UTC ISO 8860 string
     */
    public void setInitialValue(String initialValue) {
        this.initialValue = TimeEncoding.parse(initialValue);
    }

    public Long getInitialValue() {
        return (Long) initialValue;
    }

    @Override
    public Long parseString(String stringValue) {
        return TimeEncoding.parse(stringValue);
    }

    @Override
    public Type getValueType() {
        return Value.Type.TIMESTAMP;
    }

    @Override
    public String getTypeAsString() {
        return "time";
    }

    public static abstract class Builder<T extends Builder<T>> extends BaseTimeDataType.Builder<T> {
        ReferenceTime referenceTime;

        public Builder() {
        }

        public Builder(AbsoluteTimeParameterType dataType) {
            super(dataType);
            this.referenceTime = dataType.referenceTime;
        }

        public void setReferenceTime(ReferenceTime referenceTime) {
            this.referenceTime = referenceTime;
        }

        public ReferenceTime getReferenceTime() {
            return referenceTime;
        }
    }
}

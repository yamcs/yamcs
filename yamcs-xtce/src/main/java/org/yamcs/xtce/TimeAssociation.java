package org.yamcs.xtce;

/**
 * Time association for a sequence entry.
 * <p>
 * XTCE models this as a parameter instance reference plus optional interpolation and offset metadata.
 */
public class TimeAssociation extends ParameterInstanceRef {
    private static final long serialVersionUID = 1L;

    public enum UnitType {
        SI_NANOSECOND("si_nanosecond"),
        SI_MICROSECOND("si_microsecond"),
        SI_MILLSECOND("si_millsecond"),
        SI_SECOND("si_second"),
        MINUTE("minute"),
        DAY("day"),
        JULIAN_YEAR("julianYear");

        private final String xtceName;

        UnitType(String xtceName) {
            this.xtceName = xtceName;
        }

        public String xtceName() {
            return xtceName;
        }

        public static UnitType fromXtceName(String xtceName) {
            for (var unit : values()) {
                if (unit.xtceName.equals(xtceName)) {
                    return unit;
                }
            }
            throw new IllegalArgumentException("Invalid time association unit '" + xtceName + "'");
        }
    }

    private boolean interpolateTime = true;
    private Double offset;
    private UnitType unit = UnitType.SI_SECOND;

    public TimeAssociation(boolean useCalibratedValue) {
        super(useCalibratedValue);
    }

    public boolean isInterpolateTime() {
        return interpolateTime;
    }

    public void setInterpolateTime(boolean interpolateTime) {
        this.interpolateTime = interpolateTime;
    }

    public Double getOffset() {
        return offset;
    }

    public void setOffset(Double offset) {
        this.offset = offset;
    }

    public UnitType getUnit() {
        return unit;
    }

    public void setUnit(UnitType unit) {
        this.unit = unit;
    }
}

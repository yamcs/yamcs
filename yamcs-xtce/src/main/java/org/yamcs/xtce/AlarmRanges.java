package org.yamcs.xtce;

import java.io.Serializable;

import org.yamcs.utils.DoubleRange;

/**
 * Contains five ranges: Watch, Warning, Distress, Critical, and Severe each in increasing severity. 
 * Normally, only the Warning and Critical ranges are used and the color yellow is associated with Warning 
 * and the color red is associated with Critical. The ranges given are valid for numbers lower than the 
 * min and higher than the max values.   These ranges should not overlap, but if they do, assume the most
 * severe range is to be applied.  All ranges are optional and it is quite allowed for there to be only one
 * end of the range.  Range values are in calibrated engineering units.
 * 
 * Note that we actually keep here the ranges for the IN_LIMITS.
 * This means that if range.inRange(v)!=0 (meaning v out of range), then the parameter is in alarm state. 
 * 
 * @author nm
 *
 */
public class AlarmRanges implements Serializable {
    private static final long serialVersionUID = 200706052351L;
    DoubleRange watchRange = null;
    DoubleRange warningRange = null;
    DoubleRange distressRange = null;
    DoubleRange criticalRange = null;
    DoubleRange severeRange = null;

    public void addWatchRange(DoubleRange range) {
        if(this.watchRange == null) {
            this.watchRange = range;
        } else {
            this.watchRange = this.watchRange.intersectWith(range);
        }
    }

    public void addWarningRange(DoubleRange range) {
        if(this.warningRange == null) {
            this.warningRange = range;
        } else {
            this.warningRange = this.warningRange.intersectWith(range);
        }
    }

    public void addDistressRange(DoubleRange range) {
        if(this.distressRange == null) {
            this.distressRange = range;
        } else {
            this.distressRange = this.distressRange.intersectWith(range);
        }
    }

    public void addCriticalRange(DoubleRange range) {
        if(this.criticalRange == null) {
            this.criticalRange = range;
        } else {
            this.criticalRange = this.criticalRange.intersectWith(range);
        }
    }

    public void addSevereRange(DoubleRange range) {
        if(this.severeRange == null) {
            this.severeRange = range;
        } else {
            this.severeRange = this.severeRange.intersectWith(range);
        }
    }

    public void addRange(DoubleRange range, AlarmLevels level) {
        switch(level) {
        case watch:
            addWatchRange(range);
            break;
        case warning:
            addWarningRange(range);
            break;
        case distress:
            addDistressRange(range);
            break;
        case critical:
            addCriticalRange(range);
            break;
        case severe:
            addSevereRange(range);
            break;
        default:
            throw new RuntimeException("Level '"+level+"' not allowed for alarm ranges");

        }
    }

    public DoubleRange getWatchRange() {
        return watchRange;
    }
    public DoubleRange getWarningRange() {
        return warningRange;
    }
    public DoubleRange getDistressRange() {
        return distressRange;
    }
    public DoubleRange getCriticalRange() {
        return criticalRange;
    }
    public DoubleRange getSevereRange() {
        return severeRange;
    }

    public void setWarningRange(DoubleRange warningRange) {
        this.warningRange=warningRange;
    }

    @Override
    public String toString() {
        return ((watchRange!=null)?" watchRange"+watchRange:"")+
                ((warningRange!=null)?" warningRange"+warningRange:"")+
                ((distressRange!=null)?" distressRange"+distressRange:"")+
                ((criticalRange!=null)?" criticalRange"+criticalRange:"")+
                ((severeRange!=null)?" severeRange"+severeRange:"");
    }
}

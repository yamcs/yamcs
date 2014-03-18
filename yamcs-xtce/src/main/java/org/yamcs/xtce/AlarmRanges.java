package org.yamcs.xtce;

import java.io.Serializable;

/**
 * Contains five ranges: Watch, Warning, Distress, Critical, and Severe each in increasing severity. 
 * Normally, only the Warning and Critical ranges are used and the color yellow is associated with Warning 
 * and the color red is associated with Critical. The ranges given are valid for numbers lower than the 
 * min and higher than the max values.   These ranges should not overlap, but if they do, assume the most
 * severe range is to be applied.  All ranges are optional and it is quite allowed for there to be only one
 * end of the range.  Range values are in calibrated engineering units.
 * @author nm
 *
 */
public class AlarmRanges implements Serializable {
	private static final long serialVersionUID = 200706052351L;
	
	FloatRange watchRange=null;
	FloatRange warningRange=null;
	FloatRange distressRange=null;
	FloatRange criticalRange=null;
	FloatRange severeRange=null;
	
	public void addWatchRange(FloatRange watchRange) {
       if(this.watchRange == null) {
           this.watchRange = watchRange;
       } else {
           this.watchRange = this.watchRange.intersectWith(watchRange);
       }
	}
	
    public void addWarningRange(FloatRange warningRange) {
        if(this.warningRange == null) {
            this.warningRange = warningRange;
        } else {
            this.warningRange = this.warningRange.intersectWith(warningRange);
        }
    }
    
    public void addDistressRange(FloatRange distressRange) {
        if(this.distressRange == null) {
            this.distressRange = distressRange;
        } else {
            this.distressRange = this.distressRange.intersectWith(distressRange);
        }
    }
    
    public void addCriticalRange(FloatRange criticalRange) {
        if(this.criticalRange == null) {
            this.criticalRange = criticalRange;
        } else {
            this.criticalRange = this.criticalRange.intersectWith(criticalRange);
        }
    }
    
    public void addSevereRange(FloatRange severeRange) {
        if(this.severeRange == null) {
            this.severeRange = severeRange;
        } else {
            this.severeRange = this.severeRange.intersectWith(severeRange);
        }
    }
	
	public FloatRange getWatchRange() {
	    return watchRange;
	}
    public FloatRange getWarningRange() {
        return warningRange;
    }
    public FloatRange getDistressRange() {
        return distressRange;
    }
	public FloatRange getCriticalRange() {
		return criticalRange;
	}
    public FloatRange getSevereRange() {
        return severeRange;
    }

    public void setWarningRange(FloatRange warningRange) {
        this.warningRange=warningRange;
     }
    
	@Override
    public String toString() {
        return ((watchRange!=null)?"watchRange"+watchRange:"")+
               ((warningRange!=null)?"warningRange"+warningRange:"")+
               ((distressRange!=null)?"distressRange"+distressRange:"")+
               ((criticalRange!=null)?"criticalRange"+criticalRange:"")+
               ((severeRange!=null)?" severeRange"+severeRange:"");
    }
}

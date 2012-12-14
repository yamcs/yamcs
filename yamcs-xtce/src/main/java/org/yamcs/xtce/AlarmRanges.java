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
	
	//FloatRange watchRange=null;
	FloatRange warningRange=null;
	//FloatRange distressRange=null;
	FloatRange criticalRange=null;
	//FloatRange severeRange=null;
	
	public FloatRange getCriticalRange() {
		return criticalRange;
	}
	public FloatRange getWarningRange() {
		return warningRange;
	}

    public void setWarningRange(FloatRange warningRange) {
        this.warningRange=warningRange;
     }
    
	@Override
    public String toString() {
        return ((warningRange!=null)?"warningRange"+warningRange:"")+((criticalRange!=null)?" criticalRange"+criticalRange:"");
    }

}

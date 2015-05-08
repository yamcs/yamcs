package org.yamcs.xtce;

import java.io.Serializable;

/**
 *  XTCE: 
 *   Significance provides some cautionary information about the potential consequence of each MetaCommand.
 *   
 * @author nm
 *
 */
public class Significance implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public enum Levels {
        none,
        watch,
        warning,
        distress,
        critical,
        severe;
    }
    final private String reasonForWarning;
    final private Levels consequenceLevel;
    
    public Significance( Levels consequenceLevel, String reasonForWarning) {
        this.reasonForWarning = reasonForWarning;
        this.consequenceLevel = consequenceLevel;
    }

    public String getReasonForWarning() {
        return reasonForWarning;
    }

    public Levels getConsequenceLevel() {
        return consequenceLevel;
    } 
    
    public String toString() {
        return consequenceLevel+"("+reasonForWarning+")";
    }
}

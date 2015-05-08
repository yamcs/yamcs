package org.yamcs.xtce;

/**
 *  XTCE: 
 *   Significance provides some cautionary information about the potential consequence of each MetaCommand.
 *   
 * @author nm
 *
 */
public class Significance {
    
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
}

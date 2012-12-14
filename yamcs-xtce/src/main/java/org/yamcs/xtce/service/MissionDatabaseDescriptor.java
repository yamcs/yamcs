package org.yamcs.xtce.service;


/**
 * Mission database description. Object of this class are immutable.
 * 
 * @author Martin Ursik
 *
 */
public class MissionDatabaseDescriptor {

    private String name;
    
    private String validityStartDate;
    
    private String validityEndDate;
    
    private String filename;
    
    public MissionDatabaseDescriptor(String name, String validityStartDate, String validityEndDate, String filename) {
        this.name = name;
        this.validityEndDate = validityEndDate;
        this.validityStartDate = validityStartDate;
        this.filename = filename;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the validityStartDate
     */
    public String getValidityStartDate() {
        return validityStartDate;
    }

    /**
     * @return the validityEndDate End of validity period. Null means "not applicable"
     */
    public String getValidityEndDate() {
        return validityEndDate;
    }
    
    public String getFilename() {
        return filename;
    }
}

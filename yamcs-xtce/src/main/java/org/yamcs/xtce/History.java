package org.yamcs.xtce;

import java.io.Serializable;

/**
 * Capture creation or change history of document.
 * 
 * DIFFERS_FROM_XTCE XTCE only has one string-field.
 */
public class History implements Serializable {

    private static final long serialVersionUID = 1L;
    
    private String version;
    private String date;
    private String message;
    
    public History(String version, String date, String message) {
        this.version = version;
        this.date = date;
        this.message = message;
    }
    
    public String getVersion() {
        return version;
    }
    
    public String getDate() {
        return date;
    }
    
    public String getMessage() {
        return message;
    }
    
    @Override
    public String toString() {
        return version + "; " + date + (message != null ? "; " + message : "");
    }
}

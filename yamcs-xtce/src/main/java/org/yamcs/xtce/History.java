package org.yamcs.xtce;

import java.io.Serializable;

/**
 * Capture creation or change history of document.
 * 
 * DIFFERS_FROM_XTCE XTCE only has one string-field.
 */
public class History implements Serializable, Comparable<History> {

    private static final long serialVersionUID = 1L;
    
    private String version;
    private String date;
    private String message;
    
    public History(String version, String date, String message) {
        if(version == null)
            throw new IllegalArgumentException("Version can not be null");
        if(!version.matches("[0-9]+.*"))
            throw new IllegalArgumentException("Invalid version format '" + version + "'");
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

    @Override
    public int compareTo(History o) {
        if (o == null) return 1;
        String[] parts = version.split("\\.");
        String[] oparts = o.version.split("\\.");
        int len = Math.max(parts.length, oparts.length);
        for (int i = 0; i < len; i++) {
            try {
                int part = (i < parts.length) ? Integer.parseInt(parts[i]) : 0;
                int opart = (i < oparts.length) ? Integer.parseInt(oparts[i]) : 0;
                if (part < opart) return -1;
                if (part > opart) return 1;
            } catch (NumberFormatException e) {
                int c = parts[i].compareTo(oparts[i]);
                if (c != 0) return c;
            }
        }
        return 0;
    }
}

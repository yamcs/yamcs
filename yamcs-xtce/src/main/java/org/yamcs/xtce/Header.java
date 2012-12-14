package org.yamcs.xtce;

import java.io.Serializable;

/**
 * A header contains general information about the system or subsystem.
 * 
 * @author mu
 * 
 */
public class Header implements Serializable {

    private static final long serialVersionUID = 2L;
    private String            version          = null;
    private String            date             = null;


    public void setVersion(String version) {
        this.version = version;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getVersion() {
        return version;
    }

    public String getDate() {
        return date;
    }
    
    @Override
    public String toString() {
        return "version: "+version+", date: "+date;
    }
}

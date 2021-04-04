package org.yamcs.xtce.xml;

import javax.xml.stream.Location;

/**
 * unchecked exception thrown from the XTCE loader.
 * 
 * It keeps a reference to the file and the location in the file where the error occurs.
 *
 */
public class XtceLoadException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    final Location location;
    final String fileName;

    public XtceLoadException(String fileName, Location location, String message) {
        super(message);
        this.location = location;
        this.fileName = fileName;
    }

}

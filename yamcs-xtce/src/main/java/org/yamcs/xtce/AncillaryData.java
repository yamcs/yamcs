package org.yamcs.xtce;

import java.io.Serializable;
import java.net.URI;

/**
 * Used for any other data associated with each named object. May be used to
 * include administrative data (e.g., version, CM or tags) or potentially any
 * MIME type. Data may be included or given as an href.
 */
public class AncillaryData implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final String DEFAULT_MIME_TYPE = "text/plain";

    private String name;
    private String value;
    private String mimeType;
    private URI href;

    public AncillaryData(String name, String value) {
        this.name = name;
        this.value = value;
        this.mimeType = DEFAULT_MIME_TYPE;
    }

    public String getName() {
        return name;
    }

    public URI getHref() {
        return href;
    }

    public String getValue() {
        return value;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public void setHref(URI href) {
        this.href = href;
    }

    @Override
    public String toString() {
        return value;
    }
}

package org.yamcs.xtce;

import java.io.Serializable;
import java.net.URI;
import java.util.AbstractMap.SimpleEntry;

/**
 * Used for any other data associated with each named object. May be used to
 * include administrative data (e.g., version, CM or tags) or potentially any
 * MIME type. Data may be included or given as an href.
 * 
 * <p>
 * The properties used in yamcs are grouped under the key Yamcs and documented below. Only name and value are used (href
 * and mimeType are ignored).
 * 
 */
public class AncillaryData implements Serializable {
    public static final String KEY_YAMCS = "Yamcs";
    /**
     * Used to specifies that certain inputs for an algorithm are mandatory (the algorithm won't be started if they are
     * not there)
     */
    public static final String KEY_ALGO_MANDATORY_INPUT = "Yamcs:AlgorithmMandatoryInput";

    /**
     * Used to configure the SequenceContainers to be used to partition the archive data.
     * The containers will also be used for histogram building (this is the "pname" column in the tm table).
     */
    public static final String PROP_USE_AS_ARCHIVING_PARTITION = "UseAsArchivingPartition";

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

    /**
     * Tries to split the value in a "s1=s2" form and returns the (s1,s2) pair if the value can be split. Otherwise
     * returns null.
     * 
     */
    SimpleEntry<String, String> getValueAsPair() {
        String[] s = value.split("\\s*=\\s*", 2);
        if (s.length == 2) {
            return new SimpleEntry<>(s[0], s[1]);
        } else {
            return null;
        }
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

    public boolean isYamcs() {
        return KEY_YAMCS.equalsIgnoreCase(name);
    }

    @Override
    public String toString() {
        return name + ":" + value;
    }
}

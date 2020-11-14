package org.yamcs.yarch;

public class IndexFilter {
    // currently only supported is filtering on the first column part of the primary index
    public Object keyStart = null;
    public Object keyEnd = null;
    public boolean strictStart, strictEnd;

    @Override
    public String toString() {
        return "keyStart: " + keyStart + " strictStart:" + strictStart + " keyEnd: " + keyEnd + " strictEnd: "
                + strictEnd;
    }
}

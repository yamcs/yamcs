package org.yamcs.mdb;

public enum XtceVersion {
    V1_2("http://www.omg.org/spec/XTCE/20180204"),
    V1_3("http://www.omg.org/spec/XTCE/20250214");

    private String namespace;

    private XtceVersion(String namespace) {
        this.namespace = namespace;
    }

    public String getNamespace() {
        return namespace;
    }
}

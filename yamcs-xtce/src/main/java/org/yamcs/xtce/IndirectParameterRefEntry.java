package org.yamcs.xtce;

/**
 * An entry whose name is given by the value of a ParamameterInstance. This entry may be used to implement dwell
 * telemetry streams.
 * The value of the parameter in ParameterInstance must use either the name of the Parameter or its alias.
 * If it's an alias name, the alias namespace is supplied as an attribute.
 *
 */
public class IndirectParameterRefEntry extends SequenceEntry {
    private static final long serialVersionUID = 2L;
    private ParameterInstanceRef parameterRef;
    private String aliasNameSpace;

    public IndirectParameterRefEntry(int locationInContainerInBits, ReferenceLocationType location,
            ParameterInstanceRef parameterRef, String aliasNameSpace) {
        super(locationInContainerInBits, location);
        this.parameterRef = parameterRef;
        this.aliasNameSpace = aliasNameSpace;
    }

    public ParameterInstanceRef getParameterRef() {
        return parameterRef;
    }

    public void setParameterRef(ParameterInstanceRef parameterRef) {
        this.parameterRef = parameterRef;
    }

    public String getAliasNameSpace() {
        return aliasNameSpace;
    }

    public void setAliasNameSpace(String aliasNameSpace) {
        this.aliasNameSpace = aliasNameSpace;
    }

    @Override
    public String toString() {
        return "IndirectParameterRefEntry [parameterRef=" + parameterRef + ", aliasNameSpace=" + aliasNameSpace + "]";
    }
}

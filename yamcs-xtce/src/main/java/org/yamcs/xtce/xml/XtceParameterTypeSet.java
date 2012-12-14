package org.yamcs.xtce.xml;

import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.xtce.ParameterType;


public class XtceParameterTypeSet {

    /**
     * Logging subsystem
     */
    static Logger                          log                      = LoggerFactory.getLogger(XtceParameterTypeSet.class
                                                                            .getName());

    /**
     * Hashmap with all parameter types defined in the XTCE file
     */
    private HashMap<String, ParameterType> registeredParameterTypes = null;

    public XtceParameterTypeSet() {
        registeredParameterTypes = new HashMap<String, ParameterType>();
    }

    /**
     * Register the named parameter type.
     * 
     * @param parameterTypeName
     *            Name in the default namespace.
     * @param parameterType
     *            ParameterType object to be registered.
     */
    public void registerParameterType(String parameterTypeName, ParameterType parameterType) {
        if (registeredParameterTypes.put(parameterTypeName, parameterType) != null) {
            log.error("ParameterTypes with duplicate names: " + parameterTypeName);
            throw new IllegalStateException();
        }
    }

    /**
     * Access method to registered parameter types. Searches in the default
     * namespace. Aliases are currently not supported.
     * 
     * @param parameterTypeName
     * @return
     */
    public ParameterType getParameterType(String parameterTypeName) {
        return registeredParameterTypes.get(parameterTypeName);
    }
}

package org.yamcs.commanding;

import java.util.Map;

import org.yamcs.commanding.TcParameterDefinition.SwTypes;

public class EnumerationDecalibration implements Decalibration {
    private static final long serialVersionUID = 200704191654L;
    final private Map<String,Object> codes;
    
    public EnumerationDecalibration(Map<String,Object> codes) {
        this.codes=codes;
    }
    /**
     * @param engValue 
     * @param rawType 
     * @param engType 
     * @return returns the object as stored in the map. No checking is done to see if it matches the expected rawType
     * @throws DecalibrationNotSupportedException 
     * 
     */
    public Object decalibrate(Object engValue, SwTypes rawType, SwTypes engType) throws DecalibrationNotSupportedException {
        if(engValue instanceof String)
            return codes.get((String) engValue);
        else throw new DecalibrationNotSupportedException("Statecode decalibration not supported for engType"+engType+". engValue is of type "+engValue.getClass().getName());
    }
    public Map<String,Object> getCodes() {
        return codes;
    }
    
    public String toString() {
        return "Enumeration"+codes.toString();
    }
}

package org.yamcs.parameterarchive;

public class SingleParameterValueRequest {
    long start, stop;
    int[] parameterGroupIds;
    int parameterId;
    boolean ascending;
    boolean retrieveEngineeringValues = true;
    boolean retrieveRawValues = false;
    boolean retrieveParameterStatus = false;
    
    public SingleParameterValueRequest(long start, long stop, int parameterId, int[] parameterGroupIds, boolean ascending) {
        super();
        this.start = start;
        this.stop = stop;
        this.parameterGroupIds = parameterGroupIds;
        this.parameterId = parameterId;
        this.ascending = ascending;
    }
    public SingleParameterValueRequest(long start, long stop, int parameterId, int parameterGroupId, boolean ascending) {
        this(start, stop, parameterId, new int[] { parameterGroupId}, ascending);
    }
    
    public void setRetrieveEngineeringValues(boolean b) {
        this.retrieveEngineeringValues = b;
    }
    
    /**
     * Note that if this is set to true but there are no raw values stored, then the engineering values will be provided instead
     * This is because the users of this class are supposed to know when raw values should be stored and there is an 
     *  optimisation that they are not actually stored if they are equal to the engineering values.
     *  
     *  
     * @param b
     */
    public void setRetrieveRawValues(boolean b) {
        this.retrieveRawValues = b;
    }
    
    public void setRetrieveParameterStatus(boolean b) {
        this.retrieveParameterStatus = b;
    }
    
    public int[] getParameterGroupIds() {
        return parameterGroupIds;
    }
}

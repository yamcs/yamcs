package org.yamcs.parameterarchive;

public class ParameterRequest {
    long start, stop;
    
    boolean ascending;
    private boolean retrieveEngineeringValues = true;
    private boolean retrieveRawValues = false;
    private boolean retrieveParameterStatus = false;
    
    public ParameterRequest(long start, long stop, boolean ascending,
            boolean retrieveEngineeringValues, boolean retrieveRawValues, boolean retrieveParameterStatus) {
        super();
        this.start = start;
        this.stop = stop;
        this.ascending = ascending;
        this.setRetrieveEngineeringValues(retrieveEngineeringValues);
        this.setRetrieveRawValues(retrieveRawValues);
        this.setRetrieveParameterStatus(retrieveParameterStatus);
    }
 
    public boolean isAscending() {
        return ascending;
    }
    
    public long getStart() {
        return start;
    }
    
    public long getStop() {
        return stop;
    }

    public void setStart(long start) {
        this.start = start;
    }

    public boolean isRetrieveRawValues() {
        return retrieveRawValues;
    }

    public void setRetrieveRawValues(boolean retrieveRawValues) {
        this.retrieveRawValues = retrieveRawValues;
    }

    public boolean isRetrieveEngineeringValues() {
        return retrieveEngineeringValues;
    }

    public void setRetrieveEngineeringValues(boolean retrieveEngineeringValues) {
        this.retrieveEngineeringValues = retrieveEngineeringValues;
    }

    public boolean isRetrieveParameterStatus() {
        return retrieveParameterStatus;
    }

    public void setRetrieveParameterStatus(boolean retrieveParameterStatus) {
        this.retrieveParameterStatus = retrieveParameterStatus;
    }
}

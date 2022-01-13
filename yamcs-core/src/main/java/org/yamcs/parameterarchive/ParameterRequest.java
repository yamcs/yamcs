package org.yamcs.parameterarchive;

/**
 * Contains retrieval options used when extracting parameters from the parameter archive.
 *
 */
public class ParameterRequest {
    final long start, stop;
    
    final boolean ascending;
    final private boolean retrieveEngineeringValues;
    final private boolean retrieveRawValues;
    final private boolean retrieveParameterStatus;
    
    public ParameterRequest(long start, long stop, boolean ascending,
            boolean retrieveEngineeringValues, boolean retrieveRawValues, boolean retrieveParameterStatus) {
        this.start = start;
        this.stop = stop;
        this.ascending = ascending;
        this.retrieveEngineeringValues = retrieveEngineeringValues;
        this.retrieveRawValues = retrieveRawValues;
        this.retrieveParameterStatus = retrieveParameterStatus;
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

    public boolean isRetrieveRawValues() {
        return retrieveRawValues;
    }

    public boolean isRetrieveEngineeringValues() {
        return retrieveEngineeringValues;
    }


    public boolean isRetrieveParameterStatus() {
        return retrieveParameterStatus;
    }

    public ParameterRequest copy() {
        return new ParameterRequest(start, stop, ascending, retrieveEngineeringValues,
                retrieveRawValues, retrieveParameterStatus);
    }
}

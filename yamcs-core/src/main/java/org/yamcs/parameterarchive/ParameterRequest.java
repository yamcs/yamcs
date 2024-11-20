package org.yamcs.parameterarchive;

/**
 * Contains retrieval options used when extracting parameters from the parameter archive.
 */
public record ParameterRequest(
        long start,
        long stop,
        boolean ascending,
        boolean retrieveEngineeringValues,
        boolean retrieveRawValues,
        boolean retrieveParameterStatus) {

    public ParameterRequest withUpdatedStart(long newStart) {
        return new ParameterRequest(
                newStart,
                this.stop,
                this.ascending,
                this.retrieveEngineeringValues,
                this.retrieveRawValues,
                this.retrieveParameterStatus);
    }

    public ParameterRequest withUpdatedStop(long newStop) {
        return new ParameterRequest(
                this.start,
                newStop,
                this.ascending,
                this.retrieveEngineeringValues,
                this.retrieveRawValues,
                this.retrieveParameterStatus);
    }
}

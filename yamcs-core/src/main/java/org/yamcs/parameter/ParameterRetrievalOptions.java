package org.yamcs.parameter;

/**
 * Contains retrieval options used when retrieving parameters.
 */
public record ParameterRetrievalOptions(
        long start,
        long stop,
        boolean ascending,
        boolean retrieveEngineeringValues,
        boolean retrieveRawValues,
        boolean retrieveParameterStatus,
        boolean norealtime) {

    public static class Builder {
        private long start;
        private long stop;
        private boolean ascending = true;
        private boolean retrieveEngineeringValues = true;
        private boolean retrieveRawValues = true;
        private boolean retrieveParameterStatus = true;
        private boolean norealtime = false;

        public Builder withStartStop(long start, long stop) {
            this.start = start;
            this.stop = stop;
            return this;
        }

        public Builder withAscending(boolean ascending) {
            this.ascending = ascending;
            return this;
        }

        public Builder withNorealtime(boolean norealtime) {
            this.norealtime = norealtime;
            return this;
        }

        public Builder withRetrieveEngineeringValues(boolean retrieveEngineeringValues) {
            this.retrieveEngineeringValues = retrieveEngineeringValues;
            return this;
        }

        public Builder withRetrieveRawValues(boolean retrieveRawValues) {
            this.retrieveRawValues = retrieveRawValues;
            return this;
        }

        public Builder withRetrieveParameterStatus(boolean retrieveParameterStatus) {
            this.retrieveParameterStatus = retrieveParameterStatus;
            return this;
        }

        public ParameterRetrievalOptions build() {
            return new ParameterRetrievalOptions(
                    start, stop, ascending,
                    retrieveEngineeringValues, retrieveRawValues, retrieveParameterStatus,
                    norealtime);
        }
    }

    public Builder toBuilder() {
        return new Builder()
                .withStartStop(this.start, this.stop)
                .withAscending(this.ascending)
                .withRetrieveEngineeringValues(this.retrieveEngineeringValues)
                .withRetrieveRawValues(this.retrieveRawValues)
                .withRetrieveParameterStatus(this.retrieveParameterStatus);
    }

    public ParameterRetrievalOptions withUpdatedStart(long newStart) {
        return this.toBuilder().withStartStop(newStart, this.stop).build();
    }

    public ParameterRetrievalOptions withUpdatedStop(long newStop) {
        return this.toBuilder().withStartStop(this.start, newStop).build();
    }

    public static Builder newBuilder() {
        return new Builder();
    }
}

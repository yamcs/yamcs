package org.yamcs.parameter;

import org.yamcs.protobuf.Yamcs.PacketReplayRequest;

/**
 * Contains retrieval options used when retrieving parameters.
 */
public record ParameterRetrievalOptions(
        long start,
        long stop,
        boolean ascending,
        boolean retrieveEngValues,
        boolean retrieveRawValues,
        boolean retrieveParameterStatus,
        /**
         * If true, do not send data from the Parameter Cache or from the Realtime parameter archive filler
         */
        boolean norealtime,
        /**
         * If true, do not send data from the Parameter Archive
         */
        boolean noparchive,
        /**
         * If true, do not perform replays
         */
        boolean noreplay,
        /**
         * If not null and a replay is performed, this can be used to limit the packets that go in replay
         */
        PacketReplayRequest packetReplayRequest) {

    public static class Builder {
        private long start;
        private long stop;
        private boolean ascending = true;
        private boolean retrieveEngineeringValues = true;
        private boolean retrieveRawValues = true;
        private boolean retrieveParameterStatus = true;
        private boolean norealtime = false;
        private boolean noparchive = false;
        private boolean noreplay = false;
        private PacketReplayRequest packetReplayRequest = null;

        public Builder withStartStop(long start, long stop) {
            this.start = start;
            this.stop = stop;
            return this;
        }

        public Builder withStart(long start) {
            this.start = start;
            return this;
        }

        public Builder withStop(long stop) {
            this.stop = stop;
            return this;
        }

        public Builder withAscending(boolean ascending) {
            this.ascending = ascending;
            return this;
        }

        public Builder withoutRealtime(boolean norealtime) {
            this.norealtime = norealtime;
            return this;
        }

        public Builder withoutParchive(boolean noparchive) {
            this.noparchive = noparchive;
            return this;
        }

        public Builder withoutReplay(boolean noreplay) {
            this.noreplay = noreplay;
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

        public Builder withPacketReplayRequest(PacketReplayRequest packetReplayRequest) {
            this.packetReplayRequest = packetReplayRequest;
            return this;
        }


        public ParameterRetrievalOptions build() {
            return new ParameterRetrievalOptions(
                    start, stop, ascending,
                    retrieveEngineeringValues, retrieveRawValues, retrieveParameterStatus,
                    norealtime, noparchive, noreplay, packetReplayRequest);
        }

    }

    public Builder toBuilder() {
        return new Builder()
                .withStartStop(this.start, this.stop)
                .withAscending(this.ascending)
                .withRetrieveEngineeringValues(this.retrieveEngValues)
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

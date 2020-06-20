package org.yamcs.client;

import java.time.Instant;

public class Acknowledgment {

    public static final String QUEUED = "Acknowledge_Queued";
    public static final String RELEASED = "Acknowledge_Released";
    public static final String SENT = "Acknowledge_Sent";

    private String name;
    private Instant time;
    private String status;
    private String message;

    Acknowledgment(String name, Instant time, String status, String message) {
        this.name = name;
        this.time = time;
        this.status = status;
        this.message = message;
    }

    public String getName() {
        return name;
    }

    public boolean isLocal() {
        return QUEUED.equals(name) || RELEASED.equals(name) || SENT.equals(name);
    }

    /**
     * Last update time of this acknowledgment.
     */
    public Instant getTime() {
        return time;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return String.format("%s: %s", name, status);
    }
}

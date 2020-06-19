package org.yamcs.client;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;

import com.google.protobuf.Timestamp;

public class Command implements Comparable<Command> {

    private final String id;
    private final String name;
    private final String origin;
    private final int sequenceNumber;
    private final Instant generationTime;
    private Map<String, Object> attributes = new LinkedHashMap<>();

    Command(CommandId protoId, Timestamp generationTime) {
        id = protoId.getGenerationTime() + "-" + protoId.getOrigin() + "-" + protoId.getSequenceNumber();
        name = protoId.getCommandName();
        origin = protoId.hasOrigin() ? protoId.getOrigin() : null;
        sequenceNumber = protoId.getSequenceNumber();
        this.generationTime = Instant.ofEpochSecond(generationTime.getSeconds(), generationTime.getNanos());
    }

    public String getId() {
        return id;
    }

    public Instant getGenerationTime() {
        return generationTime;
    }

    public String getName() {
        return name;
    }

    public String getOrigin() {
        return origin;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    /**
     * Username of the issuer
     */
    public String getUsername() {
        return (String) attributes.get("username");
    }

    /**
     * String representation of the command
     */
    public String getSource() {
        return (String) attributes.get("source");
    }

    /**
     * Binary representation of the command
     */
    public byte[] getBinary() {
        return (byte[]) attributes.get("binary");
    }

    /**
     * Returns whether this command is complete. A command can be complete, yet still failed.
     */
    public boolean isComplete() {
        Acknowledgment ack = createAcknowledgment("CommandComplete");
        return ack != null && (ack.getStatus().equals("OK") || ack.getStatus().equals("NOK"));
    }

    /**
     * Returns true if this command has completed successfully
     */
    public boolean isSuccess() {
        Acknowledgment ack = createAcknowledgment("CommandComplete");
        return ack != null && ack.getStatus().equals("OK");
    }

    /**
     * Returns true if this command failed
     */
    public boolean isFailure() {
        Acknowledgment ack = createAcknowledgment("CommandComplete");
        return ack != null && ack.getStatus().equals("NOK");
    }

    /**
     * Error message in case this command failed
     */
    public String getError() {
        Acknowledgment ack = createAcknowledgment("CommandComplete");
        if (ack != null && ack.getStatus().equals("NOK")) {
            return ack.getMessage();
        }
        return null;
    }

    public String getComment() {
        return (String) attributes.get("comment");
    }

    void merge(CommandHistoryEntry entry) {
        for (CommandHistoryAttribute attr : entry.getAttrList()) {
            attributes.put(attr.getName(), Helpers.parseValue(attr.getValue()));
        }
    }

    /**
     * All acknowledgments by name
     */
    public LinkedHashMap<String, Acknowledgment> getAcknowledgments() {
        LinkedHashMap<String, Acknowledgment> acknowledgments = new LinkedHashMap<>();
        for (Entry<String, Object> attr : attributes.entrySet()) {
            String name = attr.getKey();
            if (name.startsWith("CommandComplete") || name.startsWith("TransmissionConstraints")) {
                continue;
            }
            if (name.endsWith("_Status")) {
                Acknowledgment ack = createAcknowledgment(name.substring(0, name.length() - 7));
                if (ack != null) {
                    acknowledgments.put(name, ack);
                }
            }
        }
        return acknowledgments;
    }

    private Acknowledgment createAcknowledgment(String name) {
        Instant time = (Instant) attributes.get(name + "_Time");
        String status = (String) attributes.get(name + "_Status");
        String message = (String) attributes.get(name + "_Message");
        if (time != null && status != null) {
            return new Acknowledgment(name, time, status, message);
        }
        return null;
    }

    @Override
    public int compareTo(Command other) {
        return id.compareTo(other.id);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof Command)) {
            return false;
        }
        Command other = (Command) obj;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return getId();
    }
}

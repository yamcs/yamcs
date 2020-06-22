package org.yamcs.client;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.IssueCommandResponse;

public class Command implements Comparable<Command> {

    private static final String ATTR_BINARY = "binary";
    private static final String ATTR_QUEUE = "queue";
    private static final String ATTR_SOURCE = "source";
    private static final String ATTR_USERNAME = "username";
    private static final String ATTR_COMMENT = "comment";
    private static final String[] STANDARD_ATTRIBUTES = new String[] {
            ATTR_BINARY,
            ATTR_COMMENT,
            ATTR_QUEUE,
            ATTR_SOURCE,
            ATTR_USERNAME,
    };

    private static final String PREFIX_COMMAND_COMPLETE = "CommandComplete";
    private static final String PREFIX_TRANSMISSION_CONTRAINTS = "TransmissionConstraints";
    private static final String[] STANDARD_ATTRIBUTE_PREFIXES = new String[] {
            PREFIX_COMMAND_COMPLETE,
            PREFIX_TRANSMISSION_CONTRAINTS,
    };

    private static final String SUFFIX_TIME = "_Time";
    private static final String SUFFIX_MESSAGE = "_Message";
    private static final String SUFFIX_STATUS = "_Status";
    private static final String[] STANDARD_ATTRIBUTE_SUFFIXES = new String[] {
            SUFFIX_TIME,
            SUFFIX_MESSAGE,
            SUFFIX_STATUS,
    };

    private final String id;
    private final String name;
    private final String origin;
    private final int sequenceNumber;
    private final Instant generationTime;
    private Map<String, Object> attributes = new LinkedHashMap<>();

    public Command(String id, String name, String origin, int sequenceNumber, Instant generationTime) {
        this.id = id;
        this.name = name;
        this.origin = origin;
        this.sequenceNumber = sequenceNumber;
        this.generationTime = generationTime;
    }

    public Command(IssueCommandResponse response) {
        this.id = response.getId();
        this.name = response.getCommandName();
        this.origin = response.getOrigin();
        this.sequenceNumber = response.getSequenceNumber();
        this.generationTime = Helpers.toInstant(response.getGenerationTime());
        if (response.hasBinary()) {
            attributes.put(ATTR_BINARY, response.getBinary().toByteArray());
        }
        if (response.hasQueue()) {
            attributes.put(ATTR_QUEUE, response.getQueue());
        }
        if (response.hasUsername()) {
            attributes.put(ATTR_USERNAME, response.getUsername());
        }
        if (response.hasSource()) {
            attributes.put(ATTR_SOURCE, response.getSource());
        }
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
        return (String) attributes.get(ATTR_USERNAME);
    }

    /**
     * The assigned command queue
     */
    public String getQueue() {
        return (String) attributes.get(ATTR_QUEUE);
    }

    /**
     * String representation of the command
     */
    public String getSource() {
        return (String) attributes.get(ATTR_SOURCE);
    }

    /**
     * Binary representation of the command
     */
    public byte[] getBinary() {
        return (byte[]) attributes.get(ATTR_BINARY);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    /**
     * Returns whether this command is complete. A command can be complete, yet still failed.
     */
    public boolean isComplete() {
        Acknowledgment ack = getAcknowledgment(PREFIX_COMMAND_COMPLETE);
        return ack != null && (ack.getStatus().equals("OK") || ack.getStatus().equals("NOK"));
    }

    /**
     * Returns true if this command has completed successfully
     */
    public boolean isSuccess() {
        Acknowledgment ack = getAcknowledgment(PREFIX_COMMAND_COMPLETE);
        return ack != null && ack.getStatus().equals("OK");
    }

    /**
     * Returns true if this command failed
     */
    public boolean isFailure() {
        Acknowledgment ack = getAcknowledgment(PREFIX_COMMAND_COMPLETE);
        return ack != null && ack.getStatus().equals("NOK");
    }

    /**
     * Error message in case this command failed
     */
    public String getError() {
        Acknowledgment ack = getAcknowledgment(PREFIX_COMMAND_COMPLETE);
        if (ack != null && ack.getStatus().equals("NOK")) {
            return ack.getMessage();
        }
        return null;
    }

    public String getComment() {
        return (String) attributes.get(ATTR_COMMENT);
    }

    public void merge(CommandHistoryEntry entry) {
        for (CommandHistoryAttribute attr : entry.getAttrList()) {
            attributes.put(attr.getName(), Helpers.parseValue(attr.getValue()));
        }
    }

    public void merge(Command other) {
        attributes.putAll(other.attributes);
    }

    /**
     * Returns non-standard attributes
     */
    public LinkedHashMap<String, Object> getExtraAttributes() {
        LinkedHashMap<String, Object> extra = new LinkedHashMap<>();
        for (Entry<String, Object> attr : attributes.entrySet()) {
            String name = attr.getKey();
            if (isExtraAttribute(name)) {
                extra.put(name, attr.getValue());
            }
        }
        return extra;
    }

    /**
     * All acknowledgments by name
     */
    public LinkedHashMap<String, Acknowledgment> getAcknowledgments() {
        LinkedHashMap<String, Acknowledgment> acknowledgments = new LinkedHashMap<>();
        for (Entry<String, Object> attr : attributes.entrySet()) {
            String name = attr.getKey();
            if (isAcknowledgmentStatusAttribute(name)) {
                Acknowledgment ack = getAcknowledgment(name.substring(0, name.length() - 7));
                if (ack != null) {
                    acknowledgments.put(name, ack);
                }
            }
        }
        return acknowledgments;
    }

    private boolean isExtraAttribute(String attributeName) {
        for (String suffix : STANDARD_ATTRIBUTE_SUFFIXES) {
            if (attributeName.endsWith(suffix)) {
                return false;
            }
        }
        for (String prefix : STANDARD_ATTRIBUTE_PREFIXES) {
            if (attributeName.startsWith(prefix)) {
                return false;
            }
        }
        return Arrays.binarySearch(STANDARD_ATTRIBUTES, attributeName) >= 0;
    }

    private boolean isAcknowledgmentStatusAttribute(String attributeName) {
        if (!attributeName.endsWith(SUFFIX_STATUS)) {
            return false;
        }
        for (String prefix : STANDARD_ATTRIBUTE_PREFIXES) {
            if (attributeName.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    public Acknowledgment getQueuedAcknowledgment() {
        return getAcknowledgment(Acknowledgment.QUEUED);
    }

    public Acknowledgment getReleasedAcknowledgment() {
        return getAcknowledgment(Acknowledgment.RELEASED);
    }

    public Acknowledgment getSentAcknowledgment() {
        return getAcknowledgment(Acknowledgment.SENT);
    }

    public Acknowledgment getAcknowledgment(String name) {
        Instant time = (Instant) attributes.get(name + SUFFIX_TIME);
        String status = (String) attributes.get(name + SUFFIX_STATUS);
        String message = (String) attributes.get(name + SUFFIX_MESSAGE);
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

package org.yamcs.client;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.yamcs.client.utils.WellKnownTypes;
import org.yamcs.protobuf.Commanding.CommandAssignment;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.IssueCommandResponse;

public class Command implements Comparable<Command> {

    private static final String ATTR_BINARY = "binary";
    private static final String ATTR_QUEUE = "queue";
    private static final String ATTR_UNPROCESSED_BINARY = "unprocessedBinary";
    private static final String ATTR_USERNAME = "username";
    private static final String ATTR_COMMENT = "comment";
    private static final String LEGACY_ATTR_SOURCE = "source";
    private static final String[] STANDARD_ATTRIBUTES = new String[] {
            ATTR_BINARY,
            ATTR_COMMENT,
            ATTR_QUEUE,
            ATTR_UNPROCESSED_BINARY,
            ATTR_USERNAME,
            LEGACY_ATTR_SOURCE,
    };

    private static final String PREFIX_COMMAND_COMPLETE = "CommandComplete";
    private static final String PREFIX_TRANSMISSION_CONSTRAINTS = "TransmissionConstraints";
    private static final String[] STANDARD_ATTRIBUTE_PREFIXES = new String[] {
            PREFIX_COMMAND_COMPLETE,
            PREFIX_TRANSMISSION_CONSTRAINTS,
    };

    private static final String SUFFIX_TIME = "_Time";
    private static final String SUFFIX_MESSAGE = "_Message";
    private static final String SUFFIX_STATUS = "_Status";
    private static final String[] STANDARD_ATTRIBUTE_SUFFIXES = new String[] {
            SUFFIX_TIME,
            SUFFIX_MESSAGE,
            SUFFIX_STATUS,
    };

    // Entries that come from a cascading server are prefixed with one or more
    // patterns of the kind: yamcs<SERVER>_
    private static final Pattern CASCADED_PREFIX = Pattern.compile("^(yamcs<[^>]+>_)+(.+)");

    private final String id;
    private final String name;
    private final Map<String, String> aliases = new HashMap<>();
    private final List<CommandAssignment> assignments;
    private final String origin;
    private final int sequenceNumber;
    private final Instant generationTime;
    private final String source;

    private Map<String, Object> attributes = Collections.synchronizedMap(new LinkedHashMap<>());

    // Command info that was relayed from an upstream (cascaded) server.
    private Map<String, Command> cascadedRecordsByPrefix = new LinkedHashMap<>();

    public Command(String id, String name, Map<String, String> aliases, List<CommandAssignment> assignments,
            String origin, int sequenceNumber,
            Instant generationTime) {
        this.id = id;
        this.name = name;
        this.aliases.putAll(aliases);
        this.assignments = assignments;
        this.origin = origin;
        this.sequenceNumber = sequenceNumber;
        this.generationTime = generationTime;
        this.source = buildSource(name, assignments);
    }

    public Command(IssueCommandResponse response) {
        this.id = response.getId();
        this.name = response.getCommandName();
        this.aliases.putAll(response.getAliasesMap());
        this.assignments = response.getAssignmentsList();
        this.origin = response.getOrigin();
        this.sequenceNumber = response.getSequenceNumber();
        this.generationTime = Helpers.toInstant(response.getGenerationTime());
        this.source = buildSource(name, response.getAssignmentsList());

        if (response.hasUnprocessedBinary()) {
            attributes.put(ATTR_UNPROCESSED_BINARY, response.getUnprocessedBinary().toByteArray());
        }
        if (response.hasBinary()) {
            attributes.put(ATTR_BINARY, response.getBinary().toByteArray());
        }
        if (response.hasQueue()) {
            attributes.put(ATTR_QUEUE, response.getQueue());
        }
        if (response.hasUsername()) {
            attributes.put(ATTR_USERNAME, response.getUsername());
        }
    }

    public Command(CommandHistoryEntry entry) {
        this(entry.getId(), entry.getCommandName(), entry.getAliasesMap(), entry.getAssignmentsList(),
                entry.getOrigin(), entry.getSequenceNumber(), Helpers.toInstant(entry.getGenerationTime()));
        merge(entry);
    }

    private static String buildSource(String name, List<CommandAssignment> assignments) {
        StringBuilder buf = new StringBuilder(name).append("(");
        buf.append(assignments.stream()
                .filter(CommandAssignment::getUserInput)
                .map(assignment -> {
                    Object value = Helpers.parseValue(assignment.getValue());
                    if (value instanceof String) {
                        return assignment.getName() + ": \"" + value + "\"";
                    } else if (value instanceof byte[]) {
                        return assignment.getName() + ": 0x" + WellKnownTypes.toHex((byte[]) value);
                    } else {
                        return assignment.getName() + ": " + value;
                    }
                }).collect(Collectors.joining(", ")));
        return buf.append(")").toString();
    }

    public String getId() {
        return id;
    }

    public Instant getGenerationTime() {
        return generationTime;
    }

    /**
     * Fully qualified command name
     */
    public String getName() {
        return name;
    }

    /**
     * Alias under the specified namespace. Returns {@code null} if this command has no such alias.
     */
    public String getName(String namespace) {
        return aliases.get(namespace);
    }

    public List<CommandAssignment> getAssignments() {
        return Collections.unmodifiableList(assignments);
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
        return source;
    }

    /**
     * Unprocessed binary representation of the command (prior to postprocessing).
     */
    public byte[] getUnprocessedBinary() {
        return (byte[]) attributes.get(ATTR_UNPROCESSED_BINARY);
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

    /**
     * Returns all attributes of this commands.
     */
    public Map<String, Object> getAttributes() {
        synchronized (attributes) {
            return new LinkedHashMap<>(attributes);
        }
    }

    public void merge(CommandHistoryEntry entry) {
        for (CommandHistoryAttribute attr : entry.getAttrList()) {
            var matcher = CASCADED_PREFIX.matcher(attr.getName());
            if (matcher.matches()) {
                var prefix = matcher.group(1);
                var cascadedCommand = cascadedRecordsByPrefix.get(prefix);
                if (cascadedCommand == null) {
                    cascadedCommand = new Command(id, name, entry.getAliasesMap(), assignments, origin, sequenceNumber,
                            generationTime);
                    cascadedRecordsByPrefix.put(prefix, cascadedCommand);
                }
                var truncatedName = matcher.group(2);
                cascadedCommand.attributes.put(truncatedName, Helpers.parseValue(attr.getValue()));
            } else {
                attributes.put(attr.getName(), Helpers.parseValue(attr.getValue()));
            }
        }
    }

    public void merge(Command other) {
        synchronized (other.attributes) {
            attributes.putAll(other.attributes);
        }
        for (var entry : other.cascadedRecordsByPrefix.entrySet()) {
            var prefix = entry.getKey();
            var cascadedRecord = entry.getValue();
            var existing = cascadedRecordsByPrefix.get(prefix);
            if (existing == null) {
                cascadedRecordsByPrefix.put(prefix, cascadedRecord);
            } else {
                synchronized (cascadedRecord.attributes) {
                    existing.attributes.putAll(cascadedRecord.attributes);
                }
            }
        }
    }

    /**
     * Returns non-standard attributes
     */
    public LinkedHashMap<String, Object> getExtraAttributes() {
        var extra = new LinkedHashMap<String, Object>();
        synchronized (attributes) {
            for (var attr : attributes.entrySet()) {
                String name = attr.getKey();
                if (isExtraAttribute(name)) {
                    extra.put(name, attr.getValue());
                }
            }
        }
        return extra;
    }

    /**
     * All acknowledgments by name
     */
    public LinkedHashMap<String, Acknowledgment> getAcknowledgments() {
        var acknowledgments = new LinkedHashMap<String, Acknowledgment>();
        synchronized (attributes) {
            for (var attr : attributes.entrySet()) {
                String name = attr.getKey();
                if (isAcknowledgmentStatusAttribute(name)) {
                    var ack = getAcknowledgment(name.substring(0, name.length() - 7));
                    if (ack != null) {
                        acknowledgments.put(ack.getName(), ack);
                    }
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

    /**
     * Returns command records that capture state of upstream (cascaded) servers.
     * <p>
     * The returned map is keyed by the cascading prefix, for example: {@code yamcs<SERVER1>_} when the information is
     * cascaded from SERVER1, or {@code yamcs<SERVER2>_yamcs<SERVER1>_} when the information is cascaded from SERVER 1
     * over SERVER2.
     */
    public Map<String, Command> getCascadedRecords() {
        return Collections.unmodifiableMap(cascadedRecordsByPrefix);
    }


    @Override
    public int compareTo(Command other) {
        return id.compareTo(other.id);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Command)) {
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

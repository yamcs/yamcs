package org.yamcs;

import org.yamcs.protobuf.Yamcs.Value;

/**
 * A command option. Instances of this class should be registered once, system-wide, against an instance of
 * {@link YamcsServer}. While not enforced, it is preferred to use a {@link Plugin#onLoad(YConfiguration)} hook as this
 * will guarantee that the registration is done only once.
 */
public class CommandOption {

    public enum CommandOptionType {
        BOOLEAN,
        NUMBER,
        STRING,
    }

    private final String id;
    private final String verboseName;
    private final CommandOptionType type;
    private String help;

    /**
     * Create a new command option.
     * 
     * @param id
     *            a system-wide unique identifier for this option. This identifier will be used by clients when
     *            submitting commands, and will also be used for storage in Command History.
     * @param verboseName
     *            a human-readable name for this option. Used by UI clients.
     * @param type
     *            the expected type of option values. UI clients may use this to enforce specific controls.
     */
    public CommandOption(String id, String verboseName, CommandOptionType type) {
        this.id = id;
        this.verboseName = verboseName;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public String getVerboseName() {
        return verboseName;
    }

    public CommandOptionType getType() {
        return type;
    }

    /**
     * Specify detailed guidance on how to use this attribute. UI clients may use this to show to users.
     */
    public CommandOption withHelp(String help) {
        this.help = help;
        return this;
    }

    public String getHelp() {
        return help;
    }

    /**
     * Returns a new value whose type matches more closely the type of this command option.
     * <p>
     * The purpose here, is that we want to be forgiving on clients that run commands, while at the same time be more
     * specific when it is recorded in Command History, or passed to a link.
     */
    public Value coerceValue(Value value) {
        switch (type) {
        case BOOLEAN:
            switch (value.getType()) {
            case STRING:
                var booleanValue = "true".equals(value.getStringValue());
                return Value.newBuilder()
                        .setType(Value.Type.BOOLEAN)
                        .setBooleanValue(booleanValue)
                        .build();
            case BOOLEAN:
                return value;
            default:
                throw new IllegalArgumentException("Command option cannot be converted to boolean");
            }
        case NUMBER:
            switch (value.getType()) {
            case STRING:
                var numberValue = Double.parseDouble(value.getStringValue());
                return Value.newBuilder()
                        .setType(Value.Type.DOUBLE)
                        .setDoubleValue(numberValue)
                        .build();
            case DOUBLE:
            case FLOAT:
            case SINT32:
            case SINT64:
            case UINT32:
            case UINT64:
                return value;
            default:
                throw new IllegalArgumentException("Command option cannot be converted to number");
            }
        case STRING:
            switch (value.getType()) {
            case STRING:
                return value;
            default:
                throw new IllegalArgumentException("Command option cannot be converted to string");
            }
        default:
            throw new IllegalStateException("Unexpected type " + type);
        }
    }

    @Override
    public String toString() {
        return verboseName;
    }
}

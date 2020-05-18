package org.yamcs;

/**
 * A command option. Instances of this class should be registered once, system-wide, against an instance of
 * {@link YamcsServer}. While not enforced, we recommend to use a {@link Plugin#onLoad()} hook as this will guarantee
 * that the registration is done only once.
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

    @Override
    public String toString() {
        return verboseName;
    }
}

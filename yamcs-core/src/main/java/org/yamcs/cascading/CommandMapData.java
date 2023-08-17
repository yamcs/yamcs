package org.yamcs.cascading;

import org.yamcs.ConfigurationException;
import org.yamcs.Spec;
import org.yamcs.YConfiguration;

public class CommandMapData {
    private CommandType type;
    private String localPath;
    private String upstreamPath;
    private String upstreamArgumentName;
    public CommandMapData(String localPath, String upstreamPath) {
        this.type = CommandType.DIRECT;
        this.localPath = localPath;
        this.upstreamPath = upstreamPath;
    }

    public CommandMapData(String localPath, String upstreamPath, String argumentName) {
        this.type = CommandType.EMBEDDED_BINARY;
        this.localPath = localPath;
        this.upstreamPath = upstreamPath;
        this.upstreamArgumentName = argumentName;
    }

    public CommandMapData() {
        setDefaultConfig();
    }

    public CommandMapData(YConfiguration config) {
        parseConfig(config);
    }

    public static Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("type", Spec.OptionType.STRING).withRequired(true);
        spec.addOption("local", Spec.OptionType.STRING).withRequired(true);
        spec.addOption("upstream", Spec.OptionType.STRING).withRequired(true);
        spec.addOption("argument", Spec.OptionType.STRING);
        return spec;
    }

    private void setDefaultConfig() {
        this.type = CommandType.DEFAULT;
    }

    private void parseConfig(YConfiguration config) {
        String t = config.getString("type");
        if (t.equals("DIRECT")) {
            this.type = CommandType.DIRECT;
        } else if (t.equals("EMBEDDED_BINARY")) {
            this.type = CommandType.EMBEDDED_BINARY;
        }

        this.localPath = config.getString("local");

        this.upstreamPath = config.getString("upstream");

        if (config.containsKey("argument")) {
            if (this.type == CommandType.EMBEDDED_BINARY) {
                this.upstreamArgumentName = config.getString("argument");
            } else {
                throw new ConfigurationException(
                        "Command mapping configuration specified an argument while not being of the embedded binary type.");
            }
        }
    }

    public CommandType getCommandType() {
        return this.type;
    }

    public String getLocalPath() {
        return this.localPath;
    }

    public String getUpstreamPath() {
        return this.upstreamPath;
    }

    public String getUpstreamArgumentName() {
        return this.upstreamArgumentName;
    }

    enum CommandType {
        DEFAULT,
        DIRECT,
        EMBEDDED_BINARY
    }
}

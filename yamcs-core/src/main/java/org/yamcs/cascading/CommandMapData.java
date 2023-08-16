package org.yamcs.cascading;

import org.yamcs.Spec;
import org.yamcs.YConfiguration;

public class CommandMapData {
    enum CommandType {
        DIRECT,
        EMBEDDED_BINARY
    }
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

    public CommandMapData(YConfiguration config) {
        parseConfig(config);
    }

    private void parseConfig(YConfiguration config) {
        if (config.containsKey("type")) {
            String t = config.getString("type");
            if (t.equals("DIRECT")) {
                this.type = CommandType.DIRECT;
            } else if (t.equals("EMBEDDED_BINARY")) {
                this.type = CommandType.EMBEDDED_BINARY;
            }
        }

        if (config.containsKey("local")) {
           this.localPath = config.getString("local");
        }

        if (config.containsKey("upstream")) {
            this.upstreamPath = config.getString("upstream");
        }

        if (config.containsKey("argument")) {
            this.upstreamArgumentName = config.getString("argument");
        }
    }

    public static Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("type", Spec.OptionType.STRING).withRequired(true);
        spec.addOption("local", Spec.OptionType.STRING).withRequired(true);
        spec.addOption("upstream", Spec.OptionType.STRING).withRequired(true);
        spec.addOption("argument", Spec.OptionType.STRING);
        return spec;
    }

    public CommandType GetCommandType(){return this.type;}
    public String GetLocalPath(){return this.localPath;}
    public String GetUpstreamPath(){return this.upstreamPath;}
    public String GetUpstreamArgumentName(){return this.upstreamArgumentName;}
}

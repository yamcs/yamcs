package org.yamcs.cli;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;

import org.yamcs.api.YamcsConnectionProperties;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/**
 * Manages CLI configuration.
 */
@Parameters(commandDescription = "Manage Yamcs client properties")
public class Config extends Command {

    public Config(YamcsCli yamcsCli) {
        super("config", yamcsCli);
        addSubCommand(new ConfigList());
        addSubCommand(new ConfigGet());
        addSubCommand(new ConfigSet());
    }

    @Parameters(commandDescription = "List client properties")
    private class ConfigList extends Command {

        public ConfigList() {
            super("list", Config.this);
        }

        @Override
        void execute() throws Exception {
            Properties props = loadProperties();
            for (Entry<Object, Object> entry : props.entrySet()) {
                console.println(entry.getKey() + " = " + entry.getValue());
            }
        }
    }

    @Parameters(commandDescription = "Get value of client property")
    private class ConfigGet extends Command {

        @Parameter(description = "property", arity = 1)
        List<String> main = new ArrayList<>();

        public ConfigGet() {
            super("get", Config.this);
        }

        @Override
        void execute() throws Exception {
            String property = main.get(0);
            Properties props = loadProperties();
            if (props.containsKey(property)) {
                console.println(props.getProperty(property));
            } else {
                throw new IllegalArgumentException("No property " + property);
            }
        }
    }

    @Parameters(commandDescription = "Set client property")
    private class ConfigSet extends Command {

        @Parameter(description = "property value", arity = 2)
        List<String> main = new ArrayList<>();

        public ConfigSet() {
            super("set", Config.this);
        }

        @Override
        void execute() throws Exception {
            String property = main.get(0);
            String value = main.get(1);
            Properties props = loadProperties();
            props.setProperty(property, value);
            File preferenceFile = YamcsConnectionProperties.getPreferenceFile();
            try (OutputStream out = new FileOutputStream(preferenceFile)) {
                props.store(out, null);
            }
        }
    }

    private static Properties loadProperties() throws IOException {
        Properties props = new Properties();
        File preferenceFile = YamcsConnectionProperties.getPreferenceFile();
        if (preferenceFile.exists()) {
            try (InputStream in = new FileInputStream(preferenceFile)) {
                props.load(in);
            }
        }
        return props;
    }
}

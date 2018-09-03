package com.spaceapplications.yamcs.scpi;

import java.io.FileInputStream;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Map;

import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

public class Config {

    public TelnetConfig telnet;
    public Map<String, DeviceConfig> devices;

    public static class TelnetConfig {
        public Integer port;
    }

    public static class DeviceConfig {
        public String locator;
        public String description;

        public Integer baudrate;
        public Integer dataBits;
        public String parity;

        public String responseTermination;
        public Long responseTimeout;
    }

    public static Config load(String path) {
        Constructor c = new Constructor(Config.class);
        TypeDescription d = new TypeDescription(Config.class);
        c.addTypeDescription(d);
        Yaml yaml = new Yaml(c);
        try (InputStream is = new FileInputStream(path)) {
            Config config = (Config) yaml.load(is);
            if (config == null) {
                throw throwRuntimeException("The file is empty.", path);
            }
            return config;
        } catch (Exception e) {
            throw throwRuntimeException("{1}", path, e);
        }
    }

    private static ConfigurationException throwRuntimeException(String msg, Object... args) {
        String baseMsg = "Error loading config file \"{0}\". ";
        msg = MessageFormat.format(baseMsg + msg, args);
        throw new ConfigurationException(msg);
    }
}

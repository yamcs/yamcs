package com.spaceapplications.yamcs.scpi;

import static pl.touk.throwing.ThrowingFunction.unchecked;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.error.YAMLException;

public class Config {
  public DaemonConfig daemon = new DaemonConfig();
  public List<DeviceConfig> decices = Arrays.asList();

  public static class DaemonConfig {
    public int port;
    public int max_connections;
  }

  public static class DeviceConfig {
    public String name = "";
  }

  public static Config load(String path) {
    Constructor c = new Constructor(Config.class);
    TypeDescription d = new TypeDescription(Config.class);
    d.putListPropertyType("devices", DeviceConfig.class);
    c.addTypeDescription(d);
    Yaml yaml = new Yaml(c);
    InputStream is = unchecked(Config::inputStream).apply(path);

    try {
      Config config = (Config) yaml.load(is);
      if (config == null)
        throw loadException("The file is empty.", path);
      return config;
    } catch (YAMLException e) {
      throw loadException("Expecting the file to have the following format: \n{1}", path, exampleYamlConfig());
    }
  }

  private static RuntimeException loadException(String msg, Object... args) {
    String baseMsg = "Error loading config file \"{0}\". ";
    msg = MessageFormat.format(baseMsg + msg, args);
    throw new RuntimeException(msg);
  }

  private static String exampleYamlConfig() {
    DumperOptions opts = new DumperOptions();
    opts.setPrettyFlow(true);
    Yaml yaml = new Yaml(opts);
    String example = yaml.dump(new Config());
    String exampleWithoutTag = example.replaceAll("^(.*)\n", "");
    return exampleWithoutTag;
  }

  private static InputStream inputStream(String path) throws IOException {
    Path p = Paths.get("config.yaml");
    return Files.newInputStream(p);
  }
}
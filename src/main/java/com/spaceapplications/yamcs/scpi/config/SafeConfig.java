package com.spaceapplications.yamcs.scpi.config;

import static pl.touk.throwing.ThrowingFunction.unchecked;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

public class SafeConfig {
  Map<String, Object> config;

  private SafeConfig(Map<String, Object> config) {
    this.config = config;
  }

  public <T> Optional<T> get(String options) {
    if (options.contains("."))
      return get(config, options.split("\\."));
    else
      return get(config, new String[] { options });
  }

  @SuppressWarnings("unchecked")
  private <T> Optional<T> get(Map<String, Object> config, String[] options) {
    T val = (T) config.get(options[0]);
    if (val instanceof Map && options.length > 1) {
      return get((Map<String, Object>) val, Arrays.copyOfRange(options, 1, options.length));
    } else
      return Optional.ofNullable(val);
  }

  @SuppressWarnings("unchecked")
  public static SafeConfig load(String path) {
    Yaml yaml = new Yaml();
    InputStream is = unchecked(SafeConfig::inputStream).apply(path);

    Object config;
    try {
      config = yaml.load(is);
    } catch (YAMLException e) {
      throw loadException("Expecting the file to have the following format: \n{1}", path);
    }

    if (config == null)
      throw loadException("The file is empty.", path);
    else if (config instanceof Map == false)
      throw loadException("The file does not contain a map, but a {1}.", path, config.getClass());
    else
      return new SafeConfig((Map<String, Object>) config);
  }

  private static RuntimeException loadException(String msg, Object... args) {
    String baseMsg = "Error loading config file \"{0}\". ";
    msg = MessageFormat.format(baseMsg + msg, args);
    throw new RuntimeException(msg);
  }

  private static InputStream inputStream(String path) throws IOException {
    Path p = Paths.get("config.yaml");
    return Files.newInputStream(p);
  }
}
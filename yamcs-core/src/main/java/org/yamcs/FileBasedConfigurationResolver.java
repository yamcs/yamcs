package org.yamcs;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.yamcs.YConfiguration.ConfigurationNotFoundException;

/**
 * A file-based configuration resolver.
 */
public class FileBasedConfigurationResolver implements YConfigurationResolver {

    private List<Path> configDirectories;

    public FileBasedConfigurationResolver(Path... configDirectories) {
        this.configDirectories = Arrays.asList(configDirectories);
    }

    @Override
    public InputStream getConfigurationStream(String name) throws ConfigurationException {
        if (name.startsWith("/")) { // YConfiguration gives us a something that looks like a classpath resource
            name = name.substring(1);
        }

        for (Path configDirectory : configDirectories) {
            Path file = configDirectory.resolve(name).normalize().toAbsolutePath();
            if (Files.exists(file)) {
                try {
                    return new FileInputStream(file.toFile());
                } catch (FileNotFoundException e) {
                    throw new ConfigurationNotFoundException(String.format(
                            "Cannot read file %s: %s", file, e.getMessage(), e));
                }
            }
        }

        throw new ConfigurationNotFoundException(String.format(
                "Configuration file %s does not exist. Searched in: %s",
                name, configDirectories));
    }
}

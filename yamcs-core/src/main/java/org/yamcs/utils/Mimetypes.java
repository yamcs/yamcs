package org.yamcs.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps files to Internet media types based on their file extension.
 * <p>
 * The mapping is read from a file {@code mime.types} which must be available in the classpath root.
 */
public class Mimetypes {

    public static final String OCTET_STREAM = "application/octet-stream";

    private static Mimetypes INSTANCE;

    private Map<String, String> mimetypeByExtension = new HashMap<>();

    private Mimetypes() {
    }

    public static synchronized Mimetypes getInstance() {
        if (INSTANCE == null) {
            try {
                INSTANCE = new Mimetypes();
                InputStream in = Mimetypes.class.getResourceAsStream("/mime.types");
                if (in == null) {
                    throw new FileNotFoundException("Cannot find the mime.types file in the classpath");
                }
                INSTANCE.load(in);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return INSTANCE;
    }

    public String getMimetype(Path file) {
        String filename = file.getFileName().toString();
        int idx = filename.lastIndexOf('.');
        if (idx != -1) {
            String extension = filename.substring(idx + 1).toLowerCase();
            String mimetype = mimetypeByExtension.get(extension);
            return (mimetype != null) ? mimetype : OCTET_STREAM;
        }
        return OCTET_STREAM;
    }

    public String getMimetype(File file) {
        return getMimetype(file.toPath());
    }

    public String getMimetype(String filename) {
        return getMimetype(Paths.get(filename));
    }

    private void load(InputStream in) throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\s+");
                for (int i = 1; i < parts.length; i++) {
                    mimetypeByExtension.put(parts[i], parts[0]);
                }
            }
        }
    }
}

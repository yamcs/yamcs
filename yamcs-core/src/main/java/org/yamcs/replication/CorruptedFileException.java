package org.yamcs.replication;

import java.nio.file.Path;

public class CorruptedFileException extends RuntimeException {
    Path path;
    public CorruptedFileException(Path path,String message) {
        super(message);
        this.path = path;
    }
    
    public String toString() {
        String msg = getMessage();
        return "Corrupted file " + path + (msg == null ? "" : ": " + msg);
    }
}

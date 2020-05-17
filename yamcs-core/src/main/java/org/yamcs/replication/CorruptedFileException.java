package org.yamcs.replication;

import java.io.File;

public class CorruptedFileException extends RuntimeException {
    File file;
    public CorruptedFileException(File file,String message) {
        super(message);
        this.file = file;
    }
    
    public String toString() {
        return "Corrupted file "+file+": "+getMessage();
    }
}

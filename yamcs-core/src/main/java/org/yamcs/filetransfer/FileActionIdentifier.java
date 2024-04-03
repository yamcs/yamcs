package org.yamcs.filetransfer;

public class FileActionIdentifier {
    public final String entityName;
    public final String fileName;

    public FileActionIdentifier(String entityName, String fileName) {
        this.entityName = entityName;
        this.fileName = fileName;
    }
}

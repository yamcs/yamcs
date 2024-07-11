package org.yamcs.mdb;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.logging.Log;
import org.yamcs.xtce.NameDescription;

/**
 * Writes MDB to files in XTCE format.
 *
 */
public class XtceMdbWriter implements SpaceSystemWriter {
    private static final long serialVersionUID = 1L;

    final static Log log = new Log(XtceMdbWriter.class);
    // subsystem name to xtceFileName
    // TODO: the subsystem name is relative because the XtceLoader which creates this writer does not know itself where
    // in the tree it might be attached.
    final Map<String, String> xtceFiles;

    public XtceMdbWriter(Map<String, String> xtceFiles) {
        for (var fn : xtceFiles.values()) {
            if (!Files.isWritable(Paths.get(fn))) {
                throw new ConfigurationException(fn + " is not writable");
            }
        }

        this.xtceFiles = xtceFiles;
    }

    public void write(String fqn, Mdb mdb) throws IOException {
        String sname = NameDescription.getName(fqn);
        String filename = xtceFiles.get(sname);
        if (filename == null) {
            throw new IllegalStateException("Have no file for subsystem '" + sname + "'. Full name is: '" + fqn + "'");
        }
        var xtce = new XtceAssembler().toXtce(mdb, fqn, name -> true);
        File f = new File(filename);
        log.debug("Writing spaceystem {} to {}", fqn, filename);
        try (FileWriter fw = new FileWriter(f)) {
            fw.write(xtce);
        }
    }
}

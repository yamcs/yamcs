package org.yamcs.xtce;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.xtce.xml.XtceStaxReader;

/**
 * XTCE XML loader
 * 
 * @author mu
 * 
 */
public class XtceLoader implements SpaceSystemLoader {

    private transient XtceStaxReader    xtceReader  = null;
    private transient String xtceFileName;
    /**
     * Logger
     */
    transient static Logger             log         = LoggerFactory.getLogger(XtceLoader.class.getName());

    /**
     * Constructor
     */
    public XtceLoader(String xtceFileName) {
        this.xtceFileName=xtceFileName;
        initialize();
    }

    /**
     * Common initialization routine for constructors
     */
    private void initialize() {

    }

    @Override
    public boolean needsUpdate(RandomAccessFile consistencyDateFile) throws IOException, ConfigurationException {

        String line = null;
        while ((line = consistencyDateFile.readLine()) != null) {
            if (line.startsWith("XTCE")) {
                String version = line.substring(5);
                return (!version.equals(getVersionFromXTCEFile()));
            }
        }
        log.info("Could not find a line starting with 'XTCE' in the consistency date file");
        return true;
    }

    private String getVersionFromXTCEFile() throws IOException, ConfigurationException {
        RandomAccessFile xtceFile = new RandomAccessFile(xtceFileName, "r");
        File file = new File(xtceFileName);
        try {

            String line;
            while ((line = xtceFile.readLine()) != null) {
                if (line.trim().startsWith("<SpaceSystem")) {
                    int nameTagPos = line.indexOf("name=\"");
                    if (nameTagPos == -1) {
                        // Space system name is not defined
                        return null;
                    }

                    String xtceFileVersion = line.substring(nameTagPos + 6,
                            line.indexOf('"', nameTagPos + 6));
                    
                    return xtceFileVersion + Long.toString(file.lastModified());
                }
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            xtceFile.close();
        }
        return null;
    }

    @Override
    public SpaceSystem load() throws ConfigurationException, DatabaseLoadException {
        try {
            xtceReader = new XtceStaxReader();
            return xtceReader.readXmlDocument(xtceFileName);
        } catch (FileNotFoundException e) {
            throw new ConfigurationException("XTCE file not found: " + xtceFileName);
        } catch (Exception e) {
            throw new DatabaseLoadException(e);
        }
    }

    @Override
    public String getConfigName() throws ConfigurationException {
        String xtceFileVersion = null;
        try {
            xtceFileVersion = getVersionFromXTCEFile();
        } catch (Exception e) {
            xtceFileVersion = "unknown";
        }

        return xtceFileVersion;
    }

    @Override
    public void writeConsistencyDate(FileWriter consistencyDateFile) throws IOException {

        String xtceFileVersion = null;
        try {
            xtceFileVersion = getVersionFromXTCEFile();
        } catch (ConfigurationException e) {
            xtceFileVersion = "unknown";
        }

        consistencyDateFile.write("XTCE " + xtceFileVersion + "\n");
    }

}

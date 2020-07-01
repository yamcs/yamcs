package org.yamcs.xtce;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.xtce.xml.XtceStaxReader;

/**
 * XTCE XML loader. Used when the MDB configuration contains the type "xtce". For example in yamcs.instance.yaml:
 * 
 * <pre>
 * mdb:
 * - type: xtce
 *   args:
 *     file: "mdb/BogusSAT-1.xml"
 * </pre>
 * 
 * The loader is tolerant for XTCE versions 1.1 and 1.2.
 * <p>
 * For strict adherence to the standard, the file can be verified against the XSD with an external tool.
 * <p>
 * Options:
 * <ul>
 * <li>file: the XML file to be loaded. Can be an absolute path or relative to the server directory. Mandatory.
 * </li>
 * <li>autoTmPartitions: if true (default) all the <{@link SequenceContainer} will be automatically set as archive
 * partitions unless they have they have a parent in the hierarchy that is manually configured for TM partitions. The
 * manual configuration for TM partitions can be achieved using an AncillaryData property with the name Yamcs and the
 * value UseAsArchivingPartition.
 * See <{@link SequenceContainer#useAsArchivePartition(boolean))}
 * </li>
 * </ul>
 * 
 * @author mu
 * 
 */
public class XtceLoader implements SpaceSystemLoader {
    private transient XtceStaxReader xtceReader = null;
    private transient String xtceFileName;
    transient static Logger log = LoggerFactory.getLogger(XtceLoader.class.getName());
    boolean autoTmPartitions = true;
    Set<String> excludedContainers;

    /**
     * Constructor
     */
    public XtceLoader(String xtceFileName) {
        this.xtceFileName = xtceFileName;
    }

    public XtceLoader(YConfiguration config) {
        if (!config.containsKey("file")) {
            throw new ConfigurationException(
                    "the configuration has to contain the keyword 'file' pointing to the XTCE file to be loaded");
        }
        this.xtceFileName = (String) config.get("file");
        if (config.containsKey("excludeTmContainers")) {
            List<String> ec = config.getList("excludeTmContainers");
            excludedContainers = new HashSet<String>(ec);
        }
        autoTmPartitions = config.getBoolean("autoTmPartitions", true);
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
            return doLoad();
        } catch (FileNotFoundException e) {
            throw new ConfigurationException("XTCE file not found: " + xtceFileName);
        } catch (XMLStreamException e) {
            throw new DatabaseLoadException("Cannot parse file: '" + xtceFileName + ": " + e.toString(), e);
        } catch (Exception e) {
            throw new DatabaseLoadException(e);
        }
    }

    private SpaceSystem doLoad() throws Exception {
        xtceReader = new XtceStaxReader();
        if (excludedContainers != null) {
            xtceReader.setExcludedContainers(excludedContainers);
        }
        SpaceSystem spaceSystem = xtceReader.readXmlDocument(xtceFileName);
        if (autoTmPartitions) {
            addTmPartitions(spaceSystem);
        }

        return spaceSystem;
    }

    private void addTmPartitions(SpaceSystem spaceSystem) {
        Set<SequenceContainer> scset = new HashSet<>();
        for (SequenceContainer sc : spaceSystem.getSequenceContainers()) {
            boolean part = true;
            SequenceContainer sc1 = sc;
            while (sc1 != null) {
                if (sc1.useAsArchivePartition()) {
                    part = false;
                    break;
                }
                sc1 = sc1.getBaseContainer();
            }
            if (part) {
                scset.add(sc);
            }
        }
        for(SequenceContainer sc: scset) {
            sc.useAsArchivePartition(true);
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

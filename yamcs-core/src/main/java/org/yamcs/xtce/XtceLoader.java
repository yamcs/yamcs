package org.yamcs.xtce;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.XMLEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.utils.GlobFileFinder;
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
    private transient List<String> xtceFileNames;
    transient static Logger log = LoggerFactory.getLogger(XtceLoader.class.getName());
    boolean autoTmPartitions = true;
    Set<String> excludedContainers;
    private final String configHash;

    public XtceLoader(String fn) {
        this.xtceFileNames = Arrays.asList(fn);
        configHash = Integer.toUnsignedString(fn.hashCode());
    }

    public XtceLoader(YConfiguration config) {
        if (config.containsKey("file")) {
            String fn = (String) config.get("file");
            this.xtceFileNames = Arrays.asList(fn);
        } else if (config.containsKey("fileset")) {
            xtceFileNames = new ArrayList<String>();
            List<String> fileset;
            if (config.get("fileset") instanceof String) {
                fileset = Arrays.asList(config.getString("fileset"));
            } else {
                fileset = config.getList("fileset");
            }
            for (String f : fileset) {
                GlobFileFinder gff = new GlobFileFinder();
                for (Path p : gff.find(f)) {
                    xtceFileNames.add(p.toAbsolutePath().normalize().toString());
                }
            }
        } else {
            throw new ConfigurationException(
                    "the configuration has to contain the keyword 'file' or 'fileset' pointing to the XTCE file(s) to be loaded");
        }
        if (config.containsKey("excludeTmContainers")) {
            List<String> ec = config.getList("excludeTmContainers");
            excludedContainers = new HashSet<String>(ec);
        }
        autoTmPartitions = config.getBoolean("autoTmPartitions", true);
        configHash = Integer.toUnsignedString(config.toString().hashCode());
    }

    @Override
    public boolean needsUpdate(RandomAccessFile consistencyDateFile) throws IOException, ConfigurationException {
        String line = null;
        String configName = getConfigName();
        while ((line = consistencyDateFile.readLine()) != null) {
            if (line.startsWith("XTCE ")) {
                String version = line.substring(5);
                return (!version.equals(configName));
            }
        }
        return true;
    }

    private String getVersionFromXTCEFile(String xtceFileName) {
        File file = new File(xtceFileName);
        try (InputStream in = new FileInputStream(xtceFileName)) {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            // Merge multiple character data blocks into a single event (e.g. algorithm text)
            factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
            
            //Sonarqube suggestion to protect Java XML Parsers from XXE attack
            //see https://rules.sonarsource.com/java/RSPEC-2755
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            XMLEventReader xmlEventReader = factory.createXMLEventReader(in);
            
            while (xmlEventReader.hasNext()) {
                XMLEvent xmlEvent = xmlEventReader.nextEvent();
                int eventType = xmlEvent.getEventType();
                if (eventType == XMLStreamConstants.START_ELEMENT) {
                    Attribute att = xmlEvent.asStartElement().getAttributeByName(new QName("name"));
                    if (att != null) {
                        return att.getValue() + Long.toString(file.lastModified());
                    } else {
                        return null;
                    }
                }
            }
        } catch (IOException | XMLStreamException e) {
            log.warn("Exception when parsing XML file ", e);
        }
        return null;
    }

    @Override
    public List<SpaceSystem> loadList() throws ConfigurationException, DatabaseLoadException {
        List<SpaceSystem> result = new ArrayList<SpaceSystem>();
        for (String xtceFileName : xtceFileNames) {
            try {
                result.add(doLoad(xtceFileName));
            } catch (FileNotFoundException e) {
                throw new ConfigurationException("XTCE file not found: " + xtceFileName);
            } catch (XMLStreamException e) {
                throw new DatabaseLoadException("Cannot parse file: '" + xtceFileName + ": " + e.toString(), e);
            } catch (Exception e) {
                throw new DatabaseLoadException(e);
            }
        }
        return result;
    }

    private SpaceSystem doLoad(String xtceFileName) throws Exception {
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
        collectAutoSeqCont(spaceSystem, scset);

        for (SequenceContainer sc : scset) {
            sc.useAsArchivePartition(true);
        }
    }

    void collectAutoSeqCont(SpaceSystem spaceSystem, Set<SequenceContainer> scset) {
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

        for (SpaceSystem ss : spaceSystem.getSubSystems()) {
            collectAutoSeqCont(ss, scset);
        }

    }

    @Override
    public String getConfigName() throws ConfigurationException {
        StringBuilder sb = new StringBuilder();
        sb.append(configHash);
        for (String fn : xtceFileNames) {
            try {
                sb.append(":").append(getVersionFromXTCEFile(fn));
            } catch (Exception e) {
                sb.append("unknown");
            }
        }
        return sb.toString();
    }

    @Override
    public void writeConsistencyDate(FileWriter consistencyDateFile) throws IOException {

        String configName = getConfigName();
        consistencyDateFile.write("XTCE " + configName + "\n");
    }

    @Override
    public SpaceSystem load() throws ConfigurationException, DatabaseLoadException {
        throw new IllegalStateException("loadList should be used instead");
    }
}

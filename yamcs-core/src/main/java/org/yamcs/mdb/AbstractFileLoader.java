package org.yamcs.mdb;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.yamcs.ConfigurationException;
import org.yamcs.logging.Log;

/**
 * Abstract class for MDB loaders that load data from files (or directories with files)
 * 
 * @author nm
 *
 */
public abstract class AbstractFileLoader implements SpaceSystemLoader {
    protected final Log log;
    protected String configName, path;

    public AbstractFileLoader(String path) throws ConfigurationException {
        this.path = path;
        this.configName = new File(path).getName() + "-" + path.hashCode();
        log = new Log(getClass());
    }

    @Override
    public boolean needsUpdate(RandomAccessFile consistencyDateFile) throws IOException, ConfigurationException {
        String line;
        while ((line = consistencyDateFile.readLine()) != null) {
            if (line.startsWith(configName)) {
                File f = new File(path);
                if (!f.exists()) {
                    throw new ConfigurationException("The file " + path + " doesn't exist");
                }
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy/DDD HH:mm:ss");
                try {
                    Date serializedDate = sdf.parse(line.substring(configName.length() + 1));
                    if (serializedDate.getTime() == f.lastModified()) {
                        log.debug("Serialized {} is up to date", configName);
                        return false;
                    } else {
                        log.debug("Serialized {} is NOT up to date: serializedDate={}, mdbConsistencyDate={}",
                                configName, serializedDate, new Date(f.lastModified()));
                        return true;
                    }
                } catch (ParseException e) {
                    log.warn("Cannot parse the date from " + line + ": ", e);
                    return true;
                }
            }
        }
        log.info("Could not find a line starting with 'MDB' in the consistency date file");
        return true;
    }

    @Override
    public void writeConsistencyDate(FileWriter consistencyDateFile) throws IOException {
        File f = new File(path);
        consistencyDateFile
                .write(configName + " " + (new SimpleDateFormat("yyyy/DDD HH:mm:ss")).format(f.lastModified()) + "\n");
    }

    @Override
    public String getConfigName() throws ConfigurationException {
        return configName;
    }
}

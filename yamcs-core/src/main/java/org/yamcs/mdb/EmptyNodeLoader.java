package org.yamcs.mdb;

import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.xtce.SpaceSystem;

/**
 * Database "loader" that simply creates an empty SpaceSystem with the given name
 * 
 * @author nm
 *
 */
public class EmptyNodeLoader implements SpaceSystemLoader {

    final String name;

    public EmptyNodeLoader(YConfiguration args) {
        name = args.getString("name");
    }

    public EmptyNodeLoader(String name) {
        this.name = name;
    }

    @Override
    public SpaceSystem load() throws ConfigurationException, DatabaseLoadException {
        return new SpaceSystem(name);
    }

    @Override
    public boolean needsUpdate(RandomAccessFile consistencyDateFile) throws IOException, ConfigurationException {
        return false;
    }

    @Override
    public String getConfigName() throws ConfigurationException {
        return name;
    }

    @Override
    public void writeConsistencyDate(FileWriter consistencyDateFile) throws IOException {
        // intentionally left empty
    }
}

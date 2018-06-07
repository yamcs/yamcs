package org.yamcs.simulator;

import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.yamcs.ConfigurationException;
import org.yamcs.xtce.DatabaseLoadException;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.SpaceSystemLoader;

public class PerfDataDbLoader implements SpaceSystemLoader {

    @Override
    public boolean needsUpdate(RandomAccessFile consistencyDateFile) throws IOException, ConfigurationException {
        return true;
    }

    @Override
    public String getConfigName() throws ConfigurationException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void writeConsistencyDate(FileWriter consistencyDateFile) throws IOException {
        // TODO Auto-generated method stub
        
    }

    @Override
    public SpaceSystem load() throws ConfigurationException, DatabaseLoadException {
        // TODO Auto-generated method stub
        return null;
    }

}

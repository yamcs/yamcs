package org.yamcs.xtce;

import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.yamcs.ConfigurationException;

public interface DatabaseLoader {
    /**
     * @param consistencyDateFile check in this file when the last databse has been loaded
     * @return if this loader has to reload the database from its source
     * @throws IOException if the consistencyDateFile can not be read for some reason
     * @throws ConfigurationException 
     */
    boolean needsUpdate(RandomAccessFile consistencyDateFile) throws IOException, ConfigurationException;
    /**
     * The filename used to save the database after all the loaders have loaded it, is based on a 
     * concatenation of the config names returned by the loaders. Thus if a loader can have multiple versions 
     * of the database, they should be saved in multiple files.
     * @return a string to be used as the filename where the serialized instance will be stored.
     * @throws ConfigurationException in case some of the configuration properties do not exist
     */
    String getConfigName() throws ConfigurationException;
    
    /**
     * @param consistencyDateFile the file in which the consistency date should be written
     * @throws IOException if the consistency date file can't be written for some reason
     */
    void writeConsistencyDate(FileWriter consistencyDateFile) throws IOException;
}

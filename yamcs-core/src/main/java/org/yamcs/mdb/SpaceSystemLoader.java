package org.yamcs.mdb;

import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.List;

import org.yamcs.ConfigurationException;
import org.yamcs.xtce.SpaceSystem;

/**
 * Interface implemented by the database loaders.
 * 
 * @author nm
 *
 */
public interface SpaceSystemLoader {

    /**
     * loads the SpaceSystem database in memory.
     * Some references may be unresolved
     * 
     * @deprecated this method is deprecated in favour of {@link #loadList()}
     * 
     * @throws DatabaseLoadException
     */
    @Deprecated
    SpaceSystem load() throws ConfigurationException, DatabaseLoadException;

    /**
     * Loads a list of SpaceSystems.
     * <p>
     * They will be added to the parent in the order in which they appear in the list.
     * <p>
     * By default this method calls the {@link #load()} and returns a list with one element.
     * 
     * @return - the list of
     * @throws DatabaseLoadException
     */
    default List<SpaceSystem> loadList() throws DatabaseLoadException {
        return Arrays.asList(load());
    }

    /**
     * @param consistencyDateFile
     *            check in this file when the last database has been loaded
     * @return if this loader has to reload the database from its source
     * @throws IOException
     *             if the consistencyDateFile can not be read for some reason
     * @throws ConfigurationException
     */
    boolean needsUpdate(RandomAccessFile consistencyDateFile) throws IOException, ConfigurationException;

    /**
     * The filename used to save the database after all the loaders have loaded it, is based on a
     * concatenation of the config names returned by the loaders. Thus if a loader can have multiple versions
     * of the database, they should be saved in multiple files.
     * 
     * @return a string to be used as the filename where the serialised instance will be stored.
     * @throws ConfigurationException
     *             in case some of the configuration properties do not exist
     */
    String getConfigName() throws ConfigurationException;

    /**
     * @param consistencyDateFile
     *            the file in which the consistency date should be written
     * @throws IOException
     *             if the consistency date file can't be written for some reason
     */
    void writeConsistencyDate(FileWriter consistencyDateFile) throws IOException;

    /**
     * This method is called when the writable property of a sub-tree is set true in the mdb configuration.
     * <p>
     * If the SpaceSystem loader supports writing, this will return a writer that will be called each time the subsystem
     * is modified.
     * <p>
     * If the loader does not support writing, it returns null.
     * <p>
     * May throw ConfigurationException if the writer is supported but the file to be written is read-only or does not
     * have write permissions.
     * 
     * @return
     */
    default SpaceSystemWriter getWriter() {
        return null;
    }

    /**
     * If the SpaceSystemLoader supports a corresponding writer (that can write to disk the MDB modifications)
     * 
     * @return
     */
    default boolean isWritable() {
        return false;
    }
}

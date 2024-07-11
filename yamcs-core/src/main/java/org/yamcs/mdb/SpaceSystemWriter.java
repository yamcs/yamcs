package org.yamcs.mdb;

import java.io.IOException;
import java.io.Serializable;

/**
 * This is the analogous to the {@link SpaceSystemLoader} and is used for writing Mdb information to files (or to other
 * media such as databases)
 * <p>
 * To mirror the loader, one writer may be responsible for multiple space systems loaded in parallel (including all
 * their subsystems).
 *
 */
public interface SpaceSystemWriter extends Serializable {
    /**
     * Write the space system with the given fully qualified name.
     * 
     * @param fqn
     *            - the fully qualified name of the space system to write. This is required in order to retrieve it from
     *            the mdb. If the writer supports multiple space systems, the one to be used is selected based on the
     *            last part of the qualified name.
     * @param mdb
     *            - the mdb containing the space system to be written
     * @throws IOException
     */
    public void write(String fqn, Mdb mdb) throws IOException;

}

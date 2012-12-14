package org.yamcs.xtce;

import org.yamcs.ConfigurationException;
import org.yamcs.ppdb.PpDefDb;


/**
 * Interface implemented by the database loaders.
 * @author nm
 *
 */
public interface PpDatabaseLoader extends DatabaseLoader {
    
	/**
	 * Called to load the database in memory.
	 * @param xtcedb
	 * @throws DatabaseLoadException 
	 */
	void loadDatabase(PpDefDb pdpb) throws ConfigurationException,  DatabaseLoadException;
}

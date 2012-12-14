package org.yamcs.xtce;

import org.yamcs.ConfigurationException;


/**
 * Interface implemented by the database loaders.
 * @author nm
 *
 */
public interface SpaceSystemLoader extends DatabaseLoader {
	
    /**
	 * loads the SpaceSystem database in memory.
	 * Some references may be unresolved
	 * 
	 * @throws DatabaseLoadException 
	 */
	SpaceSystem load() throws ConfigurationException,  DatabaseLoadException;
}

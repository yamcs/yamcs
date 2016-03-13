package org.yamcs.xtce.service;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtce.xml.XtceStaxReader;

/**
 * 
 * @author Martin Ursik
 * 
 */
public class MissionDatabaseService {

	/** Logger */
	private static final Logger logger = LoggerFactory.getLogger(MissionDatabaseService.class);

	/** File name of the properties file */
	private static final String XTCE_PROPERTIES_FILENAME = "xtce.properties";

	/** Reference to singleton instance */
	private static MissionDatabaseService instance = null;

	/**
	 * Mission database map: key is the name string (SpaceSystem name attribute
	 * content)
	 */
	private Map<String, XtceDb> xtceDbMap;

	/**
	 * Mission database descriptor map: key is the name string (SpaceSystem name
	 * attribute content)
	 */
	private Map<String, MissionDatabaseDescriptor> xtceDescMap;

	public static synchronized MissionDatabaseService getInstance()
			throws IOException {
		if (instance == null) {
			instance = new MissionDatabaseService();
		}
		return instance;
	}

	private MissionDatabaseService() throws IOException {
		initialize();
	}

	/**
	 * Load all available databases.
	 * 
	 * @throws IOException
	 */
	private void initialize() throws IOException {
		logger.info("Initializing instance");
		xtceDbMap = new HashMap<String, XtceDb>();
		xtceDescMap = new HashMap<String, MissionDatabaseDescriptor>();

		Properties properties = new Properties();
		properties.load(ClassLoader.getSystemClassLoader().getResourceAsStream(
				XTCE_PROPERTIES_FILENAME));

		String databases = properties.getProperty("mdb.databases");
		String[] dbs = null;
		if (databases != null) {
			dbs = databases.split(" ");
		}

		MissionDatabaseDescriptor mdbDescriptor = null;
		for (String database : dbs) {
			mdbDescriptor = new MissionDatabaseDescriptor(
					properties.getProperty("mdb." + database + ".name"),
					properties.getProperty("mdb." + database
							+ ".validity.start"), properties.getProperty("mdb."
							+ database + ".validity.end"),
					properties.getProperty("mdb." + database + ".path"));
			// load the xtce file

			try {
				XtceStaxReader reader = new XtceStaxReader();
				SpaceSystem ss = reader.readXmlDocument(mdbDescriptor
						.getFilename());
				XtceDb xtceDb = new XtceDb(ss);

				xtceDbMap.put(mdbDescriptor.getName(), xtceDb);
				xtceDescMap.put(mdbDescriptor.getName(), mdbDescriptor);
				logger.info("Mdb loaded: " + mdbDescriptor.getFilename());
			} catch (Exception e) {
				logger.error(e.getMessage(), e);
			}
		}
	}


	public XtceDb getXtceDb(String mdbName) {
		return xtceDbMap.get(mdbName);
	}

	public MissionDatabaseDescriptor getXtceDescriptor(String mdbName) {
		return xtceDescMap.get(mdbName);
	}

	/**
	 * Set of available mission databases.
	 * 
	 * @return Unmodifiable set of mission databases.
	 */
	public Set<String> getAvailableDatabases() {
		return Collections.unmodifiableSet(xtceDbMap.keySet());
	}
}

package org.yamcs.ppdb;

import java.io.File;

import org.yamcs.ConfigurationException;
import org.yamcs.xtce.SpreadsheetLoadException;
import org.yamcs.xtce.SpreadsheetLoader;

/**
 * Isolates the parsing of the 'ProcessedParameters'-sheet in a separate loader.
 * Useful when none of the other XTCE spreadsheet sheets are wanted.
 * <p>
 * The name of the space system is set to the filename.
 */
public class SpreadsheetPpDbLoader extends SpreadsheetLoader {
	
	private String configName;

	public SpreadsheetPpDbLoader(String path) throws ConfigurationException {
	    super(path);
        this.configName=new File(path).getName()+"-"+path.hashCode();
    }
	
	@Override
	protected void loadSheets() throws SpreadsheetLoadException {
	    loadProcessedParametersSheet(true);
	}
	
	@Override
	public String getConfigName() {
	    return configName;
	}
}

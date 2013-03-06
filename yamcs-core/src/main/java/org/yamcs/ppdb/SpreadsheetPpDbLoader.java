package org.yamcs.ppdb;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;
import jxl.read.biff.BiffException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.ProcessedParameterDefinition;
import org.yamcs.xtce.DatabaseLoadException;
import org.yamcs.xtce.PpDatabaseLoader;
import org.yamcs.xtce.xml.XtceAliasSet;

public class SpreadsheetPpDbLoader implements PpDatabaseLoader {
	
	static Logger log=LoggerFactory.getLogger(SpreadsheetPpDbLoader.class.getName());
	String path;
	String configName;

	public SpreadsheetPpDbLoader(String path) throws ConfigurationException {
        this.path = path;
        this.configName=new File(path).getName()+"-"+path.hashCode();
    }
	
	@Override
	public void loadDatabase( PpDefDb ppdb ) throws ConfigurationException,
			DatabaseLoadException {
		// Given path may be relative, so use absolute path to report issues
		File ssFile = new File( path );
		if( ! ssFile.exists() ) {
			throw new DatabaseLoadException( new FileNotFoundException( ssFile.getAbsolutePath() ) );
		}
		log.info( "Reading PP from {}", ssFile.getAbsolutePath() );
		Workbook workbook;
		try {
			workbook = Workbook.getWorkbook( ssFile );
		} catch( BiffException e ) {
			log.error( "Error whilst getting workbook", e );
			throw new DatabaseLoadException( e );
		} catch( IOException e ) {
			log.error( "Error whilst getting workbook", e );
			throw new DatabaseLoadException( e );
		}
		
		Sheet para_sheet = workbook.getSheet("ProcessedParameters");
		if( para_sheet == null ) {
			throw new ConfigurationException( "Spreadsheet from '"+ssFile.getAbsolutePath()+"' does not have 'ProcessedParameters' workbook." );
		}
		
		// Each row must specify a umi and an alias, with a group being optional
		// The same umi may have many aliases, but a single alias value must
		// be unique
		
		// Alias keys are the extra column names
		Cell [] header_cells = para_sheet.getRow( 0 );
		if( header_cells.length <= 2 ) {
			throw new ConfigurationException( "No aliases defined in ProcessedParameters sheet: Must have at least three columns including umi and group columns." );
		}
		
		log.info( "Spreadsheet has {} PP definition rows to be parsed", para_sheet.getRows() );
		
		for (int i = 1; i < para_sheet.getRows(); i++) {
			Cell[] cells = para_sheet.getRow(i);
			if ((cells == null) || (cells.length < 3) || cells[0].getContents().startsWith("#")) {
				log.debug( "Ignoring line {} because it is empty, starts with #, or has < 3 cells populated", i );
				continue;
			}
			String umi = cells[0].getContents();
			if (umi.length() == 0) {
				log.debug( "Ignoring line {} because the UMI column is empty", i );
				continue;
			}
			String group = cells[1].getContents();
			
			XtceAliasSet xtceAlias = new XtceAliasSet();
			for( int alias_index = 2; alias_index < cells.length; alias_index++ ) {
				String alias = cells[ alias_index ].getContents();
				if( ! "".equals( alias ) ) {
					if( alias_index > header_cells.length ) {
						throw new ConfigurationException( "Alias entry on line "+i+" does not have namespace specified in first row of column." );
					}
					log.debug( "Got alias '{}' with value '{}'", header_cells[ alias_index ].getContents(), alias );
					xtceAlias.addAlias( header_cells[ alias_index ].getContents(), alias );
				}
			}
			
			ProcessedParameterDefinition ppDef=new ProcessedParameterDefinition( umi, group );
			ppDef.setAliasSet( xtceAlias );

			log.debug( "Adding PP definition '{}'", ppDef.getName() );
			ppdb.add( ppDef );
		}
	}
	
	@Override
	public boolean needsUpdate( RandomAccessFile consistencyDateFile )
			throws IOException, ConfigurationException {
		String line;
		while((line=consistencyDateFile.readLine())!=null) {
			if(line.startsWith(configName)) {
				File f=new File(path);
				if(!f.exists()) throw new ConfigurationException("The file "+path+" doesn't exist");
				SimpleDateFormat sdf=new SimpleDateFormat("yyyy/DDD HH:mm:ss");
				try {
					Date serializedDate=sdf.parse(line.substring(configName.length()+1));
					if(serializedDate.getTime()>=f.lastModified()) {
						log.info("Serialized spreadsheet ppdb "+configName+" is up to date.");
						return false;
					} else {
						log.info("Serialized spreadsheet ppdb"+configName+" is NOT up to date: serializedDate="+serializedDate+" mdbConsistencyDate="+new Date(f.lastModified()));
						return true;
					}
				} catch (ParseException e) {
					log.warn("Cannot parse the date from "+line+": ", e);
					return true;
				}
			}
		}
		log.info("Could not find a line starting with 'MDB' in the consistency date file");
		return true;
	}

	@Override
	public String getConfigName() throws ConfigurationException {
		return configName;
	}

	@Override
	public void writeConsistencyDate( FileWriter consistencyDateFile )
			throws IOException {
		File f=new File(path);
		consistencyDateFile.write(configName+" "+(new SimpleDateFormat("yyyy/DDD HH:mm:ss")).format(f.lastModified())+"\n");
	}

}

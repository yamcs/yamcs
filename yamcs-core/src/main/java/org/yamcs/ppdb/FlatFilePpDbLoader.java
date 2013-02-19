package org.yamcs.ppdb;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.ProcessedParameterDefinition;
import org.yamcs.xtce.PpDatabaseLoader;
import org.yamcs.xtce.xml.XtceAliasSet;

import com.csvreader.CsvReader;


/**
 * This class loads a pp definition database from a flat file
 * 
 * The flat file is supposed to be a tab separated values containing:
 * first line (header): 
 *   Name Group NameSpace1 NameSpace2
 * 
 * next lines:
 *   name group name1 name2..
 * 
 * the name1, name2... are the names in the NameSpace1, NameSpace2...
 * 
 * any line that starts with # is ignored
 * 
 * @author nm
 *
 */
public class FlatFilePpDbLoader implements PpDatabaseLoader {
	String prefix;
	String configName, path;
	PpDefDb ppdb;
	static Logger log=LoggerFactory.getLogger(FlatFilePpDbLoader.class.getName());
	
	public FlatFilePpDbLoader(String path) throws ConfigurationException {
        this.path = path;
        this.configName=new File(path).getName()+"-"+path.hashCode();
    }
	
	@Override
    public String getConfigName(){
		return configName;
	}

	@Override
    public void loadDatabase(PpDefDb db) throws ConfigurationException {
		this.ppdb=db;
		log.info("Loading flatfile " + path);
		int linenum=0;
		try {
		    CsvReader csvReader = new CsvReader(path);
		    csvReader.setDelimiter('\t');
		    csvReader.setComment('#');
		    csvReader.setUseComments(true);
		    csvReader.setSkipEmptyRecords(true);
		    csvReader.setEscapeMode(CsvReader.ESCAPE_MODE_DOUBLED);
		    csvReader.readHeaders();
		    csvReader.setTrimWhitespace(true);
		    String[] header = csvReader.getHeaders();
		//    System.out.println("h[0]: "+header[0]);
		   
		    if(header.length<2) {
		    	throw new ConfigurationException("invalid header in file "+path);
		    }

		    if(!"Name".equals(header[0]) || (!"Group".equals(header[1]))){
		    	throw new ConfigurationException("Invalid header in file "+path);
		    }   
		    while(csvReader.readRecord()) {
		        linenum++;
		        String[] a = csvReader.getValues();
		        
			    if(a.length<2) {
			    	throw new ConfigurationException("invalid entry '"+csvReader.getRawRecord()+"' in file "+path);
			    }
			    ProcessedParameterDefinition ppDef=new ProcessedParameterDefinition(a[0], a[1]);
			    if(a.length>2) {
		                if(a.length>header.length) throw new ConfigurationException("Line "+linenum+" contains more namespaces than specified in the header");
		                XtceAliasSet xtceAlias=new XtceAliasSet();
		                for (int i=2; i<a.length;i++) {
		                	//System.out.println("Setting name "+a[i]+" for namespace '"+header[i]+"'");
		                    xtceAlias.addAlias(header[i], a[i]);
		                }
		                ppDef.setAliasSet(xtceAlias);
		            }
	                ppdb.add(ppDef);
		        }
		    csvReader.close();  
		} catch (IOException e) {
			throw new ConfigurationException("Cannot read file "+path, e);
		} 
	}

	@Override
    public boolean needsUpdate(RandomAccessFile consistencyDateFile) throws IOException, ConfigurationException {
	    String line;
		while((line=consistencyDateFile.readLine())!=null) {
			if(line.startsWith(configName)) {
				File f=new File(path);
				if(!f.exists()) throw new ConfigurationException("The file "+path+" doesn't exist");
				SimpleDateFormat sdf=new SimpleDateFormat("yyyy/DDD HH:mm:ss");
				try {
					Date serializedDate=sdf.parse(line.substring(configName.length()+1));
					if(serializedDate.getTime()>=f.lastModified()) {
						log.info("Serialized flatfile ppdb "+configName+" is up to date.");
						return false;
					} else {
						log.info("Serialized flatfile ppdb"+configName+" is NOT up to date: serializedDate="+serializedDate+" mdbConsistencyDate="+new Date(f.lastModified()));
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
    public void writeConsistencyDate(FileWriter consistencyDateFile) throws IOException {
		File f=new File(path);
		consistencyDateFile.write(configName+" "+(new SimpleDateFormat("yyyy/DDD HH:mm:ss")).format(f.lastModified())+"\n");
	}	
}

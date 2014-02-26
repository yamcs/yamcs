package org.yamcs.xtce;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jxl.Cell;
import jxl.CellType;
import jxl.NumberCell;
import jxl.Sheet;
import jxl.Workbook;
import jxl.read.biff.BiffException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.xtce.Algorithm.AutoActivateType;
import org.yamcs.xtce.NameReference.ResolvedAction;
import org.yamcs.xtce.NameReference.Type;
import org.yamcs.xtce.SequenceEntry.ReferenceLocationType;
import org.yamcs.xtce.xml.XtceAliasSet;

/**
 * This class loads database from excel spreadsheets. Used for the Solar instruments for which the TM 
 * database is too complicated to store in the MDB.
 * 
 * @author nm, ddw
 *
 */
public class SpreadsheetLoader implements SpaceSystemLoader {
	protected HashMap<String,Calibrator> calibrators = new HashMap<String, Calibrator>();
	protected HashMap<String,ArrayList<LimitDef>> limits = new HashMap<String,ArrayList<LimitDef>>();
	protected HashMap<String,EnumeratedParameterType> enumerations = new HashMap<String, EnumeratedParameterType>();
	protected HashMap<String,Parameter> parameters = new HashMap<String, Parameter>();
	
	//columns in the parameters sheet
	final static int IDX_PARAM_OPSNAME=0;
	final static int IDX_PARAM_BITLENGTH=1;
	final static int IDX_PARAM_RAWTYPE=2;
	final static int IDX_PARAM_ENGTYPE=3;
	final static int IDX_PARAM_ENGUNIT=4;
	final static int IDX_PARAM_CALIBRATION=5;
	final static int IDX_PARAM_LOWWARNILIMIT=6;
	final static int IDX_PARAM_HIGHWARNILIMIT=7;
	final static int IDX_PARAM_LOWCRITICALLIMIT=8;
	final static int IDX_PARAM_HIGHCRITICALLIMIT=9;
	
	//columns in the containers sheet
	final static int IDX_CONT_NAME=0;
	final static int IDX_CONT_PARENT=1;
	final static int IDX_CONT_CONDITION=2;
	final static int IDX_CONT_FLAGS=3;
	final static int IDX_CONT_PARA_NAME=4;
	final static int IDX_CONT_RELPOS=5;
	final static int IDX_CONT_SIZEINBITS=6;
	final static int IDX_CONT_EXPECTED_INTERVAL=7;
	
	//columns in calibrations sheet
	final static int IDX_CALIB_NAME=0;
	final static int IDX_CALIB_TYPE=1;
	final static int IDX_CALIB_CALIB1=2;
	final static int IDX_CALIB_CALIB2=3;
	
	//columns in the algorithms sheet
	final static int IDX_ALGO_NAME=0;
	final static int IDX_ALGO_TEXT=1;
	final static int IDX_ALGO_ACTIVATE=2;
	final static int IDX_ALGO_PARA_INOUT=3;
	final static int IDX_ALGO_PARA_REF=4;
	final static int IDX_ALGO_PARA_INSTANCE=5;
	final static int IDX_ALGO_PARA_NAME=6;
	
	// Increment major when breaking backward compatibility, increment minor when making backward compatible changes
	final static String FORMAT_VERSION="2.1";
	// Explicitly support these versions (i.e. load without warning)
	final static String[] FORMAT_VERSIONS_SUPPORTED = new String[]{ "1.6", "1.7", "2.0", FORMAT_VERSION };

	
	protected Workbook workbook;
	protected String opsnamePrefix;
	protected SpaceSystem spaceSystem;
	String configName, path;
	static Logger log=LoggerFactory.getLogger(SpreadsheetLoader.class.getName());
	
	
	/*
	 * configSection is the name under which this config appears in the database
	 */
	public SpreadsheetLoader(String filename) throws ConfigurationException {
	    this.configName = new File(filename).getName();
        path = filename;
    }
    
	@Override
    public String getConfigName(){
		return configName;
	}

	@Override
    public SpaceSystem load() throws ConfigurationException, DatabaseLoadException {
		log.info("Loading spreadsheet " + path);
		
		try {
			// Given path may be relative, so use absolute path to report issues
			File ssFile = new File( path );
			if( !ssFile.exists() ) throw new FileNotFoundException( ssFile.getAbsolutePath() );
			workbook = Workbook.getWorkbook( ssFile );
			loadGeneral();
			loadCalibrators();
			loadLimitsSheet();
			loadParameters();
			loadContainers();
			loadNonStandardSheets(); // Extension point
			loadAlgorithms();
		} catch (BiffException e) {
			throw new DatabaseLoadException(e);
		} catch (IOException e) {
			throw new DatabaseLoadException(e);
		}
		return spaceSystem;
	}

	@Override
    public boolean needsUpdate(RandomAccessFile consistencyDateFile) throws IOException, ConfigurationException {
		String line;
		while((line=consistencyDateFile.readLine())!=null) {
			if(line.startsWith(configName)) {
				File f=new File(path);
				if(!f.exists()) throw new ConfigurationException("The file "+f.getAbsolutePath()+" doesn't exist");
				SimpleDateFormat sdf=new SimpleDateFormat("yyyy/DDD HH:mm:ss");
				try {
					Date serializedDate=sdf.parse(line.substring(configName.length()+1));
					if(serializedDate.getTime()>=f.lastModified()) {
						log.info("Serialized excel database "+configName+" is up to date.");
						return false;
					} else {
						log.info("Serialized excel database "+configName+" is NOT up to date: serializedDate="+serializedDate+" mdbConsistencyDate="+new Date(f.lastModified()));
						return true;
					}
				} catch (ParseException e) {
					log.warn("can not parse the date from "+line+": ", e);
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

	
	private void loadGeneral() throws ConfigurationException{
		Sheet gen_sheet = workbook.getSheet("General");
		Cell[] cells = gen_sheet.getRow(1);
		
		// Version check
		String version=cells[0].getContents();
		// Specific versions supported
		boolean supported = false;
		for( String supportedVersion:FORMAT_VERSIONS_SUPPORTED ) {
			if( version.equals( supportedVersion ) ) {
				supported = true;
			}
		}
		// If not explicitly supported, check major version number...
		if( !supported ) {
			String sheetCompatVersion = version.substring( 0, version.indexOf('.') );
			String loaderCompatVersion = FORMAT_VERSION.substring( 0, FORMAT_VERSION.indexOf('.'));
			supported = loaderCompatVersion.equals( sheetCompatVersion );
			// If major version number matches, but minor number differs
			if( supported && !FORMAT_VERSION.equals( version ) ) {
				log.info( String.format( "Some spreadsheet features for '%s' may not be supported by this loader: Spreadsheet version (%s) differs from loader supported version (%s)", configName, version, FORMAT_VERSION ) );
			}
		}
		if( !supported ) {
			throw new ConfigurationException( String.format( "Spreadsheet '%s' format version (%s) not supported by loader version (%s)", configName, version, FORMAT_VERSION ) );
		}
		
		// Check we have a name
		String name = cells[1].getContents();
		if( "".equals( name ) ) {
			throw new ConfigurationException( String.format( "Must provide a name in cell B:2 in spreadsheet '%s'", configName ) );
		}
		spaceSystem=new SpaceSystem(name);
		
		// Add a header
		Header header = new Header();
		spaceSystem.setHeader( header );
		if( cells.length >= 3 ) {
			header.setVersion( cells[2].getContents() );
		}
		try {
			File wbf = new File( path );
			Date d = new Date( wbf.lastModified() );
			String date = (new SimpleDateFormat("yyyy/DDD HH:mm:ss")).format( d );
			header.setDate( date );
		} catch ( Exception e ) {
			// Ignore
		}
		
		// Opsname prefix is optional
		if( cells.length >= 4 ) {
			opsnamePrefix=cells[3].getContents();
		} else {
			opsnamePrefix="";
			log.info( "No opsnamePrefix specified for {}", configName );
		}
	}

	private void loadCalibrators() {
		 //read the calibrations
		 Sheet cal_sheet = workbook.getSheet("Calibration");
		 double[] pol_coef = null;
		 // SplinePoint = pointpair
		 ArrayList<SplinePoint>spline = null;
		 EnumeratedParameterType enumeration = null;
		 // start at 1 to not use the first line (= title line)
		 int start = 1;
		 while(true) {
			 // we first search for a row containing (= starting) a new calibration
			 while (start < cal_sheet.getRows()) {
				 Cell[] cells = cal_sheet.getRow(start);
				 if ((cells.length > 0) && (cells[0].getContents().length() > 0) && !cells[0].getContents().startsWith("#")) {
					 break;
				 }
				 start++;
			 }
			 if (start >= cal_sheet.getRows()) {
				break;
			 }
			 Cell[] cells = cal_sheet.getRow(start);
			 String name = cells[IDX_CALIB_NAME].getContents();
			 String type = cells[IDX_CALIB_TYPE].getContents();
			 
			 // now we search for the matching last row of that calibration
			 int end = start + 1;
			 while (end < cal_sheet.getRows()) {
				 cells = cal_sheet.getRow(end);
				 if (!hasColumn(cells, IDX_CALIB_CALIB1)) {
					 break;
				 }
				 end++;
			 }
			 if ("enumeration".equalsIgnoreCase(type)) {
				 enumeration = new EnumeratedParameterType(name);
			 } else if ("polynomial".equalsIgnoreCase(type)) {
				 pol_coef = new double[end - start];
			 } else if ("pointpair".equalsIgnoreCase(type)) {
				 spline = new ArrayList<SplinePoint>();
			 } else {
				 error("Calibration:"+(start+1)+" calibration type '"+type+"' not supported. Supported types: enumeration, polynomial and pointpair");
			 }
			 
			for (int j = start; j < end; j++) {
				try {
				cells = cal_sheet.getRow(j);
				if ("enumeration".equalsIgnoreCase(type)) {
					try {
						long raw=Integer.decode(cells[IDX_CALIB_CALIB1].getContents());
						enumeration.addEnumerationValue(raw, cells[IDX_CALIB_CALIB2].getContents());
					} catch(NumberFormatException e) {
							error("Can't get integer from raw value out of '"+cells[IDX_CALIB_CALIB1].getContents()+"' for Calibration sheet, line "+(j+1));
					}
				} else if ("polynomial".equalsIgnoreCase(type)) {
				    pol_coef[j - start] = getNumber(cells[IDX_CALIB_CALIB1]);
				} else if ("pointpair".equalsIgnoreCase(type)) {
					spline.add(new SplinePoint(getNumber(cells[IDX_CALIB_CALIB1]), getNumber(cells[IDX_CALIB_CALIB2])));
				}
				} catch(RuntimeException e) {
					log.error("Exception caught when reading line "+(j+1)+" from "+path);
					throw e;
				}
			 }
			if ("enumeration".equalsIgnoreCase(type)) {
				enumerations.put(name, enumeration);
			} else if ("polynomial".equalsIgnoreCase(type)) {
				calibrators.put(name, new PolynomialCalibrator(pol_coef));
			} else if ("pointpair".equalsIgnoreCase(type)) {
				calibrators.put(name, new SplineCalibrator(spline));
			}
			 start = end;
		 }
		//System.out.println("enumerations: " + enumerations + "\n");
		//System.out.println("calibrators: " + calibrators + "\n"); 
	}
	
	private double getNumber(Cell cell) {
	    if((cell.getType()==CellType.NUMBER) || (cell.getType()==CellType.NUMBER_FORMULA)) {
            return ((NumberCell) cell).getValue();
        } else {
            return Double.parseDouble(cell.getContents());
        }
	}
 	
	/**
	 * If there is a sheet with the Limits, load it now!
	 */
	private void loadLimitsSheet() {
		 //read the limits
		 Sheet lim_sheet = workbook.getSheet("Limits");
		 if(lim_sheet==null)return;
		 
		 // start at 1 to not use the first line (= title line)
		 int start = 1;
		 while(true) {
			 // we first search for a row containing (= starting) a new limit
			 while (start < lim_sheet.getRows()) {
				 Cell[] cells = lim_sheet.getRow(start);
				 if ((cells.length > 0) && (cells[0].getContents().length() > 0)) {
					 break;
				 }
				 start++;
			 }
			 if (start >= lim_sheet.getRows()) {
				 break;
			 }
			 Cell[] cells = lim_sheet.getRow(start);
			 String name = cells[0].getContents();
			 // now we search for the matching last row of the limit
			 int end = start + 1;
			 while (end < lim_sheet.getRows()) {
				 cells = lim_sheet.getRow(end);
				 if (isRowEmpty(cells) || (cells[0].getContents().length() != 0)) {
					 break;
				 }
				 end++;
			 }

			for (int j = start; j < end; j++) {
				try {
					cells = lim_sheet.getRow(j);
					String condition=cells[1].getContents();
					if(condition.length()==0) condition=null;
					String mins=(cells.length>2)?cells[2].getContents():"";
					String maxs=(cells.length>3)?cells[3].getContents():"";
					double min=(mins.length()>0)?Double.parseDouble(mins): Double.NEGATIVE_INFINITY;
					double max=(maxs.length()>0)?Double.parseDouble(maxs): Double.POSITIVE_INFINITY;					
					FloatRange range=new FloatRange(min,max);
					AlarmRanges ranges=new AlarmRanges();
					ranges.warningRange=range;
					ArrayList<LimitDef> al=limits.get(name);
					if(al==null) {
						al=new ArrayList<LimitDef>();
						limits.put(name, al);
					}
					al.add(new LimitDef(condition,ranges));
				} catch(RuntimeException e) {
					log.error("Exception caught when reading line "+(j+1)+" from "+path);
					throw e;
				}
			 }
			 ArrayList<LimitDef> al=limits.get(name);
			 if((al==null)||(al.size()==0)) {
				 log.warn("Limit definition '"+name+"' on line "+start+" contains no range; Limit ignored");
				 limits.remove(name);
			 } else if(!limits.containsKey(name) && al.size()>1) {
				 log.warn("Limit definition '"+name+"' on line "+start+" contains more than one range but has no condition; Limit ignored");
			 }
			 start = end;
		 }
		//System.out.println("enumerations: " + enumerations + "\n");
		//System.out.println("calibrators: " + calibrators + "\n"); 
	}
	
	private boolean isRowEmpty(Cell[] cells) {
		for(int i=0;i<cells.length;i++) 
			if(cells[i].getContents().length()>0) return false;
		return true;
	}
	
	private void loadParameters() {
		Sheet para_sheet = workbook.getSheet("Parameters");
		for (int i = 1; i < para_sheet.getRows(); i++) {
			Cell[] cells = para_sheet.getRow(i);
			if ((cells == null) || (cells.length < 3) || cells[0].getContents().startsWith("#")) {
				continue;
			}
			String name = cells[IDX_PARAM_OPSNAME].getContents();
			if (name.length() == 0) {
				continue;
			}
			
			Parameter param = new Parameter(name);
            parameters.put(param.getName(), param);

            XtceAliasSet xas=new XtceAliasSet();
            xas.addAlias(MdbMappings.MDB_OPSNAME, opsnamePrefix+param.getName());
            param.setAliasSet(xas);
            
            spaceSystem.addParameter(param);
            
			//String path = cells[IDX_MEAS_PATH].getContents();
			String rawtype = cells[IDX_PARAM_RAWTYPE].getContents();
			if("DerivedValue".equalsIgnoreCase(rawtype)) continue;
			int bitlength=-1;
			try {
			    String bitls=cells[IDX_PARAM_BITLENGTH].getContents();
			    if(!bitls.isEmpty()) bitlength = Integer.decode(bitls);
			} catch(NumberFormatException e) {
				error("Parameters:"+(i+1)+": can't get bitlength out of '"+cells[IDX_PARAM_BITLENGTH].getContents()+"'");
			}
			String engtype = cells[IDX_PARAM_ENGTYPE].getContents();
			String calib=null;
			if(cells.length>IDX_PARAM_CALIBRATION) calib = cells[IDX_PARAM_CALIBRATION].getContents();
			if("n".equals(calib) || "".equals(calib))calib=null;
			if("y".equalsIgnoreCase(calib)) calib=name;
			
			ParameterType ptype=null;
			if ("uint".equalsIgnoreCase(engtype)) {
				ptype = new IntegerParameterType(name);
				((IntegerParameterType)ptype).signed = false;
			} else if ("int".equalsIgnoreCase(engtype)) {
				ptype = new IntegerParameterType(name);
			} else if ("float".equalsIgnoreCase(engtype)) {
				ptype = new FloatParameterType(name);
			} else if ("enumerated".equalsIgnoreCase(engtype)) {
				if(calib==null) {
					error("parameter " + name + " has to have an enumeration");
				}
				ptype = enumerations.get(calib);
				if (ptype == null) {
					error("parameter " + name + " is supposed to have an enumeration '" + calib + "' but the enumeration does not exist");
				}
			} else	if ("string".equalsIgnoreCase(engtype)) {
				ptype = new StringParameterType(name);
			} else	if ("binary".equalsIgnoreCase(engtype)) {
				ptype = new BinaryParameterType(name);
			} else {
				error("Parameters:"+(i+1)+": unknown parameter type " + engtype);
			}
			
			String units=null;
			if(cells.length>IDX_PARAM_ENGUNIT) units = cells[IDX_PARAM_ENGUNIT].getContents();
			if(!"".equals(units) && units != null && ptype instanceof BaseDataType) {
				UnitType unitType = new UnitType(units);
				((BaseDataType) ptype).addUnit(unitType);
			}
			
			loadParameterLimits(ptype,cells);
			
			//calibrations
			DataEncoding encoding = null;
			if (("uint".equalsIgnoreCase(rawtype)) || rawtype.toLowerCase().startsWith("int")) {
			    if(bitlength==-1) error("Parameters:"+(i+1)+" for integer parameters bitlength is mandatory");
				encoding = new IntegerDataEncoding(name, bitlength);
				if (rawtype.toLowerCase().startsWith("int")) {
					if ("int".equals(rawtype)) {
						((IntegerDataEncoding)encoding).encoding = IntegerDataEncoding.Encoding.twosCompliment;
					} else {
						int startBracket = rawtype.indexOf('(');
						if (startBracket != -1) {
							int endBracket = rawtype.indexOf(')', startBracket);
							if (endBracket != -1) {
								String intRepresentation = rawtype.substring(startBracket+1, endBracket).trim().toLowerCase();
								if ("2c".equals(intRepresentation)) {
									((IntegerDataEncoding)encoding).encoding = IntegerDataEncoding.Encoding.twosCompliment;
								} else if ("si".equals(intRepresentation)) {
									((IntegerDataEncoding)encoding).encoding = IntegerDataEncoding.Encoding.signMagnitude;
								} else {
									error("Unsupported signed integer representation: "+intRepresentation);	
								}
							}
						}
					}	
				}
				if ((!"enumerated".equalsIgnoreCase(engtype)) && (calib!=null)) {
					Calibrator c = calibrators.get(calib);
					if (c == null) {
						error("parameter " + name + " is supposed to have a calibrator '" + calib + "' but the calibrator does not exist");
					}
					((IntegerDataEncoding)encoding).defaultCalibrator = c;
				}
			} else if ("bytestream".equalsIgnoreCase(rawtype)) {
			    if(bitlength==-1) error("Parameters:"+(i+1)+" for bytestream parameters bitlength is mandatory");
				encoding=new BinaryDataEncoding(name, bitlength);
			} else if ("string".equalsIgnoreCase(rawtype)) {
				// Version <= 1.6 String type
				// STRING
			    if(bitlength==-1) {
			    	// Assume null-terminated if no length specified
			        encoding=new StringDataEncoding(name, StringDataEncoding.SizeType.TerminationChar);
			    } else {
			        encoding=new StringDataEncoding(name, StringDataEncoding.SizeType.Fixed);
			        encoding.setSizeInBits(bitlength);
			    }
			} else if ( "fixedstring".equalsIgnoreCase( rawtype ) ) {
				// v1.7 String type
				// FIXEDSTRING
				if(bitlength==-1) error("Parameters:"+(i+1)+" bitlength is mandatory for fixedstring raw type");
				encoding=new StringDataEncoding(name, StringDataEncoding.SizeType.Fixed);
		        encoding.setSizeInBits(bitlength);
			} else if ( rawtype.toLowerCase().startsWith( "terminatedstring" ) ) {
				// v1.7 String type
				// TERMINATEDSTRING
				encoding=new StringDataEncoding(name, StringDataEncoding.SizeType.TerminationChar);
				// Use specified byte if found, otherwise accept class default.
				int startBracket = rawtype.indexOf( '(' );
				if( startBracket != -1 ) {
					int endBracket = rawtype.indexOf( ')', startBracket );
					if( endBracket != -1 ) {
						try {
							byte terminationChar = Byte.parseByte( rawtype.substring( rawtype.indexOf('x', startBracket)+1, endBracket ).trim(), 16 );
							((StringDataEncoding)encoding).setTerminationChar(terminationChar);
						} catch (NumberFormatException e) {
							error( "Parameters:"+(i+1)+" could not parse specified base 16 terminator from "+rawtype );
						}
					}
				}
			} else if ( rawtype.toLowerCase().startsWith( "prependedsizestring" ) ) {
				// v1.7 String type
				// PREPENDEDSIZESTRING
				encoding=new StringDataEncoding( name, StringDataEncoding.SizeType.LeadingSize );
				// Use specified size if found, otherwise accept class default.
				int startBracket = rawtype.indexOf( '(' );
				if( startBracket != -1 ) {
					int endBracket = rawtype.indexOf( ')', startBracket );
					if( endBracket != -1 ) {
						try {
							int sizeInBitsOfSizeTag = Integer.parseInt( rawtype.substring(startBracket+1, endBracket).trim() );
							((StringDataEncoding)encoding).setSizeInBitsOfSizeTag( sizeInBitsOfSizeTag );
						} catch (NumberFormatException e) {
							error( "Parameters:"+(i+1)+" could not parse integer size from "+rawtype );
						}
					}
				}
			} else if ("float".equalsIgnoreCase(rawtype)) {
			    if(bitlength==-1) error("Parameters:"+(i+1)+" for float parameters bitlength is mandatory");
				encoding=new FloatDataEncoding(name, bitlength);
				if(calib!=null) {
					Calibrator c = calibrators.get(calib);
					if (c == null) {
						error("parameter " + name + " is supposed to have a calibrator '" + calib + "' but the calibrator does not exist.");
					} else {
						((FloatDataEncoding)encoding).defaultCalibrator = c;
					}
				}
			} else {
				error("unknown raw type " + rawtype);
			}
			
			if (ptype instanceof IntegerParameterType) {
				// Integers can be encoded as strings
				if( encoding instanceof StringDataEncoding ) {
					// Create a new int encoding which uses the configured string encoding
					IntegerDataEncoding intStringEncoding = new IntegerDataEncoding(name, ((StringDataEncoding)encoding));
					if( calib != null ) {
						Calibrator c = calibrators.get(calib);
						if( c == null ) {
							error("Parameter " + name + " specified calibrator '" + calib + "' but the calibrator does not exist");
						}
						intStringEncoding.defaultCalibrator = c;
					}
					((IntegerParameterType)ptype).encoding = intStringEncoding;
				} else {
					((IntegerParameterType)ptype).encoding = encoding;
				}
			} else if (ptype instanceof BinaryParameterType) {
				((BinaryParameterType)ptype).encoding = encoding;
			} else if (ptype instanceof FloatParameterType) {
				// Floats can be encoded as strings
				if ( encoding instanceof StringDataEncoding ) {
					// Create a new float encoding which uses the configured string encoding
					FloatDataEncoding floatStringEncoding = new FloatDataEncoding( name, ((StringDataEncoding)encoding) );
					if(calib!=null) {
						Calibrator c = calibrators.get(calib);
						if( c == null ) {
							error("Parameter " + name + " specified calibrator '" + calib + "' but the calibrator does not exist.");
						} else {
							floatStringEncoding.defaultCalibrator = c;
						}
					}
					((FloatParameterType)ptype).encoding = floatStringEncoding;
				} else {
					((FloatParameterType)ptype).encoding = encoding;
				}
			} else if (ptype instanceof EnumeratedParameterType) {
				// Enumerations encoded as string integers
				if( encoding instanceof StringDataEncoding ) {
					IntegerDataEncoding intStringEncoding = new IntegerDataEncoding(name, ((StringDataEncoding)encoding));
					// Don't set calibrator, already done when making ptype
					((EnumeratedParameterType)ptype).encoding = intStringEncoding;
				} else {
					((EnumeratedParameterType)ptype).encoding = encoding;
				}
			} else if (ptype instanceof StringParameterType) {
			    ((StringParameterType)ptype).encoding = encoding;
			}     

			param.setParameterType(ptype);
		}
		
/*		System.out.println("got parameters:");
		for (Parameter p: parameters.values()) {
			System.out.println(p);
		}
*/	}

	
	private void loadParameterLimits(ParameterType ptype, Cell[] cells) {
		if (cells.length<=IDX_PARAM_LOWWARNILIMIT) return;
		
		String mins=cells[IDX_PARAM_LOWWARNILIMIT].getContents();
		//System.out.println("limits.length="+limits.size()+"limits: "+limits.keySet() +"mins="+mins);
		if(limits.containsKey(mins)) { //limit is specified on the separate sheet
			for(LimitDef ld:limits.get(mins)) {
				Pattern p=Pattern.compile("(\\w+)(==|!=|>|<|>=|<=)(\\w+)");
				if(ld.condition.length()>0) {
					Matcher m=p.matcher(ld.condition);
					if((!m.matches()) ||(m.groupCount()!=3)) {
						log.warn("Cannot parse alarm condition '"+ld.condition+"'");
						continue;
					}
					String pname=m.group(1);
					String values=m.group(3);
					String op=m.group(2);
					Parameter para=parameters.get(pname);
					if(para!=null) {

					    ParameterInstanceRef paraRef=new ParameterInstanceRef(para);
					    Comparison c;
					    if(para.parameterType instanceof IntegerParameterType) {
					        try {
					            long value=Long.parseLong(values);
					            c=new Comparison(paraRef,value,Comparison.stringToOperator(op));
					        } catch (NumberFormatException e) {
					            log.warn("Cannot parse alarm condition '"+ld.condition+"': "+e.getMessage());
					            continue;
					        }
					    } else if(para.parameterType instanceof EnumeratedParameterType) {
					        c=new Comparison(paraRef, values, Comparison.stringToOperator(op));
					    } else {
					        log.warn("Conditions not supported for parameter of type "+para.parameterType);
					        continue;
					    }
					    NumericContextAlarm nca=new NumericContextAlarm();
					    nca.setContextMatch(c);
					    nca.setStaticAlarmRanges(ld.ranges);
					    if(ptype instanceof IntegerParameterType){
					        ((IntegerParameterType)ptype).addContextAlarm(nca);
					    } else {
					        ((FloatParameterType)ptype).addContextAlarm(nca);
					    }
					} else {  //TODO
				//	    ParameterInstanceRef paraRef=new ParameterInstanceRef(new NameReference(pname));
				//	    Comparison c=new Comparison(paraRef, Comparison.stringToOperator(op));
					  
					}
				} else {
					setDefaultWarningAlarmRange(ptype, ld.ranges.warningRange);
					setDefaultCriticalAlarmRange(ptype, ld.ranges.criticalRange);
				}
			}
		} else if((cells.length>IDX_PARAM_HIGHWARNILIMIT) && ((ptype instanceof IntegerParameterType) || (ptype instanceof FloatParameterType))) {
			String maxs=cells[IDX_PARAM_HIGHWARNILIMIT].getContents();
			if((mins.length()>0) && (maxs.length()>0)) {
			    double min=(mins.length()>0)?Double.parseDouble(mins):Double.NEGATIVE_INFINITY;
			    double max=(maxs.length()>0)?Double.parseDouble(maxs):Double.POSITIVE_INFINITY;
			    setDefaultWarningAlarmRange(ptype,new FloatRange(min,max));
			}
		}
		
//		danger limits
		if((cells.length>IDX_PARAM_HIGHCRITICALLIMIT) && ((ptype instanceof IntegerParameterType) || (ptype instanceof FloatParameterType))) {
			mins=cells[IDX_PARAM_LOWCRITICALLIMIT].getContents();
			String maxs=cells[IDX_PARAM_HIGHCRITICALLIMIT].getContents();
			if((mins.length()>0) && (maxs.length()>0)) {
				double min=Double.parseDouble(mins);
				double max=Double.parseDouble(maxs);
				setDefaultCriticalAlarmRange(ptype, new FloatRange(min,max));
			}
		}
	}
	
	

	private void setDefaultCriticalAlarmRange(ParameterType ptype, FloatRange range) {
		if(range==null)return;
		if(ptype instanceof IntegerParameterType){
			((IntegerParameterType)ptype).setDefaultCriticalAlarmRange(range);
		} else {
			((FloatParameterType)ptype).setDefaultCriticalAlarmRange(range);
		}
	}
	
	private void setDefaultWarningAlarmRange(ParameterType ptype, FloatRange range) {
		if(range==null)return;
		if(ptype instanceof IntegerParameterType){
			((IntegerParameterType)ptype).setDefaultWarningAlarmRange(range);
		} else {
			((FloatParameterType)ptype).setDefaultWarningAlarmRange(range);
		}
	}
	
	private void loadContainers() throws DatabaseLoadException {
		Sheet tm_sheet = workbook.getSheet("Containers");
		HashMap<String, SequenceContainer> containers = new HashMap<String, SequenceContainer>();
		
		for (int i = 1; i < tm_sheet.getRows(); i++) {
			// search for a new packet definition, starting from row i 
		    //  (explanatory note, i is incremented inside this loop too, and that's why the following 4 lines work)
			Cell[] cells = tm_sheet.getRow(i);
			if (cells == null || cells.length<1) {
			    log.debug("Ignoring line {} because it's empty",(i+1));
			    continue;
			}
			if(cells[0].getContents().equals("")|| cells[0].getContents().startsWith("#")) {
			    log.debug("Ignoring line {} because first cell is empty or starts with '#'", (i+1));
                continue;
			}
			// at this point, cells contains the data (name, path, ...) of either
			//		a) a sub-container (inherits from another packet)
			//		b) an aggregate container (which will be used as if it were a measurement, by other (sub)containers)
			String name = cells[IDX_CONT_NAME].getContents();
			String parent=null;
			String condition=null;
			if(cells.length>IDX_CONT_PARENT) {
			    parent=cells[IDX_CONT_PARENT].getContents();
			    if(cells.length<=IDX_CONT_CONDITION) {
			        error("parent specified but without inheritance condition on container on line "+(i+1));
			    }
			    condition = cells[IDX_CONT_CONDITION].getContents();
			    //if( "".equals( condition ) ) {
			    //	error( String.format( "Container %s (from spreadsheet '%s' line %d) has a parent container specified but no inheritance condition", name, configName, i));
			    //}
			}

			if("".equals(parent)) parent=null;

			// absoluteoffset is the absolute offset of the first parameter of the container
			int absoluteoffset=-1;
			if(parent==null) {
			    absoluteoffset=0;
			} else {
			    int x=parent.indexOf(":");
			    if(x!=-1) {
			        absoluteoffset=Integer.decode(parent.substring(x+1));
			        parent=parent.substring(0, x);
			    }
			}

			int containerSizeInBits=-1;
			if(hasColumn(cells, IDX_CONT_SIZEINBITS)) {
				containerSizeInBits=Integer.decode(cells[IDX_CONT_SIZEINBITS].getContents());
			}
			
			RateInStream rate=null;
			if(hasColumn(cells, IDX_CONT_EXPECTED_INTERVAL)) {
                int expint=Integer.decode(cells[IDX_CONT_EXPECTED_INTERVAL].getContents());
                rate=new RateInStream(expint);
            }
            
			// create a new SequenceContainer that will hold the parameters (i.e. SequenceEntries) for the ORDINARY/SUB/AGGREGATE packets, and register that new SequenceContainer in the containers hashmap
			SequenceContainer container = new SequenceContainer(name);
			container.sizeInBits=containerSizeInBits;
			containers.put(name, container);
			container.setRateInStream(rate);
			
			//System.out.println("for "+name+" got absoluteoffset="+)
			// we mark the start of the TM packet and advance to the next line, to get to the first parameter (if there is one)
			int start = i++;

			// now, we start processing the parameters (or references to aggregate containers)
			boolean end = false;
			int counter = 0; // sequence number of the SequenceEntrys in the SequenceContainer
			while (!end && (i < tm_sheet.getRows())) {
				
				// get the next row, containing a measurement/aggregate reference
				cells = tm_sheet.getRow(i);
				// determine whether we have not reached the end of the packet definition.
				if ((cells == null) || (cells.length <= IDX_CONT_RELPOS) || cells[IDX_CONT_RELPOS].getContents().equals("")) {
					end = true; continue;
				}

				// extract a few variables, for further use
				String flags = cells[IDX_CONT_FLAGS].getContents();
				String paraname = cells[IDX_CONT_PARA_NAME].getContents();
				if((cells.length<=IDX_CONT_RELPOS)||cells[IDX_CONT_RELPOS].getContents().equals("")) error("relpos is not specified for "+paraname+" on line "+(i+1));
				int relpos = Integer.decode(cells[IDX_CONT_RELPOS].getContents());
				
				// we add the relative position to the absoluteoffset, to specify the location of the new parameter. We only do this if the absoluteoffset is not equal to -1, because that would mean that we cannot and should not use absolute positions anymore
				if (absoluteoffset != -1) {
					absoluteoffset += relpos;
				}
				// the repeat string will contain the number of times a measurement (or aggregate container) should be repeated. It is a String because at this point it can be either a number or a reference to another measurement
				String repeat = "";
				// we check whether the measurement (or aggregate container) has a '*' inside it, meaning that it is a repeat measurement/aggregate
				Matcher m = Pattern.compile("(.*)[*](.*)").matcher(paraname);
				if (m.matches()) {
					repeat = m.group(1);
					paraname = m.group(2);
				}

				// check whether this measurement/aggregate actually has an entry in the parameters table
				// first we check if it is a measurement by trying to retrieve it from the parameters map. If this succeeds we add it as a new parameterentry,
				// otherwise, we search for it in the containersmap, as it is probably an aggregate. If it is not, we encountered an error
				// note that one of the next 2 lines will return null, but this does not pose a problem, it makes programming easier along the way
				Parameter param = parameters.get(paraname);
				SequenceContainer sc = containers.get(paraname);
				// if the sequenceentry is repeated a fixed number of times, this number is recorded in the 'repeated' variable and used to calculate the next absoluteoffset (done below) 
				int repeated = -1; 
				if (param != null) {
					SequenceEntry se;
					if(flags.contains("L")) {
						if(param.parameterType instanceof IntegerParameterType) {
							((IntegerParameterType)param.parameterType).encoding.byteOrder=ByteOrder.LITTLE_ENDIAN;
						} else if(param.parameterType instanceof FloatParameterType) {
							((FloatParameterType)param.parameterType).encoding.byteOrder=ByteOrder.LITTLE_ENDIAN;
						} else if(param.parameterType instanceof EnumeratedParameterType) {
							((EnumeratedParameterType)param.parameterType).encoding.byteOrder=ByteOrder.LITTLE_ENDIAN;
						} else {
							error("little endian not supported for this parameter: "+param);
						}
					}
					// if absoluteoffset is -1, somewhere along the line we came across a measurement or aggregate that had as a result that the absoluteoffset could not be determined anymore; hence, a relative position is added
					if (absoluteoffset == -1) {
						se = new ParameterEntry(counter, container, relpos, ReferenceLocationType.previousEntry, param);
					} else {
						se = new ParameterEntry(counter, container, absoluteoffset, ReferenceLocationType.containerStart, param);
					}
					// also check if the parameter should be added multiple times, and if so, do so
					repeated = addRepeat(se, repeat);
					container.entryList.add(se);
				} else {
					if (sc != null) {
						// ok, we found it as an aggregate, so we add it to the entryList of container, as a new ContainerEntry
						// if absoluteoffset is -1, somewhere along the line we came across a measurement or aggregate that had as a result that the absoluteoffset could not be determined anymore; hence, a relative position is added
						SequenceEntry se;
						if (absoluteoffset == -1) {
							se = new ContainerEntry(counter, container, relpos, ReferenceLocationType.previousEntry, sc);
						} else {
							se = new ContainerEntry(counter, container, absoluteoffset, ReferenceLocationType.containerStart, sc);
						}
						// also check if the parameter should be added multiple times, and if so, do so
						repeated = addRepeat(se, repeat);
						container.entryList.add(se);
					} else {
					    throw new DatabaseLoadException("error on line "+(i+1)+" of the Containers sheet: the measurement/container '" + paraname + "' was not found in the parameters or containers map");
					}
				}
				// after adding this measurement, we need to update the absoluteoffset for the next one. For this, we add the size of the current SequenceEntry to the absoluteoffset
				int size=getSize(param,sc);
				if ((repeated != -1) && (size != -1) && (absoluteoffset != -1)) {
					absoluteoffset += repeated * size;
				} else {
					// from this moment on, absoluteoffset is set to -1, meaning that all subsequent SequenceEntries must be relative
					absoluteoffset = -1;
				}

				// increment the counters;
				i++; counter++;
			}

			// at this point, we have added all the parameters and aggregate containers to the current packets. What remains to be done is link it with its base
			if(parent!=null) {
			    // the condition is parsed and used to create the container.restrictionCriteria
			    //1) get the parent, from the same sheet
			    SequenceContainer sc = containers.get(parent);
			    //the parent is not in the same sheet, try to get from the Xtcedb
			    if(sc==null) {
			        sc = spaceSystem.getSequenceContainer(parent);
			    }
			    if (sc != null) {
	                container.setBaseContainer(sc);
			    } else {
			        final SequenceContainer c=container;
			        NameReference nr=new NameReference(parent, Type.SEQUENCE_CONTAINTER,
			                new ResolvedAction() { 
                                @Override
                                public boolean resolved(NameDescription nd) {
                                    c.setBaseContainer((SequenceContainer) nd);
                                    return true;
                                }
                            });
			        spaceSystem.addUnresolvedReference(nr);
			    }

			    // 2) extract the condition and create the restrictioncriteria
			    ComparisonList cl = new ComparisonList();
			    // If conditions have been specified...
			    if( !"".equals( condition ) ) {
			    	// 2.2) if there are multiple conditions, we get them all by splitting according to the ';' between the condition
			    	String splitted[] = condition.split(";");
			    	for (String sp: splitted) {
				        // 2.3) in each splitted part, we search for either =, !=, <=, >=, < or > and add this as a Comparison
				        Matcher m = Pattern.compile("(.*?)(=|!=|<=|>=|<|>)(.*)").matcher(sp);
				        if(!m.matches()) throw new DatabaseLoadException("error on line "+(start+1)+" of the Containers sheet: cannot parse inheritance condition '"+sp+"'");
				        Parameter param=null;
				        String paramName=m.group(1);
				        
				        param = spaceSystem.getParameter(paramName);
				        // we now get the actual comparison operator (and we change '=' to '==')
				        String operation = m.group(2);
				        if (operation.equals("="))
				            operation="==";
				        Comparison.OperatorType coperator=Comparison.stringToOperator(operation);

				        // create the Comparison and add it
				        try {
				            if (param != null) {
				                ParameterInstanceRef pref=new ParameterInstanceRef(param, false);
				                cl.comparisons.add(new Comparison(pref, Integer.decode(m.group(3)), coperator));
				            } else {
				                final ParameterInstanceRef pref=new ParameterInstanceRef(false);
				                final Comparison ucomp=new Comparison(pref, Integer.decode(m.group(3)), coperator);
				                cl.comparisons.add(ucomp);
				                
				                NameReference nr=new NameReference(paramName, Type.PARAMETER, 
				                        new ResolvedAction() {
	                                        @Override
	                                        public boolean resolved(NameDescription nd) {
	                                            pref.setParameter((Parameter)nd);
	                                            return true;
	                                        }
	                                    });
				                spaceSystem.addUnresolvedReference(nr);
				            }
				        } catch (IllegalArgumentException e) {
				            e.printStackTrace();
				            error("non-existing comparison found: " + m.group(2));
				        }		
				    }
			    }
			    // 3) add the restrictioncriteria
			    container.restrictionCriteria = cl;
			} else {
			    if(spaceSystem.getRootSequenceContainer()==null) {
			        spaceSystem.setRootSequenceContainer(container);
			    }
			}
            XtceAliasSet xas=new XtceAliasSet();
            xas.addAlias(MdbMappings.MDB_OPSNAME, opsnamePrefix+container.getName());
            container.setAliasSet(xas);
            
			spaceSystem.addSequenceContainer(container);
		}
	}
	
	/**
	 * Extension point enabling processing additional non-standard sheets. This method is
	 * called after all Parameters and Containers definitions are loaded, and just before
	 * loading the Algorithms.
	 */
	protected void loadNonStandardSheets() {
	    // By default do nothing
	}
	
    private void loadAlgorithms() throws DatabaseLoadException {
        Sheet algo_sheet = workbook.getSheet("Algorithms");
        if (algo_sheet == null) {
            return;
        }

        // start at 1 to not use the first line (= title line)
        int start = 1;
        while(true) {
            // we first search for a row containing (= starting) a new algorithm
            while (start < algo_sheet.getRows()) {
                Cell[] cells = algo_sheet.getRow(start);
                if ((cells.length > 0) && (cells[0].getContents().length() > 0) && !cells[0].getContents().startsWith("#")) {
                    break;
                }
                start++;
            }
            if (start >= algo_sheet.getRows()) {
               break;
            }

            Cell[] cells = algo_sheet.getRow(start);
            String name = cells[IDX_ALGO_NAME].getContents();
            String algorithmText = cells[IDX_ALGO_TEXT].getContents();
            AutoActivateType autoActivate = null;
            if(cells.length>IDX_ALGO_ACTIVATE && !"".equals(cells[IDX_ALGO_ACTIVATE].getContents())) {
                if("Always".equalsIgnoreCase(cells[IDX_ALGO_ACTIVATE].getContents())) {
                    autoActivate = AutoActivateType.ALWAYS;
                } else if("RealtimeOnly".equalsIgnoreCase(cells[IDX_ALGO_ACTIVATE].getContents())) {
                    autoActivate = AutoActivateType.REALTIME_ONLY;
                } else if("ReplayOnly".equalsIgnoreCase(cells[IDX_ALGO_ACTIVATE].getContents())) {
                    autoActivate = AutoActivateType.REPLAY_ONLY;
                } else {
                    error("Auto-activate '"+cells[IDX_ALGO_ACTIVATE].getContents()+"' not supported. Can only go back in time. Use values <= 0.");
                }
            }
            
            // now we search for the matching last row of that algorithm
            int end = start + 1;
            while (end < algo_sheet.getRows()) {
                cells = algo_sheet.getRow(end);
                if (!hasColumn(cells, IDX_ALGO_PARA_INOUT)) {
                    break;
                }
                end++;
            }
            
            Algorithm algorithm = new Algorithm(name);
            algorithm.addAlias(MdbMappings.MDB_OPSNAME, opsnamePrefix+algorithm.getName());
            algorithm.setLanguage("JavaScript");
            // Replace smart-quotes “ and ” with regular quotes "
            algorithm.setAlgorithmText(algorithmText.replaceAll("[\u201c\u201d]", "\""));
            
            algorithm.setAutoActivate(autoActivate);
            
            // In/out params
            for (int j = start+1; j < end; j++) {
                cells = algo_sheet.getRow(j);
                String paraRef = cells[IDX_ALGO_PARA_REF].getContents();
                String paraInout = cells[IDX_ALGO_PARA_INOUT].getContents();
                if ("in".equalsIgnoreCase(paraInout)) {
                    Parameter param = spaceSystem.getParameter(paraRef);
                    final ParameterInstanceRef parameterInstance = new ParameterInstanceRef(null);
                    if(param==null) {
                        NameReference nr=new NameReference(paraRef, Type.PARAMETER, new ResolvedAction() {
                            @Override
                            public boolean resolved(NameDescription nd) {
                                parameterInstance.setParameter((Parameter) nd); 
                                return true;
                            }
                        });
                        spaceSystem.addUnresolvedReference(nr);
                    } else {
                        parameterInstance.setParameter(param);
                    }


                    if (cells.length > IDX_ALGO_PARA_INSTANCE) {
                        if (!"".equals(cells[IDX_ALGO_PARA_INSTANCE].getContents())) {
                            int instance = Integer.valueOf(cells[IDX_ALGO_PARA_INSTANCE].getContents());
                            if (instance > 0) {
                                error("Algorithm:"+(j+1)+" instance '"+instance+"' not supported. Can only go back in time. Use values <= 0.");
                            }
                            parameterInstance.setInstance(instance);
                        }
                    }

                    InputParameter inputParameter = new InputParameter(parameterInstance);
                    if (cells.length > IDX_ALGO_PARA_NAME) {
                        if (!"".equals(cells[IDX_ALGO_PARA_NAME].getContents())) {
                            inputParameter.setInputName(cells[IDX_ALGO_PARA_NAME].getContents());
                        }
                    }
                    algorithm.addInput(inputParameter);
                } else if ("out".equalsIgnoreCase(paraInout)) {
                    Parameter param = spaceSystem.getParameter(paraRef);
                    if (param == null) {
                        throw new DatabaseLoadException("error on line "+(j+1)+" of the Algorithms sheet: the measurement '" + paraRef + "' was not found on the parameters sheet");
                    }
                    OutputParameter outputParameter = new OutputParameter(param);
                    if (cells.length > IDX_ALGO_PARA_NAME) {
                        outputParameter.setOutputName(cells[IDX_ALGO_PARA_NAME].getContents());
                    }
                    algorithm.addOutput(outputParameter);
                } else {
                    error("Algorithm:"+(j+1)+" in/out '"+paraInout+"' not supported. Must be one of 'in' or 'out'");
                }
            }
            spaceSystem.addAlgorithm(algorithm);
            start = end;
        }
   }

	private boolean hasColumn(Cell[] cells, int idx) {
	    return (cells.length>idx) && (cells[idx].getContents()!=null) && (!cells[idx].getContents().equals(""));
	}
	private void error(String s) {
		System.err.println(s);
		System.exit(-1);
	}
	

	/** 
	 */
	private int getSize(Parameter param, SequenceContainer sc) {
		// either we have a Parameter or we have a SequenceContainer, we cannot have both or neither
		if (param != null) {
			DataEncoding de = ((BaseDataType)param.getParameterType()).getEncoding();
			if(de==null) error("Cannot determine the data encoding for "+param.getName());
			if (de instanceof FloatDataEncoding) {
				return de.sizeInBits;
			} else if (de instanceof IntegerDataEncoding) {
				return de.sizeInBits;
			} else	if (de instanceof BinaryDataEncoding) {
				return de.sizeInBits;
			} else	if (de instanceof StringDataEncoding) {
				return -1;
			} else {
				error("no known size for data encoding : " + de);
			}
		} else {
			return sc.sizeInBits;
		}
		// this point should never be reached
		return 0;
	}

	/** If repeat != "", decodes it to either an integer or a parameter and adds it to the SequenceEntry
	 * If repeat is an integer, this integer is returned
	 */
	private int addRepeat(SequenceEntry se, String repeat) {
		if (!repeat.equals("")) {
			se.repeatEntry = new Repeat();
			try {
				int rep = Integer.decode(repeat);
				se.repeatEntry.count = new FixedIntegerValue();
				((FixedIntegerValue)se.repeatEntry.count).value = rep;
				return rep;
			} catch (NumberFormatException e) {
				se.repeatEntry.count = new DynamicIntegerValue();
				Parameter repeatparam=parameters.get(repeat);
				if(repeatparam==null) {
					error("Can not find the parameter for repeat "+repeat);
				}
				((DynamicIntegerValue)se.repeatEntry.count).parameter = repeatparam;	
				return -1;
			}
		} else {
			return 1;
		}
	}
	
	
	   
	class LimitDef {
		public LimitDef(String condition, AlarmRanges ranges) {
			this.condition=condition;
			this.ranges=ranges;
		}
		String condition;
		AlarmRanges ranges;
	}
}

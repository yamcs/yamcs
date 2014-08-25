package org.yamcs.xtce;

import jxl.*;
import jxl.read.biff.BiffException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.xtce.Comparison.OperatorType;
import org.yamcs.xtce.NameReference.ResolvedAction;
import org.yamcs.xtce.NameReference.Type;
import org.yamcs.xtce.SequenceEntry.ReferenceLocationType;
import org.yamcs.xtce.xml.XtceAliasSet;

import java.io.*;
import java.nio.ByteOrder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	protected HashMap<String,EnumerationDefinition> enumerations = new HashMap<String, EnumerationDefinition>();
	protected HashMap<String,Parameter> parameters = new HashMap<String, Parameter>();
	protected HashSet<Parameter> outputParameters = new HashSet<Parameter>(); // Outputs to algorithms
	protected HashSet<PotentialExtractionError> potentialErrors = new HashSet<PotentialExtractionError>();
	
	protected SpreadsheetLoadContext ctx=new SpreadsheetLoadContext();
	
	//sheet names
	protected final static String SHEET_GENERAL="General";
	protected final static String SHEET_CALIBRATION="Calibration";
	protected final static String SHEET_PARAMETERS="Parameters";
	protected final static String SHEET_CONTAINERS="Containers";
	protected final static String SHEET_PROCESSED_PARAMETERS="ProcessedParameters";
	protected final static String SHEET_ALGORITHMS="Algorithms";
	protected final static String SHEET_LIMITS="Limits"; // Deprecated. Use alarms
	protected final static String SHEET_ALARMS="Alarms";
	
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
	final static int IDX_ALGO_TRIGGER=2;
	final static int IDX_ALGO_PARA_INOUT=3;
	final static int IDX_ALGO_PARA_REF=4;
	final static int IDX_ALGO_PARA_INSTANCE=5;
	final static int IDX_ALGO_PARA_NAME=6;
	
	//columns in the alarms sheet
	final static int IDX_ALARM_PARAM_NAME=0;
	final static int IDX_ALARM_CONTEXT=1;
	final static int IDX_ALARM_REPORT=2;
	final static int IDX_ALARM_MIN_VIOLATIONS=3;
	final static int IDX_ALARM_WATCH_TRIGGER=4;
	final static int IDX_ALARM_WATCH_VALUE=5;
	final static int IDX_ALARM_WARNING_TRIGGER=6;
	final static int IDX_ALARM_WARNING_VALUE=7;
	final static int IDX_ALARM_DISTRESS_TRIGGER=8;
	final static int IDX_ALARM_DISTRESS_VALUE=9;
	final static int IDX_ALARM_CRITICAL_TRIGGER=10;
	final static int IDX_ALARM_CRITICAL_VALUE=11;
	final static int IDX_ALARM_SEVERE_TRIGGER=12;
	final static int IDX_ALARM_SEVERE_VALUE=13;
	
	//columns in the processed parameters sheet
	protected final static int IDX_PP_UMI=0;
	protected final static int IDX_PP_GROUP=1;
	protected final static int IDX_PP_ALIAS=2;
	
	// Increment major when breaking backward compatibility, increment minor when making backward compatible changes
	final static String FORMAT_VERSION="2.1";
	// Explicitly support these versions (i.e. load without warning)
	final static String[] FORMAT_VERSIONS_SUPPORTED = new String[]{ "1.6", "1.7", "2.0", FORMAT_VERSION };

	
	protected Workbook workbook;
	protected String opsnamePrefix;
	protected SpaceSystem spaceSystem;
	String path;
	static Logger log=LoggerFactory.getLogger(SpreadsheetLoader.class.getName());
	
	
	/*
	 * configSection is the name under which this config appears in the database
	 */
	public SpreadsheetLoader(String filename) {
	    ctx.file=new File(filename).getName();
        path = filename;
    }
    
	@Override
    public String getConfigName(){
		return ctx.file;
	}

	@Override
    public SpaceSystem load() {
		log.info("Loading spreadsheet " + path);
		
		try {
			// Given path may be relative, so use absolute path to report issues
			File ssFile = new File( path );
			if( !ssFile.exists() ) throw new FileNotFoundException( ssFile.getAbsolutePath() );
			workbook = Workbook.getWorkbook( ssFile );
		} catch (BiffException e) {
			throw new SpreadsheetLoadException(ctx, e);
		} catch (IOException e) {
			throw new SpreadsheetLoadException(ctx, e);
		}
		
		try {
            loadSheets();
		} catch(SpreadsheetLoadException e) {
		    throw e;
		} catch (Exception e) {
		    throw new SpreadsheetLoadException(ctx, e);
		}

		// Check errors after all sheets have been read
		for(PotentialExtractionError e : potentialErrors) {
		    e.recheck();
		}
		
		return spaceSystem;
	}
	
	protected void loadSheets() throws SpreadsheetLoadException {
	    loadGeneralSheet(true);
        loadCalibrationSheet(false);
        loadLimitsSheet(false);
        loadParametersSheet(true);
        loadContainersSheet(true);
        loadProcessedParametersSheet(false);
        loadNonStandardSheets(); // Extension point
        loadAlgorithmsSheet(false);
        loadAlarmsSheet(false);
	}

	@Override
    public boolean needsUpdate(RandomAccessFile consistencyDateFile) throws IOException, ConfigurationException {
		String line;
		while((line=consistencyDateFile.readLine())!=null) {
			if(line.startsWith(ctx.file)) {
				File f=new File(path);
				if(!f.exists()) throw new ConfigurationException("The file "+f.getAbsolutePath()+" doesn't exist");
				SimpleDateFormat sdf=new SimpleDateFormat("yyyy/DDD HH:mm:ss");
				try {
					Date serializedDate=sdf.parse(line.substring(ctx.file.length()+1));
					if(serializedDate.getTime()>=f.lastModified()) {
						log.info("Serialized excel database "+ctx.file+" is up to date.");
						return false;
					} else {
						log.info("Serialized excel database "+ctx.file+" is NOT up to date: serializedDate="+serializedDate+" mdbConsistencyDate="+new Date(f.lastModified()));
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
		consistencyDateFile.write(ctx.file+" "+(new SimpleDateFormat("yyyy/DDD HH:mm:ss")).format(f.lastModified())+"\n");
	}

	
	protected void loadGeneralSheet(boolean required) {
		Sheet sheet=switchToSheet(SHEET_GENERAL, required);
		if(sheet==null)return;
		Cell[] cells=jumpToRow(sheet, 1);
		
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
				log.info( String.format( "Some spreadsheet features for '%s' may not be supported by this loader: Spreadsheet version (%s) differs from loader supported version (%s)", ctx.file, version, FORMAT_VERSION ) );
			}
		}
		if( !supported ) {
			throw new SpreadsheetLoadException(ctx, String.format( "Format version (%s) not supported by loader version (%s)", version, FORMAT_VERSION ) );
		}
		
		String name=requireString(cells, 1);
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
			log.info( "No opsnamePrefix specified for {}", ctx.file );
		}
	}
	
	protected void loadCalibrationSheet(boolean required) {
		 //read the calibrations
	     Sheet sheet=switchToSheet(SHEET_CALIBRATION, required);
		 if(sheet==null) return;
		 
		 double[] pol_coef = null;
		 // SplinePoint = pointpair
		 ArrayList<SplinePoint>spline = null;
		 EnumerationDefinition enumeration = null;
		 // start at 1 to not use the first line (= title line)
		 int start = 1;
		 while(true) {
			 // we first search for a row containing (= starting) a new calibration
			 while (start < sheet.getRows()) {
				 Cell[] cells = jumpToRow(sheet, start);
				 if ((cells.length > 0) && (cells[0].getContents().length() > 0) && !cells[0].getContents().startsWith("#")) {
					 break;
				 }
				 start++;
			 }
			 if (start >= sheet.getRows()) {
				break;
			 }
			 Cell[] cells = jumpToRow(sheet, start);
			 String name = cells[IDX_CALIB_NAME].getContents();
			 String type = cells[IDX_CALIB_TYPE].getContents();
			 
			 // now we search for the matching last row of that calibration
			 int end = start + 1;
			 while (end < sheet.getRows()) {
				 cells = jumpToRow(sheet, end);
				 if (!hasColumn(cells, IDX_CALIB_CALIB1)) {
					 break;
				 }
				 end++;
			 }
			 if ("enumeration".equalsIgnoreCase(type)) {
				 enumeration = new EnumerationDefinition();
			 } else if ("polynomial".equalsIgnoreCase(type)) {
				 pol_coef = new double[end - start];
			 } else if ("pointpair".equalsIgnoreCase(type)) {
				 spline = new ArrayList<SplinePoint>();
			 } else {
				 throw new SpreadsheetLoadException(ctx, "Calibration type '"+type+"' not supported. Supported types: enumeration, polynomial and pointpair");
			 }
			 
			for (int j = start; j < end; j++) {
				cells = jumpToRow(sheet, j);
				if ("enumeration".equalsIgnoreCase(type)) {
					try {
						long raw=Integer.decode(cells[IDX_CALIB_CALIB1].getContents());
						enumeration.valueMap.put(raw, cells[IDX_CALIB_CALIB2].getContents());
					} catch(NumberFormatException e) {
						throw new SpreadsheetLoadException(ctx, "Can't get integer from raw value out of '"+cells[IDX_CALIB_CALIB1].getContents()+"'");
					}
				} else if ("polynomial".equalsIgnoreCase(type)) {
				    pol_coef[j - start] = getNumber(cells[IDX_CALIB_CALIB1]);
				} else if ("pointpair".equalsIgnoreCase(type)) {
					spline.add(new SplinePoint(getNumber(cells[IDX_CALIB_CALIB1]), getNumber(cells[IDX_CALIB_CALIB2])));
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
	private void loadLimitsSheet(boolean required) {
	     Sheet sheet = switchToSheet(SHEET_LIMITS, required);
		 if(sheet==null)return;
		 
		 // start at 1 to not use the first line (= title line)
		 int start = 1;
		 while(true) {
			 // we first search for a row containing (= starting) a new limit
			 while (start < sheet.getRows()) {
				 Cell[] cells = jumpToRow(sheet, start);
				 if ((cells.length > 0) && (cells[0].getContents().length() > 0)) {
					 break;
				 }
				 start++;
			 }
			 if (start >= sheet.getRows()) {
				 break;
			 }
			 Cell[] cells = jumpToRow(sheet, start);
			 String name = cells[0].getContents();
			 // now we search for the matching last row of the limit
			 int end = start + 1;
			 while (end < sheet.getRows()) {
				 cells = jumpToRow(sheet, end);
				 if (isRowEmpty(cells) || (cells[0].getContents().length() != 0)) {
					 break;
				 }
				 end++;
			 }

			for (int j = start; j < end; j++) {
				cells = jumpToRow(sheet, j);
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
	}
	
	private boolean isRowEmpty(Cell[] cells) {
		for(int i=0;i<cells.length;i++) 
			if(cells[i].getContents().length()>0) return false;
		return true;
	}
	
	protected void loadParametersSheet(boolean required) {
		Sheet sheet = switchToSheet(SHEET_PARAMETERS, required);
		if(sheet==null)return;
		
		for (int i = 1; i < sheet.getRows(); i++) {
			Cell[] cells = jumpToRow(sheet, i);
			if ((cells == null) || (cells.length < 3) || cells[0].getContents().startsWith("#")) {
				continue;
			}
			String name = cells[IDX_PARAM_OPSNAME].getContents();
			if (name.length() == 0) {
				continue;
			}
			
			final Parameter param = new Parameter(name);
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
				throw new SpreadsheetLoadException(ctx, "Can't get bitlength out of '"+cells[IDX_PARAM_BITLENGTH].getContents()+"'");
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
			} else if ("uint64".equalsIgnoreCase(engtype)) {
                ptype = new IntegerParameterType(name);
                ((IntegerParameterType)ptype).signed = false;
                ((IntegerParameterType)ptype).setSizeInBits(64);
			} else if ("int".equalsIgnoreCase(engtype)) {
				ptype = new IntegerParameterType(name);
			} else if("int64".equalsIgnoreCase(engtype)) {
			    ptype = new IntegerParameterType(name);
			    ((IntegerParameterType)ptype).setSizeInBits(64);
			} else if ("float".equalsIgnoreCase(engtype)) {
				ptype = new FloatParameterType(name);
			} else if ("double".equalsIgnoreCase(engtype)) {
			    ptype = new FloatParameterType(name);
			    ((FloatParameterType)ptype).setSizeInBits(64);
			} else if ("enumerated".equalsIgnoreCase(engtype)) {
				if(calib==null) {
					throw new SpreadsheetLoadException(ctx, "Parameter " + name + " has to have an enumeration");
				}
				EnumerationDefinition enumeration = enumerations.get(calib);
				if (enumeration == null) {
					throw new SpreadsheetLoadException(ctx, "Parameter " + name + " is supposed to have an enumeration '" + calib + "' but the enumeration does not exist");
				}
				ptype = new EnumeratedParameterType(calib);
				for (Entry<Long,String> entry:enumeration.valueMap.entrySet()) {
				    ((EnumeratedParameterType) ptype).addEnumerationValue(entry.getKey(), entry.getValue());
				}
			} else if ("string".equalsIgnoreCase(engtype)) {
				ptype = new StringParameterType(name);
			} else if ("boolean".equalsIgnoreCase(engtype)) {
			    ptype = new BooleanParameterType(name);
			} else	if ("binary".equalsIgnoreCase(engtype)) {
				ptype = new BinaryParameterType(name);
			} else {
				throw new SpreadsheetLoadException(ctx, "Unknown parameter type " + engtype);
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
			    if(bitlength==-1) {
			        potentialErrors.add(new PotentialExtractionError(ctx, "Bit length is mandatory for integer parameters") {
                        @Override
                        public boolean errorPersists() {
                            return !outputParameters.contains(param);
                        }
			        });
			    }
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
									throw new SpreadsheetLoadException(ctx, "Unsupported signed integer representation: "+intRepresentation);	
								}
							}
						}
					}
				}
				if ((!"enumerated".equalsIgnoreCase(engtype)) && (calib!=null)) {
					Calibrator c = calibrators.get(calib);
					if (c == null) {
						throw new SpreadsheetLoadException(ctx, "Parameter " + name + " is supposed to have a calibrator '" + calib + "' but the calibrator does not exist");
					}
					((IntegerDataEncoding)encoding).defaultCalibrator = c;
				}
			} else if ("bytestream".equalsIgnoreCase(rawtype)) {
			    if(bitlength==-1) throw new SpreadsheetLoadException(ctx, "Bit length is mandatory for bytestream parameters");
				encoding=new BinaryDataEncoding(name, bitlength);
            } else if ("boolean".equalsIgnoreCase(rawtype)) {
                if(bitlength!=-1) throw new SpreadsheetLoadException(ctx, "Bit length is not allowed for boolean parameters (defaults to 1). Use any other raw type if you want to specify the bitlength");
                encoding=new BooleanDataEncoding(name);
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
				if(bitlength==-1) throw new SpreadsheetLoadException(ctx, "Bit length is mandatory for fixedstring raw type");
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
							throw new SpreadsheetLoadException(ctx, "Could not parse specified base 16 terminator from "+rawtype);
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
							throw new SpreadsheetLoadException(ctx, "Could not parse integer size from "+rawtype);
						}
					}
				}
			} else if ("float".equalsIgnoreCase(rawtype)) {
			    if(bitlength==-1) {
    			    potentialErrors.add(new PotentialExtractionError(ctx, "Bit length is mandatory for float parameters") {
                        @Override
                        public boolean errorPersists() {
                            return !outputParameters.contains(param);
                        }
                    });
			    }
				encoding=new FloatDataEncoding(name, bitlength);
				if(calib!=null) {
					Calibrator c = calibrators.get(calib);
					if (c == null) {
						throw new SpreadsheetLoadException(ctx, "Parameter " + name + " is supposed to have a calibrator '" + calib + "' but the calibrator does not exist.");
					} else {
						((FloatDataEncoding)encoding).defaultCalibrator = c;
					}
				}
			} else {
				throw new SpreadsheetLoadException(ctx, "Unknown raw type " + rawtype);
			}
			
			if (ptype instanceof IntegerParameterType) {
				// Integers can be encoded as strings
				if( encoding instanceof StringDataEncoding ) {
					// Create a new int encoding which uses the configured string encoding
					IntegerDataEncoding intStringEncoding = new IntegerDataEncoding(name, ((StringDataEncoding)encoding));
					if( calib != null ) {
						Calibrator c = calibrators.get(calib);
						if( c == null ) {
							throw new SpreadsheetLoadException(ctx, "Parameter " + name + " specified calibrator '" + calib + "' but the calibrator does not exist");
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
							throw new SpreadsheetLoadException(ctx, "Parameter " + name + " specified calibrator '" + calib + "' but the calibrator does not exist.");
						} else {
							floatStringEncoding.defaultCalibrator = c;
						}
					}
					((FloatParameterType)ptype).encoding = floatStringEncoding;
				} else {
					((FloatParameterType)ptype).encoding = encoding;
				}
			} else if (ptype instanceof EnumeratedParameterType) {
			    if(((EnumeratedParameterType) ptype).getEncoding() != null) {
			        // Some other param has already led to setting the encoding of this shared ptype.
			        // Do some basic consistency checks
			        if(((EnumeratedParameterType) ptype).getEncoding().getSizeInBits() != encoding.getSizeInBits()) {
			            throw new SpreadsheetLoadException(ctx, "Multiple parameters are sharing calibrator '"+calib+"' with different bit sizes.");
			        }
			    }
			    
			    // Enumerations encoded as string integers
                if( encoding instanceof StringDataEncoding ) {
                    IntegerDataEncoding intStringEncoding = new IntegerDataEncoding(name, ((StringDataEncoding)encoding));
                    // Don't set calibrator, already done when making ptype
                    ((EnumeratedParameterType) ptype).encoding = intStringEncoding;
                } else {
                    ((EnumeratedParameterType) ptype).encoding = encoding;
                }
			} else if (ptype instanceof StringParameterType) {
			    ((StringParameterType)ptype).encoding = encoding;
			} else if (ptype instanceof BooleanParameterType) {
			    ((BooleanParameterType)ptype).encoding = encoding;
			}

			param.setParameterType(ptype);
		}
		
/*		System.out.println("got parameters:");
		for (Parameter p: parameters.values()) {
			System.out.println(p);
		}
*/	}

	
	private void loadParameterLimits(ParameterType ptype, Cell[] cells) {
	    String warnMins=null;
		if(hasColumn(cells, IDX_PARAM_LOWWARNILIMIT)) {
    		warnMins=cells[IDX_PARAM_LOWWARNILIMIT].getContents();
    		//System.out.println("limits.length="+limits.size()+"limits: "+limits.keySet() +"mins="+mins);
    		if(limits.containsKey(warnMins)) { //limit is specified on the separate sheet
    			for(LimitDef ld:limits.get(warnMins)) {
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
    		}
		}
		String warnMaxs=null;
	    if(hasColumn(cells, IDX_PARAM_HIGHWARNILIMIT) && ((ptype instanceof IntegerParameterType) || (ptype instanceof FloatParameterType))) {
            warnMaxs=cells[IDX_PARAM_HIGHWARNILIMIT].getContents();		        
		}
		
		String criticalMins=null;
	    if(hasColumn(cells, IDX_PARAM_LOWCRITICALLIMIT) && ((ptype instanceof IntegerParameterType) || (ptype instanceof FloatParameterType))) {
	        criticalMins=cells[IDX_PARAM_LOWCRITICALLIMIT].getContents();
	    }
	    
	    String criticalMaxs=null;
        if(hasColumn(cells, IDX_PARAM_HIGHCRITICALLIMIT) && ((ptype instanceof IntegerParameterType) || (ptype instanceof FloatParameterType))) {
            criticalMaxs=cells[IDX_PARAM_HIGHCRITICALLIMIT].getContents();
        }
	    
	    if(warnMins!=null || warnMaxs!=null) {
		    double min=(warnMins!=null&&!limits.containsKey(warnMins))?Double.parseDouble(warnMins):Double.NEGATIVE_INFINITY;
		    double max=(warnMaxs!=null)?Double.parseDouble(warnMaxs):Double.POSITIVE_INFINITY;
		    setDefaultWarningAlarmRange(ptype,new FloatRange(min,max));
		}
		
		if(criticalMins!=null || criticalMaxs!=null) {
            double min=(criticalMins!=null)?Double.parseDouble(criticalMins):Double.NEGATIVE_INFINITY;
            double max=(criticalMaxs!=null)?Double.parseDouble(criticalMaxs):Double.POSITIVE_INFINITY;
			setDefaultCriticalAlarmRange(ptype, new FloatRange(min,max));
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
	
	protected void loadContainersSheet(boolean required) {
		Sheet sheet = switchToSheet(SHEET_CONTAINERS, required);
		if(sheet==null)return;
		
		HashMap<String, SequenceContainer> containers = new HashMap<String, SequenceContainer>();
		
		for (int i = 1; i < sheet.getRows(); i++) {
			// search for a new packet definition, starting from row i 
		    //  (explanatory note, i is incremented inside this loop too, and that's why the following 4 lines work)
			Cell[] cells = jumpToRow(sheet, i);
			if (cells == null || cells.length<1) {
			    log.debug("Ignoring line {} because it's empty",ctx.row);
			    continue;
			}
			if(cells[0].getContents().equals("")|| cells[0].getContents().startsWith("#")) {
			    log.debug("Ignoring line {} because first cell is empty or starts with '#'", ctx.row);
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
			        throw new SpreadsheetLoadException(ctx, "Parent specified but without inheritance condition on container");
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
			while (!end && (i < sheet.getRows())) {
				
				// get the next row, containing a measurement/aggregate reference
				cells = jumpToRow(sheet, i);
				// determine whether we have not reached the end of the packet definition.
				if ((cells == null) || (cells.length <= IDX_CONT_RELPOS) || cells[IDX_CONT_RELPOS].getContents().equals("")) {
					end = true; continue;
				}

				// extract a few variables, for further use
				String flags = cells[IDX_CONT_FLAGS].getContents();
				String paraname = cells[IDX_CONT_PARA_NAME].getContents();
				if((cells.length<=IDX_CONT_RELPOS)||cells[IDX_CONT_RELPOS].getContents().equals("")) {
				    throw new SpreadsheetLoadException(ctx, "relpos is not specified for "+paraname+" on line "+(i+1));
				}
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
							throw new SpreadsheetLoadException(ctx, "Little endian not supported for parameter "+param);
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
					    throw new SpreadsheetLoadException(ctx, "The measurement/container '" + paraname + "' was not found in the parameters or containers map");
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
			    if(!"".equals(condition)) {
			        container.restrictionCriteria=toMatchCriteria(condition);
			    }
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
	
	protected void loadProcessedParametersSheet(boolean required) {
	    loadProcessedParametersSheet(required, IDX_PP_ALIAS);
	}
	
	protected void loadProcessedParametersSheet(boolean required, int firstAliasColumnIndex) {
        Sheet sheet = switchToSheet(SHEET_PROCESSED_PARAMETERS, required);
        if(sheet==null)return;
        
        // Each row must specify a umi and an alias, with a group being optional
        // The same umi may have many aliases, but a single alias value must
        // be unique
        
        // Alias keys are the extra column names
        Cell [] header_cells = jumpToRow(sheet, 0);
        if( header_cells.length <= firstAliasColumnIndex ) {
            throw new SpreadsheetLoadException(ctx, "No aliases defined in ProcessedParameters sheet: Must have at least three columns including umi and group columns.");
        }
        
        log.info( "Spreadsheet has {} PP definition rows to be parsed", sheet.getRows() );
        
        for (int i = 1; i < sheet.getRows(); i++) {
            Cell[] cells = jumpToRow(sheet, i);
            if ((cells == null) || (cells.length < firstAliasColumnIndex) || cells[0].getContents().startsWith("#")) {
                log.debug( "Ignoring line {} because it is empty, starts with #, or has < 3 cells populated", i );
                continue;
            }
            String umi = cells[IDX_PP_UMI].getContents();
            if (umi.length() == 0) {
                log.debug( "Ignoring line {} because the UMI column is empty", i );
                continue;
            }
            String group = cells[IDX_PP_GROUP].getContents();
            
            XtceAliasSet xtceAlias = new XtceAliasSet();
            for( int alias_index = firstAliasColumnIndex; alias_index < cells.length; alias_index++ ) {
                String alias = cells[ alias_index ].getContents();
                if( ! "".equals( alias ) ) {
                    if( alias_index > header_cells.length ) {
                        throw new SpreadsheetLoadException(ctx, "Alias entry on line "+i+" does not have namespace specified in first row of column.");
                    }
                    log.debug( "Got alias '{}' with value '{}'", header_cells[ alias_index ].getContents(), alias );
                    xtceAlias.addAlias( header_cells[ alias_index ].getContents(), alias );
                }
            }
            
            Parameter ppDef=new Parameter(umi);
            ppDef.setRecordingGroup(group);
            ppDef.setAliasSet( xtceAlias );

            log.debug( "Adding PP definition '{}'", ppDef.getName() );
            spaceSystem.addParameter( ppDef );
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
	
    protected void loadAlgorithmsSheet(boolean required) {
        Sheet sheet = switchToSheet(SHEET_ALGORITHMS, required);
        if (sheet == null) return;

        // start at 1 to not use the first line (= title line)
        int start = 1;
        while(true) {
            // we first search for a row containing (= starting) a new algorithm
            while (start < sheet.getRows()) {
                Cell[] cells = jumpToRow(sheet, start);
                if ((cells.length > 0) && (cells[0].getContents().length() > 0) && !cells[0].getContents().startsWith("#")) {
                    break;
                }
                start++;
            }
            if (start >= sheet.getRows()) {
               break;
            }

            Cell[] cells = jumpToRow(sheet, start);
            String name = cells[IDX_ALGO_NAME].getContents();
            String algorithmText = cells[IDX_ALGO_TEXT].getContents();
            
            // now we search for the matching last row of that algorithm
            int end = start + 1;
            while (end < sheet.getRows()) {
                cells = jumpToRow(sheet, end);
                if (!hasColumn(cells, IDX_ALGO_PARA_REF)) {
                    break;
                }
                end++;
            }
            
            Algorithm algorithm = new Algorithm(name);
            algorithm.addAlias(MdbMappings.MDB_OPSNAME, opsnamePrefix+algorithm.getName());
            algorithm.setLanguage("JavaScript");
            // Replace smart-quotes “ and ” with regular quotes "
            algorithm.setAlgorithmText(algorithmText.replaceAll("[\u201c\u201d]", "\""));
            
            // In/out params
            String paraInout=null;
            Set<String> inputParameterRefs=new HashSet<String>();
            for (int j = start+1; j < end; j++) {
                cells = jumpToRow(sheet, j);
                String paraRef = cells[IDX_ALGO_PARA_REF].getContents();
                if(hasColumn(cells, IDX_ALGO_PARA_INOUT)) {
                    paraInout=cells[IDX_ALGO_PARA_INOUT].getContents();
                }
                if(paraInout==null) throw new SpreadsheetLoadException(ctx, "You must specify in/out attribute for this parameter");
                if ("in".equalsIgnoreCase(paraInout)) {
                    inputParameterRefs.add(paraRef);
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
                                throw new SpreadsheetLoadException(ctx, "Instance '"+instance+"' not supported. Can only go back in time. Use values <= 0.");
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
                        throw new SpreadsheetLoadException(ctx, "The measurement '" + paraRef + "' was not found on the parameters sheet");
                    }
                    outputParameters.add(param);
                    OutputParameter outputParameter = new OutputParameter(param);
                    if (cells.length > IDX_ALGO_PARA_NAME) {
                        outputParameter.setOutputName(cells[IDX_ALGO_PARA_NAME].getContents());
                    }
                    algorithm.addOutput(outputParameter);
                } else {
                    throw new SpreadsheetLoadException(ctx, "In/out '"+paraInout+"' not supported. Must be one of 'in' or 'out'");
                }
            }
            
            // Add trigger conditions
            final TriggerSetType triggerSet = new TriggerSetType();
            Pattern PARAMETER_PATTERN=Pattern.compile("OnParameterUpdate\\((.*)\\)");
            Pattern FIRERATE_PATTERN=Pattern.compile("OnPeriodicRate\\((\\d+)\\)");
            cells = jumpToRow(sheet, start); // Jump back to algorithm row (for getting error msgs right)
            String triggerText = hasColumn(cells, IDX_ALGO_TRIGGER) ? cells[IDX_ALGO_TRIGGER].getContents() : "";
            if(!"".equals(triggerText)) {
                if(triggerText.startsWith("OnParameterUpdate")) {
                    Matcher matcher = PARAMETER_PATTERN.matcher(triggerText);
                    if(matcher.matches()) {
                        for(String s:matcher.group(1).split(",")) {
                            Parameter para = spaceSystem.getParameter(s.trim());
                            if(para!=null) {
                                OnParameterUpdateTrigger trigger = new OnParameterUpdateTrigger(para);
                                triggerSet.addOnParameterUpdateTrigger(trigger);
                            } else {
                                NameReference nr=new NameReference(s.trim(), Type.PARAMETER,
                                        new ResolvedAction() {
                                            @Override
                                            public boolean resolved(NameDescription nd) {
                                                OnParameterUpdateTrigger trigger = new OnParameterUpdateTrigger((Parameter) nd);
                                                triggerSet.addOnParameterUpdateTrigger(trigger);
                                                return true;
                                            }
                                        });
                                spaceSystem.addUnresolvedReference(nr);
                            }
                        }
                    } else {
                        throw new SpreadsheetLoadException(ctx, "Wrongly formatted OnParameterUpdate trigger");
                    }
                } else if(triggerText.startsWith("OnPeriodicRate")) {
                    Matcher matcher = FIRERATE_PATTERN.matcher(triggerText);
                    if(matcher.matches()) {
                        long fireRateMs = Long.parseLong(matcher.group(1), 10);
                        OnPeriodicRateTrigger trigger=new OnPeriodicRateTrigger(fireRateMs);
                        triggerSet.addOnPeriodicRateTrigger(trigger);
                    } else {
                        throw new SpreadsheetLoadException(ctx, "Wrongly formatted OnPeriodicRate trigger");
                    }
                } else {
                    throw new SpreadsheetLoadException(ctx, "Trigger '"+triggerText+"' not supported.");
                }
            } else {
                // default to all in parameters
                for(String paraRef:inputParameterRefs) {
                    Parameter para=spaceSystem.getParameter(paraRef);
                    if(para!=null) {
                        triggerSet.addOnParameterUpdateTrigger(new OnParameterUpdateTrigger(para));
                    } else {
                        NameReference nr=new NameReference(paraRef, Type.PARAMETER,
                                new ResolvedAction() {
                                    @Override
                                    public boolean resolved(NameDescription nd) {
                                        OnParameterUpdateTrigger trigger = new OnParameterUpdateTrigger((Parameter) nd);
                                        triggerSet.addOnParameterUpdateTrigger(trigger);
                                        return true;
                                    }
                                });
                        spaceSystem.addUnresolvedReference(nr);
                    }
                }
            }
            algorithm.setTriggerSet(triggerSet);
            
            spaceSystem.addAlgorithm(algorithm);
            start = end;
        }
    }
    
    protected void loadAlarmsSheet(boolean required) {
        Sheet sheet = switchToSheet(SHEET_ALARMS, required);
        if (sheet == null) return;

        // start at 1 to not use the first line (= title line)
        int start = 1;
        while(true) {
            // we first search for a row containing (= starting) a new alarm
            while (start < sheet.getRows()) {
                Cell[] cells = jumpToRow(sheet, start);
                if ((cells.length > 0) && (cells[0].getContents().length() > 0) && !cells[0].getContents().startsWith("#")) {
                    break;
                }
                start++;
            }
            if (start >= sheet.getRows()) {
               break;
            }

            Cell[] cells = jumpToRow(sheet, start);
            if(!hasColumn(cells, IDX_ALARM_PARAM_NAME)) {
                throw new SpreadsheetLoadException(ctx, "Alarms must be attached to a parameter name");
            }
            String paramName = cells[IDX_ALARM_PARAM_NAME].getContents();
            Parameter para = spaceSystem.getParameter(paramName);
            if(para == null) {
                throw new SpreadsheetLoadException(ctx, "Could not find a parameter named "+paramName);
            }
            
            // now we search for the matching last row of the alarms for this parameter
            int paramEnd = start + 1;
            while (paramEnd < sheet.getRows()) {
                cells = jumpToRow(sheet, paramEnd);
                if (hasColumn(cells, IDX_ALARM_PARAM_NAME)) {
                    break;
                }
                paramEnd++;
            }
            
            // Iterate over all rows for this parameter
            MatchCriteria previousContext=null;
            int minViolations=1;
            AlarmReportType reportType=AlarmReportType.ON_SEVERITY_CHANGE;
            for (int j = start; j < paramEnd; j++) {
                cells = jumpToRow(sheet, j);
                MatchCriteria context=previousContext;
                if(hasColumn(cells, IDX_ALARM_CONTEXT)) {
                    String contextString = cells[IDX_ALARM_CONTEXT].getContents();
                    context=toMatchCriteria(contextString);
                }
                
                if(hasColumn(cells, IDX_ALARM_MIN_VIOLATIONS)) {
                    minViolations=Integer.parseInt(cells[IDX_ALARM_MIN_VIOLATIONS].getContents());
                } else {
                    minViolations=1;
                }
                
                if(hasColumn(cells, IDX_ALARM_REPORT)) {
                    if("OnSeverityChange".equalsIgnoreCase(cells[IDX_ALARM_REPORT].getContents())) {
                        reportType=AlarmReportType.ON_SEVERITY_CHANGE;
                    } else if("OnValueChange".equalsIgnoreCase(cells[IDX_ALARM_REPORT].getContents())) {
                        reportType=AlarmReportType.ON_VALUE_CHANGE;
                    } else {
                        throw new SpreadsheetLoadException(ctx, "Unrecognized report type '"+cells[IDX_ALARM_REPORT].getContents()+"'");
                    }
                }
                
                if(hasColumn(cells, IDX_ALARM_WATCH_TRIGGER) && hasColumn(cells, IDX_ALARM_WATCH_VALUE)) {
                    String trigger=cells[IDX_ALARM_WATCH_TRIGGER].getContents();
                    String triggerValue=cells[IDX_ALARM_WATCH_VALUE].getContents();
                    if(para.getParameterType() instanceof IntegerParameterType) {
                        IntegerParameterType ipt=(IntegerParameterType)para.getParameterType();
                        if("low".equals(trigger)) {
                            ipt.addWatchAlarmRange(context, new FloatRange(Double.parseDouble(triggerValue),Double.POSITIVE_INFINITY));
                        } else if("high".equals(trigger)) {
                            ipt.addWatchAlarmRange(context, new FloatRange(Double.NEGATIVE_INFINITY, Double.parseDouble(triggerValue)));
                        } else {
                            throw new SpreadsheetLoadException(ctx, "Unexpected trigger type '"+trigger+"' for numeric parameter "+para.getName());
                        }
                    } else if(para.getParameterType() instanceof FloatParameterType) {
                        FloatParameterType fpt=(FloatParameterType)para.getParameterType();
                        if("low".equals(trigger)) {
                            fpt.addWatchAlarmRange(context, new FloatRange(Double.parseDouble(triggerValue),Double.POSITIVE_INFINITY));
                        } else if("high".equals(trigger)) {
                            fpt.addWatchAlarmRange(context, new FloatRange(Double.NEGATIVE_INFINITY, Double.parseDouble(triggerValue)));
                        } else {
                            throw new SpreadsheetLoadException(ctx, "Unexpected trigger type '"+trigger+"' for numeric parameter "+para.getName());
                        }
                    } else if(para.getParameterType() instanceof EnumeratedParameterType) {
                        EnumeratedParameterType ept=(EnumeratedParameterType)para.getParameterType();
                        if("state".equals(trigger)) {
                            ValueEnumeration enumValue=ept.enumValue(triggerValue);
                            if(enumValue==null) {
                                throw new SpreadsheetLoadException(ctx, "Unknown enumeration value '"+triggerValue+"' for alarm of enumerated parameter "+para.getName());
                            } else {
                                ept.addAlarm(context, enumValue, AlarmLevels.watch);
                            }
                        } else {
                            throw new SpreadsheetLoadException(ctx, "Unexpected trigger type '"+trigger+"' for alarm of enumerated parameter "+para.getName());
                        }
                    }
                }
                if(hasColumn(cells, IDX_ALARM_WARNING_TRIGGER) && hasColumn(cells, IDX_ALARM_WARNING_VALUE)) {
                    String trigger=cells[IDX_ALARM_WARNING_TRIGGER].getContents();
                    String triggerValue=cells[IDX_ALARM_WARNING_VALUE].getContents();
                    if(para.getParameterType() instanceof IntegerParameterType) {
                        IntegerParameterType ipt=(IntegerParameterType)para.getParameterType();
                        if("low".equals(trigger)) {
                            ipt.addWarningAlarmRange(context, new FloatRange(Double.parseDouble(triggerValue),Double.POSITIVE_INFINITY));
                        } else if("high".equals(trigger)) {
                            ipt.addWarningAlarmRange(context, new FloatRange(Double.NEGATIVE_INFINITY, Double.parseDouble(triggerValue)));
                        } else {
                            throw new SpreadsheetLoadException(ctx, "Unexpected trigger type '"+trigger+"' for numeric parameter "+para.getName());
                        }
                    } else if(para.getParameterType() instanceof FloatParameterType) {
                        FloatParameterType fpt=(FloatParameterType)para.getParameterType();
                        if("low".equals(trigger)) {
                            fpt.addWarningAlarmRange(context, new FloatRange(Double.parseDouble(triggerValue),Double.POSITIVE_INFINITY));
                        } else if("high".equals(trigger)) {
                            fpt.addWarningAlarmRange(context, new FloatRange(Double.NEGATIVE_INFINITY, Double.parseDouble(triggerValue)));
                        } else {
                            throw new SpreadsheetLoadException(ctx, "Unexpected trigger type '"+trigger+"' for numeric parameter "+para.getName());
                        }
                    } else if(para.getParameterType() instanceof EnumeratedParameterType) {
                        EnumeratedParameterType ept=(EnumeratedParameterType)para.getParameterType();
                        if("state".equals(trigger)) {
                            ValueEnumeration enumValue=ept.enumValue(triggerValue);
                            if(enumValue==null) {
                                throw new SpreadsheetLoadException(ctx, "Unknown enumeration value '"+triggerValue+"' for alarm of enumerated parameter "+para.getName());
                            } else {
                                ept.addAlarm(context, enumValue, AlarmLevels.warning);
                            }
                        } else {
                            throw new SpreadsheetLoadException(ctx, "Unexpected trigger type '"+trigger+"' for enumerated parameter "+para.getName());
                        }
                    }
                }
                if(hasColumn(cells, IDX_ALARM_DISTRESS_TRIGGER) && hasColumn(cells, IDX_ALARM_DISTRESS_VALUE)) {
                    String trigger=cells[IDX_ALARM_DISTRESS_TRIGGER].getContents();
                    String triggerValue=cells[IDX_ALARM_DISTRESS_VALUE].getContents();
                    if(para.getParameterType() instanceof IntegerParameterType) {
                        IntegerParameterType ipt=(IntegerParameterType)para.getParameterType();
                        if("low".equals(trigger)) {
                            ipt.addDistressAlarmRange(context, new FloatRange(Double.parseDouble(triggerValue),Double.POSITIVE_INFINITY));
                        } else if("high".equals(trigger)) {
                            ipt.addDistressAlarmRange(context, new FloatRange(Double.NEGATIVE_INFINITY, Double.parseDouble(triggerValue)));
                        } else {
                            throw new SpreadsheetLoadException(ctx, "Unexpected trigger type '"+trigger+"' for numeric parameter "+para.getName());
                        }
                    } else if(para.getParameterType() instanceof FloatParameterType) {
                        FloatParameterType fpt=(FloatParameterType)para.getParameterType();
                        if("low".equals(trigger)) {
                            fpt.addDistressAlarmRange(context, new FloatRange(Double.parseDouble(triggerValue),Double.POSITIVE_INFINITY));
                        } else if("high".equals(trigger)) {
                            fpt.addDistressAlarmRange(context, new FloatRange(Double.NEGATIVE_INFINITY, Double.parseDouble(triggerValue)));
                        } else {
                            throw new SpreadsheetLoadException(ctx, "Unexpected trigger type '"+trigger+"' for numeric parameter "+para.getName());
                        }
                    } else if(para.getParameterType() instanceof EnumeratedParameterType) {
                        EnumeratedParameterType ept=(EnumeratedParameterType)para.getParameterType();
                        if("state".equals(trigger)) {
                            ValueEnumeration enumValue=ept.enumValue(triggerValue);
                            if(enumValue==null) {
                                throw new SpreadsheetLoadException(ctx, "Unknown enumeration value '"+triggerValue+"' for alarm of enumerated parameter "+para.getName());
                            } else {
                                ept.addAlarm(context, enumValue, AlarmLevels.distress);
                            }
                        } else {
                            throw new SpreadsheetLoadException(ctx, "Unexpected trigger type '"+trigger+"' for enumerated parameter "+para.getName());
                        }
                    }
                }
                if(hasColumn(cells, IDX_ALARM_CRITICAL_TRIGGER) && hasColumn(cells, IDX_ALARM_CRITICAL_VALUE)) {
                    String trigger=cells[IDX_ALARM_CRITICAL_TRIGGER].getContents();
                    String triggerValue=cells[IDX_ALARM_CRITICAL_VALUE].getContents();
                    if(para.getParameterType() instanceof IntegerParameterType) {
                        IntegerParameterType ipt=(IntegerParameterType)para.getParameterType();
                        if("low".equals(trigger)) {
                            ipt.addCriticalAlarmRange(context, new FloatRange(Double.parseDouble(triggerValue),Double.POSITIVE_INFINITY));
                        } else if("high".equals(trigger)) {
                            ipt.addCriticalAlarmRange(context, new FloatRange(Double.NEGATIVE_INFINITY, Double.parseDouble(triggerValue)));
                        } else {
                            throw new SpreadsheetLoadException(ctx, "Unexpected trigger type '"+trigger+"' for numeric parameter "+para.getName());
                        }
                    } else if(para.getParameterType() instanceof FloatParameterType) {
                        FloatParameterType fpt=(FloatParameterType)para.getParameterType();
                        if("low".equals(trigger)) {
                            fpt.addCriticalAlarmRange(context, new FloatRange(Double.parseDouble(triggerValue),Double.POSITIVE_INFINITY));
                        } else if("high".equals(trigger)) {
                            fpt.addCriticalAlarmRange(context, new FloatRange(Double.NEGATIVE_INFINITY, Double.parseDouble(triggerValue)));
                        } else {
                            throw new SpreadsheetLoadException(ctx, "Unexpected trigger type '"+trigger+"' for numeric parameter "+para.getName());
                        }
                    } else if(para.getParameterType() instanceof EnumeratedParameterType) {
                        EnumeratedParameterType ept=(EnumeratedParameterType)para.getParameterType();
                        if("state".equals(trigger)) {
                            ValueEnumeration enumValue=ept.enumValue(triggerValue);
                            if(enumValue==null) {
                                throw new SpreadsheetLoadException(ctx, "Unexpected trigger type '"+trigger+"' for enumerated parameter "+para.getName());
                            } else {
                                ept.addAlarm(context, enumValue, AlarmLevels.critical);
                            }
                        } else {
                            throw new SpreadsheetLoadException(ctx, "Unexpected trigger type '"+trigger+"' for alarm of enumerated parameter "+para.getName());
                        }
                    }
                }
                if(hasColumn(cells, IDX_ALARM_SEVERE_TRIGGER) && hasColumn(cells, IDX_ALARM_SEVERE_VALUE)) {
                    String trigger=cells[IDX_ALARM_SEVERE_TRIGGER].getContents();
                    String triggerValue=cells[IDX_ALARM_SEVERE_VALUE].getContents();
                    if(para.getParameterType() instanceof IntegerParameterType) {
                        IntegerParameterType ipt=(IntegerParameterType)para.getParameterType();
                        if("low".equals(trigger)) {
                            ipt.addSevereAlarmRange(context, new FloatRange(Double.parseDouble(triggerValue),Double.POSITIVE_INFINITY));
                        } else if("high".equals(trigger)) {
                            ipt.addSevereAlarmRange(context, new FloatRange(Double.NEGATIVE_INFINITY, Double.parseDouble(triggerValue)));
                        } else {
                            throw new SpreadsheetLoadException(ctx, "Unexpected trigger type '"+trigger+"' for numeric parameter "+para.getName());
                        }
                    } else if(para.getParameterType() instanceof FloatParameterType) {
                        FloatParameterType fpt=(FloatParameterType)para.getParameterType();
                        if("low".equals(trigger)) {
                            fpt.addSevereAlarmRange(context, new FloatRange(Double.parseDouble(triggerValue),Double.POSITIVE_INFINITY));
                        } else if("high".equals(trigger)) {
                            fpt.addSevereAlarmRange(context, new FloatRange(Double.NEGATIVE_INFINITY, Double.parseDouble(triggerValue)));
                        } else {
                            throw new SpreadsheetLoadException(ctx, "Unexpected trigger type '"+trigger+"' for numeric parameter "+para.getName());
                        }
                    } else if(para.getParameterType() instanceof EnumeratedParameterType) {
                        EnumeratedParameterType ept=(EnumeratedParameterType)para.getParameterType();
                        if("state".equals(trigger)) {
                            ValueEnumeration enumValue=ept.enumValue(triggerValue);
                            if(enumValue==null) {
                                throw new SpreadsheetLoadException(ctx, "Unknown enumeration value '"+triggerValue+"' for alarm of enumerated parameter "+para.getName());
                            } else {
                                ept.addAlarm(context, enumValue, AlarmLevels.severe);
                            }
                        } else {
                            throw new SpreadsheetLoadException(ctx, "Unexpected trigger type '"+trigger+"' for enumerated parameter "+para.getName());
                        }
                    }
                }
                
                // Set minviolations and alarmreporttype
                AlarmType alarm=null;
                if(para.getParameterType() instanceof IntegerParameterType) {
                    IntegerParameterType ipt=(IntegerParameterType)para.getParameterType();
                    alarm=(context==null)?ipt.getDefaultAlarm():ipt.getNumericContextAlarm(context);
                    if(reportType != AlarmType.DEFAULT_REPORT_TYPE) {
                        ipt.createOrGetAlarm(context).setAlarmReportType(reportType);
                    }
                } else if(para.getParameterType() instanceof FloatParameterType) {
                    FloatParameterType fpt=(FloatParameterType)para.getParameterType();
                    alarm=(context==null)?fpt.getDefaultAlarm():fpt.getNumericContextAlarm(context);
                    if(reportType != AlarmType.DEFAULT_REPORT_TYPE) {
                        fpt.createOrGetAlarm(context).setAlarmReportType(reportType);
                    }
                } else if(para.getParameterType() instanceof EnumeratedParameterType) {
                    EnumeratedParameterType ept=(EnumeratedParameterType)para.getParameterType();
                    alarm=(context==null)?ept.getDefaultAlarm():ept.getContextAlarm(context);
                    if(reportType != AlarmType.DEFAULT_REPORT_TYPE) {
                        ept.createOrGetAlarm(context).setAlarmReportType(reportType);
                    }
                }
                if(alarm!=null) { // It's possible that this gets called multiple times per alarm, but doesn't matter
                    alarm.setMinViolations(minViolations);
                    alarm.setAlarmReportType(reportType);
                }
                
                previousContext=context;
            }
            
            start = paramEnd;
        }
    }
    
    private MatchCriteria toMatchCriteria(String criteriaString) {
        if(criteriaString.contains(";")) {
            ComparisonList cl = new ComparisonList();
            String splitted[] = criteriaString.split(";");
            for (String part: splitted) {
                cl.comparisons.add(toComparison(part));
            }
            return cl;
        } else {
            return toComparison(criteriaString);
        }
    }
    
    private Comparison toComparison(String comparisonString) {
        Matcher m = Pattern.compile("(.*?)(=|!=|<=|>=|<|>)(.*)").matcher(comparisonString);
        if(!m.matches()) throw new SpreadsheetLoadException(ctx, "Cannot parse condition '"+comparisonString+"'");
        String pname=m.group(1).trim();
        String op=m.group(2);
        String value=m.group(3).trim();
        
        if ("=".equals(op)) {
            op="==";
        }
        OperatorType opType=Comparison.stringToOperator(op);
        if(opType==null) {
            throw new SpreadsheetLoadException(ctx, "Unknown operator '"+op+"'");
        }
        
        Parameter compareParam = spaceSystem.getParameter(pname);
        if (compareParam != null) {
            ParameterInstanceRef pref=new ParameterInstanceRef(compareParam, false);
            try {
                return new Comparison(pref, Integer.decode(value), opType);
            } catch(NumberFormatException e) {
                pref.setUseCalibratedValue(true);
                return new Comparison(pref, value, opType);
            }
        } else {
            final ParameterInstanceRef pref=new ParameterInstanceRef(false);
            Comparison ucomp;
            try {
                ucomp=new Comparison(pref, Integer.decode(value), opType);
            } catch(NumberFormatException e) {
                pref.setUseCalibratedValue(true);
                ucomp=new Comparison(pref, value, opType);
            }
            spaceSystem.addUnresolvedReference(new NameReference(pname,
                    Type.PARAMETER, new ResolvedAction() {
                        @Override
                        public boolean resolved(NameDescription nd) {
                            pref.setParameter((Parameter) nd);
                            return true;
                        }
                    }));
            return ucomp;
        }
    }

	protected boolean hasColumn(Cell[] cells, int idx) {
	    return (cells.length>idx) && (cells[idx].getContents()!=null) && (!cells[idx].getContents().equals(""));
	}
	
	private int getSize(Parameter param, SequenceContainer sc) {
		// either we have a Parameter or we have a SequenceContainer, we cannot have both or neither
		if (param != null) {
			DataEncoding de = ((BaseDataType)param.getParameterType()).getEncoding();
			if(de==null) throw new SpreadsheetLoadException(ctx, "Cannot determine the data encoding for "+param.getName());
			if (de instanceof FloatDataEncoding) {
				return de.sizeInBits;
			} else if (de instanceof IntegerDataEncoding) {
				return de.sizeInBits;
			} else	if (de instanceof BinaryDataEncoding) {
				return de.sizeInBits;
			} else	if (de instanceof StringDataEncoding) {
				return -1;
			} else if (de instanceof BooleanDataEncoding) {
			    return de.sizeInBits;
			} else {
				throw new SpreadsheetLoadException(ctx, "No known size for data encoding : " + de);
			}
		} else {
			return sc.sizeInBits;
		}
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
					throw new SpreadsheetLoadException(ctx, "Cannot find the parameter for repeat "+repeat);
				}
				((DynamicIntegerValue)se.repeatEntry.count).parameter = repeatparam;	
				return -1;
			}
		} else {
			return 1;
		}
	}
	
	protected Sheet switchToSheet(String sheetName, boolean required) {
        Sheet sheet = workbook.getSheet(sheetName);
        ctx.sheet=sheetName;
        ctx.row=0;
        if (required && sheet==null) {
            throw new SpreadsheetLoadException(ctx, "Required sheet '"+sheetName+"' was found missing");
        }
        return sheet;
	}
	
	protected Cell[] jumpToRow(Sheet sheet, int row) {
	    ctx.row=row+1;
	    return sheet.getRow(row);
	}
	
	protected String requireString(Cell[] cells, int column) {
        String contents = cells[column].getContents();
        if("".equals(contents)) {
            char col=(char) ('A'+(char)column);
            throw new SpreadsheetLoadException(ctx, "Cell at "+col+ctx.row+" is required");
        }
        return contents;
	}
	
	private static class LimitDef {
		public LimitDef(String condition, AlarmRanges ranges) {
			this.condition=condition;
			this.ranges=ranges;
		}
		String condition;
		AlarmRanges ranges;
	}
	
    /**
     * Temporary value holder for the enumeration definition (because
     * enumerations are read before parameters, and reading sharing the same EPT
     * among all parameters is not a good approach (think different alarm
     * definitions)
     */
    protected static class EnumerationDefinition {
        public LinkedHashMap<Long,String> valueMap=new LinkedHashMap<Long,String>();
    }
	
	/**
	 * Anomaly that maybe turns out to be fine, when more sheets of the spreadsheet have been read. 
	 */
	private abstract class PotentialExtractionError {
        private SpreadsheetLoadException exc;
	    PotentialExtractionError(SpreadsheetLoadContext ctx, String error) {
	        exc=new SpreadsheetLoadException(ctx, error);
	    }

	    abstract boolean errorPersists();
	    
	    public void recheck() {
	        if(errorPersists()) {
	            throw exc;
	        }
	    }
	}
}

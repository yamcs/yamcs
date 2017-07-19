package org.yamcs.xtce;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.yamcs.YConfiguration;
import org.yamcs.utils.DoubleRange;
import org.yamcs.utils.StringConverter;
import org.yamcs.xtce.CheckWindow.TimeWindowIsRelativeToType;
import org.yamcs.xtce.CommandVerifier.TerminationAction;
import org.yamcs.xtce.NameReference.Type;
import org.yamcs.xtce.SequenceEntry.ReferenceLocationType;
import org.yamcs.xtce.xml.XtceAliasSet;
import org.yamcs.xtceproc.JavaExpressionCalibratorFactory;

import com.google.common.primitives.UnsignedLongs;

import jxl.Cell;
import jxl.CellType;
import jxl.DateCell;
import jxl.NumberCell;
import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;

/**
 * This class loads database from excel spreadsheets. Used for the Solar instruments for which the TM
 * database is too complicated to store in the MDB.
 *
 * @author nm, ddw
 *
 */
public class SpreadsheetLoader extends AbstractFileLoader {
    protected HashMap<String, Calibrator> calibrators = new HashMap<>();
    protected HashMap<String, String> javaFormulas = new HashMap<>();

    protected HashMap<String, EnumerationDefinition> enumerations = new HashMap<>();
    protected HashMap<String, Parameter> parameters = new HashMap<>();
    protected HashSet<Parameter> outputParameters = new HashSet<>(); // Outputs to algorithms
    protected HashSet<PotentialExtractionError> potentialErrors = new HashSet<>();

    final protected SpreadsheetLoadContext ctx = new SpreadsheetLoadContext();

    final ConditionParser conditionParser = new ConditionParser(ctx);
    //sheet names
    protected static final String SHEET_GENERAL = "General";
    protected static final String SHEET_CHANGELOG = "ChangeLog";

    protected static final String SHEET_CALIBRATION = "Calibration";
    protected static final String SHEET_TELEMETERED_PARAMETERS = "Parameters";
    protected static final String SHEET_LOCAL_PARAMETERS = "LocalParameters";
    protected static final String SHEET_DERIVED_PARAMETERS = "DerivedParameters";
    protected static final String SHEET_CONTAINERS = "Containers";

    protected static final String SHEET_ALGORITHMS = "Algorithms";
    protected static final String SHEET_ALARMS = "Alarms";
    protected static final String SHEET_COMMANDS = "Commands";
    protected static final String SHEET_COMMANDOPTIONS = "CommandOptions";
    protected static final String SHEET_COMMANDVERIFICATION = "CommandVerification";
    //the list of sheets that can be part of subsystems with a sub1/sub2/sub3/SheetName notation
    static String[] SUBSYSTEM_SHEET_NAMES = {SHEET_CALIBRATION, SHEET_TELEMETERED_PARAMETERS, SHEET_LOCAL_PARAMETERS, SHEET_DERIVED_PARAMETERS, 
            SHEET_CONTAINERS, SHEET_ALGORITHMS, SHEET_ALARMS, SHEET_COMMANDS, SHEET_COMMANDOPTIONS, SHEET_COMMANDVERIFICATION};

    //columns in the parameters sheet (including local parameters)
    static final int IDX_PARAM_NAME = 0;
    static final int IDX_PARAM_BITLENGTH = 1;
    static final int IDX_PARAM_RAWTYPE = 2;
    static final int IDX_PARAM_ENGTYPE = 3;
    static final int IDX_PARAM_ENGUNIT = 4;
    static final int IDX_PARAM_CALIBRATION = 5;
    static final int IDX_PARAM_DESCRIPTION = 6;


    //columns in the containers sheet
    static final int IDX_CONT_NAME = 0;
    static final int IDX_CONT_PARENT = 1;
    static final int IDX_CONT_CONDITION = 2;
    static final int IDX_CONT_FLAGS = 3;
    static final int IDX_CONT_PARA_NAME = 4;
    static final int IDX_CONT_RELPOS = 5;
    static final int IDX_CONT_SIZEINBITS = 6;
    static final int IDX_CONT_EXPECTED_INTERVAL = 7;
    static final int IDX_CONT_DESCRIPTION = 8;

    //columns in calibrations sheet
    static final int IDX_CALIB_NAME = 0;
    static final int IDX_CALIB_TYPE = 1;
    static final int IDX_CALIB_CALIB1 = 2;
    static final int IDX_CALIB_CALIB2 = 3;

    //columns in the algorithms sheet
    static final int IDX_ALGO_NAME = 0;
    static final int IDX_ALGO_LANGUGAGE = 1;
    static final int IDX_ALGO_TEXT = 2;
    static final int IDX_ALGO_TRIGGER = 3;
    static final int IDX_ALGO_PARA_INOUT=4;
    static final int IDX_ALGO_PARA_REF = 5;
    static final int IDX_ALGO_PARA_INSTANCE = 6;
    static final int IDX_ALGO_PARA_NAME = 7;
    static final int IDX_ALGO_PARA_FLAGS = 8;

    //columns in the alarms sheet
    static final int IDX_ALARM_PARAM_NAME = 0;
    static final int IDX_ALARM_CONTEXT = 1;
    static final int IDX_ALARM_REPORT = 2;
    static final int IDX_ALARM_MIN_VIOLATIONS = 3;
    static final int IDX_ALARM_WATCH_TRIGGER = 4;
    static final int IDX_ALARM_WATCH_VALUE = 5;
    static final int IDX_ALARM_WARNING_TRIGGER = 6;
    static final int IDX_ALARM_WARNING_VALUE = 7;
    static final int IDX_ALARM_DISTRESS_TRIGGER = 8;
    static final int IDX_ALARM_DISTRESS_VALUE = 9;
    static final int IDX_ALARM_CRITICAL_TRIGGER = 10;
    static final int IDX_ALARM_CRITICAL_VALUE = 11;
    static final int IDX_ALARM_SEVERE_TRIGGER = 12;
    static final int IDX_ALARM_SEVERE_VALUE = 13;

    //columns in the processed parameters sheet
    protected static final int IDX_PP_UMI = 0;
    protected static final int IDX_PP_GROUP = 1;
    protected static final int IDX_PP_ALIAS = 2;

    //columns in the command sheet
    protected static final int IDX_CMD_NAME = 0;
    protected static final int IDX_CMD_PARENT = 1;
    protected static final int IDX_CMD_ARG_ASSIGNMENT = 2;
    protected static final int IDX_CMD_FLAGS = 3;
    protected static final int IDX_CMD_ARGNAME = 4;
    protected static final int IDX_CMD_RELPOS = 5;
    protected static final int IDX_CMD_SIZEINBITS = 6;
    protected static final int IDX_CMD_ENGTYPE = 7;
    protected static final int IDX_CMD_RAWTYPE = 8;
    protected static final int IDX_CMD_DEFVALUE = 9;
    protected static final int IDX_CMD_ENGUNIT = 10;
    protected static final int IDX_CMD_CALIBRATION = 11;
    protected static final int IDX_CMD_RANGELOW = 12;
    protected static final int IDX_CMD_RANGEHIGH = 13;
    protected static final int IDX_CMD_DESCRIPTION = 14;


    //columns in the command options sheet
    protected static final int IDX_CMDOPT_NAME = 0;
    protected static final int IDX_CMDOPT_TXCONST = 1;
    protected static final int IDX_CMDOPT_TXCONST_TIMEOUT = 2;
    protected static final int IDX_CMDOPT_SIGNIFICANCE = 3;
    protected static final int IDX_CMDOPT_SIGNIFICANCE_REASON = 4;

    //columns in the command verification sheet
    protected static final int IDX_CMDVERIF_NAME = 0;
    protected static final int IDX_CMDVERIF_STAGE = 1;
    protected static final int IDX_CMDVERIF_TYPE = 2;
    protected static final int IDX_CMDVERIF_TEXT = 3;
    protected static final int IDX_CMDVERIF_CHECKWINDOW = 4;
    protected static final int IDX_CMDVERIF_CHECKWINDOW_RELATIVETO = 5;
    protected static final int IDX_CMDVERIF_ONSUCCESS = 6;
    protected static final int IDX_CMDVERIF_ONFAIL = 7;
    protected static final int IDX_CMDVERIF_ONTIMEOUT = 8;

    //columns in the changelog sheet
    protected static final int IDX_LOG_VERSION = 0;
    protected static final int IDX_LOG_DATE = 1;
    protected static final int IDX_LOG_MESSAGE = 2;

    protected static final String CALIB_TYPE_ENUMERATION = "enumeration";
    protected static final String CALIB_TYPE_POLYNOMIAL = "polynomial";
    protected static final String CALIB_TYPE_SPLINE = "spline";
    protected static final String CALIB_TYPE_JAVA_EXPRESSION = "java-expression";

    protected static final String PARAM_ENGTYPE_STRING = "string";
    protected static final String PARAM_ENGTYPE_BOOLEAN = "boolean";
    protected static final String PARAM_ENGTYPE_BINARY = "binary";
    protected static final String PARAM_ENGTYPE_ENUMERATED = "enumerated";
    protected static final String PARAM_ENGTYPE_DOUBLE = "double";
    protected static final String PARAM_ENGTYPE_UINT32 = "uint32";
    protected static final String PARAM_ENGTYPE_INT32 = "int32";
    protected static final String PARAM_ENGTYPE_UINT64 = "uint64";
    protected static final String PARAM_ENGTYPE_INT64 = "int64";
    protected static final String PARAM_ENGTYPE_FLOAT = "float";

    static final Pattern ALGO_PARAMETER_PATTERN = Pattern.compile("OnParameterUpdate\\((.*)\\)");
    static final Pattern ALGO_FIRERATE_PATTERN = Pattern.compile("OnPeriodicRate\\((\\d+)\\)");


    // Increment major when breaking backward compatibility, increment minor when making backward compatible changes
    final static String FORMAT_VERSION="5.4";
    // Explicitly support these versions (i.e. load without warning)
    final static String[] FORMAT_VERSIONS_SUPPORTED = new String[]{FORMAT_VERSION, "5.3"};
    String fileFormatVersion;


    protected Workbook workbook;
    protected SpaceSystem rootSpaceSystem;


    boolean enableAliasReferences = false;
    boolean enableXtceNameRestrictions = true; 


    public SpreadsheetLoader(Map<String, Object> config) {
        this(YConfiguration.getString(config, "file"));
        enableAliasReferences = YConfiguration.getBoolean(config, "enableAliasReferences", false);
        enableXtceNameRestrictions =  YConfiguration.getBoolean(config, "enableXtceNameRestrictions", true);
    }


    public SpreadsheetLoader(String filename) {
        super(filename);
        ctx.file = new File(filename).getName();
    }

    @Override
    public String getConfigName(){
        return ctx.file;
    }

    @Override
    public SpaceSystem load() {
        log.info("Loading spreadsheet {}", path);

        try {
            // Given path may be relative, so use absolute path to report issues
            File ssFile = new File(path);
            if(!ssFile.exists()) {
                throw new FileNotFoundException(ssFile.getAbsolutePath());
            }
            WorkbookSettings ws = new WorkbookSettings();
            ws.setEncoding("Cp1252");
            workbook = Workbook.getWorkbook(ssFile, ws);
        } catch (BiffException|IOException e) {
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

        return rootSpaceSystem;
    }

    protected void loadSheets() throws SpreadsheetLoadException {
        loadGeneralSheet(true);
        loadChangelogSheet(false);


        //filter all sheets with names ending in the standard names SUBSYSTEM_SHEET_NAMES
        List<String> relevantSheets =  Arrays.stream(workbook.getSheetNames()).filter(sheetName -> {
            return Arrays.stream(SUBSYSTEM_SHEET_NAMES).filter(s -> sheetName.endsWith(s)).findAny().isPresent();
        }).collect(Collectors.toList());

        //create all subsystems
        for(String s: relevantSheets) {
            String[] a = s.split("\\|");
            SpaceSystem ss = rootSpaceSystem;
            for(int i=0; i<a.length-1; i++) {
                SpaceSystem ss1 =  ss.getSubsystem(a[i]);
                if(ss1==null) {
                    log.debug("Creating subsystem '{}'", a[i]);
                    ss1 = new SpaceSystem(a[i]);
                    ss.addSpaceSystem(ss1);
                }
                ss = ss1;
            }
        }

        loadSpaceSystem("", rootSpaceSystem);
    }

    protected void loadSpaceSystem(String sheetNamePrefix, SpaceSystem spaceSystem) {

        loadCalibrationSheet(spaceSystem, sheetNamePrefix + SHEET_CALIBRATION);
        loadParametersSheet(spaceSystem, sheetNamePrefix + SHEET_TELEMETERED_PARAMETERS, DataSource.TELEMETERED);
        loadParametersSheet(spaceSystem, sheetNamePrefix + SHEET_DERIVED_PARAMETERS, DataSource.DERIVED);
        loadParametersSheet(spaceSystem, sheetNamePrefix + SHEET_LOCAL_PARAMETERS, DataSource.LOCAL);
        loadContainersSheet(spaceSystem, sheetNamePrefix + SHEET_CONTAINERS);
        loadAlgorithmsSheet(spaceSystem, sheetNamePrefix + SHEET_ALGORITHMS);
        loadAlarmsSheet(spaceSystem, sheetNamePrefix + SHEET_ALARMS);
        loadCommandSheet(spaceSystem, sheetNamePrefix + SHEET_COMMANDS);
        loadCommandOptionsSheet(spaceSystem, sheetNamePrefix + SHEET_COMMANDOPTIONS);
        loadCommandVerificationSheet(spaceSystem, sheetNamePrefix + SHEET_COMMANDVERIFICATION);

        for(SpaceSystem ss: spaceSystem.getSubSystems()) {
            String prefix = sheetNamePrefix.isEmpty()?ss.getName()+"|": sheetNamePrefix+ss.getName()+"|";
            loadSpaceSystem(prefix, ss);
        }
    }


    protected void loadGeneralSheet(boolean required) {
        Sheet sheet = switchToSheet(SHEET_GENERAL, required);
        if(sheet==null){
            return;
        }
        Cell[] cells=jumpToRow(sheet, 1);

        // Version check
        String version = cells[0].getContents();
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
                log.info("Some spreadsheet features for '{}' may not be supported by this loader: "
                        + "Spreadsheet version (%) differs from loader supported version (%s)", ctx.file, version, FORMAT_VERSION);
            }
        }
        if( !supported ) {
            throw new SpreadsheetLoadException(ctx, String.format( "Format version (%s) not supported by loader version (%s)", version, FORMAT_VERSION ) );
        }
        fileFormatVersion = version;

        String name=requireString(cells, 1);
        rootSpaceSystem = new SpaceSystem(name);

        // Add a header
        Header header = new Header();
        rootSpaceSystem.setHeader( header );
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
    }

    protected void loadCalibrationSheet(SpaceSystem spaceSystem, String sheetName) {
        //read the calibrations
        Sheet sheet = switchToSheet(sheetName, false);
        if(sheet==null) {
            return;
        }

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
            if ("pointpair".equalsIgnoreCase(type)) {
                type = CALIB_TYPE_SPLINE;
            }
            if (CALIB_TYPE_ENUMERATION.equalsIgnoreCase(type)) {
                enumeration = new EnumerationDefinition();
            } else if (CALIB_TYPE_POLYNOMIAL.equalsIgnoreCase(type)) {
                pol_coef = new double[end - start];
            } else if (CALIB_TYPE_SPLINE.equalsIgnoreCase(type)) {
                spline = new ArrayList<>();
            } else if (CALIB_TYPE_JAVA_EXPRESSION.equalsIgnoreCase(type)) {
                cells = jumpToRow(sheet, start);
                if(end!=start+1) {
                    throw new SpreadsheetLoadException(ctx, "Java formula must be specified on one line");
                }
                if(isEmpty(cells[IDX_CALIB_CALIB1])) {
                    throw new SpreadsheetLoadException(ctx, "Java formula must be specified on the CALIB1 column");
                }
                String javaFormula = cells[IDX_CALIB_CALIB1].getContents();
                javaFormulas.put(name, javaFormula);
                start = end;
            } else {
                throw new SpreadsheetLoadException(ctx, "Calibration type '"+type+"' not supported. Supported types: "
                        +Arrays.asList(CALIB_TYPE_ENUMERATION, CALIB_TYPE_POLYNOMIAL, CALIB_TYPE_SPLINE, CALIB_TYPE_JAVA_EXPRESSION));
            }

            for (int j = start; j < end; j++) {
                cells = jumpToRow(sheet, j);
                if (CALIB_TYPE_ENUMERATION.equalsIgnoreCase(type)) {
                    try {
                        long raw=Integer.decode(cells[IDX_CALIB_CALIB1].getContents());
                        enumeration.valueMap.put(raw, cells[IDX_CALIB_CALIB2].getContents());
                    } catch(NumberFormatException e) {
                        throw new SpreadsheetLoadException(ctx, "Can't get integer from raw value out of '"+cells[IDX_CALIB_CALIB1].getContents()+"'");
                    }
                } else if (CALIB_TYPE_POLYNOMIAL.equalsIgnoreCase(type)) {
                    pol_coef[j - start] = getNumber(cells[IDX_CALIB_CALIB1]);
                } else if (CALIB_TYPE_SPLINE.equalsIgnoreCase(type)) {
                    spline.add(new SplinePoint(getNumber(cells[IDX_CALIB_CALIB1]), getNumber(cells[IDX_CALIB_CALIB2])));
                }
            }
            if (CALIB_TYPE_ENUMERATION.equalsIgnoreCase(type)) {
                enumerations.put(name, enumeration);
            } else if (CALIB_TYPE_POLYNOMIAL.equalsIgnoreCase(type)) {
                calibrators.put(name, new PolynomialCalibrator(pol_coef));
            } else if (CALIB_TYPE_SPLINE.equalsIgnoreCase(type)) {
                calibrators.put(name, new SplineCalibrator(spline));
            } 
            start = end;
        }
    }

    private double getNumber(Cell cell) {
        if((cell.getType()==CellType.NUMBER) || (cell.getType()==CellType.NUMBER_FORMULA)) {
            return ((NumberCell) cell).getValue();
        } else {
            return Double.parseDouble(cell.getContents());
        }
    }

    protected void loadParametersSheet(SpaceSystem spaceSystem, String sheetName, DataSource dataSource) {
        Sheet sheet = switchToSheet(sheetName, false);
        if(sheet==null){
            return;
        }
        Cell[] firstRow = jumpToRow(sheet, 0);
        for (int i = 1; i < sheet.getRows(); i++) {
            Cell[] cells = jumpToRow(sheet, i);
            if ((cells == null) || (cells.length < 3) || cells[0].getContents().startsWith("#")) {
                continue;
            }
            String name = cells[IDX_PARAM_NAME].getContents();
            if (name.length() == 0) {
                continue;
            }

            validateNameType(name);
            final Parameter param = new Parameter(name);
            parameters.put(param.getName(), param);

            XtceAliasSet xas = getAliases(firstRow, cells);
            if(xas!=null) {
                param.setAliasSet(xas);
            }
            spaceSystem.addParameter(param);

            String rawtype = cells[IDX_PARAM_RAWTYPE].getContents();
            if("DerivedValue".equalsIgnoreCase(rawtype)) {
                continue;
            }
            int bitlength=-1;
            try {
                String bitls=cells[IDX_PARAM_BITLENGTH].getContents();
                if(!bitls.isEmpty()) {
                    bitlength = Integer.decode(bitls);
                }
            } catch(NumberFormatException e) {
                throw new SpreadsheetLoadException(ctx, "Can't get bitlength out of '"+cells[IDX_PARAM_BITLENGTH].getContents()+"'");
            }
            String engtype = cells[IDX_PARAM_ENGTYPE].getContents();
            String calib=null;
            if(hasColumn(cells, IDX_PARAM_CALIBRATION)) {
                calib = cells[IDX_PARAM_CALIBRATION].getContents();
            }
            if(hasColumn(cells, IDX_PARAM_DESCRIPTION)) {
                String shortDescription = cells[IDX_PARAM_DESCRIPTION].getContents();
                param.setShortDescription(shortDescription);
            }
            if("n".equals(calib) || "".equals(calib)){
                calib=null;
            } else if("y".equalsIgnoreCase(calib)) {
                calib=name;
            }
            if("uint".equalsIgnoreCase(engtype)) {
                engtype = PARAM_ENGTYPE_UINT32;
            } else if("int".equalsIgnoreCase(engtype)) {
                engtype = PARAM_ENGTYPE_INT32;
            } 

            ParameterType ptype = null;
            if (PARAM_ENGTYPE_UINT32.equalsIgnoreCase(engtype)) {
                ptype = new IntegerParameterType(name);
                ((IntegerParameterType)ptype).signed = false;
            } else if (PARAM_ENGTYPE_UINT64.equalsIgnoreCase(engtype)) {
                ptype = new IntegerParameterType(name);
                ((IntegerParameterType)ptype).signed = false;
                ((IntegerParameterType)ptype).setSizeInBits(64);
            } else if (PARAM_ENGTYPE_INT32.equalsIgnoreCase(engtype)) {
                ptype = new IntegerParameterType(name);
            } else if(PARAM_ENGTYPE_INT64.equalsIgnoreCase(engtype)) {
                ptype = new IntegerParameterType(name);
                ((IntegerParameterType)ptype).setSizeInBits(64);
            } else if (PARAM_ENGTYPE_FLOAT.equalsIgnoreCase(engtype)) {
                ptype = new FloatParameterType(name);
            } else if (PARAM_ENGTYPE_DOUBLE.equalsIgnoreCase(engtype)) {
                ptype = new FloatParameterType(name);
                ((FloatParameterType)ptype).setSizeInBits(64);
            } else if (PARAM_ENGTYPE_ENUMERATED.equalsIgnoreCase(engtype)) {
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
            } else if (PARAM_ENGTYPE_STRING.equalsIgnoreCase(engtype)) {
                ptype = new StringParameterType(name);
            } else if (PARAM_ENGTYPE_BOOLEAN.equalsIgnoreCase(engtype)) {
                ptype = new BooleanParameterType(name);
            } else if (PARAM_ENGTYPE_BINARY.equalsIgnoreCase(engtype)) {
                ptype = new BinaryParameterType(name);
            } else {
                if(engtype.isEmpty()) {
                    throw new SpreadsheetLoadException(ctx, "No engineering type specified");
                } else {
                    throw new SpreadsheetLoadException(ctx, "Unknown parameter type '" + engtype+"'");
                }
            }

            String units=null;
            if(cells.length>IDX_PARAM_ENGUNIT) {
                units = cells[IDX_PARAM_ENGUNIT].getContents();
            }
            if(!"".equals(units) && units != null && ptype instanceof BaseDataType) {
                UnitType unitType = new UnitType(units);
                ((BaseDataType) ptype).addUnit(unitType);
            }

            DataEncoding encoding = getDataEncoding(ctx, param, rawtype, engtype, bitlength, calib);

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
                    FloatDataEncoding floatStringEncoding = new FloatDataEncoding(((StringDataEncoding)encoding) );
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
            param.setDataSource(dataSource);
        }
    }

    DataEncoding getDataEncoding(SpreadsheetLoadContext ctx, Parameter param, String rawtype, String engtype, int bitlength, String calib) {
        String name = param.getName();

        DataEncoding encoding = null;
        if (("uint".equalsIgnoreCase(rawtype)) || rawtype.toLowerCase().startsWith("int")) {
            encoding = new IntegerDataEncoding(bitlength);
            if (rawtype.toLowerCase().startsWith("int")) {
                if ("int".equals(rawtype)) {
                    ((IntegerDataEncoding)encoding).encoding = IntegerDataEncoding.Encoding.twosComplement;
                } else {
                    int startBracket = rawtype.indexOf('(');
                    if (startBracket != -1) {
                        int endBracket = rawtype.indexOf(')', startBracket);
                        if (endBracket != -1) {
                            String intRepresentation = rawtype.substring(startBracket+1, endBracket).trim().toLowerCase();
                            if ("2c".equals(intRepresentation)) {
                                ((IntegerDataEncoding)encoding).encoding = IntegerDataEncoding.Encoding.twosComplement;
                            } else if ("si".equals(intRepresentation)) {
                                ((IntegerDataEncoding)encoding).encoding = IntegerDataEncoding.Encoding.signMagnitude;
                            } else {
                                throw new SpreadsheetLoadException(ctx, "Unsupported signed integer representation: "+intRepresentation);
                            }
                        }
                    }
                }
            }
            if ((!PARAM_ENGTYPE_ENUMERATED.equalsIgnoreCase(engtype)) && (calib!=null)) {
                Calibrator c = calibrators.get(calib);
                if (c == null) {
                    String msg = "Parameter " + name + " is supposed to have a calibrator '" + calib + "' but the calibrator does not exist.";
                    if(enumerations.containsKey(calib)) {
                        msg+=" Instead an enumeration with this name exists.";
                    }
                    throw new SpreadsheetLoadException(ctx, msg);
                }
                ((IntegerDataEncoding)encoding).defaultCalibrator = c;
            }
        } else if ("bytestream".equalsIgnoreCase(rawtype)) {
            if(bitlength==-1) {
                throw new SpreadsheetLoadException(ctx, "Bit length is mandatory for bytestream parameters");
            }
            encoding = new BinaryDataEncoding(name, bitlength);
        } else if ("boolean".equalsIgnoreCase(rawtype)) {
            if(bitlength!=-1) {
                throw new SpreadsheetLoadException(ctx, "Bit length is not allowed for boolean parameters (defaults to 1). Use any other raw type if you want to specify the bitlength");
            }
            encoding=new BooleanDataEncoding();
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
            if(bitlength==-1) {
                throw new SpreadsheetLoadException(ctx, "Bit length is mandatory for fixedstring raw type");
            }
            encoding = new StringDataEncoding(name, StringDataEncoding.SizeType.Fixed);
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
            encoding = new FloatDataEncoding(bitlength);
            if((!PARAM_ENGTYPE_ENUMERATED.equalsIgnoreCase(engtype)) && calib!=null) {
                Calibrator c = getFloatCalibrator(name, calib);
                ((FloatDataEncoding)encoding).defaultCalibrator = c;
            }
        } else {
            // Raw type is optional if the parameter is not part of a container
            // However a calibration is associated to a raw type
            if(calib != null) {
                throw new SpreadsheetLoadException(ctx, "Parameter " + name + ": calibration specified without raw type");
            }
        }
        return encoding;
    }

    private Calibrator getFloatCalibrator(String paraName, String calibName) {
        Calibrator c = calibrators.get(calibName);
        if (c != null) {
            return c;
        }
        String jf = javaFormulas.get(calibName);
        if(jf!=null) {
            JavaExpressionCalibrator jec = new JavaExpressionCalibrator(jf);
            try {
                JavaExpressionCalibratorFactory.compile(jec);
            } catch (IllegalArgumentException e) {
                throw new SpreadsheetLoadException(ctx, e.getMessage());
            }
            return jec;
        }

        throw new SpreadsheetLoadException(ctx, "Parameter " + paraName + " is supposed to have a calibrator '" + calibName + "' but the calibrator does not exist.");
    }
    /**
     * Searches firstRow for all cells that start with "namespace:" and adds corresponding aliases
     *
     * @param firstRow
     * @param cells
     * @param xas
     */
    private XtceAliasSet getAliases(Cell[] firstRow, Cell[] cells) {
        int n = Math.min(firstRow.length, cells.length);
        XtceAliasSet xas = null;
        for(int i=0; i<n; i++) {
            if(!isEmpty(firstRow[i]) && firstRow[i].getContents().startsWith("namespace:")
                    && !isEmpty(cells[i])) {
                if(xas==null) {
                    xas = new XtceAliasSet();
                }
                String s = firstRow[i].getContents();
                String namespace = s.substring(10, s.length());
                String alias = cells[i].getContents();
                xas.addAlias(namespace, alias);
            }
        }
        return xas;
    }

    boolean isEmpty(Cell cell) {
        return (cell==null) || (cell.getContents().isEmpty());
    }

    protected void loadContainersSheet(SpaceSystem spaceSystem, String sheetName) {
        Sheet sheet = switchToSheet(sheetName, false);
        if(sheet==null){
            return;
        }

        HashMap<String, SequenceContainer> containers = new HashMap<String, SequenceContainer>();
        HashMap<String, String> parents = new HashMap<String, String>();
        Cell[] firstRow = jumpToRow(sheet, 0);

        for (int i = 1; i < sheet.getRows(); i++) {
            // search for a new packet definition, starting from row i
            //  (explanatory note, i is incremented inside this loop too, and that's why the following 4 lines work)
            Cell[] cells = jumpToRow(sheet, i);
            if (cells == null || cells.length<1) {
                log.trace("Ignoring line {} because it's empty",ctx.row);
                continue;
            }
            if(cells[0].getContents().equals("")|| cells[0].getContents().startsWith("#")) {
                log.trace("Ignoring line {} because first cell is empty or starts with '#'", ctx.row);
                continue;
            }
            // at this point, cells contains the data (name, path, ...) of either
            //		a) a sub-container (inherits from another packet)
            //		b) an aggregate container (which will be used as if it were a measurement, by other (sub)containers)
            String name = cells[IDX_CONT_NAME].getContents();
            String parent=null;
            String condition=null;
            if(cells.length>IDX_CONT_PARENT) {
                parent = cells[IDX_CONT_PARENT].getContents();
                if(cells.length<=IDX_CONT_CONDITION) {
                    throw new SpreadsheetLoadException(ctx, "Parent specified but without inheritance condition on container");
                }
                condition = cells[IDX_CONT_CONDITION].getContents();
                parents.put(name,  parent);
            }

            if("".equals(parent)) {
                parent=null;
            }

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
                rate = new RateInStream(-1, expint);
            }

            String description="";
            if(hasColumn(cells, IDX_CONT_DESCRIPTION)) {
                description = cells[IDX_CONT_DESCRIPTION].getContents();
            }

            // create a new SequenceContainer that will hold the parameters (i.e. SequenceEntries) for the ORDINARY/SUB/AGGREGATE packets, and register that new SequenceContainer in the containers hashmap
            SequenceContainer container = new SequenceContainer(name);
            container.sizeInBits=containerSizeInBits;
            containers.put(name, container);
            container.setRateInStream(rate);
            if( !description.isEmpty()) {
                container.setShortDescription(description);
                container.setLongDescription(description);
            }

            if(hasColumn(cells, IDX_CONT_FLAGS)) {
                String flags = cells[IDX_CONT_FLAGS].getContents();
                if(flags.contains("a")) {
                    container.useAsArchivePartition(true);
                }
            }

            XtceAliasSet xas = getAliases(firstRow, cells);
            if(xas!=null) {
                container.setAliasSet(xas);
            }


            //System.out.println("for "+name+" got absoluteOffset="+)
            // we mark the start of the command and advance to the next line, to get to the first argument (if there is one)
            int start = i++;

            // now, we start processing the parameters (or references to aggregate containers)
            boolean end = false;
            int counter = 0; // sequence number of the SequenceEntrys in the SequenceContainer
            while (!end && (i < sheet.getRows())) {

                // get the next row, containing a measurement/aggregate reference
                cells = jumpToRow(sheet, i);
                // determine whether we have not reached the end of the packet definition.
                if (!hasColumn(cells,  IDX_CONT_PARA_NAME)) {
                    end = true; 
                    continue;
                }

                // extract a few variables, for further use
                String flags = cells[IDX_CONT_FLAGS].getContents();
                String paraname = cells[IDX_CONT_PARA_NAME].getContents();
                int relpos = 0;
                if (hasColumn(cells,  IDX_CONT_RELPOS)) {
                    relpos = Integer.decode(cells[IDX_CONT_RELPOS].getContents());
                }

                // we add the relative position to the absoluteOffset, to specify the location of the new parameter.
                // We only do this if the absoluteOffset is not equal to -1,
                //  because that would mean that we cannot and should not use absolute positions anymore
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
                    checkThatParameterSizeCanBeComputed(param);
                    SequenceEntry se;
                    if(flags.contains("L") || flags.contains("l")) {
                        if(param.parameterType instanceof IntegerParameterType) {
                            ((IntegerParameterType)param.parameterType).encoding.byteOrder = ByteOrder.LITTLE_ENDIAN;
                        } else if(param.parameterType instanceof FloatParameterType) {
                            ((FloatParameterType)param.parameterType).encoding.byteOrder = ByteOrder.LITTLE_ENDIAN;
                        } else if(param.parameterType instanceof EnumeratedParameterType) {
                            ((EnumeratedParameterType)param.parameterType).encoding.byteOrder = ByteOrder.LITTLE_ENDIAN;
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
                } else if (sc != null) {
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
                // after adding this measurement, we need to update the absoluteoffset for the next one. For this, we add the size of the current SequenceEntry to the absoluteoffset
                int size=getSize(param,sc);
                if ((repeated != -1) && (size != -1) && (absoluteoffset != -1)) {
                    absoluteoffset += repeated * size;
                } else {
                    // from this moment on, absoluteoffset is set to -1, meaning that all subsequent SequenceEntries must be relative
                    absoluteoffset = -1;
                }

                i++; 
                counter++;
            }

            // at this point, we have added all the parameters and aggregate containers to the current packets. What remains to be done is link it with its base
            if(parent!=null) {
                parents.put(name, parent);
                // the condition is parsed and used to create the container.restrictionCriteria
                //1) get the parent, from the same sheet
                SequenceContainer sc = containers.get(parent);
                //the parent is not in the same sheet, try to get from the Xtcedb
                if(sc==null) {
                    sc = spaceSystem.getSequenceContainer(parent);
                }
                if (sc != null) {
                    container.setBaseContainer(sc);
                    if(("5.2".compareTo(fileFormatVersion) > 0) && (!parents.containsKey(parent))) {
                        //prior to version 5.2 of the format, the second level of containers were used as archive partitions
                        //TODO: remove when switching to 6.x format
                        container.useAsArchivePartition(true);
                    }
                } else {
                    NameReference nr=new UnresolvedNameReference(parent, Type.SEQUENCE_CONTAINTER).addResolvedAction( nd -> {
                        SequenceContainer sc1 =(SequenceContainer) nd;
                        container.setBaseContainer(sc1);
                        if("5.2".compareTo(fileFormatVersion) > 0) {
                            if(sc1.getBaseContainer()==null) {
                                container.useAsArchivePartition(true);
                            }
                        }
                        return true;
                    });
                    spaceSystem.addUnresolvedReference(nr);
                }

                // 2) extract the condition and create the restrictioncriteria
                if(!"".equals(condition.trim())) {
                    container.restrictionCriteria=toMatchCriteria(spaceSystem, condition);
                    MatchCriteria.printParsedMatchCriteria(log, container.restrictionCriteria, "");
                }
            } else {
                if(spaceSystem.getRootSequenceContainer()==null) {
                    spaceSystem.setRootSequenceContainer(container);
                }
            }


            spaceSystem.addSequenceContainer(container);
        }
    }

    private void checkThatParameterSizeCanBeComputed(Parameter param) {
        DataEncoding encoding = param.getParameterType().getEncoding();
        if(encoding==null) {
            throw new SpreadsheetLoadException(ctx, "Parameter "+param.getName()+" is part of a container but has no data encoding specified");
        }
        if(encoding.getSizeInBits()>0) {
            return;
        }
        if(encoding instanceof IntegerDataEncoding) {
            IntegerDataEncoding intenc = (IntegerDataEncoding) encoding;
            if(intenc.getEncoding()!=IntegerDataEncoding.Encoding.string) {
                throw new SpreadsheetLoadException(ctx, "Parameter "+param.getName()+" is part of a container and encoded as integer but has no size in bits specified");
            }
        } else if(encoding instanceof FloatDataEncoding) {
            FloatDataEncoding fenc = (FloatDataEncoding) encoding;
            if(fenc.getEncoding()!=FloatDataEncoding.Encoding.STRING) {
                throw new SpreadsheetLoadException(ctx, "Parameter "+param.getName()+" is part of a container and encoded as float but has no size in bits specified");
            }
        }
    }

    protected void loadCommandSheet(SpaceSystem spaceSystem, String sheetName) {
        Sheet sheet = switchToSheet(sheetName, false);
        if(sheet==null) {
            return;
        }
        Cell[] firstRow = jumpToRow(sheet, 0);

        HashMap<String, MetaCommand> commands = new HashMap<>();

        for (int i = 1; i < sheet.getRows(); i++) {
            // search for a new command definition, starting from row i
            //  (explanatory note, i is incremented inside this loop too, and that's why the following 4 lines work)
            Cell[] cells = jumpToRow(sheet, i);
            if (cells == null || cells.length<1) {
                log.trace("Ignoring line {} because it's empty", ctx.row);
                continue;
            }
            if(cells[0].getContents().equals("")|| cells[0].getContents().startsWith("#")) {
                log.trace("Ignoring line {} because first cell is empty or starts with '#'", ctx.row);
                continue;
            }

            String name = cells[IDX_CMD_NAME].getContents();
            String parent=null;
            String argAssignment=null;
            if(cells.length>IDX_CMD_PARENT) {
                parent=cells[IDX_CMD_PARENT].getContents();
                if(hasColumn(cells, IDX_CMD_ARG_ASSIGNMENT)) {
                    argAssignment = cells[IDX_CMD_ARG_ASSIGNMENT].getContents();
                }
            }

            if("".equals(parent)) {
                parent=null;
            }



            // absoluteOffset is the absolute offset of the first argument or FixedValue in the container
            int absoluteOffset=-1;
            if(parent==null) {
                absoluteOffset=0;
            } else {
                int x=parent.indexOf(":");
                if(x!=-1) {
                    absoluteOffset=Integer.decode(parent.substring(x+1));
                    parent=parent.substring(0, x);
                }
            }

            // create a new SequenceContainer that will hold the parameters (i.e. SequenceEntries) for the ORDINARY/SUB/AGGREGATE packets,
            //and register that new SequenceContainer in the containers hashmap
            MetaCommandContainer container = new MetaCommandContainer(name);
            MetaCommand cmd = new MetaCommand(name);
            cmd.setMetaCommandContainer(container);
            commands.put(name, cmd);

            // load aliases
            XtceAliasSet xas = getAliases(firstRow, cells);
            if(xas!=null) {
                cmd.setAliasSet(xas);
            }

            if(hasColumn(cells, IDX_CMD_FLAGS)) {
                String flags = cells[IDX_CMD_FLAGS].getContents();
                if(flags.contains("A")){
                    cmd.setAbstract(true);
                }
            }

            if(hasColumn(cells, IDX_CMD_DESCRIPTION)) {
                String shortDescription = cells[IDX_CMD_DESCRIPTION].getContents();
                cmd.setShortDescription(shortDescription);
            }

            // we mark the start of the CMD and advance to the next line, to get to the first argument (if there is one)
            int start = i++;

            // now, we start processing the arguments
            boolean end = false;
            int counter = 0; // sequence number of the SequenceEntrys in the SequenceContainer
            while (!end && (i < sheet.getRows())) {

                // get the next row, containing a measurement/aggregate reference
                cells = jumpToRow(sheet, i);

                // determine whether we have not reached the end of the command definition.
                if (!hasColumn(cells, IDX_CMD_ARGNAME)) {
                    end = true;
                    continue;
                }


                String argname = cells[IDX_CMD_ARGNAME].getContents();
                int relpos = 0;
                if (hasColumn(cells,  IDX_CMD_RELPOS)) {
                    relpos = Integer.decode(cells[IDX_CMD_RELPOS].getContents());
                }

                if(!hasColumn(cells, IDX_CMD_ENGTYPE)) {
                    throw new SpreadsheetLoadException(ctx, "engtype is not specified for "+argname+" on line "+(i+1));
                }
                String engType = cells[IDX_CMD_ENGTYPE].getContents();
                // we add the relative position to the absoluteOffset, to specify the location of the new parameter.
                // We only do this if the absoluteOffset is not equal to -1, because that would mean that we cannot and should not use absolute positions anymore
                if (absoluteOffset != -1) {
                    absoluteOffset += relpos;
                }


                if(engType.equalsIgnoreCase("FixedValue")) {
                    if(!hasColumn(cells, IDX_CMD_DEFVALUE)) {
                        throw new SpreadsheetLoadException(ctx, "default value is not specified for "+argname+" which is a FixedValue on line "+(i+1));
                    }
                    String hexValue = cells[IDX_CMD_DEFVALUE].getContents();
                    byte[] binaryValue = StringConverter.hexStringToArray(hexValue);

                    if(!hasColumn(cells, IDX_CMD_SIZEINBITS)) {
                        throw new SpreadsheetLoadException(ctx, "sizeInBits is not specified for "+argname+" which is a FixedValue on line "+(i+1));
                    }
                    int sizeInBits = Integer.parseInt(cells[IDX_CMD_SIZEINBITS].getContents());
                    FixedValueEntry fve;
                    if (absoluteOffset == -1) {
                        fve = new FixedValueEntry(counter, container, relpos, ReferenceLocationType.previousEntry, argname, binaryValue, sizeInBits);
                    } else {
                        fve = new FixedValueEntry(counter, container, absoluteOffset, ReferenceLocationType.containerStart, argname, binaryValue, sizeInBits);
                    }
                    absoluteOffset += sizeInBits;
                    container.entryList.add(fve);
                } else {
                    absoluteOffset = loadArgument(cells, cmd, container, absoluteOffset, counter);
                }

                i++; 
                counter++;
            }

            // at this point, we have added all the parameters and aggregate containers to the current packets. What remains to be done is link it with its base
            if(parent!=null) {
                // the condition is parsed and used to create the container.restrictionCriteria
                //1) get the parent, from the same sheet
                MetaCommand parentCmd = commands.get(parent);

                //the parent is not in the same sheet, try to get from the Xtcedb
                if(parentCmd==null) {
                    parentCmd = spaceSystem.getMetaCommand(parent);
                }
                if (parentCmd != null) {
                    cmd.setBaseMetaCommand(parentCmd);
                    container.setBaseContainer(parentCmd.getCommandContainer());
                } else {
                    final MetaCommand mc = cmd;
                    final MetaCommandContainer mcc = container;
                    NameReference nr=new UnresolvedNameReference(parent, Type.META_COMMAND).addResolvedAction( nd -> {
                        mc.setBaseMetaCommand((MetaCommand) nd);
                        mcc.setBaseContainer(((MetaCommand) nd).getCommandContainer());
                        return true;
                    });
                    spaceSystem.addUnresolvedReference(nr);
                }

                // 2) extract the condition and create the restrictioncriteria
                if(argAssignment!=null) {
                    cmd.argumentAssignmentList=toArgumentAssignmentList(argAssignment);
                }
            }


            spaceSystem.addMetaCommand(cmd);
        }
    }



    protected void loadCommandOptionsSheet(SpaceSystem spaceSystem, String sheetName) {
        Sheet sheet = switchToSheet(sheetName, false);
        if(sheet==null) {
            return;
        }
        int i = 1;
        while(i<sheet.getRows()) {
            // search for a new command definition, starting from row i
            //  (explanatory note, i is incremented inside this loop too, and that's why the following 4 lines work)
            Cell[] cells = jumpToRow(sheet, i);
            if (cells == null || cells.length<1) {
                log.trace("Ignoring line {} because it's empty", ctx.row);
                i++;
                continue;
            }
            if(cells[0].getContents().equals("")|| cells[0].getContents().startsWith("#")) {
                log.trace("Ignoring line {} because first cell is empty or starts with '#'", ctx.row);
                i++;
                continue;
            }

            String cmdName = cells[IDX_CMDOPT_NAME].getContents();
            MetaCommand cmd = spaceSystem.getMetaCommand(cmdName);
            if(cmd == null) {
                throw new SpreadsheetLoadException(ctx, "Could not find a command named '"+cmdName+"'");
            }


            int cmdEnd = i + 1;
            while (cmdEnd < sheet.getRows()) {
                cells = jumpToRow(sheet, cmdEnd);
                if (hasColumn(cells, IDX_CMDOPT_NAME)) {
                    break;
                }
                cmdEnd++;
            }
            while (i<cmdEnd) {
                cells = jumpToRow(sheet, i);
                if(hasColumn(cells, IDX_CMDOPT_TXCONST)) {
                    String condition = cells[IDX_CMDOPT_TXCONST].getContents();
                    MatchCriteria criteria;
                    try {
                        criteria=toMatchCriteria(spaceSystem, condition);
                    } catch (Exception e) {
                        throw new SpreadsheetLoadException(ctx, "Cannot parse condition '"+condition+"': "+e);
                    }
                    long timeout = 0;
                    if(hasColumn(cells, IDX_CMDOPT_TXCONST_TIMEOUT)) {
                        timeout = Long.parseLong(cells[IDX_CMDOPT_TXCONST_TIMEOUT].getContents());
                    }

                    TransmissionConstraint constraint = new TransmissionConstraint(criteria, timeout);
                    cmd.addTransmissionConstrain(constraint);
                }
                if(hasColumn(cells, IDX_CMDOPT_SIGNIFICANCE)) {
                    if(cmd.getDefaultSignificance()!=null) {
                        throw new SpreadsheetLoadException(ctx,  "The command "+cmd.getName()+ " has already a default significance");
                    }
                    String significance = cells[IDX_CMDOPT_SIGNIFICANCE].getContents();
                    Significance.Levels slevel;
                    try {
                        slevel = Significance.Levels.valueOf(significance);
                    } catch (IllegalArgumentException e) {
                        throw new SpreadsheetLoadException(ctx, "Invalid significance '"+significance+"' specified. Available values are: "+Arrays.toString(Significance.Levels.values()));
                    }
                    String reason = null;
                    if(hasColumn(cells, IDX_CMDOPT_SIGNIFICANCE_REASON)) {
                        reason = cells[IDX_CMDOPT_SIGNIFICANCE_REASON].getContents();
                    }
                    cmd.setDefaultSignificance(new Significance(slevel, reason));
                }
                i++;
            }
        }
    }


    protected void loadCommandVerificationSheet(SpaceSystem spaceSystem, String sheetName) {
        Sheet sheet = switchToSheet(sheetName, false);
        if(sheet==null) {
            return;
        }
        int i = 1;
        while(i<sheet.getRows()) {
            // search for a new command definition, starting from row i
            //  (explanatory note, i is incremented inside this loop too, and that's why the following 4 lines work)
            Cell[] cells = jumpToRow(sheet, i);
            if (cells == null || cells.length<1) {
                log.trace("Ignoring line {} because it's empty", ctx.row);
                i++;
                continue;
            }
            if(cells[0].getContents().equals("")|| cells[0].getContents().startsWith("#")) {
                log.trace("Ignoring line {} because first cell is empty or starts with '#'", ctx.row);
                i++;
                continue;
            }

            String cmdName = cells[IDX_CMDVERIF_NAME].getContents();
            MetaCommand cmd = spaceSystem.getMetaCommand(cmdName);
            if(cmd == null) {
                throw new SpreadsheetLoadException(ctx, "Could not find a command named '"+cmdName+"'");
            }


            int cmdEnd = i + 1;
            while (cmdEnd < sheet.getRows()) {
                cells = jumpToRow(sheet, cmdEnd);
                if (hasColumn(cells, IDX_CMDVERIF_NAME)) {
                    break;
                }
                cmdEnd++;
            }
            while (i<cmdEnd) {
                cells = jumpToRow(sheet, i);
                if(hasColumn(cells, IDX_CMDVERIF_STAGE)) {
                    String stage =  cells[IDX_CMDVERIF_STAGE].getContents();

                    if(!hasColumn(cells, IDX_CMDVERIF_CHECKWINDOW)) {
                        throw new  SpreadsheetLoadException(ctx, "No checkwindow specified for the command verifier ");
                    }
                    String checkws = cells[IDX_CMDVERIF_CHECKWINDOW].getContents();
                    Pattern p = Pattern.compile("(\\d+),(\\d+)");
                    Matcher m = p.matcher(checkws);
                    if(!m.matches()) {
                        throw new  SpreadsheetLoadException(ctx, "Invalid checkwindow specified. Use 'start,stop' where start and stop are number of milliseconds. Both have to be positive.");
                    }
                    long start = Long.valueOf(m.group(1));
                    long stop = Long.valueOf(m.group(2));
                    if(stop<start) {
                        throw new  SpreadsheetLoadException(ctx, "Invalid checkwindow specified. Stop cannot be smaller than start");
                    }
                    CheckWindow.TimeWindowIsRelativeToType cwr = TimeWindowIsRelativeToType.timeLastVerifierPassed;

                    if(hasColumn(cells, IDX_CMDVERIF_CHECKWINDOW_RELATIVETO)) {
                        String s = cells[IDX_CMDVERIF_CHECKWINDOW_RELATIVETO].getContents();
                        try {
                            cwr =   TimeWindowIsRelativeToType.valueOf(s);
                        } catch (IllegalArgumentException  e) {
                            throw new  SpreadsheetLoadException(ctx, "Invalid value '"+s+"' specified for CheckWindow relative to parameter. Use one of "+TimeWindowIsRelativeToType.values());
                        }
                    }
                    CheckWindow cw = new CheckWindow(start, stop, cwr);
                    if(!hasColumn(cells, IDX_CMDVERIF_TYPE)) {
                        throw new  SpreadsheetLoadException(ctx, "No type specified for the command verifier ");
                    }
                    String types  =  cells[IDX_CMDVERIF_TYPE].getContents();
                    CommandVerifier.Type type = null;
                    try {
                        type = CommandVerifier.Type.valueOf(types);
                    } catch (IllegalArgumentException e) {
                        throw new SpreadsheetLoadException(ctx, "Invalid command verifier type '"+types+"' specified. Supported are: "+ Arrays.toString(CommandVerifier.Type.values()));
                    }

                    CommandVerifier cmdVerifier = new CommandVerifier(type, stage, cw);

                    if(type==CommandVerifier.Type.container) {
                        String containerName =  cells[IDX_CMDVERIF_TEXT].getContents();
                        SequenceContainer container = spaceSystem.getSequenceContainer(containerName);
                        if(container==null) {
                            throw new SpreadsheetLoadException(ctx, "Cannot find sequence container '"+containerName+"' required for the verifier");
                        }
                        cmdVerifier.setContainerRef(container);
                    } else if(type==CommandVerifier.Type.algorithm) {
                        String algoName = cells[IDX_CMDVERIF_TEXT].getContents();
                        Algorithm algo = spaceSystem.getAlgorithm(algoName);
                        if(algo==null) {
                            throw new SpreadsheetLoadException(ctx, "Cannot find algorithm '"+algoName+"' required for the verifier");
                        }
                        cmdVerifier.setAlgorithm(algo);
                    } else {
                        throw new  SpreadsheetLoadException(ctx, "Command verifier type '"+type+"' not implemented ");
                    }


                    String tas = null;
                    try {
                        if(hasColumn(cells, IDX_CMDVERIF_ONSUCCESS)) {
                            tas = cells[IDX_CMDVERIF_ONSUCCESS].getContents();
                            CommandVerifier.TerminationAction ta = TerminationAction.valueOf(tas);
                            cmdVerifier.setOnSuccess(ta);
                        }
                        if(hasColumn(cells, IDX_CMDVERIF_ONFAIL)) {
                            tas = cells[IDX_CMDVERIF_ONFAIL].getContents();
                            CommandVerifier.TerminationAction ta = TerminationAction.valueOf(tas);
                            cmdVerifier.setOnFail(ta);
                        }
                        if(hasColumn(cells, IDX_CMDVERIF_ONTIMEOUT)) {
                            tas = cells[IDX_CMDVERIF_ONTIMEOUT].getContents();
                            CommandVerifier.TerminationAction ta = TerminationAction.valueOf(tas);
                            cmdVerifier.setOnTimeout(ta);
                        }
                        cmd.addVerifier(cmdVerifier);
                    } catch (IllegalArgumentException e) {
                        throw new  SpreadsheetLoadException(ctx, "Invalid termination action '"+tas+"' specified for the command verifier. Supported actions are: "+TerminationAction.values());
                    }
                }
                i++;
            }
        }
    }

    private List<ArgumentAssignment> toArgumentAssignmentList(String argAssignment) {
        List<ArgumentAssignment> aal = new ArrayList<ArgumentAssignment>();
        String splitted[] = argAssignment.split(";");
        for (String part: splitted) {
            aal.add(toArgumentAssignment(part));
        }
        return aal;
    }


    private ArgumentAssignment toArgumentAssignment(String argAssignment) {
        Matcher m = Pattern.compile("(.*?)(=)(.*)").matcher(argAssignment);
        if(!m.matches()) {
            throw new SpreadsheetLoadException(ctx, "Cannot parse argument assignment '"+argAssignment+"'");
        }
        String aname=m.group(1).trim();
        String value=m.group(3).trim();
        return new ArgumentAssignment(aname, value);
    }

    private int loadArgument(Cell[] cells, MetaCommand cmd, MetaCommandContainer container, int absoluteOffset, int counter) {
        String engType = cells[IDX_CMD_ENGTYPE].getContents();
        String name = cells[IDX_CMD_ARGNAME].getContents();

        int relpos = hasColumn(cells,  IDX_CMD_RELPOS)?Integer.decode(cells[IDX_CMD_RELPOS].getContents()):0;

        String calib = null;
        if(hasColumn(cells, IDX_CMD_CALIBRATION)) {
            calib = cells[IDX_CMD_CALIBRATION].getContents();
        }
        String flags = null;
        if(hasColumn(cells, IDX_CMD_FLAGS)) {
            flags = cells[IDX_CMD_FLAGS].getContents();
        }
        int sizeInBits = -1;
        if(hasColumn(cells, IDX_CMD_SIZEINBITS)) {
            sizeInBits = Integer.parseInt(cells[IDX_CMD_SIZEINBITS].getContents());
        }

        String rawType = engType;
        if(hasColumn(cells, IDX_CMD_RAWTYPE)) {
            rawType = cells[IDX_CMD_RAWTYPE].getContents();
        }

        if("n".equals(calib) || "".equals(calib)){
            calib = null;
        } else if("y".equalsIgnoreCase(calib)) {
            calib = name;
        }


        ArgumentType atype=null;
        if ("uint".equalsIgnoreCase(engType)) {
            atype = new IntegerArgumentType(name);
            ((IntegerArgumentType)atype).signed = false;
        } else if ("uint64".equalsIgnoreCase(engType)) {
            atype = new IntegerArgumentType(name);
            ((IntegerArgumentType)atype).signed = false;
            ((IntegerArgumentType)atype).setSizeInBits(64);
        } else if ("int".equalsIgnoreCase(engType)) {
            atype = new IntegerArgumentType(name);
        } else if("int64".equalsIgnoreCase(engType)) {
            atype = new IntegerArgumentType(name);
            ((IntegerArgumentType)atype).setSizeInBits(64);
        } else if ("float".equalsIgnoreCase(engType)) {
            atype = new FloatArgumentType(name);
        } else if ("double".equalsIgnoreCase(engType)) {
            atype = new FloatArgumentType(name);
            ((FloatArgumentType)atype).setSizeInBits(64);
        } else if ("enumerated".equalsIgnoreCase(engType)) {
            if(calib==null) {
                throw new SpreadsheetLoadException(ctx, "Argument " + name + " has to have an enumeration");
            }
            EnumerationDefinition enumeration = enumerations.get(calib);
            if (enumeration == null) {
                throw new SpreadsheetLoadException(ctx, "Argument " + name + " is supposed to have an enumeration '" + calib + "' but the enumeration does not exist");
            }
            atype = new EnumeratedArgumentType(calib);
            for (Entry<Long,String> entry:enumeration.valueMap.entrySet()) {
                ((EnumeratedArgumentType) atype).addEnumerationValue(entry.getKey(), entry.getValue());
            }
        } else if ("string".equalsIgnoreCase(engType)) {
            atype = new StringArgumentType(name);
        } else	if ("binary".equalsIgnoreCase(engType)) {
            atype = new BinaryArgumentType(name);
        }  else if ("boolean".equalsIgnoreCase(engType)) {
            atype = new BooleanArgumentType(name);
        }else {
            throw new SpreadsheetLoadException(ctx, "Unknown argument type " + engType);
        }
        if(cmd.getArgument(name)!=null) {
            throw new SpreadsheetLoadException(ctx, "Duplicate argument with name '"+name+"'");
        }
        Argument arg = new Argument(name);
        cmd.addArgument(arg);


        if(hasColumn(cells, IDX_CMD_DEFVALUE)) {
            String v = cells[IDX_CMD_DEFVALUE].getContents();
            if(atype instanceof IntegerArgumentType) {
                try {
                    Long.decode(v);
                } catch(Exception e) {
                    throw new SpreadsheetLoadException(ctx, "Cannot parse default value '"+v+"'");
                }
                arg.setInitialValue(v);
            } else if (atype instanceof FloatArgumentType) {
                try {
                    Double.parseDouble(v);
                } catch(Exception e) {
                    throw new SpreadsheetLoadException(ctx, "Cannot parse default value '"+v+"'");
                }
                arg.setInitialValue(v);

            } else {
                arg.setInitialValue(v);
            }
        }
        if(hasColumn(cells, IDX_CMD_RANGELOW) || hasColumn(cells, IDX_CMD_RANGEHIGH)) {
            if(atype instanceof IntegerArgumentType) {
                if(((IntegerArgumentType) atype).isSigned()) {
                    long minInclusive = Long.MIN_VALUE;
                    long maxInclusive = Long.MAX_VALUE;
                    if(hasColumn(cells, IDX_CMD_RANGELOW)) {
                        minInclusive = Long.decode(cells[IDX_CMD_RANGELOW].getContents());
                    }
                    if(hasColumn(cells, IDX_CMD_RANGEHIGH)) {
                        maxInclusive = Long.decode(cells[IDX_CMD_RANGEHIGH].getContents());
                    }
                    IntegerValidRange range = new IntegerValidRange(minInclusive, maxInclusive);
                    ((IntegerArgumentType)atype).setValidRange(range);
                } else {
                    long minInclusive = 0;
                    long maxInclusive = ~0;
                    if(hasColumn(cells, IDX_CMD_RANGELOW)) {
                        minInclusive = UnsignedLongs.decode(cells[IDX_CMD_RANGELOW].getContents());
                    }
                    if(hasColumn(cells, IDX_CMD_RANGEHIGH)) {
                        maxInclusive = UnsignedLongs.decode(cells[IDX_CMD_RANGEHIGH].getContents());
                    }
                    IntegerValidRange range = new IntegerValidRange(minInclusive, maxInclusive);
                    ((IntegerArgumentType)atype).setValidRange(range);

                }
            } else if(atype instanceof FloatArgumentType) {
                double minInclusive = Double.NEGATIVE_INFINITY;
                double maxInclusive = Double.POSITIVE_INFINITY;
                if(hasColumn(cells, IDX_CMD_RANGELOW)) {
                    minInclusive = Double.parseDouble(cells[IDX_CMD_RANGELOW].getContents());
                }
                if(hasColumn(cells, IDX_CMD_RANGEHIGH)) {
                    maxInclusive = Double.parseDouble(cells[IDX_CMD_RANGEHIGH].getContents());
                }
                FloatValidRange range = new FloatValidRange(minInclusive, maxInclusive);
                ((FloatArgumentType)atype).setValidRange(range);
            }
        }

        if(hasColumn(cells, IDX_CMD_DESCRIPTION)) {
            String shortDescription = cells[IDX_CMD_DESCRIPTION].getContents();
            arg.setShortDescription(shortDescription);
        }

        ArgumentEntry ae;
        // if absoluteoffset is -1, somewhere along the line we came across a measurement or aggregate that had as a result that the absoluteoffset could not be determined anymore; hence, a relative position is added
        if (absoluteOffset == -1) {
            ae = new ArgumentEntry(counter, container, relpos, ReferenceLocationType.previousEntry, arg);
        } else {
            ae = new ArgumentEntry(counter, container, absoluteOffset, ReferenceLocationType.containerStart, arg);
        }

        container.entryList.add(ae);
        ByteOrder byteOrder = ByteOrder.BIG_ENDIAN;
        if((flags!=null) && (flags.contains("L") || flags.contains("l"))) {
            byteOrder = ByteOrder.LITTLE_ENDIAN;
        }

        // after adding this argument, we need to update the absoluteOffset for the next one.
        // For this, we add the size of the current ArgumentEntry to the absoluteOffset

        if ((sizeInBits != -1) && (absoluteOffset != -1)) {
            absoluteOffset += sizeInBits;
        } else {
            // from this moment on, absoluteOffset is set to -1, meaning that all subsequent SequenceEntries must be relative
            absoluteOffset = -1;
        }
        String units=null;
        if(hasColumn(cells, IDX_CMD_ENGUNIT)) {
            units = cells[IDX_CMD_ENGUNIT].getContents();
            if(!"".equals(units) && units != null && atype instanceof BaseDataType) {
                UnitType unitType = new UnitType(units);
                ((BaseDataType) atype).addUnit(unitType);
            }
        }

        //loadParameterLimits(ptype,cells);

        //calibrations
        DataEncoding encoding = null;
        if (("uint".equalsIgnoreCase(rawType)) || rawType.toLowerCase().startsWith("int")) {
            if(sizeInBits==-1) {
                throw new SpreadsheetLoadException(ctx, "Size in bits length is mandatory for integer arguments");
            }
            encoding = new IntegerDataEncoding(sizeInBits, byteOrder);
            if (rawType.toLowerCase().startsWith("int")) {
                if ("int".equals(rawType)) {
                    ((IntegerDataEncoding)encoding).encoding = IntegerDataEncoding.Encoding.twosComplement;
                } else {
                    int startBracket = rawType.indexOf('(');
                    if (startBracket != -1) {
                        int endBracket = rawType.indexOf(')', startBracket);
                        if (endBracket != -1) {
                            String intRepresentation = rawType.substring(startBracket+1, endBracket).trim().toLowerCase();
                            if ("2c".equals(intRepresentation)) {
                                ((IntegerDataEncoding)encoding).encoding = IntegerDataEncoding.Encoding.twosComplement;
                            } else if ("si".equals(intRepresentation)) {
                                ((IntegerDataEncoding)encoding).encoding = IntegerDataEncoding.Encoding.signMagnitude;
                            } else {
                                throw new SpreadsheetLoadException(ctx, "Unsupported signed integer representation: "+intRepresentation);
                            }
                        }
                    }
                }
            }
            if ((!"enumerated".equalsIgnoreCase(engType)) && (calib!=null)) {
                Calibrator c = calibrators.get(calib);
                if (c == null) {
                    throw new SpreadsheetLoadException(ctx, "Argument " + name + " is supposed to have a calibrator '" + calib + "' but the calibrator does not exist");
                }
                ((IntegerDataEncoding)encoding).defaultCalibrator = c;
            }
        } else if ("binary".equalsIgnoreCase(rawType) || "bytestream".equalsIgnoreCase(rawType)) {
            if(sizeInBits==-1) {
                throw new SpreadsheetLoadException(ctx, "Bit length is mandatory for binary parameters");
            }
            encoding=new BinaryDataEncoding(name, sizeInBits);
        } else if ("boolean".equalsIgnoreCase(rawType)) {
            if(sizeInBits!=-1) {
                throw new SpreadsheetLoadException(ctx, "Bit length is not allowed for boolean parameters (defaults to 1). Use any other raw type if you want to specify the bitlength");
            }
            encoding = new BooleanDataEncoding();
        } else if ("string".equalsIgnoreCase(rawType)) {
            // Version <= 1.6 String type
            // STRING
            if(sizeInBits==-1) {
                // Assume null-terminated if no length specified
                encoding=new StringDataEncoding(name, StringDataEncoding.SizeType.TerminationChar);
            } else {
                encoding=new StringDataEncoding(name, StringDataEncoding.SizeType.Fixed);
                encoding.setSizeInBits(sizeInBits);
            }
        } else if ( "fixedstring".equalsIgnoreCase( rawType ) ) {
            // v1.7 String type
            // FIXEDSTRING
            if(sizeInBits==-1) {
                throw new SpreadsheetLoadException(ctx, "SizeInBits is mandatory for fixedstring raw type");
            }
            encoding=new StringDataEncoding(name, StringDataEncoding.SizeType.Fixed);
            if((sizeInBits&0x7)!=0) {
                throw new SpreadsheetLoadException(ctx, "SizeInBits has to be multiple of 8 for fixedstring raw type");
            }
            encoding.setSizeInBits(sizeInBits);
        } else if ( rawType.toLowerCase().startsWith( "terminatedstring" ) ) {
            // v1.7 String type
            // TERMINATEDSTRING
            encoding=new StringDataEncoding(name, StringDataEncoding.SizeType.TerminationChar);
            encoding.setSizeInBits(sizeInBits);
            // Use specified byte if found, otherwise accept class default.
            int startBracket = rawType.indexOf( '(' );
            if( startBracket != -1 ) {
                int endBracket = rawType.indexOf( ')', startBracket );
                if( endBracket != -1 ) {
                    try {
                        byte terminationChar = Byte.parseByte(rawType.substring( rawType.indexOf('x', startBracket)+1, endBracket ).trim(), 16 );
                        ((StringDataEncoding)encoding).setTerminationChar(terminationChar);
                    } catch (NumberFormatException e) {
                        throw new SpreadsheetLoadException(ctx, "Could not parse specified base 16 terminator from "+rawType);
                    }
                }
            }
        } else if ( rawType.toLowerCase().startsWith( "prependedsizestring" ) ) {
            // v1.7 String type
            // PREPENDEDSIZESTRING
            encoding=new StringDataEncoding( name, StringDataEncoding.SizeType.LeadingSize );
            encoding.setSizeInBits(sizeInBits);
            // Use specified size if found, otherwise accept class default.
            int startBracket = rawType.indexOf( '(' );
            if( startBracket != -1 ) {
                int endBracket = rawType.indexOf( ')', startBracket );
                if( endBracket != -1 ) {
                    try {
                        int sizeInBitsOfSizeTag = Integer.parseInt( rawType.substring(startBracket+1, endBracket).trim() );
                        ((StringDataEncoding)encoding).setSizeInBitsOfSizeTag( sizeInBitsOfSizeTag );
                    } catch (NumberFormatException e) {
                        throw new SpreadsheetLoadException(ctx, "Could not parse integer size from "+rawType);
                    }
                }
            }
        } else if ("float".equalsIgnoreCase(rawType) || "double".equalsIgnoreCase(rawType)) {
            if(sizeInBits==-1) {
                throw new SpreadsheetLoadException(ctx, "Size in bits is mandatory for integer arguments");
            }
            encoding=new FloatDataEncoding(sizeInBits, byteOrder);
            if((!"enumerated".equalsIgnoreCase(engType)) && (calib!=null)) {
                Calibrator c = calibrators.get(calib);
                if (c == null) {
                    throw new SpreadsheetLoadException(ctx, "Parameter " + name + " is supposed to have a calibrator '" + calib + "' but the calibrator does not exist.");
                } else {
                    ((FloatDataEncoding)encoding).defaultCalibrator = c;
                }
            }
        } else {
            throw new SpreadsheetLoadException(ctx, "Unknown raw type " + rawType);
        }

        if (atype instanceof IntegerArgumentType) {
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
                ((IntegerArgumentType)atype).setEncoding(intStringEncoding);
            } else {
                ((IntegerArgumentType)atype).setEncoding(encoding);
            }
        } else if (atype instanceof BinaryArgumentType) {
            ((BinaryArgumentType)atype).encoding = encoding;
        } else if (atype instanceof FloatArgumentType) {
            // Floats can be encoded as strings
            if ( encoding instanceof StringDataEncoding ) {
                // Create a new float encoding which uses the configured string encoding
                FloatDataEncoding floatStringEncoding = new FloatDataEncoding(((StringDataEncoding)encoding));
                if(calib!=null) {
                    Calibrator c = calibrators.get(calib);
                    if( c == null ) {
                        throw new SpreadsheetLoadException(ctx, "Parameter " + name + " specified calibrator '" + calib + "' but the calibrator does not exist.");
                    } else {
                        floatStringEncoding.defaultCalibrator = c;
                    }
                }
                floatStringEncoding.setByteOrder(byteOrder);
                ((FloatArgumentType)atype).setEncoding(floatStringEncoding);
            } else {
                ((FloatArgumentType)atype).setEncoding(encoding);
            }
        } else if (atype instanceof EnumeratedArgumentType) {
            if(((EnumeratedArgumentType) atype).getEncoding() != null) {
                // Some other param has already led to setting the encoding of this shared ptype.
                // Do some basic consistency checks
                if(((EnumeratedArgumentType) atype).getEncoding().getSizeInBits() != encoding.getSizeInBits()) {
                    throw new SpreadsheetLoadException(ctx, "Multiple parameters are sharing calibrator '"+calib+"' with different bit sizes.");
                }
            }

            // Enumerations encoded as string integers
            if( encoding instanceof StringDataEncoding ) {
                IntegerDataEncoding intStringEncoding = new IntegerDataEncoding(name, ((StringDataEncoding)encoding));
                // Don't set calibrator, already done when making ptype
                ((EnumeratedArgumentType) atype).setEncoding(intStringEncoding);
                intStringEncoding.setByteOrder(byteOrder);
            } else {
                ((EnumeratedArgumentType) atype).setEncoding(encoding);
            }
        } else if (atype instanceof StringArgumentType) {
            ((StringArgumentType)atype).setEncoding(encoding);
        } else if (atype instanceof BooleanArgumentType) {
            ((BooleanArgumentType)atype).setEncoding(encoding);
        } else {
            throw new RuntimeException("Don't know what to do with "+atype);
        }
        arg.setArgumentType(atype);

        return absoluteOffset;
    }

    protected void loadChangelogSheet(boolean required) {
        Sheet sheet = switchToSheet(SHEET_CHANGELOG, required);
        if(sheet==null) {
            return;
        }
        int i = 1;
        while(i<sheet.getRows()) {
            Cell[] cells = jumpToRow(sheet, i);
            if (cells == null || cells.length<1) {
                log.trace("Ignoring line {} because it's empty", ctx.row);
                i++;
                continue;
            }
            if(cells[0].getContents().equals("")|| cells[0].getContents().startsWith("#")) {
                log.trace("Ignoring line {} because first cell is empty or starts with '#'", ctx.row);
                i++;
                continue;
            }

            if (cells.length >= 2) {
                String version = cells[IDX_LOG_VERSION].getContents();

                String date;
                Cell dateCell = cells[IDX_LOG_DATE];
                if (dateCell.getType() == CellType.DATE) {
                    Date dt = ((DateCell) dateCell).getDate();
                    date = new SimpleDateFormat("dd-MMM-YYYY").format(dt);
                } else {
                    date = cells[IDX_LOG_DATE].getContents();
                }

                String msg = null;
                if (cells.length >= 3) {
                    msg = cells[IDX_LOG_MESSAGE].getContents();
                }
                History history = new History(version, date, msg);
                rootSpaceSystem.getHeader().addHistory(history);
            }
            i++;
        }
    }

    protected void loadAlgorithmsSheet(SpaceSystem spaceSystem, String sheetName) {
        Sheet sheet = switchToSheet(sheetName, false);
        if (sheet == null) {
            return;
        }

        Cell[] firstRow = jumpToRow(sheet, 0);
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
            String algorithmLanguage = cells[IDX_ALGO_LANGUGAGE].getContents();
            if(!"JavaScript".equals(algorithmLanguage) && !"python".equals(algorithmLanguage)&& !"java".equalsIgnoreCase(algorithmLanguage)) {
                throw new SpreadsheetLoadException(ctx, "Invalid algorithm language '"+algorithmLanguage+"' specified. Supported are 'JavaScript', 'python' and java (case sensitive)");
            }

            String algorithmText = cells[IDX_ALGO_TEXT].getContents();
            XtceAliasSet xas = getAliases(firstRow, cells);

            //Check that there is not specified by mistake a in/out param already on the same line with the algorithm name
            if(hasColumn(cells, IDX_ALGO_PARA_INOUT) || hasColumn(cells, IDX_ALGO_PARA_REF)) {
                throw new SpreadsheetLoadException(ctx, "Algorithm paramters have to start on the next line from the algorithm name and text definition");
            }

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
            if(xas!=null) {
                algorithm.setAliasSet(xas);
            }
            algorithm.setLanguage(algorithmLanguage);
            // Replace smart-quotes “ and ” with regular quotes "
            algorithm.setAlgorithmText(algorithmText.replaceAll("[\u201c\u201d]", "\""));


            // In/out params
            String paraInout=null;
            Set<String> inputParameterRefs=new HashSet<String>();
            for (int j = start+1; j < end; j++) {
                cells = jumpToRow(sheet, j);
                String paraRefName = cells[IDX_ALGO_PARA_REF].getContents();
                if(hasColumn(cells, IDX_ALGO_PARA_INOUT)) {
                    paraInout=cells[IDX_ALGO_PARA_INOUT].getContents();
                }

                String flags = hasColumn(cells, IDX_ALGO_PARA_FLAGS)?cells[IDX_ALGO_PARA_FLAGS].getContents():"";

                if(paraInout==null) {
                    throw new SpreadsheetLoadException(ctx, "You must specify in/out attribute for this parameter");
                }
                if ("in".equalsIgnoreCase(paraInout)) {
                    if(paraRefName.startsWith(XtceDb.YAMCS_CMD_SPACESYSTEM_NAME) || paraRefName.startsWith(XtceDb.YAMCS_CMDHIST_SPACESYSTEM_NAME)) {
                        algorithm.setScope(Algorithm.Scope.commandVerification);
                    }
                    inputParameterRefs.add(paraRefName);
                    NameReference paramRef = getParameterReference(spaceSystem, paraRefName, false);
                    final ParameterInstanceRef parameterInstance = new ParameterInstanceRef();
                    paramRef.addResolvedAction( nd -> {
                        parameterInstance.setParameter((Parameter) nd);
                        return true;
                    });

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
                    if (hasColumn(cells, IDX_ALGO_PARA_NAME)) {
                        inputParameter.setInputName(cells[IDX_ALGO_PARA_NAME].getContents());
                    }
                    if(flags.contains("M")) {
                        inputParameter.setMandatory(true);
                    }
                    algorithm.addInput(inputParameter);
                } else if ("out".equalsIgnoreCase(paraInout)) {
                    NameReference paramRef = getParameterReference(spaceSystem, paraRefName, false);
                    OutputParameter outputParameter = new OutputParameter();
                    paramRef.addResolvedAction(nd -> {
                        Parameter param = (Parameter) nd;
                        outputParameter.setParameter(param);
                        return true;
                    });
                    if (hasColumn(cells, IDX_ALGO_PARA_NAME)) {
                        outputParameter.setOutputName(cells[IDX_ALGO_PARA_NAME].getContents());
                    }
                    algorithm.addOutput(outputParameter);
                } else {
                    throw new SpreadsheetLoadException(ctx, "In/out '"+paraInout+"' not supported. Must be one of 'in' or 'out'");
                }
            }

            // Add trigger conditions
            final TriggerSetType triggerSet = new TriggerSetType();

            cells = jumpToRow(sheet, start); // Jump back to algorithm row (for getting error msgs right)
            String triggerText = hasColumn(cells, IDX_ALGO_TRIGGER) ? cells[IDX_ALGO_TRIGGER].getContents() : "";
            if(triggerText.startsWith("OnParameterUpdate")) {
                Matcher matcher = ALGO_PARAMETER_PATTERN.matcher(triggerText);
                if(matcher.matches()) {
                    for(String s:matcher.group(1).split(",")) {
                        Parameter para = spaceSystem.getParameter(s.trim());
                        if(para!=null) {
                            OnParameterUpdateTrigger trigger = new OnParameterUpdateTrigger(para);
                            triggerSet.addOnParameterUpdateTrigger(trigger);
                        } else {
                            NameReference nr=new UnresolvedNameReference(s.trim(), Type.PARAMETER).addResolvedAction(nd -> {
                                OnParameterUpdateTrigger trigger = new OnParameterUpdateTrigger((Parameter) nd);
                                triggerSet.addOnParameterUpdateTrigger(trigger);
                                return true;
                            });
                            spaceSystem.addUnresolvedReference(nr);
                        }
                    }
                } else {
                    throw new SpreadsheetLoadException(ctx, "Wrongly formatted OnParameterUpdate trigger");
                }
            } else if(triggerText.startsWith("OnPeriodicRate")) {
                Matcher matcher = ALGO_FIRERATE_PATTERN.matcher(triggerText);
                if(matcher.matches()) {
                    long fireRateMs = Long.parseLong(matcher.group(1), 10);
                    OnPeriodicRateTrigger trigger=new OnPeriodicRateTrigger(fireRateMs);
                    triggerSet.addOnPeriodicRateTrigger(trigger);
                } else {
                    throw new SpreadsheetLoadException(ctx, "Wrongly formatted OnPeriodicRate trigger");
                }

            } else if (triggerText.startsWith("OnInputParameterUpdate")) {
                // default to all in parameters
                for(String paraRef:inputParameterRefs) {
                    Parameter para=spaceSystem.getParameter(paraRef);
                    if(para!=null) {
                        triggerSet.addOnParameterUpdateTrigger(new OnParameterUpdateTrigger(para));
                    } else {
                        NameReference nr=new UnresolvedNameReference(paraRef, Type.PARAMETER).addResolvedAction(nd -> {
                            OnParameterUpdateTrigger trigger = new OnParameterUpdateTrigger((Parameter) nd);
                            triggerSet.addOnParameterUpdateTrigger(trigger);
                            return true;
                        });
                        spaceSystem.addUnresolvedReference(nr);
                    }
                }
            } else if(triggerText.isEmpty() || triggerText.startsWith("none")) {
                //do nothing, we run with an empty trigger set
            } else {
                throw new SpreadsheetLoadException(ctx, "Trigger '"+triggerText+"' not supported.");
            }
            algorithm.setTriggerSet(triggerSet);

            spaceSystem.addAlgorithm(algorithm);
            start = end;
        }
    }

    protected void loadAlarmsSheet(SpaceSystem spaceSystem, String sheetName) {
        Sheet sheet = switchToSheet(sheetName, false);
        if (sheet == null) {
            return;
        }

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
            NameReference paraRef = getParameterReference(spaceSystem, paramName, true);

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
            int minViolations=-1;
            AlarmReportType reportType=AlarmReportType.ON_SEVERITY_CHANGE;
            for (int j = start; j < paramEnd; j++) {
                cells = jumpToRow(sheet, j);
                MatchCriteria context = previousContext;
                if(hasColumn(cells, IDX_ALARM_CONTEXT)) {
                    String contextString = cells[IDX_ALARM_CONTEXT].getContents();
                    context = toMatchCriteria(spaceSystem, contextString);
                    minViolations = -1;
                }

                if(hasColumn(cells, IDX_ALARM_MIN_VIOLATIONS)) {
                    minViolations=Integer.parseInt(cells[IDX_ALARM_MIN_VIOLATIONS].getContents());
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

                checkAndAddAlarm(cells, AlarmLevels.watch, paraRef, context, IDX_ALARM_WATCH_TRIGGER, IDX_ALARM_WATCH_VALUE);
                checkAndAddAlarm(cells, AlarmLevels.warning, paraRef, context, IDX_ALARM_WARNING_TRIGGER, IDX_ALARM_WARNING_VALUE);
                checkAndAddAlarm(cells, AlarmLevels.distress, paraRef, context, IDX_ALARM_DISTRESS_TRIGGER, IDX_ALARM_DISTRESS_VALUE);
                checkAndAddAlarm(cells, AlarmLevels.critical, paraRef, context, IDX_ALARM_CRITICAL_TRIGGER, IDX_ALARM_CRITICAL_VALUE);
                checkAndAddAlarm(cells, AlarmLevels.severe, paraRef, context, IDX_ALARM_SEVERE_TRIGGER, IDX_ALARM_SEVERE_VALUE);

                addAlarmDetails(paraRef, context, reportType, minViolations);

                previousContext=context;
            }

            start = paramEnd;
        }
    }



    private void checkAndAddAlarm(Cell[] cells, AlarmLevels level, NameReference paraRef, MatchCriteria context, int idxTrigger, int idxValue) {
        if(!hasColumn(cells, idxTrigger) || !hasColumn(cells, idxValue)) {
            return;
        }
        String trigger = cells[idxTrigger].getContents();
        String triggerValue = cells[idxValue].getContents();

        paraRef.addResolvedAction(nd -> {
            Parameter para = (Parameter)nd;
            if(para.getParameterType() instanceof IntegerParameterType) {
                IntegerParameterType ipt=(IntegerParameterType)para.getParameterType();
                if("low".equals(trigger)) {
                    ipt.addAlarmRange(context, new DoubleRange(Double.parseDouble(triggerValue),Double.POSITIVE_INFINITY), level);
                } else if("high".equals(trigger)) {
                    ipt.addAlarmRange(context, new DoubleRange(Double.NEGATIVE_INFINITY, Double.parseDouble(triggerValue)), level);
                } else {
                    throw new SpreadsheetLoadException(ctx, "Unexpected trigger type '"+trigger+"' for numeric parameter "+para.getName());
                }
            } else if(para.getParameterType() instanceof FloatParameterType) {
                FloatParameterType fpt=(FloatParameterType)para.getParameterType();
                if("low".equals(trigger)) {
                    fpt.addAlarmRange(context, new DoubleRange(Double.parseDouble(triggerValue), Double.POSITIVE_INFINITY), level);
                } else if("high".equals(trigger)) {
                    fpt.addAlarmRange(context, new DoubleRange(Double.NEGATIVE_INFINITY, Double.parseDouble(triggerValue)), level);
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
                        ept.addAlarm(context, triggerValue, level);
                    }
                } else {
                    throw new SpreadsheetLoadException(ctx, "Unexpected trigger type '"+trigger+"' for alarm of enumerated parameter "+para.getName());
                }
            }
            return true;
        });
    }

    private void addAlarmDetails(NameReference paraRef, MatchCriteria context, AlarmReportType reportType, int minViolations) {

        paraRef.addResolvedAction(nd -> {
            Parameter para = (Parameter) nd;
            ParameterType ptype = para.getParameterType();
            if(ptype == null) { //the type has to be resolved somewhere else first 
                return false;
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
                alarm.setMinViolations((minViolations==-1) ? 1 : minViolations);
                alarm.setAlarmReportType(reportType);
            }
            return true;
        });
    }

    /**
     *
     * @param criteriaString
     * @return
     */
    private MatchCriteria toMatchCriteria(SpaceSystem spaceSystem, String criteriaString) {
        criteriaString = criteriaString.trim();

        if ((criteriaString.startsWith("&(") || criteriaString.startsWith("|(")) && (criteriaString.endsWith(")"))) {
            return conditionParser.parseBooleanExpression(spaceSystem, criteriaString);
        } else if(criteriaString.contains(";")) {
            ComparisonList cl = new ComparisonList();
            String splitted[] = criteriaString.split(";");
            for (String part: splitted) {
                cl.comparisons.add(conditionParser.toComparison(spaceSystem, part));
            }
            return cl;
        } else {
            return conditionParser.toComparison(spaceSystem, criteriaString);
        }
    }

    protected boolean hasColumn(Cell[] cells, int idx) {
        return (cells!=null) && (cells.length>idx) && (cells[idx].getContents()!=null) && (!cells[idx].getContents().equals(""));
    }

    private int getSize(Parameter param, SequenceContainer sc) {
        // either we have a Parameter or we have a SequenceContainer, we cannot have both or neither
        if (param != null) {
            DataEncoding de = ((BaseDataType)param.getParameterType()).getEncoding();
            if(de==null) {
                throw new SpreadsheetLoadException(ctx, "Cannot determine the data encoding for "+param.getName());
            }

            if ((de instanceof FloatDataEncoding) || (de instanceof IntegerDataEncoding)
                    || (de instanceof BinaryDataEncoding) ||(de instanceof BooleanDataEncoding)){
                return de.sizeInBits;
            } else if (de instanceof StringDataEncoding) {
                return -1;
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
                se.repeatEntry.count = new FixedIntegerValue(rep);
                return rep;
            } catch (NumberFormatException e) {
                se.repeatEntry.count = new DynamicIntegerValue();
                Parameter repeatparam = parameters.get(repeat);
                if(repeatparam==null) {
                    throw new SpreadsheetLoadException(ctx, "Cannot find the parameter for repeat "+repeat);
                }
                ((DynamicIntegerValue)se.repeatEntry.count).setParameterInstanceRef(new ParameterInstanceRef(repeatparam, true));
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

    static NameReference getParameterReference(SpaceSystem spaceSystem, String paramName, boolean typeRequired) {
        Parameter para = spaceSystem.getParameter(paramName);
        NameReference paraRef;
        if((para == null)|| (typeRequired && para.getParameterType()==null)) {
            paraRef = new UnresolvedNameReference(paramName, Type.PARAMETER);
            spaceSystem.addUnresolvedReference(paraRef);
        } else {
            paraRef = new ResolvedNameReference(paramName, Type.PARAMETER, para);    
        }

        return paraRef;
    }


    /**
     * Temporary value holder for the enumeration definition (because
     * enumerations are read before parameters, and reading sharing the same EPT
     * among all parameters is not a good approach (think different alarm
     * definitions)
     */
    protected static class EnumerationDefinition {
        public final LinkedHashMap<Long,String> valueMap = new LinkedHashMap<>();
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

    static Pattern allowedInNameType = Pattern.compile("[\\d\\w_-]+");



    private void validateNameType(String name) {
        if(!enableXtceNameRestrictions) {
            return;
        }

        if(!allowedInNameType.matcher(name).matches()) {
            throw new SpreadsheetLoadException(ctx, "Invalid name '"+name+"'; should only contain letters, digits, _, and -");
        }
    }
}

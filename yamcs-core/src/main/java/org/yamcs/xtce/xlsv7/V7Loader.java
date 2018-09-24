package org.yamcs.xtce.xlsv7;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
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
import org.yamcs.xtce.AbsoluteTimeDataType;
import org.yamcs.xtce.AbsoluteTimeParameterType;
import org.yamcs.xtce.AggregateArgumentType;
import org.yamcs.xtce.AggregateDataType;
import org.yamcs.xtce.AggregateParameterType;
import org.yamcs.xtce.AlarmLevels;
import org.yamcs.xtce.AlarmReportType;
import org.yamcs.xtce.AlarmType;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.Algorithm.Scope;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.ArgumentEntry;
import org.yamcs.xtce.ArgumentType;
import org.yamcs.xtce.ArrayArgumentType;
import org.yamcs.xtce.ArrayDataType;
import org.yamcs.xtce.ArrayParameterEntry;
import org.yamcs.xtce.ArrayParameterType;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.BinaryArgumentType;
import org.yamcs.xtce.BinaryDataEncoding;
import org.yamcs.xtce.BinaryParameterType;
import org.yamcs.xtce.BooleanArgumentType;
import org.yamcs.xtce.BooleanDataEncoding;
import org.yamcs.xtce.BooleanParameterType;
import org.yamcs.xtce.Calibrator;
import org.yamcs.xtce.CheckWindow;
import org.yamcs.xtce.CheckWindow.TimeWindowIsRelativeToType;
import org.yamcs.xtce.CommandContainer;
import org.yamcs.xtce.CommandVerifier;
import org.yamcs.xtce.CommandVerifier.TerminationAction;
import org.yamcs.xtce.ComparisonList;
import org.yamcs.xtce.ConditionParser;
import org.yamcs.xtce.ContainerEntry;
import org.yamcs.xtce.ContextCalibrator;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.DataType;
import org.yamcs.xtce.DynamicIntegerValue;
import org.yamcs.xtce.EnumeratedArgumentType;
import org.yamcs.xtce.EnumeratedDataType;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.FixedIntegerValue;
import org.yamcs.xtce.FixedValueEntry;
import org.yamcs.xtce.FloatArgumentType;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.FloatDataType;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.FloatValidRange;
import org.yamcs.xtce.Header;
import org.yamcs.xtce.History;
import org.yamcs.xtce.IndirectParameterRefEntry;
import org.yamcs.xtce.InputParameter;
import org.yamcs.xtce.IntegerArgumentType;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerDataType;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.IntegerValidRange;
import org.yamcs.xtce.IntegerValue;
import org.yamcs.xtce.JavaExpressionCalibrator;
import org.yamcs.xtce.MatchCriteria;
import org.yamcs.xtce.Member;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.OnParameterUpdateTrigger;
import org.yamcs.xtce.OnPeriodicRateTrigger;
import org.yamcs.xtce.OutputParameter;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterEntry;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.PolynomialCalibrator;
import org.yamcs.xtce.RateInStream;
import org.yamcs.xtce.ReferenceTime;
import org.yamcs.xtce.Repeat;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SequenceEntry;
import org.yamcs.xtce.SequenceEntry.ReferenceLocationType;
import org.yamcs.xtce.Significance;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.SplineCalibrator;
import org.yamcs.xtce.SplinePoint;
import org.yamcs.xtce.SpreadsheetLoadContext;
import org.yamcs.xtce.SpreadsheetLoadException;
import org.yamcs.xtce.StringArgumentType;
import org.yamcs.xtce.StringDataEncoding;
import org.yamcs.xtce.StringDataEncoding.SizeType;
import org.yamcs.xtce.StringParameterType;
import org.yamcs.xtce.TimeEpoch;
import org.yamcs.xtce.TransmissionConstraint;
import org.yamcs.xtce.TriggerSetType;
import org.yamcs.xtce.UnitType;
import org.yamcs.xtce.ValueEnumeration;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtce.util.NameReference;
import org.yamcs.xtce.util.NameReference.Type;
import org.yamcs.xtce.util.UnresolvedNameReference;
import org.yamcs.xtce.xml.XtceAliasSet;
import org.yamcs.xtceproc.JavaExpressionCalibratorFactory;

import com.google.common.primitives.UnsignedLongs;

import jxl.Cell;
import jxl.CellType;
import jxl.DateCell;
import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;

/**
 * This class loads database from excel spreadsheets.
 *
 * @author nm, ddw
 *
 */
public class V7Loader extends V7LoaderBase {
    protected Map<String, Calibrator> calibrators = new HashMap<>();
    protected Map<String, List<ContextCalibrator>> contextCalibrators = new HashMap<>();
    protected Map<String, String> timeCalibEpochs = new HashMap<>();
    protected Map<String, String> timeCalibScales = new HashMap<>();
    protected Map<String, SpreadsheetLoadContext> timeCalibContexts = new HashMap<>();

    protected Map<String, DataTypeRecord> dataTypes = new HashMap<>();

    protected Map<String, EnumerationDefinition> enumerations = new HashMap<>();
    protected Map<String, Parameter> parameters = new HashMap<>();
    protected Set<Parameter> outputParameters = new HashSet<>(); // Outputs to algorithms
    Map<String, SequenceContainer> containers = new HashMap<>();

    final ConditionParser conditionParser = new ConditionParser(ctx);
    final Pattern FIXED_VALUE_PATTERN = Pattern.compile("FixedValue\\((\\d+)\\)");

    // Increment major when breaking backward compatibility, increment minor when making backward compatible changes
    final static String FORMAT_VERSION = "7.0";
    // Explicitly support these versions (i.e. load without warning)
    final static String[] FORMAT_VERSIONS_SUPPORTED = new String[] { FORMAT_VERSION, "7.0" };
    String fileFormatVersion;

    final static Pattern REPEAT_PATTERN = Pattern.compile("(.*)[*](.*)");
    final static Pattern REF_PATTERN = Pattern.compile("ref\\(\\s*([^,]+),\\s*(.+)?\\s*\\)");
    final static Pattern ARRAY_PATTERN = Pattern.compile("(\\w+)(\\[[\\w\\d]+\\])+");

    protected SpaceSystem rootSpaceSystem;

    boolean enableAliasReferences = false;

    public V7Loader(Map<String, Object> config) {
        this(YConfiguration.getString(config, "file"));
        enableAliasReferences = YConfiguration.getBoolean(config, "enableAliasReferences", false);
        enableXtceNameRestrictions = YConfiguration.getBoolean(config, "enableXtceNameRestrictions", true);
    }

    public V7Loader(String filename) {
        super(filename);
        ctx.file = new File(filename).getName();
    }

    public V7Loader(Map<String, Object> config, Workbook workbook) {
        this(config);
        this.workbook = workbook;
    }

    @Override
    public String getConfigName() {
        return ctx.file;
    }

    @Override
    public SpaceSystem load() {
        log.info("Loading spreadsheet {}", path);
        try {
            // Given path may be relative, so use absolute path to report issues
            File ssFile = new File(path);
            if (!ssFile.exists()) {
                throw new FileNotFoundException(ssFile.getAbsolutePath());
            }
            WorkbookSettings ws = new WorkbookSettings();
            ws.setEncoding("Cp1252");
            workbook = Workbook.getWorkbook(ssFile, ws);
        } catch (BiffException | IOException e) {
            throw new SpreadsheetLoadException(ctx, e);
        }
        headers = readHeaders(workbook);
        try {
            loadSheets();
        } catch (SpreadsheetLoadException e) {
            throw e;
        } catch (Exception e) {
            throw new SpreadsheetLoadException(ctx, e);
        }

        return rootSpaceSystem;
    }

    protected void loadSheets() throws SpreadsheetLoadException {
        loadGeneralSheet(true);
        loadChangelogSheet(false);

        // filter all sheets with names ending in the standard names SUBSYSTEM_SHEET_NAMES
        List<String> relevantSheets = Arrays.stream(workbook.getSheetNames()).filter(sheetName -> {
            return Arrays.stream(SUBSYSTEM_SHEET_NAMES).filter(s -> sheetName.endsWith(s)).findAny().isPresent();
        }).collect(Collectors.toList());

        // create all subsystems
        for (String s : relevantSheets) {
            String[] a = s.split("\\|");
            SpaceSystem ss = rootSpaceSystem;
            for (int i = 0; i < a.length - 1; i++) {
                SpaceSystem ss1 = ss.getSubsystem(a[i]);
                if (ss1 == null) {
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
        loadDataTypesSheet(spaceSystem, sheetNamePrefix + SHEET_DATATYPES);
        loadParametersSheet(spaceSystem, sheetNamePrefix + SHEET_TELEMETERED_PARAMETERS, DataSource.TELEMETERED);
        loadParametersSheet(spaceSystem, sheetNamePrefix + SHEET_DERIVED_PARAMETERS, DataSource.DERIVED);
        loadParametersSheet(spaceSystem, sheetNamePrefix + SHEET_LOCAL_PARAMETERS, DataSource.LOCAL);
        loadContainersSheet(spaceSystem, sheetNamePrefix + SHEET_CONTAINERS);
        loadAlgorithmsSheet(spaceSystem, sheetNamePrefix + SHEET_ALGORITHMS);
        loadAlarmsSheet(spaceSystem, sheetNamePrefix + SHEET_ALARMS);
        loadCommandSheet(spaceSystem, sheetNamePrefix + SHEET_COMMANDS);
        loadCommandOptionsSheet(spaceSystem, sheetNamePrefix + SHEET_COMMANDOPTIONS);
        loadCommandVerificationSheet(spaceSystem, sheetNamePrefix + SHEET_COMMANDVERIFICATION);

        for (SpaceSystem ss : spaceSystem.getSubSystems()) {
            String prefix = sheetNamePrefix.isEmpty() ? ss.getName() + "|" : sheetNamePrefix + ss.getName() + "|";
            loadSpaceSystem(prefix, ss);
        }
    }

    protected void loadGeneralSheet(boolean required) {
        Sheet sheet = switchToSheet(SHEET_GENERAL, required);
        if (sheet == null) {
            return;
        }
        Cell[] cells = jumpToRow(sheet, 1);

        // Version check
        String version = cells[0].getContents();
        // Specific versions supported
        boolean supported = false;
        for (String supportedVersion : FORMAT_VERSIONS_SUPPORTED) {
            if (version.equals(supportedVersion)) {
                supported = true;
            }
        }
        // If not explicitly supported, check major version number...
        if (!supported) {
            String sheetCompatVersion = version.substring(0, version.indexOf('.'));
            String loaderCompatVersion = FORMAT_VERSION.substring(0, FORMAT_VERSION.indexOf('.'));
            supported = loaderCompatVersion.equals(sheetCompatVersion);
            // If major version number matches, but minor number differs
            if (supported && !FORMAT_VERSION.equals(version)) {
                log.info("Some spreadsheet features for '{}' may not be supported by this loader: "
                        + "Spreadsheet version (%) differs from loader supported version (%s)", ctx.file, version,
                        FORMAT_VERSION);
            }
        }
        if (!supported) {
            throw new SpreadsheetLoadException(ctx,
                    String.format("Format version (%s) not supported by loader version (%s)", version, FORMAT_VERSION));
        }
        fileFormatVersion = version;
        if (!hasColumn(cells, 1)) {
            throw new SpreadsheetLoadException(ctx, "No value provided for the system name");
        }
        String name = cells[1].getContents();
        rootSpaceSystem = new SpaceSystem(name);

        // Add a header
        Header header = new Header();
        rootSpaceSystem.setHeader(header);
        if (cells.length >= 3) {
            header.setVersion(cells[2].getContents());
        }
        try {
            File wbf = new File(path);
            Date d = new Date(wbf.lastModified());
            String date = (new SimpleDateFormat("yyyy/DDD HH:mm:ss")).format(d);
            header.setDate(date);
        } catch (Exception e) {
            // Ignore
        }
    }

    protected void loadCalibrationSheet(SpaceSystem spaceSystem, String sheetName) {
        // read the calibrations
        Sheet sheet = switchToSheet(sheetName, false);
        if (sheet == null) {
            return;
        }
        requireColumn(CN_CALIB_NAME);
        requireColumn(CN_CALIB_TYPE);

        double[] pol_coef = null;
        // SplinePoint = pointpair
        ArrayList<SplinePoint> spline = null;
        EnumerationDefinition enumeration = null;
        // start at 1 to not use the first line (= title line)
        int idxContext = h.containsKey(CN_CONTEXT) ? h.get(CN_CONTEXT) : -1;
        int n = 1;
        while (true) {
            Range r = findRange(sheet, n, h.get(CN_CALIB_NAME), idxContext);
            if (r == null) {
                break;
            }
            n = r.lastRow;
            Cell[] cells = jumpToRow(sheet, r.firstRow);
            String name = getContent(cells, CN_CALIB_NAME);

            for (Range sr : r.subRanges) {
                cells = jumpToRow(sheet, sr.firstRow);
                MatchCriteria context = null;
                if (hasColumn(cells, CN_CONTEXT)) {
                    String contextString = getContent(cells, CN_CONTEXT);
                    context = toMatchCriteria(spaceSystem, contextString);
                }
                String type = getContent(cells, CN_CALIB_TYPE);

                if ("pointpair".equalsIgnoreCase(type)) {
                    type = CALIB_TYPE_SPLINE;
                }
                if (CALIB_TYPE_ENUMERATION.equalsIgnoreCase(type)) {
                    if (context != null) {
                        throw new SpreadsheetLoadException(ctx, "Context calibrators not supported for enumerations");
                    }
                    enumeration = new EnumerationDefinition();
                } else if (CALIB_TYPE_POLYNOMIAL.equalsIgnoreCase(type)) {
                    pol_coef = new double[sr.lastRow - sr.firstRow];
                } else if (CALIB_TYPE_SPLINE.equalsIgnoreCase(type)) {
                    spline = new ArrayList<>();
                } else if (CALIB_TYPE_JAVA_EXPRESSION.equalsIgnoreCase(type)) {
                    cells = jumpToRow(sheet, sr.firstRow);
                    if (sr.lastRow != sr.firstRow + 1) {
                        throw new SpreadsheetLoadException(ctx, "Java formula must be specified on one line");
                    }
                    if (!hasColumn(cells, CN_CALIB_CALIB1)) {
                        throw new SpreadsheetLoadException(ctx, "Java formula must be specified on the CALIB1 column");
                    }
                    String javaFormula = getContent(cells, CN_CALIB_CALIB1);
                    addCalibrator(name, context, getJavaCalibrator(javaFormula));
                } else if (CALIB_TYPE_TIME.equalsIgnoreCase(type)) {
                    if (context != null) {
                        throw new SpreadsheetLoadException(ctx, "Context calibrators not supported for time");
                    }
                    cells = jumpToRow(sheet, sr.firstRow);
                    if (sr.lastRow != sr.firstRow + 1) {
                        throw new SpreadsheetLoadException(ctx, "Time encoding must be specified on one line");
                    }
                    if (!hasColumn(cells, CN_CALIB_CALIB1)) {
                        throw new SpreadsheetLoadException(ctx,
                                "Reference epoch or parameter must be specified on the CALIB1 column");
                    }
                    timeCalibEpochs.put(name, getContent(cells, CN_CALIB_CALIB1));
                    timeCalibContexts.put(name, ctx.copy());
                    if (hasColumn(cells, CN_CALIB_CALIB2)) {
                        timeCalibScales.put(name, getContent(cells, CN_CALIB_CALIB2));
                    }
                } else {
                    throw new SpreadsheetLoadException(ctx,
                            "Calibration type '" + type + "' not supported. Supported types: "
                                    + Arrays.asList(CALIB_TYPE_ENUMERATION, CALIB_TYPE_POLYNOMIAL, CALIB_TYPE_SPLINE,
                                            CALIB_TYPE_JAVA_EXPRESSION, CALIB_TYPE_TIME));
                }

                for (int j = sr.firstRow; j < sr.lastRow; j++) {
                    cells = jumpToRow(sheet, j);
                    if (CALIB_TYPE_ENUMERATION.equalsIgnoreCase(type)) {
                        try {
                            long raw = Integer.decode(getContent(cells, CN_CALIB_CALIB1));
                            enumeration.valueMap.put(raw, getContent(cells, CN_CALIB_CALIB2));
                        } catch (NumberFormatException e) {
                            throw new SpreadsheetLoadException(ctx, "Can't get integer from raw value out of '"
                                    + getContent(cells, CN_CALIB_CALIB1) + "'");
                        }
                    } else if (CALIB_TYPE_POLYNOMIAL.equalsIgnoreCase(type)) {
                        pol_coef[j - sr.firstRow] = parseDouble(getCell(cells, CN_CALIB_CALIB1));
                    } else if (CALIB_TYPE_SPLINE.equalsIgnoreCase(type)) {
                        spline.add(new SplinePoint(parseDouble(getCell(cells, CN_CALIB_CALIB1)),
                                parseDouble(getCell(cells, CN_CALIB_CALIB2))));
                    }
                }
                if (CALIB_TYPE_ENUMERATION.equalsIgnoreCase(type)) {
                    enumerations.put(name, enumeration);
                } else if (CALIB_TYPE_POLYNOMIAL.equalsIgnoreCase(type)) {
                    addCalibrator(name, context, new PolynomialCalibrator(pol_coef));
                } else if (CALIB_TYPE_SPLINE.equalsIgnoreCase(type)) {
                    addCalibrator(name, context, new SplineCalibrator(spline));
                }
            }
        }
    }

    private void addCalibrator(String name, MatchCriteria context, Calibrator c) {
        if (context == null) {
            if (calibrators.containsKey(name)) {
                throw new SpreadsheetLoadException(ctx, "There is already a calibrator named '" + name + "' defined");
            }
            calibrators.put(name, c);
        } else {

            List<ContextCalibrator> l = contextCalibrators.computeIfAbsent(name, k -> new ArrayList<>());
            l.add(new ContextCalibrator(context, c));
        }
    }

    private void requireColumn(String colName) throws SpreadsheetLoadException {
        if (!h.containsKey(colName)) {
            throw new SpreadsheetLoadException(ctx, "Ssheet does not contain required column '" + colName + "'");
        }
    }

    protected void loadDataTypesSheet(SpaceSystem spaceSystem, String sheetName) {
        Sheet sheet = switchToSheet(sheetName, false);
        if (sheet == null) {
            return;
        }
        for (int i = 1; i < sheet.getRows(); i++) {
            Cell[] cells = jumpToRow(sheet, i);
            if (isEmptyOrCommentedOut(cells)) {
                continue;
            }
            if (!hasColumn(cells, CN_DTYPE_NAME)) {
                continue;
            }
            DataTypeRecord dtr = new DataTypeRecord();
            dtr.row = i;
            dtr.name = getContent(cells, CN_DTYPE_NAME);
            if (dataTypes.containsKey(dtr.name)) {
                throw new SpreadsheetLoadException(ctx, "There is already a type with the name '" + dtr.name + "'");
            }
            validateNameType(dtr.name);

            dtr.engType = getContent(cells, CN_DTYPE_ENGTYPE).trim();
            dtr.rawType = getContent(cells, CN_DTYPE_RAWTYPE, null);
            dtr.encoding = getContent(cells, CN_DTYPE_ENCODING, null);
            dtr.engUnit = getContent(cells, CN_DTYPE_ENGUNIT, null);
            String calib = getContent(cells, CN_DTYPE_CALIBRATION, null);
            if ("n".equals(calib) || "".equals(calib)) {
                calib = null;
            } else if ("y".equalsIgnoreCase(calib)) {
                calib = dtr.name;
            }
            dtr.calibration = calib;
            dataTypes.put(dtr.name, dtr);
        }
    }

    protected DataType createDataType(SpaceSystem spaceSystem, DataTypeRecord dtr, boolean param) {

        String name = dtr.name;
        String engtype = dtr.engType;
        String rawtype = dtr.rawType;
        String encodings = dtr.encoding;
        String calib = dtr.calibration;
        String units = dtr.engUnit;

        DataType dtype = createParamOrArgType(spaceSystem, name, engtype, param);

        if (units != null && dtype instanceof BaseDataType) {
            UnitType unitType = new UnitType(units);
            ((BaseDataType) dtype).addUnit(unitType);
        }

        DataEncoding encoding = getDataEncoding(spaceSystem, ctx, "Data type " + name, rawtype,
                engtype, encodings, calib);

        if (dtype instanceof IntegerDataType) {
            // Integers can be encoded as strings
            if (encoding instanceof StringDataEncoding) {
                // Create a new int encoding which uses the configured string encoding
                IntegerDataEncoding intStringEncoding = new IntegerDataEncoding(name,
                        ((StringDataEncoding) encoding));
                if (calib != null) {
                    Calibrator c = calibrators.get(calib);
                    if (c == null) {
                        throw new SpreadsheetLoadException(ctx, "Parameter " + name + " specified calibrator '"
                                + calib + "' but the calibrator does not exist");
                    }
                    intStringEncoding.setDefaultCalibrator(c);
                }
                ((IntegerDataType) dtype).setEncoding(intStringEncoding);
            } else {
                ((IntegerDataType) dtype).setEncoding(encoding);
            }
        } else if (dtype instanceof FloatDataType) {
            // Floats can be encoded as strings
            if (encoding instanceof StringDataEncoding) {
                // Create a new float encoding which uses the configured string encoding
                FloatDataEncoding floatStringEncoding = new FloatDataEncoding(((StringDataEncoding) encoding));
                if (calib != null) {
                    Calibrator c = calibrators.get(calib);
                    if (c == null) {
                        throw new SpreadsheetLoadException(ctx, "Parameter " + name + " specified calibrator '"
                                + calib + "' but the calibrator does not exist.");
                    } else {
                        floatStringEncoding.setDefaultCalibrator(c);
                    }
                }
                ((FloatDataType) dtype).setEncoding(floatStringEncoding);
            } else {
                ((FloatDataType) dtype).setEncoding(encoding);
            }
        } else if (dtype instanceof EnumeratedDataType) {
            EnumeratedDataType edtype = (EnumeratedDataType) dtype;
            // Enumerations encoded as string integers
            if (encoding instanceof StringDataEncoding) {
                IntegerDataEncoding intStringEncoding = new IntegerDataEncoding(name,
                        ((StringDataEncoding) encoding));
                // Don't set calibrator, already done when making ptype
                ((BaseDataType) dtype).setEncoding(intStringEncoding);
            } else {
                ((BaseDataType) dtype).setEncoding(encoding);
            }
            if (calib == null) {
                throw new SpreadsheetLoadException(ctx, "Data type " + name
                        + " has to have an enumeration (specified in the " + CN_DTYPE_CALIBRATION + " column");
            }
            EnumerationDefinition enumeration = enumerations.get(calib);
            if (enumeration == null) {
                throw new SpreadsheetLoadException(ctx, "Data type " + name
                        + " is supposed to have an enumeration '" + calib + "' but the enumeration does not exist");
            }
            for (Entry<Long, String> entry : enumeration.valueMap.entrySet()) {
                edtype.addEnumerationValue(entry.getKey(), entry.getValue());
            }
        } else if (dtype instanceof AbsoluteTimeDataType) {
            ((AbsoluteTimeDataType) dtype).setEncoding(encoding);
            populateTimeParameter(spaceSystem, (AbsoluteTimeDataType) dtype, calib);
        } else if (dtype instanceof AggregateDataType) {
            if (encodings != null || rawtype != null) {
                throw new SpreadsheetLoadException(ctx,
                        name + ": encoding or raw type cannot be specified for aggregate data types");
            }
        } else if (dtype instanceof ArrayDataType) {
            if (encodings != null || rawtype != null) {
                throw new SpreadsheetLoadException(ctx,
                        name + ": encoding or raw type cannot be specified for array data types");
            }
        } else {
            ((BaseDataType) dtype).setEncoding(encoding);
        }
        /*
         * if (hasColumn(cells, CN_DTYPE_DESCRIPTION)) {
         * if (hasColumn(cells, CN_DTYPE_DESCRIPTION)) {
         * String shortDescription = getContent(cells, CN_DTYPE_DESCRIPTION);
         * // ptype.setShortDescription(shortDescription);
         * }
         * }
         */
        return dtype;
    }

    // creates either a ParameterDataType or ArgumentDataType
    private DataType createParamOrArgType(SpaceSystem spaceSystem, String name, String engtype, boolean param) {
        if ("uint".equalsIgnoreCase(engtype)) {
            engtype = PARAM_ENGTYPE_UINT32;
        } else if ("int".equalsIgnoreCase(engtype)) {
            engtype = PARAM_ENGTYPE_INT32;
        }

        DataType ptype = null;
        if (PARAM_ENGTYPE_UINT32.equalsIgnoreCase(engtype)) {
            ptype = param ? new IntegerParameterType(name) : new IntegerArgumentType(name);
            ((IntegerDataType) ptype).setSigned(false);
        } else if (PARAM_ENGTYPE_UINT64.equalsIgnoreCase(engtype)) {
            ptype = param ? new IntegerParameterType(name) : new IntegerArgumentType(name);
            ((IntegerDataType) ptype).setSigned(false);
            ((IntegerDataType) ptype).setSizeInBits(64);
        } else if (PARAM_ENGTYPE_INT32.equalsIgnoreCase(engtype)) {
            ptype = param ? new IntegerParameterType(name) : new IntegerArgumentType(name);
        } else if (PARAM_ENGTYPE_INT64.equalsIgnoreCase(engtype)) {
            ptype = param ? new IntegerParameterType(name) : new IntegerArgumentType(name);
            ((IntegerDataType) ptype).setSizeInBits(64);
        } else if (PARAM_ENGTYPE_FLOAT.equalsIgnoreCase(engtype)) {
            ptype = param ? new FloatParameterType(name) : new FloatArgumentType(name);
        } else if (PARAM_ENGTYPE_DOUBLE.equalsIgnoreCase(engtype)) {
            ptype = param ? new FloatParameterType(name) : new FloatArgumentType(name);
            ((FloatDataType) ptype).setSizeInBits(64);
        } else if (PARAM_ENGTYPE_ENUMERATED.equalsIgnoreCase(engtype)) {
            ptype = param ? new EnumeratedParameterType(name) : new EnumeratedArgumentType(name);
        } else if (PARAM_ENGTYPE_STRING.equalsIgnoreCase(engtype)) {
            ptype = param ? new StringParameterType(name) : new StringArgumentType(name);
        } else if (PARAM_ENGTYPE_BOOLEAN.equalsIgnoreCase(engtype)) {
            ptype = param ? new BooleanParameterType(name) : new BooleanArgumentType(name);
        } else if (PARAM_ENGTYPE_BINARY.equalsIgnoreCase(engtype)) {
            ptype = param ? new BinaryParameterType(name) : new BinaryArgumentType(name);
        } else if (PARAM_ENGTYPE_TIME.equalsIgnoreCase(engtype)) {
            if (!param) {
                throw new SpreadsheetLoadException(ctx, "Absolute time arguments are not supported");
            }
            ptype = new AbsoluteTimeParameterType(name);
        } else if (engtype.startsWith("{")) {
            if (!engtype.endsWith("}")) {
                throw new SpreadsheetLoadException(ctx, "Missing ending { from the aggregate");
            }
            ptype = createAggregateType(spaceSystem, name, engtype, param);
        } else if (engtype.endsWith("]")) {
            ptype = createArrayType(spaceSystem, name, engtype, param);
        } else {
            throw new SpreadsheetLoadException(ctx, "Unknown engineering type '" + engtype + "'");
        }
        return ptype;
    }

    private DataType createAggregateType(SpaceSystem spaceSystem, String name, String engtype, boolean param) {
        AggregateDataType atype = param ? new AggregateParameterType(name) : new AggregateArgumentType(name);
        List<AggrMember> l = parseAggregateExpr(engtype);
        for (AggrMember m : l) {
            validateNameType(m.name);
            DataTypeRecord dtr = dataTypes.get(m.dataType);
            if (dtr == null) {
                throw new SpreadsheetLoadException(ctx,
                        "Aggregate " + name + " makes reference to unknown type '" + m.dataType);
            }
            DataType dtype = createDataType(spaceSystem, dtr, param);
            Member member = new Member(m.name);
            member.setDataType(dtype);
            atype.addMember(member);
        }
        return atype;
    }

    Pattern arrayPattern = Pattern.compile("(\\w+)(\\[\\d*\\])+");
    Pattern sqBracket = Pattern.compile("\\[\\d*\\]");

    private DataType createArrayType(SpaceSystem spaceSystem, String name, String engtype, boolean param) {
        ArrayDataType atype = param ? new ArrayParameterType(name) : new ArrayArgumentType(name);
        Matcher m = arrayPattern.matcher(engtype);
        if (!m.matches()) {
            throw new SpreadsheetLoadException(ctx, "Cannot match array '" + engtype + "'");
        }
        DataTypeRecord dtr = dataTypes.get(m.group(1));
        if (dtr == null) {
            throw new SpreadsheetLoadException(ctx,
                    "Array " + name + " makes reference to unknown type '" + m.group(1));
        }
        m = sqBracket.matcher(engtype);
        int c = 0;
        while (m.find()) {
            c++;
        }
        atype.setNumberOfDimensions(c);
        atype.setElementType(createDataType(spaceSystem, dtr, param));
        return atype;
    }

    protected void loadParametersSheet(SpaceSystem spaceSystem, String sheetName, DataSource dataSource) {
        Sheet sheet = switchToSheet(sheetName, false);
        if (sheet == null) {
            return;
        }
        Cell[] firstRow = jumpToRow(sheet, 0);
        for (int i = 1; i < sheet.getRows(); i++) {
            Cell[] cells = jumpToRow(sheet, i);
            if (isEmptyOrCommentedOut(cells)) {
                continue;
            }
            if (!hasColumn(cells, CN_PARAM_NAME)) {
                continue;
            }
            String name = getContent(cells, CN_PARAM_NAME);
            validateNameType(name);
            String dtype = getContent(cells, CN_PARAM_DTYPE);
            DataTypeRecord dtr = dataTypes.get(dtype);
            if (dtr == null) {
                throw new SpreadsheetLoadException(ctx, "Cannot find a  data type on name '" + dtype + "'");
            }
            ParameterType ptype = (ParameterType) createDataType(spaceSystem, dtr, true);
            final Parameter param = new Parameter(name);
            parameters.put(name, param);
            param.setParameterType(ptype);
            param.setDataSource(dataSource);

            XtceAliasSet xas = getAliases(firstRow, cells);
            if (xas != null) {
                param.setAliasSet(xas);
            }
            spaceSystem.addParameter(param);
        }
    }

    private void populateTimeParameter(SpaceSystem spaceSystem, AbsoluteTimeDataType ptype, String calib) {
        if (calib == null) {
            return;
        }
        SpreadsheetLoadContext ctx1 = timeCalibContexts.get(calib);
        String ref = timeCalibEpochs.get(calib);
        if (ref.startsWith("epoch:")) {
            String epochs = ref.substring(6).toUpperCase();
            try {
                TimeEpoch.CommonEpochs ce = TimeEpoch.CommonEpochs.valueOf(epochs);
                ReferenceTime rt = new ReferenceTime(new TimeEpoch(ce));
                ptype.setReferenceTime(rt);
            } catch (IllegalArgumentException e) {
                throw new SpreadsheetLoadException(ctx1,
                        "Invalid epoch '" + epochs + "'for time calibration. Known epochs are "
                                + Arrays.toString(TimeEpoch.CommonEpochs.values()));
            }
        } else if (ref.toLowerCase().startsWith("parameter:")) {
            String paraRefName = ref.substring(10);
            NameReference paramRef = getParameterReference(spaceSystem, paraRefName, false);
            final ParameterInstanceRef parameterInstance = new ParameterInstanceRef();
            paramRef.addResolvedAction(nd -> {
                parameterInstance.setParameter((Parameter) nd);
                return true;
            });
            ReferenceTime rt = new ReferenceTime(parameterInstance);
            ptype.setReferenceTime(rt);

        } else {
            throw new SpreadsheetLoadException(ctx1, "Invalid epoch reference '" + ref
                    + "' for time calibration. It has to start with 'epoch:' or 'parameter:'");
        }

        String scaling = timeCalibScales.get(calib);
        if (scaling != null) {
            String[] a = scaling.split(":");
            if (a.length != 2) {
                throw new SpreadsheetLoadException(ctx1,
                        "Invalid scaling '" + scaling + "' for time calibration. Please use 'offset:scale'.");
            }
            try {
                double offset = Double.parseDouble(a[0]);
                double scale = Double.parseDouble(a[1]);
                ptype.setScaling(true, offset, scale);

            } catch (NumberFormatException e) {
                throw new SpreadsheetLoadException(ctx1,
                        "Invalid scaling '" + scaling + "'for time calibration. Please use 'offset:scale'.");
            }
        }

    }

    DataEncoding getDataEncoding(SpaceSystem spaceSystem, SpreadsheetLoadContext ctx, String paraArgDescr,
            String rawtype, String engtype, String encodings, String calib) {

        if ((rawtype == null) || rawtype.isEmpty()) {
            // Raw type is optional if the parameter is not part of a container
            // However a calibration is associated to a raw type
            if (calib != null) {
                throw new SpreadsheetLoadException(ctx, paraArgDescr + ": calibration specified without raw type");
            }
            return null;
        }

        if ("bytestream".equalsIgnoreCase(rawtype)) {
            rawtype = PARAM_RAWTYPE_BINARY;
        }

        if (encodings == null || PARAM_ENCODING_PATTERN_old.matcher(encodings).matches()) {
            int bitsize = -1;
            if (encodings != null) {
                bitsize = Integer.parseInt(encodings);
            }
            RawTypeEncoding rte = oldToNewEncoding(ctx, bitsize, rawtype);
            rawtype = rte.rawType;
            encodings = rte.encoding;
        }
        String encodingType = "";
        String[] encodingArgs = {};

        if (encodings != null) {
            Matcher m = PARAM_ENCODING_PATTERN.matcher(encodings);
            if (!m.matches()) {
                throw new SpreadsheetLoadException(ctx,
                        "Invalid encoding '" + encodings + "' has to match pattern: " + PARAM_ENCODING_PATTERN);
            }
            encodingType = m.group(1);
            encodingArgs = m.group(2).split("\\s*,\\s*");
        }
        int customBitLength = -1;
        NameReference customFromBinaryTransform = null;

        if ("custom".equalsIgnoreCase(encodingType)) {
            if (encodingArgs.length > 2 || encodingArgs.length < 1) {
                throw new SpreadsheetLoadException(ctx,
                        "Invalid specification of custom encoding. Use 'custom(<n>, a.b.c.ClassName)");
            }
            String algoName;
            if (encodingArgs.length == 2) {
                customBitLength = parseInt(encodingArgs[0]);
                algoName = encodingArgs[1];
            } else {
                algoName = encodingArgs[0];
            }
            customFromBinaryTransform = new UnresolvedNameReference(algoName, Type.ALGORITHM);
            spaceSystem.addUnresolvedReference(customFromBinaryTransform);
            customFromBinaryTransform.addResolvedAction(nd -> {
                ((Algorithm) nd).setScope(Scope.CONTAINER_PROCESSING);
                return true;
            });
        }
        DataEncoding encoding = null;
        if (PARAM_RAWTYPE_INT.equalsIgnoreCase(rawtype) || PARAM_RAWTYPE_UINT.equalsIgnoreCase(rawtype)) {
            if (customFromBinaryTransform != null) {
                IntegerDataEncoding e = new IntegerDataEncoding(customBitLength);
                customFromBinaryTransform.addResolvedAction(nd -> {
                    e.setFromBinaryTransformAlgorithm((Algorithm) nd);
                    return true;
                });
                encoding = e;
            } else {
                if (encodingArgs.length == 0) {
                    throw new SpreadsheetLoadException(ctx, "Size in bits mandatory for int encoding.");
                }
                int bitlength = parseInt(encodingArgs[0]);
                encoding = new IntegerDataEncoding(bitlength);
                ((IntegerDataEncoding) encoding).setEncoding(getIntegerEncoding(ctx, encodingType));
                if (encodingArgs.length > 1) {
                    ((IntegerDataEncoding) encoding).setByteOrder(getByteOrder(ctx, encodingArgs[1]));
                }
            }

            if (calib != null && !PARAM_ENGTYPE_ENUMERATED.equalsIgnoreCase(engtype)
                    && !PARAM_ENGTYPE_TIME.equalsIgnoreCase(engtype)) {
                ((IntegerDataEncoding) encoding).setDefaultCalibrator(getNumberCalibrator(paraArgDescr, calib));
                ((IntegerDataEncoding) encoding).setContextCalibratorList(contextCalibrators.get(calib));
            }
        } else if (PARAM_RAWTYPE_FLOAT.equalsIgnoreCase(rawtype)) {
            if (customFromBinaryTransform != null) {
                FloatDataEncoding e = new FloatDataEncoding(customBitLength);
                customFromBinaryTransform.addResolvedAction(nd -> {
                    e.setFromBinaryTransformAlgorithm((Algorithm) nd);
                    return true;
                });
                encoding = e;
            } else {
                if (encodingArgs.length == 0) {
                    throw new SpreadsheetLoadException(ctx, "Size in bits mandatory for float encoding.");
                }
                int bitlength = parseInt(encodingArgs[0]);
                ByteOrder byteOrder = ByteOrder.BIG_ENDIAN;
                if (encodingArgs.length > 1) {
                    byteOrder = getByteOrder(ctx, encodingArgs[1]);
                }
                encoding = new FloatDataEncoding(bitlength, byteOrder, getFloatEncoding(ctx, encodingType));
            }
            if (calib != null && !PARAM_ENGTYPE_ENUMERATED.equalsIgnoreCase(engtype)
                    && !PARAM_ENGTYPE_TIME.equalsIgnoreCase(engtype)) {
                ((FloatDataEncoding) encoding).setDefaultCalibrator(getNumberCalibrator(paraArgDescr, calib));
                ((FloatDataEncoding) encoding).setContextCalibratorList(contextCalibrators.get(calib));
            }
        } else if (PARAM_RAWTYPE_BOOLEAN.equalsIgnoreCase(rawtype)) {
            if (customFromBinaryTransform != null) {
                BooleanDataEncoding e = new BooleanDataEncoding();
                e.setSizeInBits(customBitLength);
                customFromBinaryTransform.addResolvedAction(nd -> {
                    e.setFromBinaryTransformAlgorithm((Algorithm) nd);
                    return true;
                });
                encoding = e;
            } else {
                if (encodings != null) {
                    throw new SpreadsheetLoadException(ctx,
                            "Encoding is not allowed for boolean parameters. Use any other raw type if you want to specify the bitlength");
                }
                encoding = new BooleanDataEncoding();
            }
        } else if (PARAM_RAWTYPE_STRING.equalsIgnoreCase(rawtype)) {
            String charset = "UTF-8";
            if (!"custom".equalsIgnoreCase(encodingType)) {
                if (encodingArgs.length > 2) {
                    charset = encodingArgs[1];
                    try {
                        Charset.forName(charset);
                    } catch (IllegalCharsetNameException e) {
                        throw new SpreadsheetLoadException(ctx, "Unsupported charset '" + charset + "'");
                    }
                }
            }
            if (customFromBinaryTransform != null) {
                StringDataEncoding e = new StringDataEncoding(SizeType.CUSTOM);
                e.setSizeInBits(customBitLength);
                customFromBinaryTransform.addResolvedAction(nd -> {
                    e.setFromBinaryTransformAlgorithm((Algorithm) nd);
                    return true;
                });
                encoding = e;
            } else if ("fixed".equalsIgnoreCase(encodingType)) {
                if (encodingArgs.length == 0) {
                    throw new SpreadsheetLoadException(ctx, "Encodings for fixed strings need to specify size in bits");
                }
                encoding = new StringDataEncoding(SizeType.FIXED);
                int bitlength = parseInt(encodingArgs[0]);
                encoding.setSizeInBits(bitlength);
            } else if ("terminated".equalsIgnoreCase(encodingType)) {
                if (encodingArgs.length == 0) {
                    throw new SpreadsheetLoadException(ctx,
                            "Encodings for terminated strings need to specify termination char");
                }
                encoding = new StringDataEncoding(SizeType.TERMINATION_CHAR);
                ((StringDataEncoding) encoding).setTerminationChar(parseByte(ctx, encodingArgs[0]));
                if (encodingArgs.length >= 3) {
                    encoding.setSizeInBits(parseInt(encodingArgs[2]));
                }
            } else if ("PrependedSize".equalsIgnoreCase(encodingType)) {
                if (encodingArgs.length == 0) {
                    throw new SpreadsheetLoadException(ctx,
                            "Encodings for PrependedSize strings need to specify the size in bits of the size tag.");
                }
                encoding = new StringDataEncoding(SizeType.LEADING_SIZE);
                ((StringDataEncoding) encoding).setSizeInBitsOfSizeTag(parseInt(encodingArgs[0]));
                if (encodingArgs.length >= 3) {
                    encoding.setSizeInBits(parseInt(encodingArgs[2]));
                }
            } else {
                throw new SpreadsheetLoadException(ctx, "Unsupported encoding type " + encodingType
                        + " Use one of 'fixed', 'terminated', 'PrependedSize' or 'custom'");
            }
            ((StringDataEncoding) encoding).setEncoding(charset);
        } else if (PARAM_RAWTYPE_BINARY.equalsIgnoreCase(rawtype)) {
            if (customFromBinaryTransform != null) {
                BinaryDataEncoding e = new BinaryDataEncoding(BinaryDataEncoding.Type.CUSTOM);
                e.setSizeInBits(customBitLength);
                customFromBinaryTransform.addResolvedAction(nd -> {
                    e.setFromBinaryTransformAlgorithm((Algorithm) nd);
                    return true;
                });
                encoding = e;
            } else if ("fixed".equalsIgnoreCase(encodingType)) {
                if (encodingArgs.length == 0) {
                    throw new SpreadsheetLoadException(ctx, "Encodings for fixed strings need to specify size in bits");
                }
                encoding = new BinaryDataEncoding(BinaryDataEncoding.Type.FIXED_SIZE);
                int bitlength = parseInt(encodingArgs[0]);
                encoding.setSizeInBits(bitlength);
            } else if ("PrependedSize".equalsIgnoreCase(encodingType)) {
                if (encodingArgs.length == 0) {
                    throw new SpreadsheetLoadException(ctx,
                            "Encodings for PrependedSize strings need to specify the size in bits of the size tag.");
                }
                encoding = new BinaryDataEncoding(BinaryDataEncoding.Type.LEADING_SIZE);
                ((BinaryDataEncoding) encoding).setSizeInBitsOfSizeTag(parseInt(encodingArgs[0]));
            } else {
                throw new SpreadsheetLoadException(ctx, "Unsupported encoding type " + encodingType
                        + " Use one of 'fixed', 'PrependedSize' or 'custom'");
            }
        } else {
            throw new SpreadsheetLoadException(ctx, "Invalid rawType '" + rawtype + "'");
        }
        return encoding;
    }

    private Calibrator getNumberCalibrator(String paraArgDescr, String calibName) {
        Calibrator c = calibrators.get(calibName);
        if (c != null) {
            return c;
        }
        if (!contextCalibrators.containsKey(calibName)) {
            throw new SpreadsheetLoadException(ctx, paraArgDescr + " is supposed to have a calibrator '" + calibName
                    + "' but the calibrator does not exist.");
        }
        return null;
    }

    JavaExpressionCalibrator getJavaCalibrator(String javaFormula) {
        JavaExpressionCalibrator jec = new JavaExpressionCalibrator(javaFormula);
        try {
            JavaExpressionCalibratorFactory.compile(jec);
        } catch (IllegalArgumentException e) {
            throw new SpreadsheetLoadException(ctx, e.getMessage());
        }
        return jec;
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
        for (int i = 0; i < n; i++) {
            if (!isEmpty(firstRow[i]) && firstRow[i].getContents().startsWith("namespace:")
                    && !isEmpty(cells[i])) {
                if (xas == null) {
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
        return (cell == null) || (cell.getContents().isEmpty());
    }

    protected void loadContainersSheet(SpaceSystem spaceSystem, String sheetName) {
        Sheet sheet = switchToSheet(sheetName, false);
        if (sheet == null) {
            return;
        }
        containers.clear();
        HashMap<String, String> parents = new HashMap<>();
        Cell[] firstRow = jumpToRow(sheet, 0);

        for (int i = 1; i < sheet.getRows(); i++) {
            // search for a new packet definition, starting from row i
            // (explanatory note, i is incremented inside this loop too, and that's why the following 4 lines work)
            Cell[] cells = jumpToRow(sheet, i);
            if (cells == null || cells.length < 1) {
                log.trace("Ignoring line {} because it's empty", ctx.row);
                continue;
            }
            if (cells[0].getContents().equals("") || cells[0].getContents().startsWith("#")) {
                log.trace("Ignoring line {} because first cell is empty or starts with '#'", ctx.row);
                continue;
            }
            // at this point, cells contains the data (name, path, ...) of either
            // a) a sub-container (inherits from another packet)
            // b) an aggregate container (which will be used as if it were a measurement, by other (sub)containers)
            String containerName = getContent(cells, CN_CONT_NAME);
            String parent = null;
            String condition = null;
            if (hasColumn(cells, CN_CONT_PARENT)) {
                parent = getContent(cells, CN_CONT_PARENT);
                if (!hasColumn(cells, CN_CONT_CONDITION)) {
                    throw new SpreadsheetLoadException(ctx,
                            "Parent specified but without inheritance condition on container");
                }
                condition = getContent(cells, CN_CONT_CONDITION);
                parents.put(containerName, parent);
            }

            if ("".equals(parent)) {
                parent = null;
            }

            // absoluteoffset is the absolute offset of the first parameter of the container
            int absoluteoffset = -1;
            if (parent == null) {
                absoluteoffset = 0;
            } else {
                int x = parent.indexOf(':');
                if (x != -1) {
                    absoluteoffset = Integer.decode(parent.substring(x + 1));
                    parent = parent.substring(0, x);
                }
            }

            int containerSizeInBits = -1;
            if (hasColumn(cells, CN_CONT_SIZEINBITS)) {
                containerSizeInBits = Integer.decode(getContent(cells, CN_CONT_SIZEINBITS));
            }

            RateInStream rate = null;
            if (hasColumn(cells, CN_CONT_EXPECTED_INTERVAL)) {
                int expint = Integer.decode(getContent(cells, CN_CONT_EXPECTED_INTERVAL));
                rate = new RateInStream(-1, expint);
            }

            String description = "";
            if (hasColumn(cells, CN_CONT_DESCRIPTION)) {
                description = getContent(cells, CN_CONT_DESCRIPTION);
            }

            // create a new SequenceContainer that will hold the parameters (i.e. SequenceEntries) for the
            // ORDINARY/SUB/AGGREGATE packets, and register that new SequenceContainer in the containers hashmap
            SequenceContainer container = new SequenceContainer(containerName);
            container.setSizeInBits(containerSizeInBits);
            containers.put(containerName, container);
            container.setRateInStream(rate);
            if (!description.isEmpty()) {
                container.setShortDescription(description);
                container.setLongDescription(description);
            }

            if (hasColumn(cells, CN_CONT_FLAGS)) {
                String flags = getContent(cells, CN_CONT_FLAGS);
                if (flags.contains("a")) {
                    container.useAsArchivePartition(true);
                }
            }

            XtceAliasSet xas = getAliases(firstRow, cells);
            if (xas != null) {
                container.setAliasSet(xas);
            }

            // we mark the start of the command and advance to the next line, to get to the first argument (if there is
            // one)
            i++;

            // now, we start processing the parameters (or references to containers)
            int counter = 0; // sequence number of the SequenceEntrys in the SequenceContainer
            while (i < sheet.getRows()) {
                // get the next row, containing a measurement/container reference
                cells = jumpToRow(sheet, i);
                // determine whether we have not reached the end of the packet definition.
                if (!hasColumn(cells, CN_CONT_ENTRY)) {
                    break;
                }
                absoluteoffset = addEntry(container, absoluteoffset, counter, cells);
                i++;
                counter++;
            }

            // at this point, we have added all the parameters and containers to the current packets. What
            // remains to be done is link it with its base
            if (parent != null) {
                parents.put(containerName, parent);
                // the condition is parsed and used to create the container.restrictionCriteria
                // 1) get the parent, from the same sheet
                SequenceContainer sc = containers.get(parent);
                // the parent is not in the same sheet, try to get from the Xtcedb
                if (sc == null) {
                    sc = spaceSystem.getSequenceContainer(parent);
                }
                if (sc != null) {
                    container.setBaseContainer(sc);
                } else {
                    NameReference nr = new UnresolvedNameReference(parent, Type.SEQUENCE_CONTAINER)
                            .addResolvedAction(nd -> {
                                SequenceContainer sc1 = (SequenceContainer) nd;
                                container.setBaseContainer(sc1);
                                return true;
                            });
                    spaceSystem.addUnresolvedReference(nr);
                }

                // 2) extract the condition and create the restrictioncriteria
                if (!"".equals(condition.trim())) {
                    container.setRestrictionCriteria(toMatchCriteria(spaceSystem, condition));
                    MatchCriteria.printParsedMatchCriteria(log, container.getRestrictionCriteria(), "");
                }
            } else {
                if (spaceSystem.getRootSequenceContainer() == null) {
                    spaceSystem.setRootSequenceContainer(container);
                }
            }

            spaceSystem.addSequenceContainer(container);
        }
    }

    private int addEntry(SequenceContainer container, int absoluteoffset, int counter, Cell[] cells) {
        String paraname = getContent(cells, CN_CONT_ENTRY);
        int relpos = 0;
        if (hasColumn(cells, CN_CONT_RELPOS)) {
            relpos = Integer.decode(getContent(cells, CN_CONT_RELPOS));
        }

        int pos;
        ReferenceLocationType location;
        // absoluteOffset = -1 means we have to add relative entries.
        // We prefer absolute if possible because we can process them without processing the previous ones
        if (absoluteoffset != -1) {
            absoluteoffset += relpos;
            pos = absoluteoffset;
            location = ReferenceLocationType.containerStart;
        } else {
            pos = relpos;
            location = ReferenceLocationType.previousEntry;
        }
        // the repeat string will contain the number of times a measurement (or container) should be
        // repeated. It is a String because at this point it can be either a number or a reference to another
        // measurement
        String repeat = null;
        // we check whether the measurement (or container) has a '*' inside it, meaning that it is a
        // repeat measurement/container
        Matcher repeatMatcher = REPEAT_PATTERN.matcher(paraname);
        if (repeatMatcher.matches()) {
            repeat = repeatMatcher.group(1);
            paraname = repeatMatcher.group(2);
        }
        SequenceEntry se;
        int size;

        Matcher arrayMatcher = ARRAY_PATTERN.matcher(paraname);
        Matcher refMatcher = REF_PATTERN.matcher(paraname);
        if (arrayMatcher.matches()) {
            se = makeArrayEntry(arrayMatcher.group(1), arrayMatcher.group(2));
            size = -1;
        } else if (refMatcher.matches()) {
            String refParamName = refMatcher.group(1);
            String aliasNameSpace = (refMatcher.groupCount() > 1) ? refMatcher.group(2) : null;
            Parameter refParam = parameters.get(refParamName);
            if (refParam == null) {
                throw new SpreadsheetLoadException(ctx,
                        "Entry '" + paraname + "' makes reference to unkonw parameter '" + refParamName + "'");
            }
            se = new IndirectParameterRefEntry(pos, location, new ParameterInstanceRef(refParam), aliasNameSpace);
            size = -1;
        } else if (parameters.containsKey(paraname)) {
            Parameter param = parameters.get(paraname);
            checkThatParameterSizeCanBeComputed(param.getName(), param.getParameterType());
            se = new ParameterEntry(pos, location, param);
            size = getParameterSize(param.getName(), param.getParameterType());
        } else if (containers.containsKey(paraname)) {
            SequenceContainer sc = containers.get(paraname);
            se = new ContainerEntry(pos, location, sc);
            size = sc.getSizeInBits();
        } else {

            throw new SpreadsheetLoadException(ctx, "The measurement/container '" + paraname
                    + "' was not found in the parameters or containers map");
        }

        int repeated = addRepeat(se, repeat);
        container.addEntry(se);
        // after adding this measurement, we need to update the absoluteoffset for the next one. For this, we
        // add the size of the current SequenceEntry to the absoluteoffset
        if ((repeated != -1) && (size != -1) && (absoluteoffset != -1)) {
            absoluteoffset += repeated * size;
        } else {
            // from this moment on, absoluteoffset is set to -1, meaning that all subsequent SequenceEntries
            // must be relative
            absoluteoffset = -1;
        }

        return absoluteoffset;
    }

    private ArrayParameterEntry makeArrayEntry(String arrayparam, String arraystr) {
        // array parameter
        ArrayParameterEntry se = new ArrayParameterEntry();
        Parameter param = parameters.get(arrayparam);
        if (param == null) {
            throw new SpreadsheetLoadException(ctx, "The array parameter '" + arrayparam
                    + "' was not found in the parameters or containers map");
        }
        if (!(param.getParameterType() instanceof ArrayParameterType)) {
            throw new SpreadsheetLoadException(ctx, "The parameter '" + arrayparam
                    + "' is not an array parameter but " + param.getParameterType().getClass().getTypeName());
        }
        se.setParameter(param);

        ArrayParameterType aptype = (ArrayParameterType) param.getParameterType();
        Matcher m1 = Pattern.compile("\\[([\\d\\w]+)\\]").matcher(arraystr);
        List<IntegerValue> l = new ArrayList<>();
        while (m1.find()) {
            String dim = m1.group(1);
            try {
                int rep = Integer.decode(dim);
                l.add(new FixedIntegerValue(rep));
            } catch (NumberFormatException e) {
                Parameter repeatparam = parameters.get(dim);
                if (repeatparam == null) {
                    throw new SpreadsheetLoadException(ctx,
                            "Cannot find the parameter for array dimension '" + dim + "'");
                }
                l.add(new DynamicIntegerValue(new ParameterInstanceRef(repeatparam, true)));
            }

        }
        if (l.size() != aptype.getNumberOfDimensions()) {
            throw new SpreadsheetLoadException(ctx,
                    "Invalid number of dimensions " + l.size() + " specified for array parameter '" + arrayparam
                            + "' should be " + aptype.getNumberOfDimensions());
        }
        se.setSize(l);

        return se;
    }

    private void checkThatParameterSizeCanBeComputed(String paraName, ParameterType ptype) {
        if (ptype instanceof BaseDataType) {
            DataEncoding encoding = ((BaseDataType) ptype).getEncoding();
            if (encoding == null) {
                throw new SpreadsheetLoadException(ctx,
                        "Parameter " + paraName + " is part of a container but has no data encoding specified");
            }
            if (encoding.getSizeInBits() > 0) {
                return;
            }
            if (encoding instanceof IntegerDataEncoding) {
                IntegerDataEncoding intenc = (IntegerDataEncoding) encoding;
                if (intenc.getEncoding() != IntegerDataEncoding.Encoding.STRING) {
                    throw new SpreadsheetLoadException(ctx, "Parameter " + paraName
                            + " is part of a container and encoded as integer but has no size in bits specified");
                }
            } else if (encoding instanceof FloatDataEncoding) {
                FloatDataEncoding fenc = (FloatDataEncoding) encoding;
                if (fenc.getEncoding() != FloatDataEncoding.Encoding.STRING) {
                    throw new SpreadsheetLoadException(ctx, "Parameter " + paraName
                            + " is part of a container and encoded as float but has no size in bits specified");
                }
            }
        } else if (ptype instanceof AggregateParameterType) {
            for (Member m : ((AggregateParameterType) ptype).getMemberList()) {
                checkThatParameterSizeCanBeComputed(paraName + "/" + m.getName(), (ParameterType) m.getType());
            }
        } else if (ptype instanceof ArrayParameterType) {
            checkThatParameterSizeCanBeComputed(paraName,
                    (ParameterType) ((ArrayParameterType) ptype).getElementType());
        }
    }

    protected void loadCommandSheet(SpaceSystem spaceSystem, String sheetName) {
        Sheet sheet = switchToSheet(sheetName, false);
        if (sheet == null) {
            return;
        }
        Cell[] firstRow = jumpToRow(sheet, 0);

        HashMap<String, MetaCommand> commands = new HashMap<>();

        for (int i = 1; i < sheet.getRows(); i++) {
            // search for a new command definition, starting from row i
            // (explanatory note, i is incremented inside this loop too, and that's why the following 4 lines work)
            Cell[] cells = jumpToRow(sheet, i);
            if (cells == null || cells.length < 1) {
                log.trace("Ignoring line {} because it's empty", ctx.row);
                continue;
            }
            if (cells[0].getContents().equals("") || cells[0].getContents().startsWith("#")) {
                log.trace("Ignoring line {} because first cell is empty or starts with '#'", ctx.row);
                continue;
            }

            String name = getContent(cells, CN_CMD_NAME);
            String parent = getContent(cells, CN_CMD_PARENT, null);
            String argAssignment = null;
            if (parent != null) {
                argAssignment = getContent(cells, CN_CMD_ARG_ASSIGNMENT, null);
            }

            // extraOffset is the absolute offset of the first argument or FixedValue in the container
            // it will be added also to all absolute offsets specified
            int extraOffset = -1;
            if (parent != null) {
                int x = parent.indexOf(':');
                if (x != -1) {
                    extraOffset = Integer.decode(parent.substring(x + 1));
                    parent = parent.substring(0, x);
                }
            }

            CommandContainer container = new CommandContainer(name);
            MetaCommand cmd = new MetaCommand(name);
            cmd.setCommandContainer(container);
            commands.put(name, cmd);

            // load aliases
            XtceAliasSet xas = getAliases(firstRow, cells);
            if (xas != null) {
                cmd.setAliasSet(xas);
            }
            String flags = getContent(cells, CN_CMD_FLAGS, null);
            if (flags != null && flags.contains("A")) {
                cmd.setAbstract(true);
            }
            cmd.setShortDescription(getContent(cells, CN_CMD_DESCRIPTION, null));

            // we mark the start of the CMD and advance to the next line, to get to the first argument (if there is one)
            i++;

            // now, we start processing the arguments
            boolean end = false;
            int counter = 0; // sequence number of the SequenceEntrys in the SequenceContainer
            while (!end && (i < sheet.getRows())) {

                // get the next row, containing a measurement/container reference
                cells = jumpToRow(sheet, i);

                // determine whether we have not reached the end of the command definition.
                if (!hasColumn(cells, CN_CMD_ARGNAME)) {
                    end = true;
                    continue;
                }

                String argname = getContent(cells, CN_CMD_ARGNAME);
                Position pos = Position.RELATIVE_ZERO;
                if (hasColumn(cells, CN_CMD_POSITION)) {
                    pos = getPosition(ctx, getContent(cells, CN_CMD_POSITION));
                }

                if (pos.relative && counter == 0 && extraOffset != -1) {
                    pos = new Position(pos.pos, false);
                }
                String dataType = getContent(cells, CN_CMD_DTYPE);

                if (dataType.startsWith("FixedValue")) {
                    Matcher m = FIXED_VALUE_PATTERN.matcher(dataType);
                    if (!m.matches()) {
                        throw new SpreadsheetLoadException(ctx, "FixedValue does not specify sizeInBits for " + argname
                                + " on line " + (i + 1)
                                + ". Please use something like FixedValue(n) where n is the size in bits.");
                    }
                    int sizeInBits = Integer.parseInt(m.group(1));
                    String hexValue = getContent(cells, CN_CMD_DEFVALUE);
                    byte[] binaryValue = StringConverter.hexStringToArray(hexValue);
                    FixedValueEntry fve;
                    if (pos.relative) {
                        fve = new FixedValueEntry(pos.pos, ReferenceLocationType.previousEntry, argname, binaryValue,
                                sizeInBits);
                    } else {
                        fve = new FixedValueEntry(pos.pos + ((extraOffset != -1) ? extraOffset : 0),
                                ReferenceLocationType.containerStart, argname, binaryValue, sizeInBits);
                    }
                    container.addEntry(fve);
                } else {
                    loadArgument(spaceSystem, cells, cmd, container, extraOffset, counter);
                }
                i++;
                counter++;
            }

            // at this point, we have added all the parameters and containers to the current packets. What
            // remains to be done is link it with its base
            if (parent != null) {
                // the condition is parsed and used to create the container.restrictionCriteria
                // 1) get the parent, from the same sheet
                MetaCommand parentCmd = commands.get(parent);

                // the parent is not in the same sheet, try to get from the Xtcedb
                if (parentCmd == null) {
                    parentCmd = spaceSystem.getMetaCommand(parent);
                }
                if (parentCmd != null) {
                    cmd.setBaseMetaCommand(parentCmd);
                    container.setBaseContainer(parentCmd.getCommandContainer());
                } else {
                    final MetaCommand mc = cmd;
                    final CommandContainer mcc = container;
                    NameReference nr = new UnresolvedNameReference(parent, Type.META_COMMAND).addResolvedAction(nd -> {
                        mc.setBaseMetaCommand((MetaCommand) nd);
                        mcc.setBaseContainer(((MetaCommand) nd).getCommandContainer());
                        return true;
                    });
                    spaceSystem.addUnresolvedReference(nr);
                }

                // 2) extract the condition and create the restrictioncriteria
                if (argAssignment != null) {
                    cmd.setArgumentAssignmentList(toArgumentAssignmentList(argAssignment));
                }
            }

            spaceSystem.addMetaCommand(cmd);
        }
    }

    private void loadArgument(SpaceSystem spaceSystem, Cell[] cells, MetaCommand cmd, CommandContainer container,
            int extraOffset, int counter) {
        String dtype = getContent(cells, CN_CMD_DTYPE);
        String name = getContent(cells, CN_CMD_ARGNAME);
        Position pos = Position.RELATIVE_ZERO;

        if (hasColumn(cells, CN_CMD_POSITION)) {
            pos = getPosition(ctx, getContent(cells, CN_CMD_POSITION));
        }
        if (pos.relative && counter == 0 && extraOffset != -1) {
            pos = new Position(pos.pos, false);
        }

        DataTypeRecord dtr = dataTypes.get(dtype);
        if (dtr == null) {
            throw new SpreadsheetLoadException(ctx, "Cannot find a  data type on name '" + dtype + "'");
        }
        ArgumentType atype = (ArgumentType) createDataType(spaceSystem, dtr, false);

        if (cmd.getArgument(name) != null) {
            throw new SpreadsheetLoadException(ctx, "Duplicate argument with name '" + name + "'");
        }
        Argument arg = new Argument(name);
        cmd.addArgument(arg);

        arg.setArgumentType(atype);

        if (hasColumn(cells, CN_CMD_DEFVALUE)) {
            String v = getContent(cells, CN_CMD_DEFVALUE);
            if (atype instanceof IntegerArgumentType) {
                try {
                    Long.decode(v);
                } catch (Exception e) {
                    throw new SpreadsheetLoadException(ctx, "Cannot parse default value '" + v + "'");
                }
                arg.setInitialValue(v);
            } else if (atype instanceof FloatArgumentType) {
                try {
                    Double.parseDouble(v);
                } catch (Exception e) {
                    throw new SpreadsheetLoadException(ctx, "Cannot parse default value '" + v + "'");
                }
                arg.setInitialValue(v);
            } else {
                arg.setInitialValue(v);
            }
        }
        if (hasColumn(cells, CN_CMD_RANGELOW) || hasColumn(cells, CN_CMD_RANGEHIGH)) {
            if (atype instanceof IntegerArgumentType) {
                if (((IntegerArgumentType) atype).isSigned()) {
                    long minInclusive = Long.MIN_VALUE;
                    long maxInclusive = Long.MAX_VALUE;
                    if (hasColumn(cells, CN_CMD_RANGELOW)) {
                        minInclusive = Long.decode(getContent(cells, CN_CMD_RANGELOW));
                    }
                    if (hasColumn(cells, CN_CMD_RANGEHIGH)) {
                        maxInclusive = Long.decode(getContent(cells, CN_CMD_RANGEHIGH));
                    }
                    IntegerValidRange range = new IntegerValidRange(minInclusive, maxInclusive);
                    ((IntegerArgumentType) atype).setValidRange(range);
                } else {
                    long minInclusive = 0;
                    long maxInclusive = ~0;
                    if (hasColumn(cells, CN_CMD_RANGELOW)) {
                        minInclusive = UnsignedLongs.decode(getContent(cells, CN_CMD_RANGELOW));
                    }
                    if (hasColumn(cells, CN_CMD_RANGEHIGH)) {
                        maxInclusive = UnsignedLongs.decode(getContent(cells, CN_CMD_RANGEHIGH));
                    }
                    IntegerValidRange range = new IntegerValidRange(minInclusive, maxInclusive);
                    ((IntegerArgumentType) atype).setValidRange(range);

                }
            } else if (atype instanceof FloatArgumentType) {
                double minInclusive = Double.NEGATIVE_INFINITY;
                double maxInclusive = Double.POSITIVE_INFINITY;
                if (hasColumn(cells, CN_CMD_RANGELOW)) {
                    minInclusive = Double.parseDouble(getContent(cells, CN_CMD_RANGELOW));
                }
                if (hasColumn(cells, CN_CMD_RANGEHIGH)) {
                    maxInclusive = Double.parseDouble(getContent(cells, CN_CMD_RANGEHIGH));
                }
                FloatValidRange range = new FloatValidRange(minInclusive, maxInclusive);
                ((FloatArgumentType) atype).setValidRange(range);
            }
        }

        arg.setShortDescription(getContent(cells, CN_CMD_DESCRIPTION, null));

        ArgumentEntry ae;
        // if absoluteoffset is -1, somewhere along the line we came across a measurement or container that had as a
        // result that the absoluteoffset could not be determined anymore; hence, a relative position is added
        if (pos.relative) {
            ae = new ArgumentEntry(pos.pos, ReferenceLocationType.previousEntry, arg);
        } else {
            ae = new ArgumentEntry(pos.pos + ((extraOffset != -1) ? extraOffset : 0),
                    ReferenceLocationType.containerStart, arg);
        }

        container.addEntry(ae);
    }

    protected void loadCommandOptionsSheet(SpaceSystem spaceSystem, String sheetName) {
        Sheet sheet = switchToSheet(sheetName, false);
        if (sheet == null) {
            return;
        }
        int i = 1;
        while (i < sheet.getRows()) {
            // search for a new command definition, starting from row i
            // (explanatory note, i is incremented inside this loop too, and that's why the following 4 lines work)
            Cell[] cells = jumpToRow(sheet, i);
            if (cells == null || cells.length < 1) {
                log.trace("Ignoring line {} because it's empty", ctx.row);
                i++;
                continue;
            }
            if (cells[0].getContents().equals("") || cells[0].getContents().startsWith("#")) {
                log.trace("Ignoring line {} because first cell is empty or starts with '#'", ctx.row);
                i++;
                continue;
            }

            String cmdName = cells[IDX_CMDOPT_NAME].getContents();
            MetaCommand cmd = spaceSystem.getMetaCommand(cmdName);
            if (cmd == null) {
                throw new SpreadsheetLoadException(ctx, "Could not find a command named '" + cmdName + "'");
            }

            int cmdEnd = i + 1;
            while (cmdEnd < sheet.getRows()) {
                cells = jumpToRow(sheet, cmdEnd);
                if (hasColumn(cells, IDX_CMDOPT_NAME)) {
                    break;
                }
                cmdEnd++;
            }
            while (i < cmdEnd) {
                cells = jumpToRow(sheet, i);
                if (hasColumn(cells, IDX_CMDOPT_TXCONST)) {
                    String condition = cells[IDX_CMDOPT_TXCONST].getContents();
                    MatchCriteria criteria;
                    try {
                        criteria = toMatchCriteria(spaceSystem, condition);
                    } catch (Exception e) {
                        throw new SpreadsheetLoadException(ctx, "Cannot parse condition '" + condition + "': " + e);
                    }
                    long timeout = 0;
                    if (hasColumn(cells, IDX_CMDOPT_TXCONST_TIMEOUT)) {
                        timeout = Long.parseLong(cells[IDX_CMDOPT_TXCONST_TIMEOUT].getContents());
                    }

                    TransmissionConstraint constraint = new TransmissionConstraint(criteria, timeout);
                    cmd.addTransmissionConstrain(constraint);
                }
                if (hasColumn(cells, IDX_CMDOPT_SIGNIFICANCE)) {
                    if (cmd.getDefaultSignificance() != null) {
                        throw new SpreadsheetLoadException(ctx,
                                "The command " + cmd.getName() + " has already a default significance");
                    }
                    String significance = cells[IDX_CMDOPT_SIGNIFICANCE].getContents();
                    Significance.Levels slevel;
                    try {
                        slevel = Significance.Levels.valueOf(significance);
                    } catch (IllegalArgumentException e) {
                        throw new SpreadsheetLoadException(ctx,
                                "Invalid significance '" + significance + "' specified. Available values are: "
                                        + Arrays.toString(Significance.Levels.values()));
                    }
                    String reason = null;
                    if (hasColumn(cells, IDX_CMDOPT_SIGNIFICANCE_REASON)) {
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
        if (sheet == null) {
            return;
        }
        int i = 1;
        while (i < sheet.getRows()) {
            // search for a new command definition, starting from row i
            // (explanatory note, i is incremented inside this loop too, and that's why the following 4 lines work)
            Cell[] cells = jumpToRow(sheet, i);
            if (cells == null || cells.length < 1) {
                log.trace("Ignoring line {} because it's empty", ctx.row);
                i++;
                continue;
            }
            if (cells[0].getContents().equals("") || cells[0].getContents().startsWith("#")) {
                log.trace("Ignoring line {} because first cell is empty or starts with '#'", ctx.row);
                i++;
                continue;
            }

            String cmdName = getContent(cells, CN_CMDVERIF_NAME);
            MetaCommand cmd = spaceSystem.getMetaCommand(cmdName);
            if (cmd == null) {
                throw new SpreadsheetLoadException(ctx, "Could not find a command named '" + cmdName + "'");
            }

            int cmdEnd = i + 1;
            while (cmdEnd < sheet.getRows()) {
                cells = jumpToRow(sheet, cmdEnd);
                if (hasColumn(cells, CN_CMDVERIF_NAME)) {
                    break;
                }
                cmdEnd++;
            }
            while (i < cmdEnd) {
                cells = jumpToRow(sheet, i);
                if (hasColumn(cells, CN_CMDVERIF_STAGE)) {
                    String stage = getContent(cells, CN_CMDVERIF_STAGE);

                    if (!hasColumn(cells, CN_CMDVERIF_CHECKWINDOW)) {
                        throw new SpreadsheetLoadException(ctx, "No checkwindow specified for the command verifier ");
                    }
                    String checkws = getContent(cells, CN_CMDVERIF_CHECKWINDOW);
                    Pattern p = Pattern.compile("(\\d+),(\\d+)");
                    Matcher m = p.matcher(checkws);
                    if (!m.matches()) {
                        throw new SpreadsheetLoadException(ctx,
                                "Invalid checkwindow specified. Use 'start,stop' where start and stop are number of milliseconds. Both have to be positive.");
                    }
                    long start = Long.valueOf(m.group(1));
                    long stop = Long.valueOf(m.group(2));
                    if (stop < start) {
                        throw new SpreadsheetLoadException(ctx,
                                "Invalid checkwindow specified. Stop cannot be smaller than start");
                    }
                    CheckWindow.TimeWindowIsRelativeToType cwr = TimeWindowIsRelativeToType.LastVerifier;

                    if (hasColumn(cells, CN_CMDVERIF_CHECKWINDOW_RELATIVETO)) {
                        String s = getContent(cells, CN_CMDVERIF_CHECKWINDOW_RELATIVETO);
                        try {
                            cwr = TimeWindowIsRelativeToType.valueOf(s);
                        } catch (IllegalArgumentException e) {
                            throw new SpreadsheetLoadException(ctx,
                                    "Invalid value '" + s
                                            + "' specified for CheckWindow relative to parameter. Use one of "
                                            + Arrays.toString(TimeWindowIsRelativeToType.values()));
                        }
                    }
                    CheckWindow cw = new CheckWindow(start, stop, cwr);
                    if (!hasColumn(cells, CN_CMDVERIF_TYPE)) {
                        throw new SpreadsheetLoadException(ctx, "No type specified for the command verifier ");
                    }
                    String types = getContent(cells, CN_CMDVERIF_TYPE);
                    CommandVerifier.Type type = null;
                    try {
                        type = CommandVerifier.Type.valueOf(types.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new SpreadsheetLoadException(ctx, "Invalid command verifier type '" + types
                                + "' specified. Supported are: " + Arrays.toString(CommandVerifier.Type.values()));
                    }

                    CommandVerifier cmdVerifier = new CommandVerifier(type, stage, cw);

                    if (type == CommandVerifier.Type.CONTAINER) {
                        String containerName = getContent(cells, CN_CMDVERIF_TEXT);
                        SequenceContainer container = spaceSystem.getSequenceContainer(containerName);
                        if (container == null) {
                            throw new SpreadsheetLoadException(ctx,
                                    "Cannot find sequence container '" + containerName + "' required for the verifier");
                        }
                        cmdVerifier.setContainerRef(container);
                    } else if (type == CommandVerifier.Type.ALGORITHM) {
                        String algoName = getContent(cells, CN_CMDVERIF_TEXT);
                        Algorithm algo = spaceSystem.getAlgorithm(algoName);
                        if (algo == null) {
                            throw new SpreadsheetLoadException(ctx,
                                    "Cannot find algorithm '" + algoName + "' required for the verifier");
                        }
                        cmdVerifier.setAlgorithm(algo);
                    } else {
                        throw new SpreadsheetLoadException(ctx,
                                "Command verifier type '" + type + "' not implemented ");
                    }

                    String tas = null;
                    try {
                        if (hasColumn(cells, CN_CMDVERIF_ONSUCCESS)) {
                            tas = getContent(cells, CN_CMDVERIF_ONSUCCESS);
                            CommandVerifier.TerminationAction ta = TerminationAction.valueOf(tas);
                            cmdVerifier.setOnSuccess(ta);
                        }
                        if (hasColumn(cells, CN_CMDVERIF_ONFAIL)) {
                            tas = getContent(cells, CN_CMDVERIF_ONFAIL);
                            CommandVerifier.TerminationAction ta = TerminationAction.valueOf(tas);
                            cmdVerifier.setOnFail(ta);
                        }
                        if (hasColumn(cells, CN_CMDVERIF_ONTIMEOUT)) {
                            tas = getContent(cells, CN_CMDVERIF_ONTIMEOUT);
                            CommandVerifier.TerminationAction ta = TerminationAction.valueOf(tas);
                            cmdVerifier.setOnTimeout(ta);
                        }
                        cmd.addVerifier(cmdVerifier);
                    } catch (IllegalArgumentException e) {
                        throw new SpreadsheetLoadException(ctx,
                                "Invalid termination action '" + tas
                                        + "' specified for the command verifier. Supported actions are: "
                                        + TerminationAction.values());
                    }
                }
                i++;
            }
        }
    }

    private List<ArgumentAssignment> toArgumentAssignmentList(String argAssignment) {
        List<ArgumentAssignment> aal = new ArrayList<>();
        String[] splitted = argAssignment.split("\\r?\\n");
        if (splitted.length < 2) {
            splitted = argAssignment.split(";");
        }

        for (String part : splitted) {
            aal.add(toArgumentAssignment(part.trim()));
        }
        return aal;
    }

    private ArgumentAssignment toArgumentAssignment(String argAssignment) {
        Matcher m = Pattern.compile("(.*?)(=)([^=]*)").matcher(argAssignment);
        if (!m.matches()) {
            throw new SpreadsheetLoadException(ctx, "Cannot parse argument assignment '" + argAssignment + "'");
        }
        String aname = m.group(1).trim();
        String value = m.group(3).trim();
        return new ArgumentAssignment(aname, value);
    }

    protected void loadChangelogSheet(boolean required) {
        Sheet sheet = switchToSheet(SHEET_CHANGELOG, required);
        if (sheet == null) {
            return;
        }
        int i = 1;
        while (i < sheet.getRows()) {
            Cell[] cells = jumpToRow(sheet, i);
            if (cells == null || cells.length < 1) {
                log.trace("Ignoring line {} because it's empty", ctx.row);
                i++;
                continue;
            }
            if (cells[0].getContents().equals("") || cells[0].getContents().startsWith("#")) {
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
                    date = new SimpleDateFormat("dd-MMM-yyyy").format(dt);
                } else {
                    date = cells[IDX_LOG_DATE].getContents();
                }

                String msg = null;
                if (cells.length >= 3) {
                    msg = cells[IDX_LOG_MESSAGE].getContents();
                }

                String author = null;
                if (cells.length >= 4) {
                    author = cells[IDX_LOG_AUTHOR].getContents();
                }
                History history = new History(version, date, msg, author);
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
        while (true) {
            // we first search for a row containing (= starting) a new algorithm
            while (start < sheet.getRows()) {
                Cell[] cells = jumpToRow(sheet, start);
                if ((cells.length > 0) && (cells[0].getContents().length() > 0)
                        && !cells[0].getContents().startsWith("#")) {
                    break;
                }
                start++;
            }
            if (start >= sheet.getRows()) {
                break;
            }

            Cell[] cells = jumpToRow(sheet, start);
            String name = getContent(cells, CN_ALGO_NAME);
            String algorithmLanguage = getContent(cells, CN_ALGO_LANGUGAGE);
            if (!"JavaScript".equals(algorithmLanguage) && !"python".equals(algorithmLanguage)
                    && !"java".equalsIgnoreCase(algorithmLanguage)) {
                throw new SpreadsheetLoadException(ctx, "Invalid algorithm language '" + algorithmLanguage
                        + "' specified. Supported are 'JavaScript', 'python' and java (case sensitive)");
            }

            String algorithmText = getContent(cells, CN_ALGO_TEXT);
            XtceAliasSet xas = getAliases(firstRow, cells);

            // Check that there is not specified by mistake a in/out param already on the same line with the algorithm
            // name
            if (hasColumn(cells, CN_ALGO_PARA_INOUT) || hasColumn(cells, CN_ALGO_PARA_REF)) {
                throw new SpreadsheetLoadException(ctx,
                        "Algorithm paramters have to start on the next line from the algorithm name and text definition");
            }

            // now we search for the matching last row of that algorithm
            int end = start + 1;
            while (end < sheet.getRows()) {
                cells = jumpToRow(sheet, end);
                if (!hasColumn(cells, CN_ALGO_PARA_REF)) {
                    break;
                }
                end++;
            }

            CustomAlgorithm algorithm = new CustomAlgorithm(name);
            if (xas != null) {
                algorithm.setAliasSet(xas);
            }
            algorithm.setLanguage(algorithmLanguage);
            // Replace smart-quotes “ and ” with regular quotes "
            algorithm.setAlgorithmText(algorithmText.replaceAll("[\u201c\u201d]", "\""));

            // In/out params
            String paraInout = null;
            Set<String> inputParameterRefs = new HashSet<>();
            for (int j = start + 1; j < end; j++) {
                cells = jumpToRow(sheet, j);
                String paraRefName = getContent(cells, CN_ALGO_PARA_REF);
                if (hasColumn(cells, CN_ALGO_PARA_INOUT)) {
                    paraInout = getContent(cells, CN_ALGO_PARA_INOUT);
                }

                String flags = getContent(cells, CN_ALGO_PARA_FLAGS, "");

                if (paraInout == null) {
                    throw new SpreadsheetLoadException(ctx, "You must specify in/out attribute for this parameter");
                }
                if ("in".equalsIgnoreCase(paraInout)) {
                    if (paraRefName.startsWith(XtceDb.YAMCS_CMD_SPACESYSTEM_NAME)
                            || paraRefName.startsWith(XtceDb.YAMCS_CMDHIST_SPACESYSTEM_NAME)) {
                        algorithm.setScope(Algorithm.Scope.COMMAND_VERIFICATION);
                    }
                    inputParameterRefs.add(paraRefName);
                    NameReference paramRef = getParameterReference(spaceSystem, paraRefName, false);
                    final ParameterInstanceRef parameterInstance = new ParameterInstanceRef();
                    paramRef.addResolvedAction(nd -> {
                        parameterInstance.setParameter((Parameter) nd);
                        return true;
                    });

                    if (hasColumn(cells, CN_ALGO_PARA_INSTANCE)) {
                        int instance = Integer.valueOf(getContent(cells, CN_ALGO_PARA_INSTANCE));
                        if (instance > 0) {
                            throw new SpreadsheetLoadException(ctx, "Instance '" + instance
                                    + "' not supported. Can only go back in time. Use values <= 0.");
                        }
                        parameterInstance.setInstance(instance);
                    }

                    InputParameter inputParameter = new InputParameter(parameterInstance);
                    if (hasColumn(cells, CN_ALGO_PARA_NAME)) {
                        inputParameter.setInputName(getContent(cells, CN_ALGO_PARA_NAME));
                    }
                    if (flags.contains("M")) {
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
                    if (hasColumn(cells, CN_ALGO_PARA_NAME)) {
                        outputParameter.setOutputName(getContent(cells, CN_ALGO_PARA_NAME));
                    }
                    algorithm.addOutput(outputParameter);
                } else {
                    throw new SpreadsheetLoadException(ctx,
                            "In/out '" + paraInout + "' not supported. Must be one of 'in' or 'out'");
                }
            }

            // Add trigger conditions
            final TriggerSetType triggerSet = new TriggerSetType();

            cells = jumpToRow(sheet, start); // Jump back to algorithm row (for getting error msgs right)
            String triggerText = getContent(cells, CN_ALGO_TRIGGER, "");
            if (triggerText.startsWith("OnParameterUpdate")) {
                Matcher matcher = ALGO_PARAMETER_PATTERN.matcher(triggerText);
                if (matcher.matches()) {
                    for (String s : matcher.group(1).split(",")) {
                        Parameter para = spaceSystem.getParameter(s.trim());
                        if (para != null) {
                            OnParameterUpdateTrigger trigger = new OnParameterUpdateTrigger(para);
                            triggerSet.addOnParameterUpdateTrigger(trigger);
                        } else {
                            NameReference nr = new UnresolvedNameReference(s.trim(), Type.PARAMETER)
                                    .addResolvedAction(nd -> {
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
            } else if (triggerText.startsWith("OnPeriodicRate")) {
                Matcher matcher = ALGO_FIRERATE_PATTERN.matcher(triggerText);
                if (matcher.matches()) {
                    long fireRateMs = Long.parseLong(matcher.group(1), 10);
                    OnPeriodicRateTrigger trigger = new OnPeriodicRateTrigger(fireRateMs);
                    triggerSet.addOnPeriodicRateTrigger(trigger);
                } else {
                    throw new SpreadsheetLoadException(ctx, "Wrongly formatted OnPeriodicRate trigger");
                }

            } else if (triggerText.startsWith("OnInputParameterUpdate")) {
                // default to all in parameters
                for (String paraRef : inputParameterRefs) {
                    Parameter para = spaceSystem.getParameter(paraRef);
                    if (para != null) {
                        triggerSet.addOnParameterUpdateTrigger(new OnParameterUpdateTrigger(para));
                    } else {
                        NameReference nr = new UnresolvedNameReference(paraRef, Type.PARAMETER)
                                .addResolvedAction(nd -> {
                                    OnParameterUpdateTrigger trigger = new OnParameterUpdateTrigger((Parameter) nd);
                                    triggerSet.addOnParameterUpdateTrigger(trigger);
                                    return true;
                                });
                        spaceSystem.addUnresolvedReference(nr);
                    }
                }
            } else if (triggerText.isEmpty() || triggerText.startsWith("none")) {
                // do nothing, we run with an empty trigger set
            } else {
                throw new SpreadsheetLoadException(ctx, "Trigger '" + triggerText + "' not supported.");
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
        while (true) {
            // we first search for a row containing (= starting) a new alarm
            while (start < sheet.getRows()) {
                Cell[] cells = jumpToRow(sheet, start);
                if ((cells.length > 0) && (cells[0].getContents().length() > 0)
                        && !cells[0].getContents().startsWith("#")) {
                    break;
                }
                start++;
            }
            if (start >= sheet.getRows()) {
                break;
            }

            Cell[] cells = jumpToRow(sheet, start);
            String paramName = getContent(cells, CN_ALARM_PARAM_NAME);
            NameReference paraRef = getParameterReference(spaceSystem, paramName, true);

            // now we search for the matching last row of the alarms for this parameter
            int paramEnd = start + 1;
            while (paramEnd < sheet.getRows()) {
                cells = jumpToRow(sheet, paramEnd);
                if (hasColumn(cells, CN_ALARM_PARAM_NAME)) {
                    break;
                }
                paramEnd++;
            }

            // Iterate over all rows for this parameter
            MatchCriteria previousContext = null;
            int minViolations = -1;
            AlarmReportType reportType = AlarmReportType.ON_SEVERITY_CHANGE;
            for (int j = start; j < paramEnd; j++) {
                cells = jumpToRow(sheet, j);
                MatchCriteria context = previousContext;
                if (hasColumn(cells, CN_ALARM_CONTEXT)) {
                    String contextString = getContent(cells, CN_ALARM_CONTEXT);
                    context = toMatchCriteria(spaceSystem, contextString);
                    minViolations = -1;
                }

                if (hasColumn(cells, CN_ALARM_MIN_VIOLATIONS)) {
                    minViolations = Integer.parseInt(getContent(cells, CN_ALARM_MIN_VIOLATIONS));
                }

                if (hasColumn(cells, CN_ALARM_REPORT)) {
                    if ("OnSeverityChange".equalsIgnoreCase(getContent(cells, CN_ALARM_REPORT))) {
                        reportType = AlarmReportType.ON_SEVERITY_CHANGE;
                    } else if ("OnValueChange".equalsIgnoreCase(getContent(cells, CN_ALARM_REPORT))) {
                        reportType = AlarmReportType.ON_VALUE_CHANGE;
                    } else {
                        throw new SpreadsheetLoadException(ctx,
                                "Unrecognized report type '" + getContent(cells, CN_ALARM_REPORT) + "'");
                    }
                }

                checkAndAddAlarm(cells, AlarmLevels.watch, paraRef, context, CN_ALARM_WATCH_TRIGGER,
                        CN_ALARM_WATCH_VALUE);
                checkAndAddAlarm(cells, AlarmLevels.warning, paraRef, context,
                        CN_ALARM_WARNING_TRIGGER, CN_ALARM_WARNING_VALUE);
                checkAndAddAlarm(cells, AlarmLevels.distress, paraRef, context,
                        CN_ALARM_DISTRESS_TRIGGER, CN_ALARM_DISTRESS_VALUE);
                checkAndAddAlarm(cells, AlarmLevels.critical, paraRef, context,
                        CN_ALARM_CRITICAL_TRIGGER, CN_ALARM_CRITICAL_VALUE);
                checkAndAddAlarm(cells, AlarmLevels.severe, paraRef, context,
                        CN_ALARM_SEVERE_TRIGGER, CN_ALARM_SEVERE_VALUE);

                addAlarmDetails(paraRef, context, reportType, minViolations);

                previousContext = context;
            }

            start = paramEnd;
        }
    }

    private void checkAndAddAlarm(Cell[] cells, AlarmLevels level, NameReference paraRef, MatchCriteria context,
            String cnTrigger, String cnValue) {
        if (!hasColumn(cells, cnTrigger) || !hasColumn(cells, cnValue)) {
            return;
        }
        String trigger = getContent(cells, cnTrigger);
        String triggerValue = getContent(cells, cnValue);

        SpreadsheetLoadContext ctx1 = ctx.copy();
        paraRef.addResolvedAction(nd -> {

            Parameter para = (Parameter) nd;
            if (para.getParameterType() instanceof IntegerParameterType) {
                double tvd = parseDouble(ctx1, cells[h.get(cnValue)]);
                IntegerParameterType ipt = (IntegerParameterType) para.getParameterType();
                if ("low".equals(trigger)) {
                    ipt.addAlarmRange(context, new DoubleRange(tvd, Double.POSITIVE_INFINITY), level);
                } else if ("high".equals(trigger)) {
                    ipt.addAlarmRange(context, new DoubleRange(Double.NEGATIVE_INFINITY, tvd), level);
                } else {
                    throw new SpreadsheetLoadException(ctx1,
                            "Unexpected trigger type '" + trigger + "' for numeric parameter " + para.getName());
                }
            } else if (para.getParameterType() instanceof FloatParameterType) {
                double tvd = parseDouble(ctx1, cells[h.get(cnValue)]);
                FloatParameterType fpt = (FloatParameterType) para.getParameterType();
                if ("low".equals(trigger)) {
                    fpt.addAlarmRange(context, new DoubleRange(tvd, Double.POSITIVE_INFINITY), level);
                } else if ("high".equals(trigger)) {
                    fpt.addAlarmRange(context, new DoubleRange(Double.NEGATIVE_INFINITY, tvd), level);
                } else {
                    throw new SpreadsheetLoadException(ctx1,
                            "Unexpected trigger type '" + trigger + "' for numeric parameter " + para.getName());
                }
            } else if (para.getParameterType() instanceof EnumeratedParameterType) {
                EnumeratedParameterType ept = (EnumeratedParameterType) para.getParameterType();
                if ("state".equals(trigger)) {
                    ValueEnumeration enumValue = ept.enumValue(triggerValue);
                    if (enumValue == null) {
                        throw new SpreadsheetLoadException(ctx1, "Unknown enumeration value '" + triggerValue
                                + "' for alarm of enumerated parameter " + para.getName());
                    } else {
                        ept.addAlarm(context, triggerValue, level);
                    }
                } else {
                    throw new SpreadsheetLoadException(ctx1, "Unexpected trigger type '" + trigger
                            + "' for alarm of enumerated parameter " + para.getName());
                }
            }
            return true;
        });
    }

    private void addAlarmDetails(NameReference paraRef, MatchCriteria context, AlarmReportType reportType,
            int minViolations) {

        paraRef.addResolvedAction(nd -> {
            Parameter para = (Parameter) nd;
            ParameterType ptype = para.getParameterType();
            if (ptype == null) { // the type has to be resolved somewhere else first
                return false;
            }

            // Set minviolations and alarmreporttype
            AlarmType alarm = null;
            if (para.getParameterType() instanceof IntegerParameterType) {
                IntegerParameterType ipt = (IntegerParameterType) para.getParameterType();
                alarm = (context == null) ? ipt.getDefaultAlarm() : ipt.getNumericContextAlarm(context);
                if (reportType != AlarmType.DEFAULT_REPORT_TYPE) {
                    ipt.createOrGetAlarm(context).setAlarmReportType(reportType);
                }
            } else if (para.getParameterType() instanceof FloatParameterType) {
                FloatParameterType fpt = (FloatParameterType) para.getParameterType();
                alarm = (context == null) ? fpt.getDefaultAlarm() : fpt.getNumericContextAlarm(context);
                if (reportType != AlarmType.DEFAULT_REPORT_TYPE) {
                    fpt.createOrGetAlarm(context).setAlarmReportType(reportType);
                }
            } else if (para.getParameterType() instanceof EnumeratedParameterType) {
                EnumeratedParameterType ept = (EnumeratedParameterType) para.getParameterType();
                alarm = (context == null) ? ept.getDefaultAlarm() : ept.getContextAlarm(context);
                if (reportType != AlarmType.DEFAULT_REPORT_TYPE) {
                    ept.createOrGetAlarm(context).setAlarmReportType(reportType);
                }
            }
            if (alarm != null) { // It's possible that this gets called multiple times per alarm, but doesn't matter
                alarm.setMinViolations((minViolations == -1) ? 1 : minViolations);
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
        } else if (criteriaString.contains(";")) {
            ComparisonList cl = new ComparisonList();
            String splitted[] = criteriaString.split(";");
            for (String part : splitted) {
                cl.addComparison(conditionParser.toComparison(spaceSystem, part));
            }
            return cl;
        } else {
            return conditionParser.toComparison(spaceSystem, criteriaString);
        }
    }

    private int getParameterSize(String paramName, ParameterType ptype) {
        if (ptype instanceof BaseDataType) {
            DataEncoding de = ((BaseDataType) ptype).getEncoding();
            if (de == null) {
                throw new SpreadsheetLoadException(ctx,
                        "Cannot determine the data encoding for " + paramName);
            }
            return de.getSizeInBits();
        } else if (ptype instanceof AggregateParameterType) {
            int ts = 0;
            for (Member m : ((AggregateParameterType) ptype).getMemberList()) {
                int s = getParameterSize(paramName + "/" + m.getName(), (ParameterType) m.getType());
                if (s == -1) {
                    return -1;
                } else {
                    ts += s;
                }
            }
            return ts;
        } else if (ptype instanceof ArrayParameterType) {
            return -1;
        } else {
            throw new SpreadsheetLoadException(ctx, "Unknown parameter type " + ptype);
        }
    }

    /**
     * If repeat != null, decodes it to either an integer or a parameter and adds it to the SequenceEntry If repeat is
     * an integer, this integer is returned
     */
    private int addRepeat(SequenceEntry se, String repeat) {
        if (repeat != null) {
            try {
                int rep = Integer.decode(repeat);
                se.setRepeatEntry(new Repeat(new FixedIntegerValue(rep)));
                return rep;
            } catch (NumberFormatException e) {
                Parameter repeatparam = parameters.get(repeat);
                if (repeatparam == null) {
                    throw new SpreadsheetLoadException(ctx, "Cannot find the parameter for repeat " + repeat);
                }
                se.setRepeatEntry(new Repeat(new DynamicIntegerValue(new ParameterInstanceRef(repeatparam, true))));
                return -1;
            }
        } else {
            return 1;
        }
    }
}

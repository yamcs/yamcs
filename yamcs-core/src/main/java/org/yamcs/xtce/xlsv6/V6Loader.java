package org.yamcs.xtce.xlsv6;

import java.io.File;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.yamcs.YConfiguration;
import org.yamcs.mdb.ConditionParser;
import org.yamcs.mdb.JavaExpressionCalibratorFactory;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.SpreadsheetLoadContext;
import org.yamcs.mdb.SpreadsheetLoadException;
import org.yamcs.utils.StringConverter;
import org.yamcs.xtce.AbsoluteTimeParameterType;
import org.yamcs.xtce.AlarmLevels;
import org.yamcs.xtce.AlarmReportType;
import org.yamcs.xtce.AlarmType;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.Algorithm.Scope;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.ArgumentEntry;
import org.yamcs.xtce.ArgumentType;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.BinaryArgumentType;
import org.yamcs.xtce.BinaryDataEncoding;
import org.yamcs.xtce.BinaryParameterType;
import org.yamcs.xtce.BooleanArgumentType;
import org.yamcs.xtce.BooleanDataEncoding;
import org.yamcs.xtce.BooleanDataType;
import org.yamcs.xtce.BooleanParameterType;
import org.yamcs.xtce.Calibrator;
import org.yamcs.xtce.CheckWindow;
import org.yamcs.xtce.CheckWindow.TimeWindowIsRelativeToType;
import org.yamcs.xtce.CommandContainer;
import org.yamcs.xtce.CommandVerifier;
import org.yamcs.xtce.CommandVerifier.TerminationAction;
import org.yamcs.xtce.ComparisonList;
import org.yamcs.xtce.ContainerEntry;
import org.yamcs.xtce.ContextCalibrator;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.DataType;
import org.yamcs.xtce.DynamicIntegerValue;
import org.yamcs.xtce.EnumeratedArgumentType;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.FixedIntegerValue;
import org.yamcs.xtce.FixedValueEntry;
import org.yamcs.xtce.FloatArgumentType;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.FloatValidRange;
import org.yamcs.xtce.Header;
import org.yamcs.xtce.History;
import org.yamcs.xtce.InputParameter;
import org.yamcs.xtce.IntegerArgumentType;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.IntegerValidRange;
import org.yamcs.xtce.JavaExpressionCalibrator;
import org.yamcs.xtce.MatchCriteria;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.OnParameterUpdateTrigger;
import org.yamcs.xtce.OnPeriodicRateTrigger;
import org.yamcs.xtce.OutputParameter;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterEntry;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.ParameterInstanceRef.InstanceRelativeTo;
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
import org.yamcs.xtce.StringArgumentType;
import org.yamcs.xtce.StringDataEncoding;
import org.yamcs.xtce.StringDataEncoding.SizeType;
import org.yamcs.xtce.StringParameterType;
import org.yamcs.xtce.TimeEpoch;
import org.yamcs.xtce.TransmissionConstraint;
import org.yamcs.xtce.TriggerSetType;
import org.yamcs.xtce.UnitType;
import org.yamcs.xtce.ValueEnumeration;
import org.yamcs.xtce.util.DoubleRange;
import org.yamcs.xtce.util.NameReference;
import org.yamcs.xtce.util.NameReference.Type;
import org.yamcs.xtce.xml.XtceAliasSet;

import com.google.common.base.Objects;
import com.google.common.primitives.UnsignedLongs;

import jxl.Cell;
import jxl.CellType;
import jxl.DateCell;
import jxl.Sheet;
import jxl.Workbook;

/**
 * This class loads database from excel spreadsheets.
 *
 * @author nm, ddw
 *
 */
public class V6Loader extends V6LoaderBase {
    protected HashMap<String, Calibrator> calibrators = new HashMap<>();
    protected HashMap<String, List<ContextCalibrator>> contextCalibrators = new HashMap<>();
    protected HashMap<String, String> timeCalibEpochs = new HashMap<>();
    protected HashMap<String, String> timeCalibScales = new HashMap<>();
    protected HashMap<String, SpreadsheetLoadContext> timeCalibContexts = new HashMap<>();

    protected HashMap<String, EnumerationDefinition> enumerations = new HashMap<>();
    protected HashMap<String, Parameter> parameters = new HashMap<>();
    protected HashSet<Parameter> outputParameters = new HashSet<>(); // Outputs to algorithms
    BasicPrefFactory prefFactory = new BasicPrefFactory();
    Map<Parameter, DataType.Builder<?>> parameterDataTypesBuilders = new HashMap<>();
    Map<DataEncoding.Builder<?>, NameReference> algoReferences = new HashMap<>();

    final ConditionParser conditionParser = new ConditionParser(prefFactory);

    // Increment major when breaking backward compatibility, increment minor when making backward compatible changes
    final static String FORMAT_VERSION = "6.3";
    // Explicitly support these versions (i.e. load without warning)
    final static String[] FORMAT_VERSIONS_SUPPORTED = new String[] { FORMAT_VERSION, "5.3", "5.4", "5.5", "5.6",
            "6.0", "6.1", "6.2", "6.3" };
    String fileFormatVersion;

    protected SpaceSystem rootSpaceSystem;

    public V6Loader(YConfiguration config, Workbook workbook) {
        this(config);
        this.workbook = workbook;
    }

    public V6Loader(YConfiguration config) {
        this(config.getString("file"));
    }

    public V6Loader(String filename) {
        super(filename);
        ctx.file = new File(filename).getName();
    }

    @Override
    public String getConfigName() {
        return ctx.file;
    }

    @Override
    public SpaceSystem load() {
        log.info("Loading spreadsheet {}", path);
        if (workbook == null) {
            loadWorkbook();
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
                        + "Spreadsheet version ({}) differs from loader supported version ({})", ctx.file, version,
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
                            enumeration.add(raw, getContent(cells, CN_CALIB_CALIB2));
                        } catch (NumberFormatException e) {
                            throw new SpreadsheetLoadException(ctx, "Can't get integer from raw value out of '"
                                    + getContent(cells, CN_CALIB_CALIB1) + "'");
                        }
                    } else if (CALIB_TYPE_POLYNOMIAL.equalsIgnoreCase(type)) {
                        pol_coef[j - sr.firstRow] = parseDouble(ctx, getCell(cells, CN_CALIB_CALIB1));
                    } else if (CALIB_TYPE_SPLINE.equalsIgnoreCase(type)) {
                        spline.add(new SplinePoint(parseDouble(ctx, getCell(cells, CN_CALIB_CALIB1)),
                                parseDouble(ctx, getCell(cells, CN_CALIB_CALIB2))));
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

    protected void loadParametersSheet(SpaceSystem spaceSystem, String sheetName, DataSource dataSource) {
        Sheet sheet = switchToSheet(sheetName, false);
        if (sheet == null) {
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
            if (xas != null) {
                param.setAliasSet(xas);
            }
            spaceSystem.addParameter(param);

            String rawtype = cells[IDX_PARAM_RAWTYPE].getContents();
            if ("DerivedValue".equalsIgnoreCase(rawtype)) {
                continue;
            }
            String encodings = null;
            if (hasColumn(cells, IDX_PARAM_ENCODING)) {
                encodings = cells[IDX_PARAM_ENCODING].getContents();
            }

            String engtype = cells[IDX_PARAM_ENGTYPE].getContents();
            String calib = null;
            if (hasColumn(cells, IDX_PARAM_CALIBRATION)) {
                calib = cells[IDX_PARAM_CALIBRATION].getContents();
            }
            if (hasColumn(cells, IDX_PARAM_DESCRIPTION)) {
                String shortDescription = cells[IDX_PARAM_DESCRIPTION].getContents();
                param.setShortDescription(shortDescription);
            }
            if ("n".equals(calib) || "".equals(calib)) {
                calib = null;
            } else if ("y".equalsIgnoreCase(calib)) {
                calib = name;
            }
            if ("uint".equalsIgnoreCase(engtype)) {
                engtype = PARAM_ENGTYPE_UINT32;
            } else if ("int".equalsIgnoreCase(engtype)) {
                engtype = PARAM_ENGTYPE_INT32;
            }

            BaseDataType.Builder<?> ptypeb = null;
            if (PARAM_ENGTYPE_UINT32.equalsIgnoreCase(engtype)) {
                ptypeb = new IntegerParameterType.Builder();
                ((IntegerParameterType.Builder) ptypeb).setSigned(false);
            } else if (PARAM_ENGTYPE_UINT64.equalsIgnoreCase(engtype)) {
                ptypeb = new IntegerParameterType.Builder();
                ((IntegerParameterType.Builder) ptypeb).setSigned(false);
                ((IntegerParameterType.Builder) ptypeb).setSizeInBits(64);
            } else if (PARAM_ENGTYPE_INT32.equalsIgnoreCase(engtype)) {
                ptypeb = new IntegerParameterType.Builder();
            } else if (PARAM_ENGTYPE_INT64.equalsIgnoreCase(engtype)) {
                ptypeb = new IntegerParameterType.Builder();
                ((IntegerParameterType.Builder) ptypeb).setSizeInBits(64);
            } else if (PARAM_ENGTYPE_FLOAT.equalsIgnoreCase(engtype)) {
                ptypeb = new FloatParameterType.Builder();
            } else if (PARAM_ENGTYPE_DOUBLE.equalsIgnoreCase(engtype)) {
                ptypeb = new FloatParameterType.Builder();
                ((FloatParameterType.Builder) ptypeb).setSizeInBits(64);
            } else if (PARAM_ENGTYPE_ENUMERATED.equalsIgnoreCase(engtype)) {
                if (calib == null) {
                    throw new SpreadsheetLoadException(ctx, "Parameter " + name + " has to have an enumeration");
                }
                EnumerationDefinition enumeration = enumerations.get(calib);
                if (enumeration == null) {
                    throw new SpreadsheetLoadException(ctx, "Parameter " + name
                            + " is supposed to have an enumeration '" + calib + "' but the enumeration does not exist");
                }
                ptypeb = new EnumeratedParameterType.Builder();
                for (ValueEnumeration ve : enumeration.values) {
                    ((EnumeratedParameterType.Builder) ptypeb).addEnumerationValue(ve);
                }
            } else if (PARAM_ENGTYPE_STRING.equalsIgnoreCase(engtype)) {
                ptypeb = new StringParameterType.Builder();
            } else if (PARAM_ENGTYPE_BOOLEAN.equalsIgnoreCase(engtype)) {
                ptypeb = new BooleanParameterType.Builder();
            } else if (PARAM_ENGTYPE_BINARY.equalsIgnoreCase(engtype)) {
                ptypeb = new BinaryParameterType.Builder();
            } else if (PARAM_ENGTYPE_TIME.equalsIgnoreCase(engtype)) {
                ptypeb = new AbsoluteTimeParameterType.Builder();
                populateTimeParameter(spaceSystem, (AbsoluteTimeParameterType.Builder) ptypeb, calib);
            } else {
                if (engtype.isEmpty()) {
                    throw new SpreadsheetLoadException(ctx, "No engineering type specified");
                } else {
                    throw new SpreadsheetLoadException(ctx, "Unknown parameter type '" + engtype + "'");
                }
            }
            ptypeb.setName(name);

            String units = null;
            if (hasColumn(cells, IDX_PARAM_ENGUNIT)) {
                units = cells[IDX_PARAM_ENGUNIT].getContents();
            }

            if (!"".equals(units) && units != null && ptypeb instanceof BaseDataType.Builder) {
                UnitType unitType = new UnitType(units);
                ((BaseDataType.Builder<?>) ptypeb).addUnit(unitType);
            }

            DataEncoding.Builder<?> encoding = getDataEncoding(spaceSystem, ctx, "Parameter " + param.getName(),
                    rawtype, engtype,
                    encodings, calib);

            if (ptypeb instanceof IntegerParameterType.Builder) {
                // Integers can be encoded as strings
                if (encoding instanceof StringDataEncoding.Builder) {
                    StringDataEncoding sde = ((StringDataEncoding.Builder) encoding).build();
                    // Create a new int encoding which uses the configured string encoding
                    IntegerDataEncoding.Builder intStringEncoding = new IntegerDataEncoding.Builder()
                            .setStringEncoding(sde);
                    if (calib != null) {
                        Calibrator c = calibrators.get(calib);
                        if (c == null) {
                            throw new SpreadsheetLoadException(ctx, "Parameter " + name + " specified calibrator '"
                                    + calib + "' but the calibrator does not exist");
                        }
                        intStringEncoding.setDefaultCalibrator(c);
                    }
                    ((IntegerParameterType.Builder) ptypeb).setEncoding(intStringEncoding);
                } else {
                    ((IntegerParameterType.Builder) ptypeb).setEncoding(encoding);
                }
            } else if (ptypeb instanceof FloatParameterType.Builder) {
                // Floats can be encoded as strings
                if (encoding instanceof StringDataEncoding.Builder) {
                    StringDataEncoding sde = ((StringDataEncoding.Builder) encoding).build();
                    // Create a new float encoding which uses the configured string encoding
                    FloatDataEncoding.Builder floatStringEncoding = new FloatDataEncoding.Builder()
                            .setStringEncoding(sde);
                    if (calib != null) {
                        Calibrator c = calibrators.get(calib);
                        if (c == null) {
                            throw new SpreadsheetLoadException(ctx, "Parameter " + name + " specified calibrator '"
                                    + calib + "' but the calibrator does not exist.");
                        } else {
                            floatStringEncoding.setDefaultCalibrator(c);
                        }
                    }
                    ((FloatParameterType.Builder) ptypeb).setEncoding(floatStringEncoding);
                } else {
                    ((FloatParameterType.Builder) ptypeb).setEncoding(encoding);
                }
            } else if (ptypeb instanceof EnumeratedParameterType.Builder) {
                if (((EnumeratedParameterType.Builder) ptypeb).getEncoding() != null) {
                    // Some other param has already led to setting the encoding of this shared ptype.
                    // Do some basic consistency checks
                    Integer sib1 = ((EnumeratedParameterType.Builder) ptypeb).getEncoding().getSizeInBits();
                    Integer sib2 = encoding.getSizeInBits();
                    if (!Objects.equal(sib1, sib2)) {
                        throw new SpreadsheetLoadException(ctx,
                                "Multiple parameters are sharing calibrator '" + calib + "' with different bit sizes.");
                    }
                }

                // Enumerations encoded as string integers
                if (encoding instanceof StringDataEncoding.Builder) {
                    StringDataEncoding sde = ((StringDataEncoding.Builder) encoding).build();
                    IntegerDataEncoding.Builder intStringEncoding = new IntegerDataEncoding.Builder()
                            .setStringEncoding(sde);
                    // Don't set calibrator, already done when making ptype
                    ptypeb.setEncoding(intStringEncoding);
                    ;
                } else {
                    ptypeb.setEncoding(encoding);
                }

            } else {
                ptypeb.setEncoding(encoding);
            }
            parameterDataTypesBuilders.put(param, ptypeb);
            ParameterType ptype = (ParameterType) ptypeb.build();

            NameReference nr = algoReferences.get(encoding);
            if (nr != null) {
                BaseDataType bdt = (BaseDataType) ptype;
                nr.addResolvedAction(nd -> {
                    bdt.getEncoding().setFromBinaryTransformAlgorithm((Algorithm) nd);
                });
            }
            param.setParameterType(ptype);
            param.setDataSource(dataSource);
        }
    }

    private void populateTimeParameter(SpaceSystem spaceSystem, AbsoluteTimeParameterType.Builder ptype, String calib) {
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
            NameReference paramRef = getParameterReference(spaceSystem, paraRefName);
            final ParameterInstanceRef parameterInstance = new ParameterInstanceRef();
            paramRef.addResolvedAction(nd -> {
                parameterInstance.setParameter((Parameter) nd);
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
                ptype.setScaling(offset, scale);

            } catch (NumberFormatException e) {
                throw new SpreadsheetLoadException(ctx1,
                        "Invalid scaling '" + scaling + "'for time calibration. Please use 'offset:scale'.");
            }
        }

    }

    DataEncoding.Builder<?> getDataEncoding(SpaceSystem spaceSystem, SpreadsheetLoadContext ctx, String paraArgDescr,
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
            V6LoaderBase.RawTypeEncoding rte = V6LoaderBase.oldToNewEncoding(ctx, bitsize, rawtype);
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
                customBitLength = parseInt(ctx, encodingArgs[0]);
                algoName = encodingArgs[1];
            } else {
                algoName = encodingArgs[0];
            }
            customFromBinaryTransform = new NameReference(algoName, Type.ALGORITHM);
            spaceSystem.addUnresolvedReference(customFromBinaryTransform);
            customFromBinaryTransform.addResolvedAction(nd -> {
                ((Algorithm) nd).setScope(Scope.CONTAINER_PROCESSING);
            });
        }
        DataEncoding.Builder<?> encoding = null;
        if (PARAM_RAWTYPE_INT.equalsIgnoreCase(rawtype) || PARAM_RAWTYPE_UINT.equalsIgnoreCase(rawtype)) {
            if (customFromBinaryTransform != null) {
                IntegerDataEncoding.Builder e = new IntegerDataEncoding.Builder().setSizeInBits(customBitLength);
                customFromBinaryTransform.addResolvedAction(nd -> {
                    e.setFromBinaryTransformAlgorithm((Algorithm) nd);
                });
                encoding = e;
            } else {
                if (encodingArgs.length == 0) {
                    throw new SpreadsheetLoadException(ctx, "Size in bits mandatory for int encoding.");
                }
                int bitlength = parseInt(ctx, encodingArgs[0]);
                encoding = new IntegerDataEncoding.Builder().setSizeInBits(bitlength);
                ((IntegerDataEncoding.Builder) encoding).setEncoding(getIntegerEncoding(ctx, encodingType));
                if (encodingArgs.length > 1) {
                    ((IntegerDataEncoding.Builder) encoding).setByteOrder(getByteOrder(ctx, encodingArgs[1]));
                }
            }

            if (calib != null && !PARAM_ENGTYPE_ENUMERATED.equalsIgnoreCase(engtype)
                    && !PARAM_ENGTYPE_TIME.equalsIgnoreCase(engtype)) {
                ((IntegerDataEncoding.Builder) encoding).setDefaultCalibrator(getNumberCalibrator(paraArgDescr, calib));
                ((IntegerDataEncoding.Builder) encoding).setContextCalibratorList(contextCalibrators.get(calib));
            }
        } else if (PARAM_RAWTYPE_FLOAT.equalsIgnoreCase(rawtype)) {
            if (customFromBinaryTransform != null) {
                FloatDataEncoding.Builder e = new FloatDataEncoding.Builder().setSizeInBits(customBitLength);
                customFromBinaryTransform.addResolvedAction(nd -> {
                    e.setFromBinaryTransformAlgorithm((Algorithm) nd);
                });
                encoding = e;
            } else {
                if (encodingArgs.length == 0) {
                    throw new SpreadsheetLoadException(ctx, "Size in bits mandatory for float encoding.");
                }
                int bitlength = parseInt(ctx, encodingArgs[0]);
                ByteOrder byteOrder = ByteOrder.BIG_ENDIAN;
                if (encodingArgs.length > 1) {
                    byteOrder = getByteOrder(ctx, encodingArgs[1]);
                }
                encoding = new FloatDataEncoding.Builder()
                        .setSizeInBits(bitlength)
                        .setByteOrder(byteOrder)
                        .setFloatEncoding(getFloatEncoding(ctx, encodingType));
            }
            if (calib != null && !PARAM_ENGTYPE_ENUMERATED.equalsIgnoreCase(engtype)
                    && !PARAM_ENGTYPE_TIME.equalsIgnoreCase(engtype)) {
                ((FloatDataEncoding.Builder) encoding).setDefaultCalibrator(getNumberCalibrator(paraArgDescr, calib));
                ((FloatDataEncoding.Builder) encoding).setContextCalibratorList(contextCalibrators.get(calib));
            }
        } else if (PARAM_RAWTYPE_BOOLEAN.equalsIgnoreCase(rawtype)) {
            if (customFromBinaryTransform != null) {
                BooleanDataEncoding.Builder e = new BooleanDataEncoding.Builder();
                e.setSizeInBits(customBitLength);
                customFromBinaryTransform.addResolvedAction(nd -> {
                    e.setFromBinaryTransformAlgorithm((Algorithm) nd);
                });
                encoding = e;
            } else {
                if (encodings != null) {
                    throw new SpreadsheetLoadException(ctx,
                            "Encoding is not allowed for boolean parameters. Use any other raw type if you want to specify the bitlength");
                }
                encoding = new BooleanDataEncoding.Builder();
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
                StringDataEncoding.Builder e = new StringDataEncoding.Builder().setSizeType(SizeType.CUSTOM);
                e.setSizeInBits(customBitLength);
                customFromBinaryTransform.addResolvedAction(nd -> {
                    e.setFromBinaryTransformAlgorithm((Algorithm) nd);
                });
                encoding = e;
            } else if ("fixed".equalsIgnoreCase(encodingType)) {
                if (encodingArgs.length == 0) {
                    throw new SpreadsheetLoadException(ctx, "Encodings for fixed strings need to specify size in bits");
                }
                encoding = new StringDataEncoding.Builder().setSizeType(SizeType.FIXED);
                int bitlength = parseInt(ctx, encodingArgs[0]);
                encoding.setSizeInBits(bitlength);
            } else if ("terminated".equalsIgnoreCase(encodingType)) {
                if (encodingArgs.length == 0) {
                    throw new SpreadsheetLoadException(ctx,
                            "Encodings for terminated strings need to specify termination char");
                }
                encoding = new StringDataEncoding.Builder().setSizeType(SizeType.TERMINATION_CHAR);
                ((StringDataEncoding.Builder) encoding).setTerminationChar(parseByte(ctx, encodingArgs[0]));
                if (encodingArgs.length >= 3) {
                    encoding.setSizeInBits(parseInt(ctx, encodingArgs[2]));
                }
            } else if ("PrependedSize".equalsIgnoreCase(encodingType)) {
                if (encodingArgs.length == 0) {
                    throw new SpreadsheetLoadException(ctx,
                            "Encodings for PrependedSize strings need to specify the size in bits of the size tag.");
                }
                encoding = new StringDataEncoding.Builder().setSizeType(SizeType.LEADING_SIZE);
                ((StringDataEncoding.Builder) encoding).setSizeInBitsOfSizeTag(parseInt(ctx, encodingArgs[0]));
                if (encodingArgs.length >= 3) {
                    encoding.setSizeInBits(parseInt(ctx, encodingArgs[2]));
                }
            } else {
                throw new SpreadsheetLoadException(ctx, "Unsupported encoding type " + encodingType
                        + " Use one of 'fixed', 'terminated', 'PrependedSize' or 'custom'");
            }
            ((StringDataEncoding.Builder) encoding).setEncoding(charset);
        } else if (PARAM_RAWTYPE_BINARY.equalsIgnoreCase(rawtype)) {
            if (customFromBinaryTransform != null) {
                BinaryDataEncoding.Builder e = new BinaryDataEncoding.Builder().setType(BinaryDataEncoding.Type.CUSTOM);
                e.setSizeInBits(customBitLength);
                customFromBinaryTransform.addResolvedAction(nd -> {
                    e.setFromBinaryTransformAlgorithm((Algorithm) nd);
                });
                encoding = e;
            } else if ("fixed".equalsIgnoreCase(encodingType)) {
                if (encodingArgs.length == 0) {
                    throw new SpreadsheetLoadException(ctx, "Encodings for fixed strings need to specify size in bits");
                }
                encoding = new BinaryDataEncoding.Builder().setType(BinaryDataEncoding.Type.FIXED_SIZE);
                int bitlength = parseInt(ctx, encodingArgs[0]);
                encoding.setSizeInBits(bitlength);
            } else if ("PrependedSize".equalsIgnoreCase(encodingType)) {
                if (encodingArgs.length == 0) {
                    throw new SpreadsheetLoadException(ctx,
                            "Encodings for PrependedSize strings need to specify the size in bits of the size tag.");
                }
                encoding = new BinaryDataEncoding.Builder()
                        .setType(BinaryDataEncoding.Type.LEADING_SIZE)
                        .setSizeInBitsOfSizeTag(parseInt(ctx, encodingArgs[0]));
            } else {
                throw new SpreadsheetLoadException(ctx, "Unsupported encoding type " + encodingType
                        + " Use one of 'fixed', 'PrependedSize' or 'custom'");
            }
        } else {
            throw new SpreadsheetLoadException(ctx, "Invalid rawType '" + rawtype + "'");
        }

        if (customFromBinaryTransform != null) {
            algoReferences.put(encoding, customFromBinaryTransform);
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

        HashMap<String, SequenceContainer> containers = new HashMap<>();
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
            String name = cells[IDX_CONT_NAME].getContents();
            String parent = null;
            String condition = null;
            if (cells.length > IDX_CONT_PARENT) {
                parent = cells[IDX_CONT_PARENT].getContents();
                if (cells.length <= IDX_CONT_CONDITION) {
                    throw new SpreadsheetLoadException(ctx,
                            "Parent specified but without inheritance condition on container");
                }
                condition = cells[IDX_CONT_CONDITION].getContents();
                parents.put(name, parent);
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
            if (hasColumn(cells, IDX_CONT_SIZEINBITS)) {
                containerSizeInBits = Integer.decode(cells[IDX_CONT_SIZEINBITS].getContents());
            }

            RateInStream rate = null;
            if (hasColumn(cells, IDX_CONT_EXPECTED_INTERVAL)) {
                int expint = Integer.decode(cells[IDX_CONT_EXPECTED_INTERVAL].getContents());
                rate = new RateInStream(-1, expint);
            }

            String description = "";
            if (hasColumn(cells, IDX_CONT_DESCRIPTION)) {
                description = cells[IDX_CONT_DESCRIPTION].getContents();
            }

            // create a new SequenceContainer that will hold the parameters (i.e. SequenceEntries) for the
            // ORDINARY/SUB/AGGREGATE packets, and register that new SequenceContainer in the containers hashmap
            SequenceContainer container = new SequenceContainer(name);
            container.setSizeInBits(containerSizeInBits);
            containers.put(name, container);
            container.setRateInStream(rate);
            if (!description.isEmpty()) {
                container.setShortDescription(description);
                container.setLongDescription(description);
            }

            if (hasColumn(cells, IDX_CONT_FLAGS)) {
                String flags = cells[IDX_CONT_FLAGS].getContents();
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

            // now, we start processing the parameters (or references to aggregate containers)
            boolean end = false;
            int counter = 0; // sequence number of the SequenceEntrys in the SequenceContainer
            while (!end && (i < sheet.getRows())) {

                // get the next row, containing a measurement/aggregate reference
                cells = jumpToRow(sheet, i);
                // determine whether we have not reached the end of the packet definition.
                if (!hasColumn(cells, IDX_CONT_PARA_NAME)) {
                    end = true;
                    continue;
                }

                // extract a few variables, for further use
                String flags = cells[IDX_CONT_FLAGS].getContents();
                String paraname = cells[IDX_CONT_PARA_NAME].getContents();
                int relpos = 0;
                if (hasColumn(cells, IDX_CONT_RELPOS)) {
                    relpos = Integer.decode(cells[IDX_CONT_RELPOS].getContents());
                }

                // we add the relative position to the absoluteOffset, to specify the location of the new parameter.
                // We only do this if the absoluteOffset is not equal to -1,
                // because that would mean that we cannot and should not use absolute positions anymore
                if (absoluteoffset != -1) {
                    absoluteoffset += relpos;
                }
                // the repeat string will contain the number of times a measurement (or aggregate container) should be
                // repeated. It is a String because at this point it can be either a number or a reference to another
                // measurement
                String repeat = "";
                // we check whether the measurement (or aggregate container) has a '*' inside it, meaning that it is a
                // repeat measurement/aggregate
                Matcher m = Pattern.compile("(.*)[*](.*)").matcher(paraname);
                if (m.matches()) {
                    repeat = m.group(1);
                    paraname = m.group(2);
                }

                // check whether this measurement/aggregate actually has an entry in the parameters table
                // first we check if it is a measurement by trying to retrieve it from the parameters map. If this
                // succeeds we add it as a new parameterentry,
                // otherwise, we search for it in the containersmap, as it is probably an aggregate. If it is not, we
                // encountered an error
                // note that one of the next 2 lines will return null, but this does not pose a problem, it makes
                // programming easier along the way
                Parameter param = parameters.get(paraname);
                SequenceContainer sc = containers.get(paraname);
                // if the sequenceentry is repeated a fixed number of times, this number is recorded in the 'repeated'
                // variable and used to calculate the next absoluteoffset (done below)
                int repeated = -1;
                if (param != null) {
                    checkThatParameterSizeCanBeComputed(param);
                    SequenceEntry se;
                    if (flags.contains("L") || flags.contains("l")) {
                        throw new SpreadsheetLoadException(ctx,
                                "Cannot specify (anymore) the endianess of a parameter in the container sheet. Please use the encoding column in the parameter sheet. ");
                    }

                    // if absoluteoffset is -1, somewhere along the line we came across a measurement or aggregate that
                    // had as a result that the absoluteoffset could not be determined anymore; hence, a relative
                    // position is added
                    if (absoluteoffset == -1) {
                        se = new ParameterEntry(relpos, ReferenceLocationType.PREVIOUS_ENTRY, param);
                    } else {
                        se = new ParameterEntry(absoluteoffset, ReferenceLocationType.CONTAINER_START, param);
                    }
                    // also check if the parameter should be added multiple times, and if so, do so
                    repeated = addRepeat(se, repeat);
                    container.addEntry(se);
                } else if (sc != null) {
                    // ok, we found it as an aggregate, so we add it to the entryList of container, as a new
                    // ContainerEntry
                    // if absoluteoffset is -1, somewhere along the line we came across a measurement or aggregate that
                    // had as a result that the absoluteoffset could not be determined anymore; hence, a relative
                    // position is added
                    SequenceEntry se;
                    if (absoluteoffset == -1) {
                        se = new ContainerEntry(relpos, ReferenceLocationType.PREVIOUS_ENTRY, sc);
                    } else {
                        se = new ContainerEntry(absoluteoffset,
                                ReferenceLocationType.CONTAINER_START, sc);
                    }
                    // also check if the parameter should be added multiple times, and if so, do so
                    repeated = addRepeat(se, repeat);
                    container.addEntry(se);
                } else {
                    throw new SpreadsheetLoadException(ctx, "The measurement/container '" + paraname
                            + "' was not found in the parameters or containers map");
                }
                // after adding this measurement, we need to update the absoluteoffset for the next one. For this, we
                // add the size of the current SequenceEntry to the absoluteoffset
                int size = getSize(param, sc);
                if ((repeated != -1) && (size != -1) && (absoluteoffset != -1)) {
                    absoluteoffset += repeated * size;
                } else {
                    // from this moment on, absoluteoffset is set to -1, meaning that all subsequent SequenceEntries
                    // must be relative
                    absoluteoffset = -1;
                }

                i++;
                counter++;
            }

            // at this point, we have added all the parameters and aggregate containers to the current packets. What
            // remains to be done is link it with its base
            if (parent != null) {
                parents.put(name, parent);
                // the condition is parsed and used to create the container.restrictionCriteria
                // 1) get the parent, from the same sheet
                SequenceContainer sc = containers.get(parent);
                // the parent is not in the same sheet, try to get from the MDB
                if (sc == null) {
                    sc = spaceSystem.getSequenceContainer(parent);
                }
                if (sc != null) {
                    container.setBaseContainer(sc);
                    if (("5.2".compareTo(fileFormatVersion) > 0) && (!parents.containsKey(parent))) {
                        // prior to version 5.2 of the format, the second level of containers were used as archive
                        // partitions
                        // TODO: remove when switching to 6.x format
                        container.useAsArchivePartition(true);
                    }
                } else {
                    NameReference nr = new NameReference(parent, Type.SEQUENCE_CONTAINER)
                            .addResolvedAction(nd -> {
                                SequenceContainer sc1 = (SequenceContainer) nd;
                                container.setBaseContainer(sc1);
                                if ("5.2".compareTo(fileFormatVersion) > 0) {
                                    if (sc1.getBaseContainer() == null) {
                                        container.useAsArchivePartition(true);
                                    }
                                }
                            });
                    spaceSystem.addUnresolvedReference(nr);
                }

                // 2) extract the condition and create the restrictioncriteria
                if (!"".equals(condition.trim())) {
                    container.setRestrictionCriteria(toMatchCriteria(spaceSystem, condition));
                    MatchCriteria.printParsedMatchCriteria(log.getJulLogger(), container.getRestrictionCriteria(), "");
                }
            }

            spaceSystem.addSequenceContainer(container);
        }
    }

    private void checkThatParameterSizeCanBeComputed(Parameter param) {
        DataEncoding encoding = ((BaseDataType) param.getParameterType()).getEncoding();
        if (encoding == null) {
            throw new SpreadsheetLoadException(ctx,
                    "Parameter " + param.getName() + " is part of a container but has no data encoding specified");
        }
        if (encoding.getSizeInBits() > 0) {
            return;
        }
        if (encoding instanceof IntegerDataEncoding) {
            IntegerDataEncoding intenc = (IntegerDataEncoding) encoding;
            if (intenc.getEncoding() != IntegerDataEncoding.Encoding.STRING) {
                throw new SpreadsheetLoadException(ctx, "Parameter " + param.getName()
                        + " is part of a container and encoded as integer but has no size in bits specified");
            }
        } else if (encoding instanceof FloatDataEncoding) {
            FloatDataEncoding fenc = (FloatDataEncoding) encoding;
            if (fenc.getEncoding() != FloatDataEncoding.Encoding.STRING) {
                throw new SpreadsheetLoadException(ctx, "Parameter " + param.getName()
                        + " is part of a container and encoded as float but has no size in bits specified");
            }
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

            String name = cells[IDX_CMD_NAME].getContents();
            String parent = null;
            String argAssignment = null;
            if (cells.length > IDX_CMD_PARENT) {
                parent = cells[IDX_CMD_PARENT].getContents();
                if (hasColumn(cells, IDX_CMD_ARG_ASSIGNMENT)) {
                    argAssignment = cells[IDX_CMD_ARG_ASSIGNMENT].getContents();
                }
            }

            if ("".equals(parent)) {
                parent = null;
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

            if (hasColumn(cells, IDX_CMD_FLAGS)) {
                String flags = cells[IDX_CMD_FLAGS].getContents();
                if (flags.contains("A")) {
                    cmd.setAbstract(true);
                }
            }

            if (hasColumn(cells, IDX_CMD_DESCRIPTION)) {
                String shortDescription = cells[IDX_CMD_DESCRIPTION].getContents();
                cmd.setShortDescription(shortDescription);
            }

            // we mark the start of the CMD and advance to the next line, to get to the first argument (if there is one)
            i++;

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
                Position pos = Position.RELATIVE_ZERO;
                if (hasColumn(cells, IDX_CMD_RELPOS)) {
                    pos = getPosition(ctx, cells[IDX_CMD_RELPOS].getContents());
                }

                if (pos.relative && counter == 0 && extraOffset != -1) {
                    pos = new Position(pos.pos, false);
                }

                if (!hasColumn(cells, IDX_CMD_ENGTYPE)) {
                    throw new SpreadsheetLoadException(ctx,
                            "engtype is not specified for " + argname + " on line " + (i + 1));
                }
                String engType = cells[IDX_CMD_ENGTYPE].getContents();

                if (engType.equalsIgnoreCase("FixedValue")) {
                    if (!hasColumn(cells, IDX_CMD_DEFVALUE)) {
                        throw new SpreadsheetLoadException(ctx, "default value is not specified for " + argname
                                + " which is a FixedValue on line " + (i + 1));
                    }
                    String hexValue = cells[IDX_CMD_DEFVALUE].getContents();
                    byte[] binaryValue = StringConverter.hexStringToArray(hexValue);

                    if (!hasColumn(cells, IDX_CMD_ENCODING)) {
                        throw new SpreadsheetLoadException(ctx, "sizeInBits is not specified for " + argname
                                + " which is a FixedValue on line " + (i + 1));
                    }
                    int sizeInBits = parseInt(ctx, cells[IDX_CMD_ENCODING].getContents());
                    FixedValueEntry fve;
                    if (pos.relative) {
                        fve = new FixedValueEntry(pos.pos, ReferenceLocationType.PREVIOUS_ENTRY,
                                argname, binaryValue, sizeInBits);
                    } else {
                        fve = new FixedValueEntry(pos.pos + ((extraOffset != -1) ? extraOffset : 0),
                                ReferenceLocationType.CONTAINER_START, argname, binaryValue, sizeInBits);
                    }
                    container.addEntry(fve);
                } else {
                    loadArgument(spaceSystem, cells, cmd, container, extraOffset, counter);
                }

                i++;
                counter++;
            }

            // at this point, we have added all the parameters and aggregate containers to the current packets. What
            // remains to be done is link it with its base
            if (parent != null) {
                // the condition is parsed and used to create the container.restrictionCriteria
                // 1) get the parent, from the same sheet
                MetaCommand parentCmd = commands.get(parent);

                // the parent is not in the same sheet, try to get from the MDB
                if (parentCmd == null) {
                    parentCmd = spaceSystem.getMetaCommand(parent);
                }
                if (parentCmd != null) {
                    cmd.setBaseMetaCommand(parentCmd);
                    container.setBaseContainer(parentCmd.getCommandContainer());
                } else {
                    final MetaCommand mc = cmd;
                    final CommandContainer mcc = container;
                    NameReference nr = new NameReference(parent, Type.META_COMMAND).addResolvedAction(nd -> {
                        mc.setBaseMetaCommand((MetaCommand) nd);
                        mcc.setBaseContainer(((MetaCommand) nd).getCommandContainer());
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
                        slevel = Significance.Levels.valueOf(significance.toUpperCase());
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

            String cmdName = cells[IDX_CMDVERIF_NAME].getContents();
            MetaCommand cmd = spaceSystem.getMetaCommand(cmdName);
            if (cmd == null) {
                throw new SpreadsheetLoadException(ctx, "Could not find a command named '" + cmdName + "'");
            }

            int cmdEnd = i + 1;
            while (cmdEnd < sheet.getRows()) {
                cells = jumpToRow(sheet, cmdEnd);
                if (hasColumn(cells, IDX_CMDVERIF_NAME)) {
                    break;
                }
                cmdEnd++;
            }
            while (i < cmdEnd) {
                cells = jumpToRow(sheet, i);
                if (hasColumn(cells, IDX_CMDVERIF_STAGE)) {
                    String stage = cells[IDX_CMDVERIF_STAGE].getContents();

                    if (!hasColumn(cells, IDX_CMDVERIF_CHECKWINDOW)) {
                        throw new SpreadsheetLoadException(ctx, "No checkwindow specified for the command verifier ");
                    }
                    String checkws = cells[IDX_CMDVERIF_CHECKWINDOW].getContents();
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
                    CheckWindow.TimeWindowIsRelativeToType cwr = TimeWindowIsRelativeToType.LAST_VERIFIER;

                    if (hasColumn(cells, IDX_CMDVERIF_CHECKWINDOW_RELATIVETO)) {
                        String s = cells[IDX_CMDVERIF_CHECKWINDOW_RELATIVETO].getContents();
                        try {
                            cwr = TimeWindowIsRelativeToType.fromXls(s);
                        } catch (IllegalArgumentException e) {
                            throw new SpreadsheetLoadException(ctx,
                                    "Invalid value '" + s
                                            + "' specified for CheckWindow relative to parameter. Use one of Use one of [CommandRelease, LastVerifier]");
                        }
                    }
                    CheckWindow cw = new CheckWindow(start, stop, cwr);
                    if (!hasColumn(cells, IDX_CMDVERIF_TYPE)) {
                        throw new SpreadsheetLoadException(ctx, "No type specified for the command verifier ");
                    }
                    String types = cells[IDX_CMDVERIF_TYPE].getContents();
                    CommandVerifier.Type type = null;
                    try {
                        type = CommandVerifier.Type.valueOf(types.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new SpreadsheetLoadException(ctx, "Invalid command verifier type '" + types
                                + "' specified. Supported are: " + Arrays.toString(CommandVerifier.Type.values()));
                    }

                    CommandVerifier cmdVerifier = new CommandVerifier(type, stage, cw);

                    if (type == CommandVerifier.Type.CONTAINER) {
                        String containerName = cells[IDX_CMDVERIF_TEXT].getContents();
                        SequenceContainer container = spaceSystem.getSequenceContainer(containerName);
                        if (container == null) {
                            throw new SpreadsheetLoadException(ctx,
                                    "Cannot find sequence container '" + containerName + "' required for the verifier");
                        }
                        cmdVerifier.setContainerRef(container);
                    } else if (type == CommandVerifier.Type.ALGORITHM) {
                        String algoName = cells[IDX_CMDVERIF_TEXT].getContents();
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
                        if (hasColumn(cells, IDX_CMDVERIF_ONSUCCESS)) {
                            tas = cells[IDX_CMDVERIF_ONSUCCESS].getContents();
                            CommandVerifier.TerminationAction ta = TerminationAction.valueOf(tas);
                            cmdVerifier.setOnSuccess(ta);
                        }
                        if (hasColumn(cells, IDX_CMDVERIF_ONFAIL)) {
                            tas = cells[IDX_CMDVERIF_ONFAIL].getContents();
                            CommandVerifier.TerminationAction ta = TerminationAction.valueOf(tas);
                            cmdVerifier.setOnFail(ta);
                        }
                        if (hasColumn(cells, IDX_CMDVERIF_ONTIMEOUT)) {
                            tas = cells[IDX_CMDVERIF_ONTIMEOUT].getContents();
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
        String splitted[] = argAssignment.split(";");
        for (String part : splitted) {
            aal.add(toArgumentAssignment(part));
        }
        return aal;
    }

    private ArgumentAssignment toArgumentAssignment(String argAssignment) {
        Matcher m = Pattern.compile("(.*?)(=)(.*)").matcher(argAssignment);
        if (!m.matches()) {
            throw new SpreadsheetLoadException(ctx, "Cannot parse argument assignment '" + argAssignment + "'");
        }
        String aname = m.group(1).trim();
        String value = m.group(3).trim();
        return new ArgumentAssignment(aname, value);
    }

    private void loadArgument(SpaceSystem spaceSystem, Cell[] cells, MetaCommand cmd, CommandContainer container,
            int extraOffset, int counter) {
        String engType = cells[IDX_CMD_ENGTYPE].getContents();
        String name = cells[IDX_CMD_ARGNAME].getContents();

        Position pos = Position.RELATIVE_ZERO;

        if (hasColumn(cells, IDX_CMD_RELPOS)) {
            pos = getPosition(ctx, cells[IDX_CMD_RELPOS].getContents());
        }
        if (pos.relative && counter == 0 && extraOffset != -1) {
            pos = new Position(pos.pos, false);
        }

        String calib = null;
        if (hasColumn(cells, IDX_CMD_CALIBRATION)) {
            calib = cells[IDX_CMD_CALIBRATION].getContents();
        }
        String flags = null;
        if (hasColumn(cells, IDX_CMD_FLAGS)) {
            flags = cells[IDX_CMD_FLAGS].getContents();
        }

        String encodings = null;
        if (hasColumn(cells, IDX_CMD_ENCODING)) {
            encodings = cells[IDX_CMD_ENCODING].getContents();
        }

        String rawType = engType;
        if (hasColumn(cells, IDX_CMD_RAWTYPE)) {
            rawType = cells[IDX_CMD_RAWTYPE].getContents();
            if ("double".equals(rawType)) {
                rawType = "float";
            }
        }

        if ("n".equals(calib) || "".equals(calib)) {
            calib = null;
        } else if ("y".equalsIgnoreCase(calib)) {
            calib = name;
        }

        BaseDataType.Builder atype = null;
        if ("uint".equalsIgnoreCase(engType)) {
            atype = new IntegerArgumentType.Builder();
            ((IntegerArgumentType.Builder) atype).setSigned(false);
        } else if (PARAM_ENGTYPE_UINT64.equalsIgnoreCase(engType)) {
            atype = new IntegerArgumentType.Builder();
            ((IntegerArgumentType.Builder) atype).setSigned(false);
            ((IntegerArgumentType.Builder) atype).setSizeInBits(64);
        } else if ("int".equalsIgnoreCase(engType)) {
            atype = new IntegerArgumentType.Builder();
        } else if (PARAM_ENGTYPE_INT64.equalsIgnoreCase(engType)) {
            atype = new IntegerArgumentType.Builder();
            ((IntegerArgumentType.Builder) atype).setSizeInBits(64);
        } else if (PARAM_ENGTYPE_FLOAT.equalsIgnoreCase(engType)) {
            atype = new FloatArgumentType.Builder();
        } else if (PARAM_ENGTYPE_DOUBLE.equalsIgnoreCase(engType)) {
            atype = new FloatArgumentType.Builder();
            ((FloatArgumentType.Builder) atype).setSizeInBits(64);
        } else if ("enumerated".equalsIgnoreCase(engType)) {
            if (calib == null) {
                throw new SpreadsheetLoadException(ctx, "Argument " + name + " has to have an enumeration");
            }
            EnumerationDefinition enumeration = enumerations.get(calib);
            if (enumeration == null) {
                throw new SpreadsheetLoadException(ctx, "Argument " + name + " is supposed to have an enumeration '"
                        + calib + "' but the enumeration does not exist");
            }
            atype = new EnumeratedArgumentType.Builder();
            for (ValueEnumeration ve : enumeration.values) {
                ((EnumeratedArgumentType.Builder) atype).addEnumerationValue(ve);
            }
        } else if ("string".equalsIgnoreCase(engType)) {
            atype = new StringArgumentType.Builder();
        } else if ("binary".equalsIgnoreCase(engType)) {
            atype = new BinaryArgumentType.Builder();
        } else if ("boolean".equalsIgnoreCase(engType)) {
            atype = new BooleanArgumentType.Builder();
        } else {
            throw new SpreadsheetLoadException(ctx, "Unknown argument type " + engType);
        }
        if (cmd.getArgument(name) != null) {
            throw new SpreadsheetLoadException(ctx, "Duplicate argument with name '" + name + "'");
        }

        atype.setName(name);
        Argument arg = new Argument(name);
        cmd.addArgument(arg);

        if (hasColumn(cells, IDX_CMD_DEFVALUE)) {
            String v = cells[IDX_CMD_DEFVALUE].getContents();
            try {
                if (atype instanceof BooleanArgumentType.Builder) {
                    if ("true".equalsIgnoreCase(v)) {
                        v = BooleanDataType.DEFAULT_ONE_STRING_VALUE;
                    } else if ("false".equalsIgnoreCase(v)) {
                        v = BooleanDataType.DEFAULT_ZERO_STRING_VALUE;
                    }
                    arg.setInitialValue(atype.build().convertType(v));
                }
            } catch (Exception e) {
                throw new SpreadsheetLoadException(ctx, "Cannot parse default value '" + v + "'");
            }
        }

        if (hasColumn(cells, IDX_CMD_RANGELOW) || hasColumn(cells, IDX_CMD_RANGEHIGH)) {
            if (atype instanceof IntegerArgumentType.Builder) {
                if (((IntegerArgumentType.Builder) atype).isSigned()) {
                    long minInclusive = Long.MIN_VALUE;
                    long maxInclusive = Long.MAX_VALUE;
                    if (hasColumn(cells, IDX_CMD_RANGELOW)) {
                        minInclusive = Long.decode(cells[IDX_CMD_RANGELOW].getContents());
                    }
                    if (hasColumn(cells, IDX_CMD_RANGEHIGH)) {
                        maxInclusive = Long.decode(cells[IDX_CMD_RANGEHIGH].getContents());
                    }
                    IntegerValidRange range = new IntegerValidRange(minInclusive, maxInclusive);
                    ((IntegerArgumentType.Builder) atype).setValidRange(range);
                } else {
                    long minInclusive = 0;
                    long maxInclusive = ~0;
                    if (hasColumn(cells, IDX_CMD_RANGELOW)) {
                        minInclusive = UnsignedLongs.decode(cells[IDX_CMD_RANGELOW].getContents());
                    }
                    if (hasColumn(cells, IDX_CMD_RANGEHIGH)) {
                        maxInclusive = UnsignedLongs.decode(cells[IDX_CMD_RANGEHIGH].getContents());
                    }
                    IntegerValidRange range = new IntegerValidRange(minInclusive, maxInclusive);
                    ((IntegerArgumentType.Builder) atype).setValidRange(range);

                }
            } else if (atype instanceof FloatArgumentType.Builder) {
                double minInclusive = Double.NEGATIVE_INFINITY;
                double maxInclusive = Double.POSITIVE_INFINITY;
                if (hasColumn(cells, IDX_CMD_RANGELOW)) {
                    minInclusive = Double.parseDouble(cells[IDX_CMD_RANGELOW].getContents());
                }
                if (hasColumn(cells, IDX_CMD_RANGEHIGH)) {
                    maxInclusive = Double.parseDouble(cells[IDX_CMD_RANGEHIGH].getContents());
                }
                FloatValidRange range = new FloatValidRange(minInclusive, maxInclusive);
                ((FloatArgumentType.Builder) atype).setValidRange(range);
            }
        }

        if (hasColumn(cells, IDX_CMD_DESCRIPTION)) {
            String shortDescription = cells[IDX_CMD_DESCRIPTION].getContents();
            arg.setShortDescription(shortDescription);
        }

        ArgumentEntry ae;
        // if absoluteoffset is -1, somewhere along the line we came across a measurement or aggregate that had as a
        // result that the absoluteoffset could not be determined anymore; hence, a relative position is added
        if (pos.relative) {
            ae = new ArgumentEntry(pos.pos, ReferenceLocationType.PREVIOUS_ENTRY, arg);
        } else {
            ae = new ArgumentEntry(pos.pos + ((extraOffset != -1) ? extraOffset : 0),
                    ReferenceLocationType.CONTAINER_START, arg);
        }

        container.addEntry(ae);
        ByteOrder byteOrder = ByteOrder.BIG_ENDIAN;
        if ((flags != null) && (flags.contains("L") || flags.contains("l"))) {
            throw new SpreadsheetLoadException(ctx,
                    "Specifying little endian using argument flags is not anymore supported. Please use the encoding column");
        }

        String units = null;
        if (hasColumn(cells, IDX_CMD_ENGUNIT)) {
            units = cells[IDX_CMD_ENGUNIT].getContents();
            if (!"".equals(units) && units != null && atype instanceof BaseDataType.Builder) {
                UnitType unitType = new UnitType(units);
                ((BaseDataType.Builder) atype).addUnit(unitType);
            }
        }

        DataEncoding.Builder<?> encoding = getDataEncoding(spaceSystem, ctx, "Argument " + arg.getName(), rawType,
                engType,
                encodings, calib);

        if (atype instanceof IntegerArgumentType.Builder) {
            // Integers can be encoded as strings
            if (encoding instanceof StringDataEncoding.Builder) {
                StringDataEncoding sde = ((StringDataEncoding.Builder) encoding).build();
                // Create a new int encoding which uses the configured string encoding
                IntegerDataEncoding.Builder intStringEncoding = new IntegerDataEncoding.Builder()
                        .setStringEncoding(sde);
                if (calib != null) {
                    Calibrator c = calibrators.get(calib);
                    if (c == null) {
                        throw new SpreadsheetLoadException(ctx, "Parameter " + name + " specified calibrator '" + calib
                                + "' but the calibrator does not exist");
                    }
                    intStringEncoding.setDefaultCalibrator(c);
                }
                ((IntegerArgumentType.Builder) atype).setEncoding(intStringEncoding);
            } else {
                ((IntegerArgumentType.Builder) atype).setEncoding(encoding);
            }
        } else if (atype instanceof BinaryArgumentType.Builder) {
            ((BinaryArgumentType.Builder) atype).setEncoding(encoding);
        } else if (atype instanceof FloatArgumentType.Builder) {
            // Floats can be encoded as strings
            if (encoding instanceof StringDataEncoding.Builder) {
                StringDataEncoding sde = ((StringDataEncoding.Builder) encoding).build();
                // Create a new float encoding which uses the configured string encoding
                FloatDataEncoding.Builder floatStringEncoding = new FloatDataEncoding.Builder().setStringEncoding(sde);
                if (calib != null) {
                    Calibrator c = calibrators.get(calib);
                    if (c == null) {
                        throw new SpreadsheetLoadException(ctx, "Parameter " + name + " specified calibrator '" + calib
                                + "' but the calibrator does not exist.");
                    } else {
                        floatStringEncoding.setDefaultCalibrator(c);
                    }
                }
                floatStringEncoding.setByteOrder(byteOrder);
                ((FloatArgumentType.Builder) atype).setEncoding(floatStringEncoding);
            } else {
                ((FloatArgumentType.Builder) atype).setEncoding(encoding);
            }
        } else if (atype instanceof EnumeratedArgumentType.Builder) {
            if (((EnumeratedArgumentType.Builder) atype).getEncoding() != null) {
                // Some other param has already led to setting the encoding of this shared ptype.
                // Do some basic consistency checks
                if (((EnumeratedArgumentType.Builder) atype).getEncoding().getSizeInBits() != encoding
                        .getSizeInBits()) {
                    throw new SpreadsheetLoadException(ctx,
                            "Multiple parameters are sharing calibrator '" + calib + "' with different bit sizes.");
                }
            }

            // Enumerations encoded as string integers
            if (encoding instanceof StringDataEncoding.Builder) {
                StringDataEncoding sde = ((StringDataEncoding.Builder) encoding).build();
                IntegerDataEncoding.Builder intStringEncoding = new IntegerDataEncoding.Builder()
                        .setStringEncoding(sde);
                // Don't set calibrator, already done when making ptype
                ((EnumeratedArgumentType.Builder) atype).setEncoding(intStringEncoding);
                intStringEncoding.setByteOrder(byteOrder);
            } else {
                ((EnumeratedArgumentType.Builder) atype).setEncoding(encoding);
            }
        } else if (atype instanceof StringArgumentType.Builder) {
            ((StringArgumentType.Builder) atype).setEncoding(encoding);
        } else if (atype instanceof BooleanArgumentType.Builder) {
            ((BooleanArgumentType.Builder) atype).setEncoding(encoding);
        } else {
            throw new IllegalStateException("Don't know what to do with " + atype);
        }
        arg.setArgumentType((ArgumentType) atype.build());
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
            String name = cells[IDX_ALGO_NAME].getContents();
            String algorithmLanguage = cells[IDX_ALGO_LANGUGAGE].getContents();
            if (!"JavaScript".equals(algorithmLanguage) && !"python".equals(algorithmLanguage)
                    && !"java".equalsIgnoreCase(algorithmLanguage)) {
                throw new SpreadsheetLoadException(ctx, "Invalid algorithm language '" + algorithmLanguage
                        + "' specified. Supported are 'JavaScript', 'python' and java (case sensitive)");
            }

            String algorithmText = cells[IDX_ALGO_TEXT].getContents();
            XtceAliasSet xas = getAliases(firstRow, cells);

            // Check that there is not specified by mistake a in/out param already on the same line with the algorithm
            // name
            if (hasColumn(cells, IDX_ALGO_PARA_INOUT) || hasColumn(cells, IDX_ALGO_PARA_REF)) {
                throw new SpreadsheetLoadException(ctx,
                        "Algorithm paramters have to start on the next line from the algorithm name and text definition");
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
                String paraRefName = cells[IDX_ALGO_PARA_REF].getContents();
                if (hasColumn(cells, IDX_ALGO_PARA_INOUT)) {
                    paraInout = cells[IDX_ALGO_PARA_INOUT].getContents();
                }

                String flags = hasColumn(cells, IDX_ALGO_PARA_FLAGS) ? cells[IDX_ALGO_PARA_FLAGS].getContents() : "";

                if (paraInout == null) {
                    throw new SpreadsheetLoadException(ctx, "You must specify in/out attribute for this parameter");
                }
                if ("in".equalsIgnoreCase(paraInout)) {
                    if (paraRefName.startsWith(Mdb.YAMCS_CMD_SPACESYSTEM_NAME)
                            || paraRefName.startsWith(Mdb.YAMCS_CMDHIST_SPACESYSTEM_NAME)) {
                        algorithm.setScope(Algorithm.Scope.COMMAND_VERIFICATION);
                    }
                    inputParameterRefs.add(paraRefName);
                    NameReference paramRef = getParameterReference(spaceSystem, paraRefName);
                    final ParameterInstanceRef parameterInstance = new ParameterInstanceRef();
                    parameterInstance.setRelativeTo(InstanceRelativeTo.PACKET_START_ACROSS_PACKETS);
                    paramRef.addResolvedAction(nd -> {
                        parameterInstance.setParameter((Parameter) nd);
                    });

                    if (cells.length > IDX_ALGO_PARA_INSTANCE) {
                        if (!"".equals(cells[IDX_ALGO_PARA_INSTANCE].getContents())) {
                            int instance = Integer.valueOf(cells[IDX_ALGO_PARA_INSTANCE].getContents());
                            if (instance > 0) {
                                throw new SpreadsheetLoadException(ctx, "Instance '" + instance
                                        + "' not supported. Can only go back in time. Use values <= 0.");
                            }
                            parameterInstance.setInstance(instance);
                        }
                    }

                    InputParameter inputParameter = new InputParameter(parameterInstance);
                    if (hasColumn(cells, IDX_ALGO_PARA_NAME)) {
                        inputParameter.setInputName(cells[IDX_ALGO_PARA_NAME].getContents());
                    }
                    if (flags.contains("M")) {
                        inputParameter.setMandatory(true);
                    }
                    algorithm.addInput(inputParameter);
                } else if ("out".equalsIgnoreCase(paraInout)) {
                    NameReference paramRef = getParameterReference(spaceSystem, paraRefName);
                    OutputParameter outputParameter = new OutputParameter();
                    paramRef.addResolvedAction(nd -> {
                        Parameter param = (Parameter) nd;
                        outputParameter.setParameter(param);
                    });
                    if (hasColumn(cells, IDX_ALGO_PARA_NAME)) {
                        outputParameter.setOutputName(cells[IDX_ALGO_PARA_NAME].getContents());
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
            String triggerText = hasColumn(cells, IDX_ALGO_TRIGGER) ? cells[IDX_ALGO_TRIGGER].getContents() : "";
            if (triggerText.startsWith("OnParameterUpdate")) {
                Matcher matcher = ALGO_PARAMETER_PATTERN.matcher(triggerText);
                if (matcher.matches()) {
                    for (String s : matcher.group(1).split(",")) {
                        Parameter para = spaceSystem.getParameter(s.trim());
                        if (para != null) {
                            OnParameterUpdateTrigger trigger = new OnParameterUpdateTrigger(para);
                            triggerSet.addOnParameterUpdateTrigger(trigger);
                        } else {
                            NameReference nr = new NameReference(s.trim(), Type.PARAMETER)
                                    .addResolvedAction(nd -> {
                                        OnParameterUpdateTrigger trigger = new OnParameterUpdateTrigger((Parameter) nd);
                                        triggerSet.addOnParameterUpdateTrigger(trigger);
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
                        NameReference nr = new NameReference(paraRef, Type.PARAMETER)
                                .addResolvedAction(nd -> {
                                    OnParameterUpdateTrigger trigger = new OnParameterUpdateTrigger((Parameter) nd);
                                    triggerSet.addOnParameterUpdateTrigger(trigger);
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
            if (!hasColumn(cells, IDX_ALARM_PARAM_NAME)) {
                throw new SpreadsheetLoadException(ctx, "Alarms must be attached to a parameter name");
            }
            String paramName = cells[IDX_ALARM_PARAM_NAME].getContents();
            NameReference paraRef = getParameterReference(spaceSystem, paramName);

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
            MatchCriteria previousContext = null;
            int minViolations = -1;
            AlarmReportType reportType = AlarmReportType.ON_SEVERITY_CHANGE;
            for (int j = start; j < paramEnd; j++) {
                cells = jumpToRow(sheet, j);
                MatchCriteria context = previousContext;
                if (hasColumn(cells, IDX_ALARM_CONTEXT)) {
                    String contextString = cells[IDX_ALARM_CONTEXT].getContents();
                    context = toMatchCriteria(spaceSystem, contextString);
                    minViolations = -1;
                }

                if (hasColumn(cells, IDX_ALARM_MIN_VIOLATIONS)) {
                    minViolations = Integer.parseInt(cells[IDX_ALARM_MIN_VIOLATIONS].getContents());
                }

                if (hasColumn(cells, IDX_ALARM_REPORT)) {
                    if ("OnSeverityChange".equalsIgnoreCase(cells[IDX_ALARM_REPORT].getContents())) {
                        reportType = AlarmReportType.ON_SEVERITY_CHANGE;
                    } else if ("OnValueChange".equalsIgnoreCase(cells[IDX_ALARM_REPORT].getContents())) {
                        reportType = AlarmReportType.ON_VALUE_CHANGE;
                    } else {
                        throw new SpreadsheetLoadException(ctx,
                                "Unrecognized report type '" + cells[IDX_ALARM_REPORT].getContents() + "'");
                    }
                }

                checkAndAddAlarm(cells, AlarmLevels.WATCH, paraRef, context, IDX_ALARM_WATCH_TRIGGER,
                        IDX_ALARM_WATCH_VALUE);
                checkAndAddAlarm(cells, AlarmLevels.WARNING, paraRef, context, IDX_ALARM_WARNING_TRIGGER,
                        IDX_ALARM_WARNING_VALUE);
                checkAndAddAlarm(cells, AlarmLevels.DISTRESS, paraRef, context, IDX_ALARM_DISTRESS_TRIGGER,
                        IDX_ALARM_DISTRESS_VALUE);
                checkAndAddAlarm(cells, AlarmLevels.CRITICAL, paraRef, context, IDX_ALARM_CRITICAL_TRIGGER,
                        IDX_ALARM_CRITICAL_VALUE);
                checkAndAddAlarm(cells, AlarmLevels.SEVERE, paraRef, context, IDX_ALARM_SEVERE_TRIGGER,
                        IDX_ALARM_SEVERE_VALUE);

                addAlarmDetails(paraRef, context, reportType, minViolations);

                previousContext = context;
            }

            start = paramEnd;
        }
    }

    private void checkAndAddAlarm(Cell[] cells, AlarmLevels level, NameReference paraRef, MatchCriteria context,
            int idxTrigger, int idxValue) {
        if (!hasColumn(cells, idxTrigger) || !hasColumn(cells, idxValue)) {
            return;
        }
        String trigger = cells[idxTrigger].getContents();
        String triggerValue = cells[idxValue].getContents();

        SpreadsheetLoadContext ctx1 = ctx.copy();
        paraRef.addResolvedAction(nd -> {

            Parameter para = (Parameter) nd;
            DataType.Builder ptype = parameterDataTypesBuilders.get(para);

            if (ptype instanceof IntegerParameterType.Builder) {
                double tvd = parseDouble(ctx1, cells[idxValue]);
                IntegerParameterType.Builder ipt = (IntegerParameterType.Builder) ptype;
                if ("low".equals(trigger)) {
                    ipt.addAlarmRange(context, new DoubleRange(tvd, Double.POSITIVE_INFINITY), level);
                } else if ("high".equals(trigger)) {
                    ipt.addAlarmRange(context, new DoubleRange(Double.NEGATIVE_INFINITY, tvd), level);
                } else {
                    throw new SpreadsheetLoadException(ctx1,
                            "Unexpected trigger type '" + trigger + "' for numeric parameter " + para.getName());
                }
            } else if (ptype instanceof FloatParameterType.Builder) {
                double tvd = parseDouble(ctx1, cells[idxValue]);
                FloatParameterType.Builder fpt = (FloatParameterType.Builder) ptype;
                if ("low".equals(trigger)) {
                    fpt.addAlarmRange(context, new DoubleRange(tvd, Double.POSITIVE_INFINITY), level);
                } else if ("high".equals(trigger)) {
                    fpt.addAlarmRange(context, new DoubleRange(Double.NEGATIVE_INFINITY, tvd), level);
                } else {
                    throw new SpreadsheetLoadException(ctx1,
                            "Unexpected trigger type '" + trigger + "' for numeric parameter " + para.getName());
                }
            } else if (ptype instanceof EnumeratedParameterType) {
                EnumeratedParameterType.Builder ept = (EnumeratedParameterType.Builder) ptype;
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
        });
    }

    private void addAlarmDetails(NameReference paraRef, MatchCriteria context, AlarmReportType reportType,
            int minViolations) {

        paraRef.addResolvedAction(nd -> {
            Parameter para = (Parameter) nd;
            DataType.Builder<?> ptype = parameterDataTypesBuilders.get(para);

            // Set minviolations and alarmreporttype
            AlarmType alarm = null;

            if (ptype instanceof IntegerParameterType) {
                IntegerParameterType.Builder ipt = (IntegerParameterType.Builder) ptype;
                alarm = (context == null) ? ipt.getDefaultAlarm() : ipt.getNumericContextAlarm(context);
                if (reportType != AlarmType.DEFAULT_REPORT_TYPE) {
                    ipt.createOrGetAlarm(context).setAlarmReportType(reportType);
                }
            } else if (ptype instanceof FloatParameterType.Builder) {
                FloatParameterType.Builder fpt = (FloatParameterType.Builder) ptype;
                alarm = (context == null) ? fpt.getDefaultAlarm() : fpt.getNumericContextAlarm(context);
                if (reportType != AlarmType.DEFAULT_REPORT_TYPE) {
                    fpt.createOrGetAlarm(context).setAlarmReportType(reportType);
                }
            } else if (ptype instanceof EnumeratedParameterType.Builder) {
                EnumeratedParameterType.Builder ept = (EnumeratedParameterType.Builder) ptype;
                alarm = (context == null) ? ept.getDefaultAlarm() : ept.getContextAlarm(context);
                if (reportType != AlarmType.DEFAULT_REPORT_TYPE) {
                    ept.createOrGetAlarm(context).setAlarmReportType(reportType);
                }
            }
            if (alarm != null) { // It's possible that this gets called multiple times per alarm, but doesn't matter
                alarm.setMinViolations((minViolations == -1) ? 1 : minViolations);
                alarm.setAlarmReportType(reportType);
            }
        });
    }

    /**
     *
     * @param criteriaString
     * @return
     */
    private MatchCriteria toMatchCriteria(SpaceSystem spaceSystem, String criteriaString) {
        criteriaString = criteriaString.trim();
        prefFactory.setCurrentSpaceSystem(spaceSystem);
        try {
            if ((criteriaString.startsWith("&(") || criteriaString.startsWith("|("))
                    && (criteriaString.endsWith(")"))) {
                return conditionParser.parseBooleanExpression(criteriaString);
            } else if (criteriaString.contains(";")) {
                ComparisonList cl = new ComparisonList();
                String splitted[] = criteriaString.split(";");
                for (String part : splitted) {
                    cl.addComparison(conditionParser.toComparison(part));
                }
                return cl;
            } else {
                return conditionParser.toComparison(criteriaString);
            }
        } catch (ParseException e) {
            throw new SpreadsheetLoadException(ctx, e.getMessage());
        }
    }

    private int getSize(Parameter param, SequenceContainer sc) {
        // either we have a Parameter or we have a SequenceContainer, we cannot have both or neither
        if (param != null) {
            DataEncoding de = ((BaseDataType) param.getParameterType()).getEncoding();
            if (de == null) {
                throw new SpreadsheetLoadException(ctx, "Cannot determine the data encoding for " + param.getName());
            }

            if ((de instanceof FloatDataEncoding) || (de instanceof IntegerDataEncoding)
                    || (de instanceof BinaryDataEncoding) || (de instanceof BooleanDataEncoding)) {
                return de.getSizeInBits();
            } else if (de instanceof StringDataEncoding) {
                return -1;
            } else {
                throw new SpreadsheetLoadException(ctx, "No known size for data encoding : " + de);
            }
        } else {
            return sc.getSizeInBits();
        }
    }

    /**
     * If repeat != "", decodes it to either an integer or a parameter and adds it to the SequenceEntry If repeat is an
     * integer, this integer is returned
     */
    private int addRepeat(SequenceEntry se, String repeat) {
        if (!repeat.equals("")) {
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

package org.yamcs.xtce.xlsv7;

import java.io.StringReader;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.yamcs.mdb.BaseSpreadsheetLoader;
import org.yamcs.mdb.SpreadsheetLoadContext;
import org.yamcs.mdb.SpreadsheetLoadException;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.xlsv7.parser.AggrMember;
import org.yamcs.xtce.xlsv7.parser.AggregateTypeParser;

import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;

/**
 * static fields and methods to reduce the size of the SpreadsheetLoader
 * 
 * @author nm
 *
 */
public abstract class V7LoaderBase extends BaseSpreadsheetLoader {

    public V7LoaderBase(String filename) {
        super(filename);
    }

    // columns in the containers sheet
    static final String CN_CONT_NAME = "container name";
    static final String CN_CONT_PARENT = "parent";
    static final String CN_CONT_CONDITION = "condition";
    static final String CN_CONT_FLAGS = "flags";
    static final String CN_CONT_ENTRY = "entry";
    static final String CN_CONT_RELPOS = "position";
    static final String CN_CONT_SIZEINBITS = "size in bits";
    static final String CN_CONT_EXPECTED_INTERVAL = "expected interval";
    static final String CN_CONT_DESCRIPTION = "description";
    static final String CN_CONT_LONG_DESCRIPTION = "long description";

    // columns in the algorithms sheet
    static final String CN_ALGO_NAME = "algorithm name";
    static final String CN_ALGO_LANGUGAGE = "language";
    static final String CN_ALGO_TEXT = "text";
    static final String CN_ALGO_TRIGGER = "trigger";
    static final String CN_ALGO_DESCRIPTION = "description";
    static final String CN_ALGO_LONG_DESCRIPTION = "long description";
    static final String CN_ALGO_PARA_INOUT = "in/out";
    static final String CN_ALGO_PARA_REF = "parameter reference";
    static final String CN_ALGO_PARA_INSTANCE = "instance";
    static final String CN_ALGO_PARA_NAME = "variable name";
    static final String CN_ALGO_PARA_FLAGS = "flags";

    // columns in the alarms sheet
    static final String CN_ALARM_PARAM_NAME = "parameter reference";
    static final String CN_ALARM_CONTEXT = "context";
    static final String CN_ALARM_REPORT = "report";
    static final String CN_ALARM_MIN_VIOLATIONS = "min violations";
    static final String CN_ALARM_WATCH_TRIGGER = "watch trigger type";
    static final String CN_ALARM_WATCH_VALUE = "watch trigger value";
    static final String CN_ALARM_WARNING_TRIGGER = "warning trigger type";
    static final String CN_ALARM_WARNING_VALUE = "warning trigger value";
    static final String CN_ALARM_DISTRESS_TRIGGER = "distress trigger type";
    static final String CN_ALARM_DISTRESS_VALUE = "distress trigger value";
    static final String CN_ALARM_CRITICAL_TRIGGER = "critical trigger type";
    static final String CN_ALARM_CRITICAL_VALUE = "critical trigger value";
    static final String CN_ALARM_SEVERE_TRIGGER = "severe trigger type";
    static final String CN_ALARM_SEVERE_VALUE = "severe trigger value";

    // columns in the command sheet
    protected static final String CN_CMD_NAME = "command name";
    protected static final String CN_CMD_PARENT = "parent";
    protected static final String CN_CMD_ARG_ASSIGNMENT = "argument assignment";
    protected static final String CN_CMD_FLAGS = "flags";
    protected static final String CN_CMD_ARGNAME = "argument name";
    protected static final String CN_CMD_POSITION = "position";
    protected static final String CN_CMD_DTYPE = "data type";
    protected static final String CN_CMD_DEFVALUE = "default value";
    protected static final String CN_CMD_RANGELOW = "range low";
    protected static final String CN_CMD_RANGEHIGH = "range high";
    protected static final String CN_CMD_DESCRIPTION = "description";
    protected static final String CN_CMD_LONG_DESCRIPTION = "long description";

    // columns in the command options sheet
    protected static final int IDX_CMDOPT_NAME = 0;
    protected static final int IDX_CMDOPT_TXCONST = 1;
    protected static final int IDX_CMDOPT_TXCONST_TIMEOUT = 2;
    protected static final int IDX_CMDOPT_SIGNIFICANCE = 3;
    protected static final int IDX_CMDOPT_SIGNIFICANCE_REASON = 4;

    // columns in the command verification sheet
    protected static final String CN_CMDVERIF_NAME = "command name";
    protected static final String CN_CMDVERIF_STAGE = "cmdverifier stage";
    protected static final String CN_CMDVERIF_TYPE = "cmdverifier type";
    protected static final String CN_CMDVERIF_TEXT = "cmdverifier text";;
    protected static final String CN_CMDVERIF_CHECKWINDOW = "time check window";
    protected static final String CN_CMDVERIF_CHECKWINDOW_RELATIVETO = "checkwindow is relative to";
    protected static final String CN_CMDVERIF_ONSUCCESS = "onsuccess";
    protected static final String CN_CMDVERIF_ONFAIL = "onfail";
    protected static final String CN_CMDVERIF_ONTIMEOUT = "ontimeout";

    // columns in the changelog sheet
    protected static final int IDX_LOG_VERSION = 0;
    protected static final int IDX_LOG_DATE = 1;
    protected static final int IDX_LOG_MESSAGE = 2;
    protected static final int IDX_LOG_AUTHOR = 3;

    protected static final String CALIB_TYPE_ENUMERATION = "enumeration";
    protected static final String CALIB_TYPE_POLYNOMIAL = "polynomial";
    protected static final String CALIB_TYPE_SPLINE = "spline";
    protected static final String CALIB_TYPE_JAVA_EXPRESSION = "java-expression";
    protected static final String CALIB_TYPE_TIME = "time";

    static final Pattern ALGO_PARAMETER_PATTERN = Pattern.compile("OnParameterUpdate\\((.*)\\)");
    static final Pattern ALGO_FIRERATE_PATTERN = Pattern.compile("OnPeriodicRate\\((\\d+)\\)");

    static final Pattern PARAM_ENCODING_PATTERN_old = Pattern.compile("\\d+");
    static final Pattern PARAM_ENCODING_PATTERN = Pattern.compile("(\\w+)\\s?\\(([\\w\\s,\\-\\.]+)\\)");

    protected static final String PARAM_RAWTYPE_STRING_PREPENDED = "prependedsizestring";
    protected static final String PARAM_RAWTYPE_STRING_TERMINATED = "terminatedstring";
    protected static final String PARAM_RAWTYPE_STRING_FIXED = "fixedstring";

    // columns names in the data type sheet
    static final String CN_DTYPE_NAME = "type name";
    static final String CN_DTYPE_ENCODING = "encoding";
    static final String CN_DTYPE_RAWTYPE = "raw type";
    static final String CN_DTYPE_ENGTYPE = "eng type";
    static final String CN_DTYPE_ENGUNIT = "eng unit";
    static final String CN_DTYPE_CALIBRATION = "calibration";
    static final String CN_DTYPE_INITVALUE = "initial value";
    static final String CN_DTYPE_DESCRIPTION = "description";
    static final String CN_DTYPE_LONG_DESCRIPTION = "long description";

    // columns names in the parameter type sheet
    static final String CN_PARAM_NAME = "parameter name";
    static final String CN_PARAM_DTYPE = "data type";
    static final String CN_PARAM_INITVALUE = "initial value";
    static final String CN_PARAM_DESCRIPTION = "description";
    static final String CN_PARAM_FLAGS = "flags";
    static final String CN_PARAM_LONG_DESCRIPTION = "long description";

    protected static final String SHEET_DATATYPES = "DataTypes";

    // final static Pattern AGGREGATE_PATTERN = Pattern.compile("aggregate:?\\(?\\{(.+)\\}\\)?");
    final static Pattern AGGREGATE_PATTERN = Pattern.compile("\\s*\\(?\\s*\\{(.+)\\}\\s*\\)?");

    // the list of sheets that can be part of subsystems with a sub1/sub2/sub3/SheetName notation
    static String[] SUBSYSTEM_SHEET_NAMES = {
            SHEET_CALIBRATION,
            SHEET_TELEMETERED_PARAMETERS,
            SHEET_LOCAL_PARAMETERS,
            SHEET_DERIVED_PARAMETERS,
            SHEET_CONTAINERS,
            SHEET_ALGORITHMS,
            SHEET_ALARMS,
            SHEET_COMMANDS,
            SHEET_COMMANDOPTIONS,
            SHEET_COMMANDVERIFICATION,
            SHEET_DATATYPES
    };

    public static Map<String, Map<String, Integer>> readHeaders(Workbook workbook) {
        List<String> relevantSheets = Arrays.stream(workbook.getSheetNames()).filter(sheetName -> {
            return Arrays.stream(SUBSYSTEM_SHEET_NAMES).filter(s -> sheetName.endsWith(s)).findAny().isPresent();
        }).collect(Collectors.toList());

        Map<String, Map<String, Integer>> results = new HashMap<>();

        for (String sheetName : relevantSheets) {
            Map<String, Integer> m = new HashMap<>();
            Sheet sheet = workbook.getSheet(sheetName);
            Cell[] cells = sheet.getRow(0);
            for (int i = 0; i < cells.length; i++) {
                String hname = cells[i].getContents().toLowerCase();
                if (hname != null && !hname.isEmpty()) {
                    if (m.containsKey(hname)) {
                        throw new SpreadsheetLoadException(new SpreadsheetLoadContext(),
                                "Duplicate column name '" + hname + "' on header line in" + sheetName);
                    }
                    m.put(hname, i);
                }
            }

            results.put(sheetName, m);
        }
        return results;
    }

    public static RawTypeEncoding oldToNewEncoding(SpreadsheetLoadContext ctx, int bitsize, String rawtype) {
        RawTypeEncoding rte = null;

        if ("uint".equalsIgnoreCase(rawtype)) {
            rte = new RawTypeEncoding("int", "unsigned(" + bitsize + ")");
        } else if (rawtype.toLowerCase().startsWith("int")) {
            if ("int".equals(rawtype)) {
                rte = new RawTypeEncoding("int", "twosComplement(" + bitsize + ")");
            } else {
                int startBracket = rawtype.indexOf('(');
                if (startBracket != -1) {
                    int endBracket = rawtype.indexOf(')', startBracket);
                    if (endBracket != -1) {
                        String intRepresentation = rawtype.substring(startBracket + 1, endBracket).trim().toLowerCase();
                        if ("2c".equals(intRepresentation)) {
                            rte = new RawTypeEncoding("int", "twosComplement(" + bitsize + ")");
                        } else if ("si".equals(intRepresentation)) {
                            rte = new RawTypeEncoding("int", "signMagnitude(" + bitsize + ")");
                        } else {
                            throw new SpreadsheetLoadException(ctx,
                                    "Unsupported signed integer representation: " + intRepresentation);
                        }
                    }
                } else {
                    rte = new RawTypeEncoding("int", "twosComplement(" + bitsize + ")");
                }
            }
        } else if (PARAM_RAWTYPE_FLOAT.equalsIgnoreCase(rawtype)) {
            rte = new RawTypeEncoding("float", "IEEE754_1985(" + bitsize + ")");
        } else if (PARAM_RAWTYPE_STRING.equalsIgnoreCase(rawtype)) {
            // Version <= 1.6 String type
            if (bitsize == -1) {
                // Assume null-terminated if no length specified
                rte = new RawTypeEncoding("string", "Terminated(0x0, UTF-8)");
            } else {
                rte = new RawTypeEncoding("string", "fixed(" + bitsize + ", UTF-8)");
            }
        } else if (PARAM_RAWTYPE_STRING_FIXED.equalsIgnoreCase(rawtype)) {
            // v1.7 String type
            // FIXEDSTRING
            if (bitsize == -1) {
                throw new SpreadsheetLoadException(ctx, "Bit length is mandatory for fixedstring raw type");
            }
            rte = new RawTypeEncoding("string", "fixed(" + bitsize + ", UTF-8)");
        } else if (rawtype.toLowerCase().startsWith(PARAM_RAWTYPE_STRING_TERMINATED)) {
            // v1.7 String type
            // TERMINATEDSTRING
            String termChar = "0x0";
            // Use specified byte if found, otherwise accept class default.
            int startBracket = rawtype.indexOf('(');
            if (startBracket != -1) {
                int endBracket = rawtype.indexOf(')', startBracket);
                if (endBracket != -1) {
                    try {
                        termChar = rawtype.substring(rawtype.indexOf('x', startBracket) - 1, endBracket).trim();
                    } catch (NumberFormatException e) {
                        throw new SpreadsheetLoadException(ctx,
                                "Could not parse specified base 16 terminator from " + rawtype);
                    }
                }
            }
            if (bitsize != -1) {
                rte = new RawTypeEncoding("string", "Terminated(" + termChar + ", UTF-8, " + bitsize + ")");
            } else {
                rte = new RawTypeEncoding("string", "Terminated(" + termChar + ", UTF-8)");
            }
        } else if (rawtype.toLowerCase().startsWith(PARAM_RAWTYPE_STRING_PREPENDED)) {
            // v1.7 String type
            // PREPENDEDSIZESTRING
            int sizeInBitsOfSizeTag = 16;

            // Use specified size if found, otherwise accept class default.
            int startBracket = rawtype.indexOf('(');
            if (startBracket != -1) {
                int endBracket = rawtype.indexOf(')', startBracket);
                if (endBracket != -1) {
                    try {
                        sizeInBitsOfSizeTag = Integer.parseInt(rawtype.substring(startBracket + 1, endBracket).trim());
                    } catch (NumberFormatException e) {
                        throw new SpreadsheetLoadException(ctx, "Could not parse integer size from " + rawtype);
                    }
                }
                if (bitsize != -1) {
                    rte = new RawTypeEncoding("string",
                            "PrependedSize(" + sizeInBitsOfSizeTag + ", UTF-8, " + bitsize + ")");
                } else {
                    rte = new RawTypeEncoding("string", "PrependedSize(" + sizeInBitsOfSizeTag + ", UTF-8)");
                }
            } else {
                rte = new RawTypeEncoding("string", "fixed(" + bitsize + ", UTF-8)");
            }
        } else if (PARAM_RAWTYPE_BINARY.equalsIgnoreCase(rawtype)) {
            rte = new RawTypeEncoding("binary", "fixed(" + bitsize + ")");
        } else if (PARAM_RAWTYPE_BOOLEAN.equalsIgnoreCase(rawtype)) {
            if (bitsize != -1) {
                throw new SpreadsheetLoadException(ctx,
                        "Bit length is not allowed for boolean parameters (defaults to 1). Use any other raw type if you want to specify the bitlength");
            }
            rte = new RawTypeEncoding("boolean", null);
        } else {
            throw new SpreadsheetLoadException(ctx, "Invalid raw type '" + rawtype + "'");
        }

        return rte;
    }

    static FloatDataEncoding.Encoding getFloatEncoding(SpreadsheetLoadContext ctx, String encodingType) {
        if ("IEEE754_1985".equalsIgnoreCase(encodingType)) {
            return FloatDataEncoding.Encoding.IEEE754_1985;
        } else if ("string".equalsIgnoreCase(encodingType)) {
            return FloatDataEncoding.Encoding.STRING;
        } else {
            throw new SpreadsheetLoadException(ctx, "Unsupported float data encoding type '" + encodingType
                    + "'. Supported values: IEEE754_1985 and string");
        }

    }

    static IntegerDataEncoding.Encoding getIntegerEncoding(SpreadsheetLoadContext ctx, String encodingType) {
        if ("twosComplement".equalsIgnoreCase(encodingType)) {
            return IntegerDataEncoding.Encoding.TWOS_COMPLEMENT;
        } else if ("signMagnitude".equalsIgnoreCase(encodingType)) {
            return IntegerDataEncoding.Encoding.SIGN_MAGNITUDE;
        } else if ("onesComplement".equalsIgnoreCase(encodingType)) {
            return IntegerDataEncoding.Encoding.ONES_COMPLEMENT;
        } else if ("unsigned".equalsIgnoreCase(encodingType)) {
            return IntegerDataEncoding.Encoding.UNSIGNED;
        } else if ("string".equalsIgnoreCase(encodingType)) {
            return IntegerDataEncoding.Encoding.STRING;
        } else {
            throw new SpreadsheetLoadException(ctx, "Unsupported integer data encoding type '" + encodingType
                    + "'. Supported values: unsigned, twosComplement, signMagnitude and string");
        }

    }

    static ByteOrder getByteOrder(SpreadsheetLoadContext ctx, String bo) {
        if ("LE".equalsIgnoreCase(bo)) {
            return ByteOrder.LITTLE_ENDIAN;
        } else if ("BE".equalsIgnoreCase(bo)) {
            return ByteOrder.BIG_ENDIAN;
        } else {
            throw new SpreadsheetLoadException(ctx, "Unsupported byte order '" + bo + "'. Supported values: LE|BE");
        }
    }

    static int findEnd(Sheet sheet, int startRow, int endRow, int col) {
        int r = startRow;
        int i = startRow + 1;
        while (i < endRow) {
            Cell[] cells = sheet.getRow(i);
            if (cells.length > col
                    && !cells[0].getContents().startsWith("#")
                    && cells[col].getContents().length() > 0) {
                break;
            }
            if (cells.length > 0 && !cells[0].getContents().startsWith("#")) {
                r = i;
            }
            i++;
        }
        return r;
    }

    static Position getPosition(SpreadsheetLoadContext ctx, String pos) {
        if (!pos.contains(":")) {
            return new Position(parseInt(ctx, pos), true);
        } else {
            String[] a = pos.split("\\s*:\\s*");
            if (a.length != 2) {
                throw new SpreadsheetLoadException(ctx, getInvalidPositionMsg(pos));
            }
            int p = parseInt(ctx, a[1]);
            if ("r".equalsIgnoreCase(a[0])) {
                return new Position(p, true);
            } else if ("a".equalsIgnoreCase(a[0])) {
                return new Position(p, false);
            } else {
                throw new SpreadsheetLoadException(ctx, getInvalidPositionMsg(pos));
            }
        }
    }

    /**
     * parses strings of type
     *
     * <pre>
     *   {
     *      type1 name1;
     *      type2 name2
     *   }
     * </pre>
     *
     * into a map mapping names to types
     * @throws ParseException 
     */
    protected List<AggrMember> parseAggregateExpr(String engType) throws ParseException {
        AggregateTypeParser parser = new AggregateTypeParser(new StringReader(engType));        
        return parser.parse();        
    }


    static String getInvalidPositionMsg(String pos) {
        return "Invalid position '" + pos + "' specified. "
                + "Use 'r:<d>' or 'a:<d>' for relative respectively absolute position. '<d>' can be used as a shortcut for 'r:<d>'";
    }

    static class RawTypeEncoding {
        String rawType;
        String encoding;

        public RawTypeEncoding(String rawType, String encoding) {
            this.rawType = rawType;
            this.encoding = encoding;
        }
    }

    static class Position {
        public static final Position RELATIVE_ZERO = new Position(0, true);
        final int pos;
        final boolean relative;// or absolute

        public Position(int pos, boolean relative) {
            this.pos = pos;
            this.relative = relative;
        }
    }

    static class DataTypeRecord {
        int row;
        String name;
        String encoding;
        String rawType;
        String engType;
        String engUnit;
        String calibration;
        String initialValue;
        String description;
        String longDescription;
        SpaceSystem spaceSystem;
    }

}

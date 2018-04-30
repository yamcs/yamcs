package org.yamcs.xtce;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jxl.Cell;
import jxl.CellType;
import jxl.NumberCell;
import jxl.Sheet;
import jxl.Workbook;

/**
 * static fields and methods to reduce the size of the SpreadsheetLoader
 * 
 * @author nm
 *
 */
public class SpreadsheetLoaderBits {
    // columns in the parameters sheet (including local parameters)
    static final int IDX_PARAM_NAME = 0;
    static final int IDX_PARAM_ENCODING = 1;
    static final int IDX_PARAM_RAWTYPE = 2;
    static final int IDX_PARAM_ENGTYPE = 3;
    static final int IDX_PARAM_ENGUNIT = 4;
    static final int IDX_PARAM_CALIBRATION = 5;
    static final int IDX_PARAM_DESCRIPTION = 6;

    // columns in the containers sheet
    static final int IDX_CONT_NAME = 0;
    static final int IDX_CONT_PARENT = 1;
    static final int IDX_CONT_CONDITION = 2;
    static final int IDX_CONT_FLAGS = 3;
    static final int IDX_CONT_PARA_NAME = 4;
    static final int IDX_CONT_RELPOS = 5;
    static final int IDX_CONT_SIZEINBITS = 6;
    static final int IDX_CONT_EXPECTED_INTERVAL = 7;
    static final int IDX_CONT_DESCRIPTION = 8;

    // columns in the algorithms sheet
    static final int IDX_ALGO_NAME = 0;
    static final int IDX_ALGO_LANGUGAGE = 1;
    static final int IDX_ALGO_TEXT = 2;
    static final int IDX_ALGO_TRIGGER = 3;
    static final int IDX_ALGO_PARA_INOUT = 4;
    static final int IDX_ALGO_PARA_REF = 5;
    static final int IDX_ALGO_PARA_INSTANCE = 6;
    static final int IDX_ALGO_PARA_NAME = 7;
    static final int IDX_ALGO_PARA_FLAGS = 8;

    // columns in the alarms sheet
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

    // columns in the processed parameters sheet
    protected static final int IDX_PP_UMI = 0;
    protected static final int IDX_PP_GROUP = 1;
    protected static final int IDX_PP_ALIAS = 2;

    // columns in the command sheet
    protected static final int IDX_CMD_NAME = 0;
    protected static final int IDX_CMD_PARENT = 1;
    protected static final int IDX_CMD_ARG_ASSIGNMENT = 2;
    protected static final int IDX_CMD_FLAGS = 3;
    protected static final int IDX_CMD_ARGNAME = 4;
    protected static final int IDX_CMD_RELPOS = 5;
    protected static final int IDX_CMD_ENCODING = 6;
    protected static final int IDX_CMD_ENGTYPE = 7;
    protected static final int IDX_CMD_RAWTYPE = 8;
    protected static final int IDX_CMD_DEFVALUE = 9;
    protected static final int IDX_CMD_ENGUNIT = 10;
    protected static final int IDX_CMD_CALIBRATION = 11;
    protected static final int IDX_CMD_RANGELOW = 12;
    protected static final int IDX_CMD_RANGEHIGH = 13;
    protected static final int IDX_CMD_DESCRIPTION = 14;

    // columns in the command options sheet
    protected static final int IDX_CMDOPT_NAME = 0;
    protected static final int IDX_CMDOPT_TXCONST = 1;
    protected static final int IDX_CMDOPT_TXCONST_TIMEOUT = 2;
    protected static final int IDX_CMDOPT_SIGNIFICANCE = 3;
    protected static final int IDX_CMDOPT_SIGNIFICANCE_REASON = 4;

    // columns in the command verification sheet
    protected static final int IDX_CMDVERIF_NAME = 0;
    protected static final int IDX_CMDVERIF_STAGE = 1;
    protected static final int IDX_CMDVERIF_TYPE = 2;
    protected static final int IDX_CMDVERIF_TEXT = 3;
    protected static final int IDX_CMDVERIF_CHECKWINDOW = 4;
    protected static final int IDX_CMDVERIF_CHECKWINDOW_RELATIVETO = 5;
    protected static final int IDX_CMDVERIF_ONSUCCESS = 6;
    protected static final int IDX_CMDVERIF_ONFAIL = 7;
    protected static final int IDX_CMDVERIF_ONTIMEOUT = 8;

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
    protected static final String PARAM_ENGTYPE_TIME = "time";

    protected static final String PARAM_RAWTYPE_FLOAT = "float";
    protected static final String PARAM_RAWTYPE_INT = "int";
    protected static final String PARAM_RAWTYPE_UINT = "uint";
    protected static final String PARAM_RAWTYPE_DOUBLE = "double";
    protected static final String PARAM_RAWTYPE_BOOLEAN = "boolean";
    protected static final String PARAM_RAWTYPE_BINARY = "binary";
    protected static final String PARAM_RAWTYPE_BINARY_PREPENDED = "prependedbinary";
    protected static final String PARAM_RAWTYPE_BINARY_TERMINATED = "terminatedbinary";
    protected static final String PARAM_RAWTYPE_STRING = "string";

    static final Pattern ALGO_PARAMETER_PATTERN = Pattern.compile("OnParameterUpdate\\((.*)\\)");
    static final Pattern ALGO_FIRERATE_PATTERN = Pattern.compile("OnPeriodicRate\\((\\d+)\\)");

    static final Pattern PARAM_ENCODING_PATTERN_old = Pattern.compile("\\d+");
    static final Pattern PARAM_ENCODING_PATTERN = Pattern.compile("(\\w+)\\s?\\(([\\w\\s,\\-\\.]+)\\)");

    protected static final String PARAM_RAWTYPE_STRING_PREPENDED = "prependedsizestring";
    protected static final String PARAM_RAWTYPE_STRING_TERMINATED = "terminatedstring";
    protected static final String PARAM_RAWTYPE_STRING_FIXED = "fixedstring";

    static final String CN_CONTEXT = "context";

    // columns names in calibrations sheet
    static final String CN_CALIB_NAME = "calibrator name";
    static final String CN_CALIB_TYPE = "type";
    static final String CN_CALIB_CALIB1 = "calib1";
    static final String CN_CALIB_CALIB2 = "calib2";

    // sheet names
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
            SHEET_COMMANDVERIFICATION
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
                String hname = cells[i].getContents();
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

    static int parseInt(SpreadsheetLoadContext ctx, String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new SpreadsheetLoadException(ctx, "Could not parse integer from '" + s + "'");
        }
    }

    static double parseDouble(SpreadsheetLoadContext ctx, Cell cell) {
        // Important that we check cell type here. This avoid issues with french vs american commas.
        if ((cell.getType() == CellType.NUMBER) || (cell.getType() == CellType.NUMBER_FORMULA)) {
            return ((NumberCell) cell).getValue();
        } else {
            try {
                return Double.parseDouble(cell.getContents());
            } catch (NumberFormatException e) {
                throw new SpreadsheetLoadException(ctx, "Could not parse double from '" + cell.getContents() + "'");
            }
        }
    }

    /*
     * Used for parsing calibration and alarms with context
     * 
     * scans col1 starting with startRow until it finds some content
     * then starts creating sub ranges based on col2
     * 
     * a range starts when the col1 is not empty. The start of the range automatically starts its first subRange
     * 
     * a subrange is finished in one of the three cases:
     * - an empty line is encountered
     * - another range starts (col1 is not empty)
     * - another subRange starts(col2 is not empty)
     * 
     * the range is finished by the last line before the start of new range
     * 
     * if col2 is -1 then only one subRange is returned
     * 
     */
    static Range findRange(Sheet sheet, int startRow, int col1, int col2) {
        int numRows = sheet.getRows();
        int rangeStart = -1;
        int rangeStop = -1;
        int subRangeStart = -1;
        int i = startRow;
        List<Range> subRange = new ArrayList<>();
        while (i < numRows) {
            Cell[] cells = sheet.getRow(i);
            if (cells.length > 0 && cells[0].getContents().startsWith("#")) {// ignore comments
                i++;
                continue;
            }
            if (rangeStart == -1) { // looking for range start
                if (!isCellEmpty(cells, col1)) {
                    rangeStart = i;
                    subRangeStart = i;
                }
            } else if (subRangeStart == -1) { // looking for subRange start (can only happen if col2 > -1)
                if (!isCellEmpty(cells, col1)) {
                    break;
                }
                if (!isCellEmpty(cells, col2)) {
                    subRangeStart = i;
                }
            } else { // looking for range or subRange end
                if (isRowEmpty(cells)) {
                    subRange.add(new Range(subRangeStart, i));
                    if (col2 > -1) {
                        subRangeStart = -1;
                    } else {
                        rangeStop = i;
                        break;
                    }
                } else if (!isCellEmpty(cells, col1)) {
                    subRange.add(new Range(subRangeStart, i));
                    rangeStop = i;
                    break;
                } else if (col2 > -1 && !isCellEmpty(cells, col2)) {
                    subRange.add(new Range(subRangeStart, i));
                    subRangeStart = i;
                }
            }
            i++;
        }
        if (rangeStart == -1) {
            return null;
        }
        if(i==numRows) {
            if(rangeStop==-1) {
                rangeStop = numRows;
            }
            if(subRangeStart!=-1) {
                subRange.add(new Range(subRangeStart, numRows));
            }
        }
        Range r = new Range(rangeStart, rangeStop);
        r.subRanges = subRange;
        return r;
    }

    private static boolean isCellEmpty(Cell[] cells, int col) {
        return cells.length <= col || cells[col].getContents().isEmpty();
    }

    private static boolean isRowEmpty(Cell[] cells) {
        for(Cell cell: cells) {
            if(!cell.getContents().isEmpty()) {
                return false;
            }
        }
        return true;
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

    static byte parseByte(SpreadsheetLoadContext ctx, String s) {
        try {
            return Byte.decode(s);
        } catch (NumberFormatException e) {
            throw new SpreadsheetLoadException(ctx, "Could not parse byte from '" + s + "'");
        }
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

    static class Range {
        

        final int firstRow;
        final int lastRow;// note that lastRow is NOT part of the range

        public Range(int firstRow, int lastRow) {
            this.firstRow = firstRow;
            this.lastRow = lastRow;
        }

        List<Range> subRanges;
        @Override
        public String toString() {
            return "Range [firstRow=" + firstRow + ", lastRow=" + lastRow + ", subRanges=" + subRanges + "]";
        }
    }
}

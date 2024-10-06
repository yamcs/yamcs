package org.yamcs.mdb;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.yamcs.ConfigurationException;
import org.yamcs.mdb.ConditionParser.ParameterReferenceFactory;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.ValueEnumeration;
import org.yamcs.xtce.util.NameReference;
import org.yamcs.xtce.util.ParameterReference;

import jxl.Cell;
import jxl.CellType;
import jxl.NumberCell;
import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;

/**
 * Base for spreadsheet loader - this contains common properties for both V6 and V7 loaders.
 * 
 * @author nm
 *
 */
public abstract class BaseSpreadsheetLoader extends AbstractFileLoader {
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

    protected static final String CN_CONTEXT = "context";

    // columns names in calibrations sheet
    protected static final String CN_CALIB_NAME = "calibrator name";
    protected static final String CN_CALIB_TYPE = "type";
    protected static final String CN_CALIB_CALIB1 = "calib1";
    protected static final String CN_CALIB_CALIB2 = "calib2";
    protected static final String CN_CALIB_DESCRIPTION = "description";

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
    final protected SpreadsheetLoadContext ctx = new SpreadsheetLoadContext();
    protected Workbook workbook;

    // sheet name -> column name -> column number
    protected Map<String, Map<String, Integer>> headers;

    // current sheet header
    protected Map<String, Integer> h;

    final static Pattern XTCE_ALLOWED_IN_NAME = Pattern.compile("[^./:\\[\\]\\s]+");
    final static int[] NOT_ALLOWED_IN_NAME;

    static {
        NOT_ALLOWED_IN_NAME = new int[] { '.', '/', ':', '[', ']' };
        Arrays.sort(NOT_ALLOWED_IN_NAME);
    }

    public BaseSpreadsheetLoader(String path) throws ConfigurationException {
        super(path);
    }

    protected Cell[] jumpToRow(Sheet sheet, int row) {
        ctx.row = row + 1;
        return sheet.getRow(row);
    }

    protected void loadWorkbook() {
        try {
            File ssFile = new File(path);
            if (!ssFile.exists()) {
                throw new FileNotFoundException(ssFile.getAbsolutePath());
            }
            WorkbookSettings ws = new WorkbookSettings();
            ws.setEncoding("Cp1252");
            ws.setGCDisabled(true);
            workbook = Workbook.getWorkbook(ssFile, ws);
        } catch (BiffException | IOException e) {
            throw new DatabaseLoadException("Cannot open xls file: " + e.getMessage(), e);
        }
    }

    protected Sheet switchToSheet(String sheetName, boolean required) {
        Sheet sheet = workbook.getSheet(sheetName);
        ctx.sheet = sheetName;
        ctx.row = 0;
        if (required && sheet == null) {
            throw new SpreadsheetLoadException(ctx, "Required sheet '" + sheetName + "' was found missing");
        }
        if (headers != null) {
            h = headers.get(sheetName);
        }
        return sheet;
    }

    protected boolean isEmptyOrCommentedOut(Cell[] cells) {
        return ((cells == null) || cells.length < 1 || cells[0].getContents().startsWith("#"));
    }

    protected boolean hasColumn(Cell[] cells, int idx) {
        return (cells != null) && (cells.length > idx) && (cells[idx].getContents() != null)
                && (!cells[idx].getContents().isEmpty());
    }

    protected boolean hasColumn(Cell[] cells, String colName) {
        if (!h.containsKey(colName)) {
            return false;
        }
        int idx = h.get(colName);

        return (cells != null) && (cells.length > idx) && (cells[idx].getContents() != null)
                && (!cells[idx].getContents().isEmpty());
    }

    protected int parseInt(String s) {
        return parseInt(ctx, s);
    }

    protected double parseDouble(Cell cell) {
        return parseDouble(ctx, cell);
    }

    protected static int parseInt(SpreadsheetLoadContext ctx, String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new SpreadsheetLoadException(ctx, "Could not parse integer from '" + s + "'");
        }
    }

    protected static double parseDouble(SpreadsheetLoadContext ctx, Cell cell) {
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

    protected static byte parseByte(SpreadsheetLoadContext ctx, String s) {
        try {
            return Byte.decode(s);
        } catch (NumberFormatException e) {
            throw new SpreadsheetLoadException(ctx, "Could not parse byte from '" + s + "'");
        }
    }

    protected String getContent(Cell[] cells, String colName) {
        Integer x = h.get(colName);
        if (x == null) {
            throw new SpreadsheetLoadException(ctx, "Unexisting column '" + colName + "'");
        }
        if (!hasColumn(cells, x)) {
            throw new SpreadsheetLoadException(ctx, "No value provided in column '" + colName + "'");
        }
        return cells[x].getContents().trim();
    }

    protected String getContent(Cell[] cells, String colName, String defaultValue) {
        if (hasColumn(cells, colName)) {
            return getContent(cells, colName);
        } else {
            return defaultValue;
        }
    }

    protected Cell getCell(Cell[] cells, String colName) {
        if (!h.containsKey(colName)) {
            throw new SpreadsheetLoadException(ctx, "Unexisting column '" + colName + "'");
        }
        int idx = h.get(colName);
        return cells[idx];
    }

    /**
     * Temporary value holder for the enumeration definition; needed because enumerations are read before parameters,
     * and reading sharing the same EPT among all parameters is not a good approach (think different alarm definitions)
     */
    public static class EnumerationDefinition {
        public final List<ValueEnumeration> values = new ArrayList<>();

        public void add(long value, String label) {
            values.add(new ValueEnumeration(value, label));
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
    protected static Range findRange(Sheet sheet, int startRow, int col1, int col2) {
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
                    rangeStop = i;
                    if (col2 > -1) {
                        subRangeStart = -1;
                    } else {
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
        if (i == numRows) {
            if (rangeStop == -1) {
                rangeStop = numRows;
            }
            if (subRangeStart != -1) {
                subRange.add(new Range(subRangeStart, numRows));
            }
        }
        Range r = new Range(rangeStart, rangeStop);
        r.subRanges = subRange;
        return r;
    }

    public static class Range {
        public final int firstRow;
        public final int lastRow;// note that lastRow is NOT part of the range

        public Range(int firstRow, int lastRow) {
            this.firstRow = firstRow;
            this.lastRow = lastRow;
        }

        public List<Range> subRanges;

        @Override
        public String toString() {
            return "Range [firstRow=" + firstRow + ", lastRow=" + lastRow + ", subRanges=" + subRanges + "]";
        }
    }

    protected static boolean isCellEmpty(Cell[] cells, int col) {
        return cells.length <= col || cells[col].getContents().isEmpty();
    }

    protected static boolean isRowEmpty(Cell[] cells) {
        for (Cell cell : cells) {
            if (!cell.getContents().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static ParameterReference getParameterReference(SpaceSystem spaceSystem, String paramName) {
        ParameterReference paraRef = new ParameterReference(paramName);
        spaceSystem.addUnresolvedReference(paraRef);

        return paraRef;
    }

    protected void validateNameType(String name) {
        if (!XTCE_ALLOWED_IN_NAME.matcher(name).matches()) {
            String na = Arrays.stream(NOT_ALLOWED_IN_NAME).mapToObj(x -> Character.toString((char) x))
                    .collect(Collectors.joining(", ", "\"", "\""));
            throw new SpreadsheetLoadException(ctx,
                    "Invalid name '" + name + "'; should not contain whitespace or any of the following characters: "
                            + na);
        }
    }

    public static class BasicPrefFactory implements ParameterReferenceFactory {
        SpaceSystem spaceSystem;

        @Override
        public NameReference getReference(String pname) {
            return getParameterReference(spaceSystem, pname);
        }

        public void setCurrentSpaceSystem(SpaceSystem spaceSystem) {
            this.spaceSystem = spaceSystem;
        }

    }
}

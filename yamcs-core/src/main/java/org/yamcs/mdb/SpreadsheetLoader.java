package org.yamcs.mdb;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.xtce.SpaceSystem;

import com.google.common.collect.ImmutableMap;

import jxl.Cell;
import jxl.Sheet;

/**
 * Wrapper around v6 or v7 xls loader.
 * 
 * @author nm
 *
 */
public class SpreadsheetLoader extends BaseSpreadsheetLoader {
    SpaceSystemLoader delegate;

    public SpreadsheetLoader(YConfiguration config) {
        super(config.getString("file"));
        int version = checkVersion(config.getString("file"));
        if (version == 6) {
            delegate = new org.yamcs.xtce.xlsv6.V6Loader(config, workbook);
        } else {
            delegate = new org.yamcs.xtce.xlsv7.V7Loader(config, workbook);
        }
    }

    public SpreadsheetLoader(String filename) {
        this(YConfiguration.wrap(ImmutableMap.of("file", filename)));
    }

    public int checkVersion(String filename) {
        loadWorkbook();
        Sheet sheet = switchToSheet(SHEET_GENERAL, true);
        Cell[] cells = jumpToRow(sheet, 1);
        String version = cells[0].getContents();
        if (version.startsWith("5") || version.startsWith("6")) {
            return 6;
        } else {
            return 7;
        }
    }

    @Override
    public SpaceSystem load() throws ConfigurationException, DatabaseLoadException {
        return delegate.load();
    }
}

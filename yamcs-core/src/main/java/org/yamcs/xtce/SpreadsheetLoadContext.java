package org.yamcs.xtce;

/**
 * Keeps track of current whereabouts of the spreadsheet loader. For better
 * contextual error messages.
 */
public class SpreadsheetLoadContext {
    String file;
    String sheet;
    int row;
    
    @Override
    public String toString() {
        if(sheet!=null) {
            return file+":"+sheet+":"+row;
        } else {
            return file;
        }
    }
}

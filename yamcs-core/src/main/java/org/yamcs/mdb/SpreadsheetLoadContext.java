package org.yamcs.mdb;

/**
 * Keeps track of current whereabouts of the spreadsheet loader. For better
 * contextual error messages.
 * 
 * Careful when using this in lambda expressions: make sure to use the copy() method to create a new one for each lambda.
 * How to automate this??
 * 
 */
public class SpreadsheetLoadContext {
    public String file;
    public String sheet;
    public int row;
    
    public SpreadsheetLoadContext(String file, String sheet, int row) {
       this.file = file;
       this.sheet = sheet;
       this.row = row;
    }

    public SpreadsheetLoadContext() {
    }

    @Override
    public String toString() {
        if(sheet!=null) {
            return file+":"+sheet+":"+row;
        } else {
            return file;
        }
    }

    public SpreadsheetLoadContext copy() {
        return new SpreadsheetLoadContext(file, sheet, row);
    }
}

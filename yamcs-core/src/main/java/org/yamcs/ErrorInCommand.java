package org.yamcs;

import org.yamcs.YamcsException;


public class ErrorInCommand extends YamcsException {
    public int errorLine;
    public int errorColumn;
    public ErrorInCommand(String message) {
        super(message);
    }

    public ErrorInCommand(String _errorMessage, int _errorLine, int _errorColumn) {
        super(_errorMessage);
        errorLine = _errorLine;
        errorColumn = _errorColumn; 
    }

}

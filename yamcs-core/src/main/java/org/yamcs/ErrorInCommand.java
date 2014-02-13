package org.yamcs;

import org.yamcs.YamcsException;


public class ErrorInCommand extends YamcsException {
    public String errorSource;
    public int errorLine;
    public int errorColumn;
    public ErrorInCommand(String message) {
        super(message);
    }

    public ErrorInCommand(String _errorMessage, String _errorSource, int _errorLine, int _errorColumn) {
        super(_errorMessage);
        errorSource = _errorSource;
        errorLine = _errorLine;
        errorColumn = _errorColumn; 
    }

}

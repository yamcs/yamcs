package org.yamcs.ui.archivebrowser;

import java.text.ParseException;

import javax.swing.JFormattedTextField;
import javax.swing.text.DefaultFormatter;

import org.yamcs.utils.TimeEncoding;


public class InstantFormat extends DefaultFormatter {
    private static final long serialVersionUID = 1L;
    public InstantFormat() {
        setOverwriteMode(false);
    }
    
    
    @Override
    public Object stringToValue(String s) throws ParseException {
        if(s==null || s.isEmpty()) return TimeEncoding.INVALID_INSTANT;
        try {
            return TimeEncoding.parse(s);
        } catch (IllegalArgumentException e) {
            throw new ParseException(e.getMessage(), 0);
        }
    }

    @Override
    public String valueToString(Object obj) throws ParseException {
        if(obj==null) return null;
            long instant=(Long)obj;
            if(instant!=TimeEncoding.INVALID_INSTANT) {
                return TimeEncoding.toString(instant);
            } else {
                return "";
            }
        
    }
    
    @Override
    public void install(final JFormattedTextField ftf) {
        int savedCaretPos = ftf.getCaretPosition();
        super.install(ftf);
        if(savedCaretPos<ftf.getText().length())
            ftf.setCaretPosition(savedCaretPos);
    }
}
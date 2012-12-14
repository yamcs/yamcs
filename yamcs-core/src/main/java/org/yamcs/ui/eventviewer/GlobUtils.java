package org.yamcs.ui.eventviewer;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.regex.Pattern;

/**
 * Auxiliary class for work with glob patterns.
 * 
 * @author mu
 * 
 */
final class GlobUtils {

    /**
     * Transforms globbing expression into regular expression. Currently these
     * characters have special meaning in the globbing expression: ? (one
     * arbitrary character), * (sequence of arbitrary characters)
     * 
     * @todo This method do not translates all other special characters in the
     *       globbing string which can have special meaning in regular
     *       expression.
     * @param glob Glob expression to be transformed into regexp.
     * @return Regular expression with the same meaning.
     */
    static public String globToRegExp(String glob) {
        return glob.replace("[","\\[").replace("]","\\]")
            .replace(".", "[.]").replace("*", ".*").replace("?", ".");
    }

    /**
     * Matched the string against the regular expression
     * 
     * @param regex
     *            Regular expresssion
     * @param matched
     *            String to be matched
     * @return True if string matches the regular expression.
     */
    static public boolean isMatch(String regex, String matched) {
        Pattern patt = Pattern.compile(regex);
        return patt.matcher(matched).matches();
    }

    /**
     * Matched the string against the glob expression
     * 
     * @param glob
     *            Glob expression
     * @param matched
     *            String to be matched
     * @return True if string matches the glob expression.
     */
    static synchronized public boolean isMatchGlob(String glob, String matched) {
        if (glob.equals("*")){
            return true;
        } else {
            return isMatch(globToRegExp(glob), matched);
        }
    }

    /**
     * Matches the strings in array against the globbing expression.
     * 
     * @param expressions
     *            Array of strings to be matched
     * @param glob
     *            Globing expression, must not be null
     * @return Vector of strings which each matches the glob expression
     */
    static public List<String> getAllMatched(String[] expressions, String glob) {
        ArrayList<String> matched = new ArrayList<String>();
        String regex = globToRegExp(glob);

        for (String expression : expressions) {
            if (isMatch(regex, expression)) {
                matched.add(expression);
            }
        }
        return matched;
    }
}

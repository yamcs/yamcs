package org.yamcs.parser;

import java.util.ArrayList;

/**
 * Holder for a telecommand as parsed from the hlcl command string
 * @author mache
 *
 */

public class HlclParsedCommand {
	public String commandName=null;
	public String pathname=null;
	public ArrayList<HlclParsedParameter> parameterList;
}

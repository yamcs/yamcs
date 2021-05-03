package org.yamcs.xtce;

/**
 * Parameters made on the fly.
 * 
 * DataSource should be one of SYSTEM, COMMAND or COMMAND_HISTORY (so the class name is a misnomer);
 * 
 * @author nm
 * 
 */
public class SystemParameter extends Parameter {
    private static final long serialVersionUID = 2L;

    private SystemParameter(String spaceSystemName, String name, DataSource ds) {
        super(name);
        setQualifiedName(spaceSystemName + "/" + name);
        setDataSource(ds);
    }

    public static SystemParameter getForFullyQualifiedName(String fqname) {
        DataSource ds = getSystemParameterDataSource(fqname);
        SystemParameter sp = new SystemParameter(NameDescription.getSubsystemName(fqname),
                NameDescription.getName(fqname), ds);
        // set the recording name "/yamcs/a/b/c" -> "/yamcs/a"
        int pos = fqname.indexOf(PATH_SEPARATOR, 0);
        pos = fqname.indexOf(PATH_SEPARATOR, pos + 1);
        pos = fqname.indexOf(PATH_SEPARATOR, pos + 1);
        sp.setRecordingGroup(fqname.substring(0, pos));

        return sp;
    }

    private static DataSource getSystemParameterDataSource(String fqname) {
        if (fqname.startsWith(XtceDb.YAMCS_CMD_SPACESYSTEM_NAME)) {
            return DataSource.COMMAND;
        } else if (fqname.startsWith(XtceDb.YAMCS_CMDHIST_SPACESYSTEM_NAME)) {
            return DataSource.COMMAND_HISTORY;
        } else {
            return DataSource.SYSTEM;
        }
    }

    @Override
    public String toString() {
        return "SysParam(qname=" + getQualifiedName() + ")";
    }
}

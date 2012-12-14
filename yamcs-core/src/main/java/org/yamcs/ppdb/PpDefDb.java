package org.yamcs.ppdb;

import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.yamcs.ProcessedParameterDefinition;

import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.NamedDescriptionIndex;

/**
 * Definition of Processed Parameters
 * @author nm
 *
 */
public class PpDefDb implements Serializable {
    NamedDescriptionIndex<ProcessedParameterDefinition> params=new NamedDescriptionIndex<ProcessedParameterDefinition>();

    private static final long serialVersionUID = 4L;
    
    public void add(ProcessedParameterDefinition ppDef) {
        params.add(ppDef);
    }
    
    /**
     * returns the definition of a processed parameter for a given (name, namespace).
     *
     */
    public ProcessedParameterDefinition getProcessedParameter(NamedObjectId id) {
        ProcessedParameterDefinition p=null;
        if(id.hasNamespace()) {
            p=params.get(id.getNamespace(), id.getName());
        } else {
            p=params.get(id.getName());
        }
        return p;
    }

    public ProcessedParameterDefinition getProcessedParameter(String name) {
        return params.get(name);
    }

    /**
     * 
     * @return all the groups
     */
    public Collection<String> getGroups() {
        List<String> groups=new ArrayList<String>();
        for(ProcessedParameterDefinition ppdef:params.getObjects()) {
           groups.add(ppdef.getGroup()); 
        }
        return groups;
    }
   

    public int size() {
        return params.size();
    }

    public Collection<ProcessedParameterDefinition> getProcessedParameterDefinitions() {
        return params.getObjects();
    }
    
    
    /**
     * Prints the content of the database in the given stream
     * @param out
     */
    public void print(PrintStream out) {
        out.print("\n************************Processed Parameters: \n");
        for(ProcessedParameterDefinition ppdef:params.getObjects()) {
            out.println("\t"+ppdef);
         }
    }
}
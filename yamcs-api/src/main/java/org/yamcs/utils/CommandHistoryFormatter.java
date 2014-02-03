package org.yamcs.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;


/**
 * Formats command history entries. 
 * WARNING: the header composed of the columns names is not known at the beginning. Therefore this class writes all the values into a temporary file,
 *  and only at the end it writes the header into the passed buffer and copies the temporary file in there as well.
 * 
 * @author nm
 *
 */
public class CommandHistoryFormatter {
    private BufferedWriter writer;
    private HashMap<String, Integer> columns=new HashMap<String, Integer>();
    File tmpFile;
    BufferedWriter tmpWriter;
    
    public CommandHistoryFormatter(BufferedWriter writer) throws IOException {
        this.writer = writer;
        tmpFile = File.createTempFile("cmdhist-out", null);
        tmpWriter=new BufferedWriter(new FileWriter(tmpFile));
    }

    /**
     *returns the size in characters of the written command
    */
    public int writeCommand(CommandHistoryEntry che) throws IOException {
        ArrayList<String> values=new ArrayList<String>(columns.size());
               
        //initialize the list with nulls so we can do set later
        for(int i=0; i<columns.size(); i++) {
            values.add(null);
        }
        
        for(int i=0; i<che.getAttrCount(); i++) {
            CommandHistoryAttribute a = che.getAttr(i);
            String name=a.getName();
            if(!columns.containsKey(name)) {
                columns.put(name, columns.size());
                values.add(null);
            }
            int idx = columns.get(name);
            String value = StringConvertors.toString(a.getValue(), false);
            value = value.replaceAll("[\n\t]", " ");
            values.set(idx, value);
        }
        StringBuilder sb=new StringBuilder();
        boolean first=true;
        for(int i=0;i<values.size();i++) {
            if(first) first=false;
            else sb.append("\t");
            if(values.get(i)!=null) { 
                sb.append(values.get(i));
            }
        }
        tmpWriter.write(sb.toString());
        tmpWriter.write("\n");
        return sb.length()+1;
    }

    public void close() throws IOException {
        String[] colNames=new String[columns.size()];
        
        for(Map.Entry<String, Integer> e:columns.entrySet()) {
            colNames[e.getValue()]=e.getKey();
        }
        boolean first=true;
        for(String c: colNames) {
            if(first) first=false;
            else writer.append("\t");
            writer.append(c);
        }
        writer.newLine();
        tmpWriter.close();
        
        
        BufferedReader br = new BufferedReader(new FileReader(tmpFile));
        String line;
        while((line=br.readLine())!=null) {
            writer.write(line);
            writer.newLine();
        }
        writer.close();
        tmpFile.delete();
    }
}

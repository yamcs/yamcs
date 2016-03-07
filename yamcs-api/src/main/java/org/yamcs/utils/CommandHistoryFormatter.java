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

import com.csvreader.CsvWriter;


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
    static char DEFAULT_COLUMN_SEPARATOR = '\t';
    CsvWriter tmpCsvWriter;
    char columnSeparator;
    
    public CommandHistoryFormatter(BufferedWriter writer, char columnSeparator) throws IOException {
        this.writer = writer;
        this.columnSeparator = columnSeparator;
        tmpFile = File.createTempFile("cmdhist-out", null);
        BufferedWriter tmpWriter=new BufferedWriter(new FileWriter(tmpFile));
        tmpCsvWriter = new CsvWriter(tmpWriter, columnSeparator);
    }
    
    public CommandHistoryFormatter(BufferedWriter writer) throws IOException {
        this(writer, DEFAULT_COLUMN_SEPARATOR);
    }
    
    /**
     *returns the size in characters of the written command (without the separators)
    */
    public int writeCommand(CommandHistoryEntry che) throws IOException {
     //   System.out.println("che: "+che);
        ArrayList<String> values=new ArrayList<String>(columns.size());
               
        //initialize the list with nulls so we can do set later
        for(int i=0; i<columns.size(); i++) {
            values.add(null);
        }
        int size=0;
        for(int i=0; i<che.getAttrCount(); i++) {
            CommandHistoryAttribute a = che.getAttr(i);
            String name=a.getName();
            if(!columns.containsKey(name)) {
                columns.put(name, columns.size());
                values.add(null);
            }
            int idx = columns.get(name);
            String value = StringConverter.toString(a.getValue(), false);
       //     System.out.println("value: "+value);
            //value = value.replaceAll("[\n\t]", " ");
            values.set(idx, value);
            size+=value.length();
        }
        tmpCsvWriter.writeRecord(values.toArray(new String[0]));
        
        return size;
    }

    public void close() throws IOException {
        String[] colNames=new String[columns.size()];
        
        for(Map.Entry<String, Integer> e:columns.entrySet()) {
            colNames[e.getValue()]=e.getKey();
        }
        CsvWriter csvWriter = new CsvWriter(writer, columnSeparator);
        csvWriter.writeRecord(colNames);
        writer.newLine();
        tmpCsvWriter.close();
        
        
        BufferedReader br = new BufferedReader(new FileReader(tmpFile));
        String line;
        while((line=br.readLine())!=null) {
            writer.write(line);
            writer.newLine();
        }
        tmpFile.delete();
        br.close();
        csvWriter.close();
    }
}

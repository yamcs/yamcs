package org.yamcs.utils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.Map.Entry;

import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.Value;

public class PetParameterFormatter extends ParameterFormatter {

    public PetParameterFormatter(BufferedWriter writer, Collection<NamedObjectId> paramList) {
        super(writer, paramList);
        this.columnSeparator=";";
    }

    public void writeHeader() throws IOException {
        // print header line with ops names
        writer.write("\";\";\"PET:2\";\"yyyy-MM-dd HH:mm:ss.SSS\";\"\";\"TIME, RAW, ENG\"\n");

        for(NamedObjectId noid:subscribedParameters.keySet()) {
            writer.write("\"Alias\";\""+noid.getName()+"\";\n");
        }   
        if (printTime) {
            writer.write("\"Packet Time\";");
        }

        for(int i=0;i<subscribedParameters.keySet().size();i++) {
            if(printRaw) writer.write("\"Raw\";");
            writer.write("\"Eng\";");
            
        }
        writer.newLine();
    }
    
    @Override
    protected void writeParameters() throws IOException {
        if(first) {
            writeHeader();
            first=false;
        }
        if (unsavedLineCount == 0) {
            return;
        }

        StringBuilder sb=new StringBuilder();
        boolean skip = false;
        for(Entry<NamedObjectId, ParameterValue>entry :subscribedParameters.entrySet()) {
            ParameterValue pv=entry.getValue();
            if(pv!=null) {
                Value ev=pv.getEngValue();
                if(ev!=null) {
                    sb.append('"').append(StringConvertors.toString(ev, false)).append('"');
                } else {
                    System.err.println("got parameter without an engineering value for "+entry.getKey());
                    //skip=true;
                }
                sb.append(columnSeparator);
                if(printRaw) {
                    Value rv=pv.getRawValue();
                    if(rv!=null) {
                        sb.append('"').append(StringConvertors.toString(rv, false)).append('"');
                    }
                    sb.append(columnSeparator);
                }
            } else {
                if (allParametersPresent) {
                    skip = true;
                    break;
                }
                sb.append(columnSeparator);
                if(printRaw) sb.append(columnSeparator);
            }
        }

        if (!skip) {
            final String line=sb.toString();
            if(!printUnique || !line.equals(previousLine)) {
                if(printTime) {
                    writer.write('"'+TimeEncoding.toString(lastLineInstant)+'"');
                    writer.write(columnSeparator);
                }
                writer.write(line);
                writer.newLine();
                previousLine=line;
                linesSaved++;
            } else {
                skip=true;
            }
        }
        unsavedLineCount = 0;
    }
}

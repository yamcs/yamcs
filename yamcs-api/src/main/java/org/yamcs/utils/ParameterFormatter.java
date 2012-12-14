package org.yamcs.utils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.Value;

/**
 * Formats in tab separated format parameters. The list of the possible parameters has to be known in advance.
 * @author nm
 *
 */
public class ParameterFormatter {
    protected BufferedWriter writer;
    protected boolean printTime=true;
    protected boolean printRaw=false;
    protected boolean printUnique=false;
    protected boolean keepValues=false; //if set to true print the latest known value of a parameter even for parameters not retrieved in a packet
    protected boolean allParametersPresent = false; // true = print only those lines that contain all parameters' values
    protected int timewindow = -1; // [ms], -1 = no window at all
    String columnSeparator="\t";
    String previousLine;
    long lastLineInstant;
    protected int unsavedLineCount;
    
    Map<NamedObjectId, ParameterValue> subscribedParameters=new LinkedHashMap<NamedObjectId, ParameterValue>();
    protected int linesSaved, linesReceived;
    protected boolean first=true;
    
    public ParameterFormatter(BufferedWriter writer, Collection<NamedObjectId> paramList) {
        this.writer=writer;
        
        for(NamedObjectId id:paramList){
            subscribedParameters.put(id,null);
        }
    }

    public void setPrintRaw(boolean printRaw) {
        this.printRaw = printRaw;
    }

    public void setPrintTime(boolean printTime) {
        this.printTime = printTime;
    }

    public void setPrintUnique(boolean printUnique) {
        this.printUnique = printUnique;
    }
    
    public void setAllParametersPresent(boolean allParametersPresent) {
        this.allParametersPresent = allParametersPresent;
    }

    public void setKeepValues(boolean keepValues) {
        this.keepValues = keepValues;
    }

    public void setTimeWindow(int timewindow) {
        this.timewindow = timewindow;
    }

    public void resetTimeWindow() {
        this.timewindow = -1;
    }

    private void writeHeader() throws IOException {
        // print header line with ops names

        if (printTime) {
            writer.write("Time"+columnSeparator);
        }
        for(NamedObjectId noid:subscribedParameters.keySet()) {
            writer.write(noid.getName() + columnSeparator);
            if(printRaw) writer.write(noid.getName() + "_RAW"+columnSeparator);
        }
        writer.newLine();
    }
    /**
     * adds new parameters - if they are written to the output buffer or not depends on the settings
     * @param parameterList
     * @throws IOException
     */
    public void writeParameters(List<ParameterValue> parameterList) throws IOException {
        long t=parameterList.get(0).getGenerationTime();
        if ( t - lastLineInstant > timewindow ) {
            writeParameters();
            lastLineInstant = t;

            if(!keepValues) {
                for(Entry<NamedObjectId, ParameterValue>entry :subscribedParameters.entrySet()) {
                    entry.setValue(null);
                }
            }
        }

        for(int i=0;i<parameterList.size();i++) {
            ParameterValue pv=parameterList.get(i);
            subscribedParameters.put(pv.getId(), pv);
        }
        linesReceived++;
        ++unsavedLineCount;
    }

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
                    sb.append(StringConvertors.toString(ev, false));
                } else {
                    System.err.println("got parameter without an engineering value for "+entry.getKey());
                    //skip=true;
                }
                sb.append(columnSeparator);
                if(printRaw) {
                    Value rv=pv.getRawValue();
                    if(rv!=null) {
                        sb.append(StringConvertors.toString(rv, false));
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
                    writer.write(TimeEncoding.toString(lastLineInstant));
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

    public void close() throws IOException {
       writeParameters();//write the remaining parameters
       writer.close();
    }

    public int getLinesSaved() {
        return linesSaved;
    }

    public int getLinesReceived() {
        return linesReceived;
    }

}    
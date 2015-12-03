package org.yamcs.web.rest.archive;


import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;

import org.yamcs.protobuf.Archive;
import org.yamcs.protobuf.Pvalue;
import org.yamcs.protobuf.Yamcs;
import org.yamcs.utils.ParameterFormatter;

import io.netty.util.CharsetUtil;

/**
 * Created by msc on 13/04/15.
 */

/*
 * CsvFormater
 * Wrap the ParameterFormatter to print parameters on CSV format by chunk
 * Currently prints only parameters (no packets, command history, events...)
 */
public class CsvGenerator {

    List<String> insertedHeader;

    public void insertRows(Archive.DumpArchiveResponse response, OutputStream channelOut) throws IOException {

        // Parameter
        insertParameterRow(response, channelOut);
        return;

//        // TM Packet
//        CsvWriter csvWriter=new CsvWriter(channelOut, ';', CharsetUtil.UTF_8);
//        Yamcs.TmPacketData tmPacketData = responseb.getPacketData();
//        if(tmPacketData != null)
//        {
//            csvWriter.writeRecord(new String[]{
//                    TimeEncoding.toString(tmPacketData.getGenerationTime()),
//                    TimeEncoding.toString(tmPacketData.getReceptionTime()),
//                    tmPacketData.getSequenceNumber()+""
//            });
//        }
//        // Command History
//        Commanding.CommandHistoryEntry commandHistoryEntry = responseb.getCommand();
//        if(commandHistoryEntry != null)
//        {}
//        csvWriter.close();
    }

    private ParameterFormatter pf;
    public void initParameterFormatter(Collection<Yamcs.NamedObjectId> parameterNames)
    {
        boolean printRaw=true,
                printMonitoring = true,
                printUnique=false,
                printTime=true,
                allParametersPresent=true,
                keepValues = false;
        int timewindow = 10000;

        pf=new ParameterFormatter(null, parameterNames);
        pf.setPrintRaw(printRaw);
        pf.setPrintMonitoring(printMonitoring);
        pf.setPrintTime(printTime); // generation time
        pf.setPrintUnique(printUnique);
        pf.setAllParametersPresent(allParametersPresent);
        pf.setKeepValues(keepValues);
        pf.setTimeWindow(timewindow);
    }

    private void insertParameterRow(Archive.DumpArchiveResponse response, OutputStream channelOut) throws IOException {
        // convert ChannelBufferOutputStream -> OutputStreamWriter -> Writer -> BufferedWriter

        pf.updateWriter(channelOut, CharsetUtil.UTF_8);
        for(Pvalue.ParameterData parameterData : response.getParameterDataList()) {
            pf.writeParameters(parameterData.getParameterList());
        }
        pf.close();

    }



}

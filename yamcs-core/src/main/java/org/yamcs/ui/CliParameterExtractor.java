package org.yamcs.ui;

import static org.yamcs.api.Protocol.DATA_TYPE_HEADER_NAME;
import static org.yamcs.api.Protocol.decode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.concurrent.Semaphore;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.MessageHandler;
import org.yamcs.YamcsException;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsConnectData;
import org.yamcs.api.YamcsSession;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.protobuf.Yamcs.ParameterReplayRequest;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplaySpeed.ReplaySpeedType;
import org.yamcs.protobuf.Yamcs.ReplayStatus;
import org.yamcs.protobuf.Yamcs.ReplayStatus.ReplayState;
import org.yamcs.protobuf.Yamcs.StringMessage;
import org.yamcs.utils.ParameterFormatter;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.MdbMappings;

/**
 * Command line tool to retrieve parameters
 * @author nm
 *
 */
public class CliParameterExtractor {
    
    
    static void printUsageAndExit(String error) {
        if(error!=null) System.err.println(error);
        System.err.println("Usage: parameter-extractor.sh [OPTIONS] yamcs-url startTime stopTime [parameter_opsnames | -f file_with_parameters]");
        System.err.println("The yamcs-url has to contain the archive instance");
        System.err.println("OPTIONS:");
        System.err.println("         \t-a      print only those lines where all parameters are set");
        System.err.println("         \t-h      print this help");
        //System.err.println("         \t-i      ignore invalid parameters");
        System.err.println("         \t-k      keep previous parameters");
        System.err.println("         \t-r      print raw values in addition to engineering values");
        System.err.println("         \t-t      print the generation time on the first column");
        System.err.println("         \t-u      print unique lines only");
        System.err.println("         \t-w <ms> join multiple lines with timestamps within the given time window");
        System.err.println("Example:\n parameter-extractor.sh yamcs://localhost/yops 2007-08-01T12:34:00 2007-08-23T18:34:00 IntegerPara11 FloatPara11_1");
        System.exit(1);
    }
    
    public static ParameterReplayRequest getRequest(String... params) {
        ParameterReplayRequest.Builder prr=ParameterReplayRequest.newBuilder();
        for(String p:params) {
            prr.addNameFilter(NamedObjectId.newBuilder().setName(p).setNamespace(MdbMappings.MDB_OPSNAME));
        }
        return prr.build();
    }
    
    
   
    /**
     * @param args
     * yamcs://aces-test/aces-test 2010-05-19T00:00:00 2011-05-20T00:00:00   aces_SHM_HP_SET aces_SHM_HP_MEAS aces_SHM_IS_HEATER
     * 
     */
    public static void main(String[] args) throws YamcsApiException, URISyntaxException, HornetQException, IOException, YamcsException, InterruptedException {
        if(args.length<1) printUsageAndExit(null);
        
        int k=0, timewindow = -1;
        boolean printRaw=false, printUnique=false, printTime=false, ignoreInvalidParameters=false,
        allParametersPresent=false, keepValues = false;
        while(args[k].startsWith("-")) {
            if(args[k].equals("-t")) printTime=true;
            else if(args[k].equals("-r")) printRaw=true;
            else if(args[k].equals("-u")) printUnique=true;
            else if(args[k].equals("-i")) ignoreInvalidParameters=true;
            else if(args[k].equals("-a")) allParametersPresent=true;
            else if(args[k].equals("-k")) keepValues=true;
            else if(args[k].equals("-h")) printUsageAndExit(null);
            else if(args[k].equals("-w")) {
                boolean ok = true;
                try {
                    if ( ++k < args.length ) {
                        timewindow = Integer.valueOf(args[k]);
                    } else {
                        printUsageAndExit("timewindow value missing");
                    }
                    if ( timewindow < 0 ) ok = false;
                } catch (NumberFormatException x) {
                    ok = false;
                }
                if ( !ok ) {
                    printUsageAndExit("timewindow must be a non-negative integer (got '"+args[k]+"')");
                }
            } else printUsageAndExit("Unknown option '"+args[k]+"'");
            k++;
        }
        
        
        if(args.length<k+4)printUsageAndExit("too few arguments");
      
        YamcsConnectData ycd=YamcsConnectData.parse(args[k++]);
        if(ycd.getInstance()==null) printUsageAndExit("The Yamcs URL does not contain the archive instance. Use something like yamcs://hostname/archiveInstance");
        
        TimeEncoding.setUp();
        
        long start=TimeEncoding.parse(args[k++]);
        long stop=TimeEncoding.parse(args[k++]);
        ParameterReplayRequest prr=null;
        
        if("-f".equals(args[k])) {
            prr=ParameterReplayRequest.newBuilder()
                .addAllNameFilter(ParameterRetrievalGui.loadParameters(new BufferedReader(new FileReader(args[k+1]))))
                .build();
        } else {
            prr=getRequest(Arrays.copyOfRange(args, k, args.length));
        }
        
        ReplayRequest rr=ReplayRequest.newBuilder().setEndAction(EndAction.QUIT).setSpeed(ReplaySpeed.newBuilder().setType(ReplaySpeedType.AFAP).build())
            .setParameterRequest(prr)
            .setStart(start).setStop(stop).build();
      
        final ParameterFormatter pf=new ParameterFormatter(new BufferedWriter(new PrintWriter(System.out)), prr.getNameFilterList());
        pf.setPrintRaw(printRaw);
        pf.setPrintTime(printTime);
        pf.setPrintUnique(printUnique);
        pf.setAllParametersPresent(allParametersPresent);
        pf.setKeepValues(keepValues);
        pf.setTimeWindow(timewindow);
        
        YamcsSession ysession=YamcsSession.newBuilder().setConnectionParams(ycd).build();
        YamcsClient yclient=ysession.newClientBuilder().setRpc(true).setDataConsumer(null, null).build();
        SimpleString packetReplayAddress=null;
        
        try {
            StringMessage answer = (StringMessage) yclient.executeRpc(Protocol.getReplayControlAddress(ycd.getInstance()),
                        "createReplay", rr, StringMessage.newBuilder());
            packetReplayAddress=new SimpleString(answer.getMessage());
        } catch (YamcsException e) {
            
            if("InvalidIdentification".equals(e.getType())) {
                NamedObjectList nol=(NamedObjectList)e.decodeExtra(NamedObjectList.newBuilder());
                System.out.println("got invalid identification params: "+nol);
            }
            throw e;
        }
            

        final Semaphore semaphore=new Semaphore(0);
        yclient.dataConsumer.setMessageHandler(new MessageHandler() {
            @Override
            public void onMessage(ClientMessage pmsg) {
                try {
                    int t = pmsg.getIntProperty(DATA_TYPE_HEADER_NAME);
                    ProtoDataType pdt=ProtoDataType.valueOf(t);
                    if(pdt==ProtoDataType.STATE_CHANGE) {
                        ReplayStatus status = (ReplayStatus) decode(pmsg, ReplayStatus.newBuilder());
                        if(status.getState()==ReplayState.CLOSED) {
                            pf.close();
                            semaphore.release();
                        } else if(status.getState()==ReplayState.ERROR) {
                            System.err.println("Got error during retrieval: "+status.getErrorMessage());
                            pf.close();
                            semaphore.release();
                        }
                    } else {
                        ParameterData pdata=(ParameterData)decode(pmsg, ParameterData.newBuilder());
                        pf.writeParameters(pdata.getParameterList());
                    }
                } catch (Exception e) {
                    System.err.println("cannot decode parameter message"+e);
                }

            }
        });
        yclient.executeRpc(packetReplayAddress, "start", null, null);
        semaphore.acquire();
        yclient.close();
        ysession.close();
    }
}

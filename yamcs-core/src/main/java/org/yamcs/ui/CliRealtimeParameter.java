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

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.yamcs.YamcsException;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsConnectData;
import org.yamcs.api.YamcsSession;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.utils.ParameterFormatter;
import org.yamcs.utils.TimeEncoding;

/**
 * Command line tool to retrieve parameters
 * @author nm
 *
 */
public class CliRealtimeParameter {
    
    
    static void printUsageAndExit(String error) {
        if(error!=null) System.err.println(error);
        System.err.println("Usage: realtime-parameter.sh [OPTIONS] yamcs-url [parameter_names | -f file_with_parameters]");
        System.err.println("The yamcs-url has to contain the archive instance");
        System.err.println("OPTIONS:");
        System.err.println("         \t-a      print only those lines where all parameters are set");
        System.err.println("         \t-h      print this help");
    //    System.err.println("         \t-i      ignore invalid parameters");
        System.err.println("         \t-k      keep previous parameters");
        System.err.println("         \t-r      print raw values in addition to engineering values");
        System.err.println("         \t-t      print the generation time on the first column");
        System.err.println("         \t-u      print unique lines only");
        System.err.println("         \t-w <ms> join multiple lines with timestamps within the given time window");
        System.err.println("Example:\n realtime-parameter.sh yamcs://localhost/yops IntegerPara11 FloatPara11_1");
        System.exit(1);
    }
    
    public static NamedObjectList getRequest(String... params) {
        NamedObjectList.Builder nolb=NamedObjectList.newBuilder();
        for(String p:params) {
            nolb.addList(NamedObjectId.newBuilder().setName(p));
        }
        return nolb.build();
    }
    
    
   
    /**
     * @param args
     * yamcs://aces-test/aces-test  aces_SHM_HP_SET aces_SHM_HP_MEAS aces_SHM_IS_HEATER
     * 
     */
    public static void main(String[] args) throws YamcsApiException, URISyntaxException, ActiveMQException, IOException, YamcsException, InterruptedException {
        if(args.length<1) printUsageAndExit(null);
        
        int k=0, timewindow = -1;
        boolean printRaw=false, printUnique=false, printTime=false, allParametersPresent=false, keepValues = false;
        while(args[k].startsWith("-")) {
            if(args[k].equals("-t")) printTime=true;
            else if(args[k].equals("-r")) printRaw=true;
            else if(args[k].equals("-u")) printUnique=true;
    //        else if(args[k].equals("-i")) ignoreInvalidParameters=true;
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
        
        
        if(args.length<k+2)printUsageAndExit("too few arguments");
      
        YamcsConnectData ycd=YamcsConnectData.parse(args[k++]);
        if(ycd.getInstance()==null) printUsageAndExit("The Yamcs URL does not contain the archive instance. Use something like yamcs://hostname/archiveInstance");
        
        TimeEncoding.setUp();
        
        final YamcsSession ysession=YamcsSession.newBuilder().setConnectionParams(ycd).build();
        final YamcsClient  yclient=ysession.newClientBuilder().setRpc(true).setDataConsumer(null, null).build();

        final SimpleString rpcAddr=Protocol.getParameterRealtimeAddress(ycd.getInstance());
    
        NamedObjectList nol;
        
        if("-f".equals(args[k])) {
            nol = NamedObjectList.newBuilder().addAllList(ParameterRetrievalGui.loadParameters(new BufferedReader(new FileReader(args[k+1])))).build();
        } else {
            nol=getRequest(Arrays.copyOfRange(args, k, args.length));
        }
        
        final NamedObjectList subscriptionList = nol;
    
        final BufferedWriter writer = new BufferedWriter(new PrintWriter(System.out));
        final ParameterFormatter pf=new ParameterFormatter(writer, subscriptionList.getListList());
        pf.setPrintRaw(printRaw);
        pf.setPrintTime(printTime);
        pf.setPrintUnique(printUnique);
        pf.setAllParametersPresent(allParametersPresent);
        pf.setKeepValues(keepValues);
        pf.setTimeWindow(timewindow);
     
        
        try {
            yclient.executeRpc(rpcAddr, "subscribe", subscriptionList, null);
        } catch (YamcsException e) {
            if("InvalidIdentification".equals(e.getType())) {
                NamedObjectList nol1=(NamedObjectList)e.decodeExtra(NamedObjectList.newBuilder());
                System.err.println("got invalid identification for the following parameters: "+nol1.getListList());
            }
            
            System.exit(1);
        }
        
        //System.out.println("adding shutdown hook");
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    yclient.executeRpc(rpcAddr, "unsubscribe", subscriptionList, null);
                    yclient.close();
                    ysession.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }));

        final Semaphore semaphore=new Semaphore(0);
        yclient.dataConsumer.setMessageHandler(new MessageHandler() {
            @Override
            public void onMessage(ClientMessage pmsg) {
                try {
                    int t=pmsg.getIntProperty(DATA_TYPE_HEADER_NAME);
                    ProtoDataType pdt=ProtoDataType.valueOf(t);
                    if(pdt==ProtoDataType.STATE_CHANGE) {
                        pf.close();
                        semaphore.release();
                    } else {
                        ParameterData pdata=(ParameterData)decode(pmsg, ParameterData.newBuilder());
                        pf.writeParameters(pdata.getParameterList());
                        writer.flush();
                    }
                } catch (Exception e) {
                    System.err.println("cannot decode parameter message"+e);
                }

            }
        });
        
        semaphore.acquire();
    }
}

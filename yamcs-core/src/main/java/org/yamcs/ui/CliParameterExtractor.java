package org.yamcs.ui;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsApiException.RestExceptionData;
import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.api.rest.BulkRestDataReceiver;
import org.yamcs.api.rest.RestClient;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Rest.BulkDownloadParameterValueRequest;
import org.yamcs.protobuf.Web.RestExceptionMessage;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.ParameterFormatter;
import org.yamcs.utils.TimeEncoding;

import com.google.protobuf.InvalidProtocolBufferException;

/**
 * Command line tool to retrieve parameters
 * @author nm
 *
 */
public class CliParameterExtractor {


    static void printUsageAndExit(String error) {
        if(error!=null) {
            System.err.println(error);
        }
        System.err.println("Usage: parameter-extractor.sh [OPTIONS] yamcs-url startTime stopTime [parameter_opsnames | -f file_with_parameters]");
        System.err.println("The yamcs-url has to contain the archive instance");
        System.err.println("OPTIONS:");
        System.err.println("         \t-a           print only those lines where all parameters are set");
        System.err.println("         \t-h           print this help");
        System.err.println("         \t-k           keep previous parameters");
        System.err.println("         \t-r           print raw values in addition to engineering values");
        System.err.println("         \t-t           print the generation time on the first column");
        System.err.println("         \t-u           print unique lines only");
        System.err.println("         \t-n NAMESPACE alternative parameter namespace (e.g. \"MDB:OPS Name\")");
        System.err.println("         \t-w MILLIS    join multiple lines with timestamps within the given time window");
        System.err.println("Example:\n parameter-extractor.sh http://localhost:8090/yops 2007-08-01T12:34:00 2007-08-23T18:34:00 /SUBSYS1/IntegerPara11 /SUBSYS1/FloatPara11_1");
        System.exit(1);
    }

    public static BulkDownloadParameterValueRequest.Builder getRequest(String namespace, String... params) {
        BulkDownloadParameterValueRequest.Builder prr=BulkDownloadParameterValueRequest.newBuilder();
        for(String p:params) {
            if (namespace != null) {
                prr.addId(NamedObjectId.newBuilder().setName(p).setNamespace(namespace));
            } else {
                prr.addId(NamedObjectId.newBuilder().setName(p));
            }
        }
        return prr;
    }



    /**
     * @param args
     * http://aces-test:8090/aces-test 2010-05-19T00:00:00 2011-05-20T00:00:00   aces_SHM_HP_SET aces_SHM_HP_MEAS aces_SHM_IS_HEATER
     * @throws ExecutionException
     * @throws URISyntaxException
     * @throws IOException
     * @throws FileNotFoundException
     * @throws InterruptedException
     *
     */
    public static void main(String[] args) throws ExecutionException, URISyntaxException, FileNotFoundException, IOException, InterruptedException {
        if(args.length<1) {
            printUsageAndExit(null);
        }

        int k=0;
        int timewindow = -1;
        boolean printRaw = false;
        boolean printUnique = false;
        boolean printTime = false;
        boolean allParametersPresent = false;
        boolean keepValues = false;
        String namespace = null;

        while(args[k].startsWith("-")) {
            if(args[k].equals("-t")) {
                printTime=true;
            } else if(args[k].equals("-r")) {
                printRaw=true;
            } else if(args[k].equals("-u")) {
                printUnique=true;
            } else if(args[k].equals("-a")) {
                allParametersPresent=true;
            } else if(args[k].equals("-k")) {
                keepValues=true;
            } else if(args[k].equals("-n")) {
                namespace=args[++k];
            } else if(args[k].equals("-h")) {
                printUsageAndExit(null);
            } else if(args[k].equals("-w")) {
                boolean ok = true;
                try {
                    if ( ++k < args.length ) {
                        timewindow = Integer.valueOf(args[k]);
                    } else {
                        printUsageAndExit("timewindow value missing");
                    }
                    if ( timewindow < 0 ) {
                        ok = false;
                    }
                } catch (NumberFormatException x) {
                    ok = false;
                }
                if ( !ok ) {
                    printUsageAndExit("timewindow must be a non-negative integer (got '"+args[k]+"')");
                }
            } else printUsageAndExit("Unknown option '"+args[k]+"'");
            k++;
        }


        if(args.length<k+4){
            printUsageAndExit("too few arguments");
        }

        YamcsConnectionProperties ycd = YamcsConnectionProperties.parse(args[k++]);
        if(ycd.getInstance()==null) {
            printUsageAndExit("The Yamcs URL does not contain the archive instance. Use something like http://hostname/archiveInstance");
        }

        TimeEncoding.setUp();

        String start = args[k++];
        String stop = args[k++];
        BulkDownloadParameterValueRequest.Builder prr=null;

        if("-f".equals(args[k])) {
            prr=BulkDownloadParameterValueRequest.newBuilder()
                    .addAllId(ParameterRetrievalGui.loadParameters(new BufferedReader(new FileReader(args[k+1]))));
        } else {
            prr=getRequest(namespace, Arrays.copyOfRange(args, k, args.length));
        }

        prr.setStart(start).setStop(stop);

        final ParameterFormatter pf=new ParameterFormatter(new BufferedWriter(new PrintWriter(System.out)), prr.getIdList());
        pf.setPrintRaw(printRaw);
        pf.setPrintTime(printTime);
        pf.setPrintUnique(printUnique);
        pf.setAllParametersPresent(allParametersPresent);
        pf.setKeepValues(keepValues);
        pf.setTimeWindow(timewindow);
        RestClient restClient = new RestClient(ycd);

        CompletableFuture<Void> completableFuture = restClient.doBulkGetRequest("/archive/"+ycd.getInstance()+"/downloads/parameters", prr.build().toByteArray(), new BulkRestDataReceiver() {
            @Override
            public void receiveData(byte[] data) throws YamcsApiException {
                ParameterData pd;
                try {
                    pd = ParameterData.parseFrom(data);
                    pf.writeParameters(pd.getParameterList());
                } catch (InvalidProtocolBufferException e) {
                    System.err.println("Cannot decode parameter message: " + e);
                } catch (IOException e) {
                    System.err.println("Error when saving parameters: " + e);
                }
            }
        });

        try {
            completableFuture.get();
        } catch (ExecutionException e) {
            Throwable t = (e.getCause() != null ? e.getCause() : e);
            if (t instanceof YamcsApiException) {
                RestExceptionData msg = ((YamcsApiException) t).getRestData();
                if (msg != null) {
                    System.err.println(msg.getType() + ": " + msg.getMessage());
                } else {
                    System.err.println(t.getMessage());
                }
            } else {
                System.err.println(t);
            }
        } finally {
            pf.close();
            restClient.close();
        }
    }
}

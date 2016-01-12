package org.yamcs.web.rest;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;

import org.yamcs.api.MediaType;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.yarch.Tuple;

import com.csvreader.CsvWriter;

import io.netty.buffer.ByteBufOutputStream;

/**
 * Facilitates sending CSV data with chunked transfer encoding.
 * It wraps the ByteBufOutputSteam with a CSVWriter
 */
public abstract class StreamToChunkedCSVEncoder extends StreamToChunkedTransferEncoder {
    
    private CsvWriter csvWriter;
    
    public StreamToChunkedCSVEncoder(RestRequest req) throws HttpException {
        super(req, MediaType.CSV);
        
        String[] csvHeader = getCSVHeader();
        if (csvHeader != null) {
            try {
                csvWriter.writeRecord(csvHeader);
            } catch (IOException e) {
                throw new InternalServerErrorException(e);
            }
        }
    }
    
    @Override
    protected void resetBuffer() {
        super.resetBuffer();
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(bufOut));
        csvWriter = new CsvWriter(bw, '\t');
    }
    
    @Override
    protected void closeBufferOutputStream() throws IOException {
        csvWriter.close();
    }

    @Override
    public void processTuple(Tuple tuple, ByteBufOutputStream bufOut) throws IOException {
        processTuple(tuple, csvWriter);
    }
    
    public abstract void processTuple(Tuple tuple, CsvWriter csvWriter) throws IOException;

    public String[] getCSVHeader() {
        return null;
    }
}

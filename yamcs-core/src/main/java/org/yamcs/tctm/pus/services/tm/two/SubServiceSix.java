package org.yamcs.tctm.pus.services.tm.two;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;

import org.yamcs.InitException;
import org.yamcs.YConfiguration;
import org.yamcs.yarch.Bucket;
import org.yamcs.filetransfer.FileSaveHandler;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmPacket;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class SubServiceSix implements PusSubService {
    String yamcsInstance;
    Log log;

    int registerAddressSize;
    int registerValueSize;
    int registerStartOffset;

    Bucket registerDumpBucket;
    FileSaveHandler fileSaveHandler;

    Gson gson;

    public SubServiceSix(String yamcsInstance, YConfiguration subServiceSixConfig) {
        this.yamcsInstance = yamcsInstance;
        log = new Log(getClass(), yamcsInstance);

        registerAddressSize = subServiceSixConfig.getInt("addressSize", 4);
        registerValueSize = subServiceSixConfig.getInt("valueSize", 4);
        registerStartOffset = 4;

        try{
            registerDumpBucket = getBucket("deviceRegisterDump");
        } catch (InitException e) {
            log.error("Unable to create a `deviceRegisterDump bucket` for (Service - 2 | SubService - 6)", e);
            throw new YarchException("Failed to create RDB based bucket: deviceRegisterDump", e);
        }

        // Create Gson instance
        gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public Bucket getBucket(String bucketName) throws InitException {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);

        try {
            Bucket bucket = ydb.getBucket(bucketName);
            if (bucket == null) {
                bucket = ydb.createBucket(bucketName);
            }
            return bucket;
        } catch (IOException e) {
            throw new InitException(e);
        }
    }

    @Override
    public void process(PusTmPacket pusTmPacket) {
        byte[] dataField = pusTmPacket.getDataField();

        int numberOfRegisters = ByteArrayUtils.decodeInt(dataField, 0);
        HashMap<Integer, Integer> registerValues = new HashMap<>(numberOfRegisters);

        for(int registerIndex = 0; registerIndex < numberOfRegisters; registerIndex++){
            
            int address = ByteArrayUtils.decodeInt(dataField, registerStartOffset + registerIndex * (registerAddressSize + registerValueSize));
            int value = ByteArrayUtils.decodeInt(dataField, registerStartOffset + registerIndex * (registerAddressSize + registerValueSize) + registerAddressSize);

            registerValues.put(address, value);
        }

        // FIXME: What should the filename be? What should the metadata be?
        String registerDumpFileName = "";
        HashMap<String, String> registerDumpMetadata = new HashMap<>();
        
        // Serialize the HashMap to JSONString
        String registerDumpValuesJSONString = gson.toJson(registerValues);

        // Save file to deviceRegisterDump bucket
        try {
            registerDumpBucket.putObject(registerDumpFileName, "JSON", registerDumpMetadata, registerDumpValuesJSONString.getBytes());
        } catch(IOException e) {
            throw new UncheckedIOException("Cannot save device register dump report in bucket: " + registerDumpFileName + (registerDumpBucket != null ? " -> " + registerDumpBucket.getName() : ""), e);
        }
    }
}

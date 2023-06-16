package org.yamcs.filetransfer;

import com.csvreader.CsvReader;
import com.google.protobuf.Descriptors;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.protobuf.RemoteFile;
import org.yamcs.utils.TimestampUtil;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Parses a directory listing from Comma Separated Values (CSV) formatted data.
 * Uses configurable mappings to associate columns to the correct file properties.
 * Timestamps can be formatted as numbers or as strings in the ISO format.
 */
public class CsvListingParser extends FileListingParser {

    /**
     * Maps RemoteFile protobuf field names to the CSV colum number.
     * Example: [ name -> 0, isDirectory -> 1, size -> 2, modified -> 3 ]
     */
    Map<String, Integer> protobufColumnNumberMapping; // Protobuf names -> column numbers

    /**
     * Maps CSV header column names to RemoteFile protobuf field names.
     * Example: [ filename -> name, is_dir -> isDirectory, size -> size, last_updated -> modified ]
     */
    Map<String, String> headerProtobufMapping; // CSV column names -> protobuf names

    /**
     * Whether to parse the CSV header and to use for mapping values to the RemoteFile fields
     */
    private boolean useCsvHeader;

    /**
     * Multiplier for number encoded timestamps to get to the value in milliseconds
     */
    private double timestampMultiplier;

    private EventProducer eventProducer;

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("useCsvHeader", OptionType.BOOLEAN).withDefault(false);
        spec.addOption("timestampMultiplier", OptionType.FLOAT).withDefault(1000);
        spec.addOption("protobufColumnNumberMapping", OptionType.MAP).withSpec(Spec.ANY).withDefault(new HashMap<>( // Default: protobuf order
                    RemoteFile.getDescriptor().getFields().stream()
                            .collect(Collectors.toMap(Descriptors.FieldDescriptor::getName,fieldDescriptor -> fieldDescriptor.getNumber() - 1))));
        spec.addOption("headerProtobufMapping", OptionType.MAP).withSpec(Spec.ANY).withDefault(new HashMap<>( // Default: assumes same names as protobuf
                        RemoteFile.getDescriptor().getFields().stream()
                                .collect(Collectors.toMap(Descriptors.FieldDescriptor::getName, Descriptors.FieldDescriptor::getName))));
        return spec;
    }

    @Override
    public void init(String yamcsInstance, YConfiguration config) {
        super.init(yamcsInstance, config);
        if(!"".equals(yamcsInstance)) {
            eventProducer = EventProducerFactory.getEventProducer(yamcsInstance, "CsvListingParser", 10000);
        }

        useCsvHeader = config.getBoolean("useCsvHeader");
        timestampMultiplier = config.getDouble("timestampMultiplier");
        protobufColumnNumberMapping = config.getMap("protobufColumnNumberMapping");
        headerProtobufMapping = config.getMap("headerProtobufMapping");
    }

    @Override
    public List<RemoteFile> parse(String remotePath, byte[] data) {
        String textData = new String(data);
        ArrayList<RemoteFile> files = new ArrayList<>();

        CsvReader reader = CsvReader.parse(textData);
        try {
            Map<String, Integer> mapping = getMapping(reader);

            while (reader.readRecord()) {
                List<String> values = List.of(reader.getValues());

                RemoteFile.Builder builder = RemoteFile.newBuilder();

                RemoteFile.getDescriptor().getFields().forEach(field -> {
                    Integer id = mapping.get(field.getName());
                    if (id != null && id < values.size() && values.get(id) != null) {
                        try{
                            builder.setField(field, parseValue(values.get(id), field));
                        } catch (IllegalArgumentException | DateTimeParseException e) {
                            sendInfo("Failed to parse value from directory listing CSV: " + values.get(id) + " as " + field.getJavaType() + " (" + e.getMessage() + ")");
                        }
                   }
                });

                if(builder.hasName()) {
                    files.add(builder.build());
                } else
                    sendInfo("Failed to parse file info from file listing (no filename?): " + reader.getRawRecord());
            }
        } catch (IOException e) {
            sendWarning("Exception while parsing directory listing CSV: " + e.getMessage());
        }

        files.sort(fileDirComparator);
        return files;
    }

    private Object parseValue(String value, Descriptors.FieldDescriptor field) {
        switch (field.getJavaType()) {
        case INT:
            return Integer.parseInt(value);
        case LONG:
            return Long.parseLong(value);
        case FLOAT:
            return Float.parseFloat(value);
        case DOUBLE:
            return Double.parseDouble(value);
        case BOOLEAN:
            return Boolean.parseBoolean(value);
        case STRING:
            return value.strip();
        case MESSAGE:
            if(field.getMessageType().getFullName().equals("google.protobuf.Timestamp")) {
                try {
                    return TimestampUtil.java2Timestamp((long) (Double.parseDouble(value) * timestampMultiplier));
                } catch (NumberFormatException e) {
                    return TimestampUtil.java2Timestamp(Instant.parse(value).toEpochMilli());
                }
            }
            // Else do default case
        case BYTE_STRING:
        case ENUM:
        default:
            throw new IllegalArgumentException("Unsupported type in directory listing CSV: " + value + " (" + field.getJavaType() + ", " + field.getName() + ")");
        }
    }

    private Map<String, Integer> getMapping(CsvReader reader) throws IOException {
        if (useCsvHeader) {
            if (reader.readHeaders()) {
                String[] headers = reader.getHeaders();
                if (headers != null && headers.length > 0) {
                    Map<String, Integer> mapping = new HashMap<>();
                    for (int i = 0; i < headers.length; i++) {
                        String protobufName = headerProtobufMapping.get(headers[i]);
                        if (protobufName != null) {
                            mapping.put(protobufName, i);
                        } else {
                            sendInfo("Unknown directory listing CSV header value: " + headers[i]);
                        }
                    }

                    return mapping;
                }
            }
            sendWarning("Error parsing CSV header in directory listing");
        }

        return protobufColumnNumberMapping;
    }

    private void sendInfo(String msg) {
        if (eventProducer != null) {
            eventProducer.sendInfo(msg);
        }
    }

    private void sendWarning(String msg) {
        if (eventProducer != null) {
            eventProducer.sendWarning(msg);
        }
    }

}

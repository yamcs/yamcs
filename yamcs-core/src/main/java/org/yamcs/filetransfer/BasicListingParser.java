package org.yamcs.filetransfer;

import org.yamcs.Spec;
import org.yamcs.YConfiguration;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.protobuf.RemoteFile;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parses a directory listing from a linebreak separated list of filenames
 * Directories are detected when the file name ends with a directory terminator
 */
public class BasicListingParser extends FileListingParser {

    private boolean removePrependingRemotePath;
    private final List<String> DEFAULT_DIRECTORY_TERMINATORS = List.of("/");
    private List<String> directoryTerminators;
    private String directoryTerminatorsRegex;

    private EventProducer eventProducer;

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("removePrependingRemotePath", Spec.OptionType.BOOLEAN).withDefault(true);
        spec.addOption("directoryTerminators", Spec.OptionType.LIST).withElementType(Spec.OptionType.STRING)
                .withDefault(DEFAULT_DIRECTORY_TERMINATORS);
        return spec;
    }

    public BasicListingParser() {
        setDirectoryTerminators(DEFAULT_DIRECTORY_TERMINATORS);
    }

    @Override
    public void init(String yamcsInstance, YConfiguration config) {
        super.init(yamcsInstance, config);
        if(!"".equals(yamcsInstance)) {
            eventProducer = EventProducerFactory.getEventProducer(yamcsInstance, "BasicListingParser", 10000);
        }
        removePrependingRemotePath = config.getBoolean("removePrependingRemotePath");
        List<String> terminators = config.getList("directoryTerminators");
        if(!terminators.equals(DEFAULT_DIRECTORY_TERMINATORS)) { // Only overwrite the directory terminators if config is not default
            setDirectoryTerminators(terminators);
        }
    }

    /**
     * Sets the directory terminators for parsing
     * @param directoryTerminators directory terminators
     */
    public void setDirectoryTerminators(List<String> directoryTerminators) {
        this.directoryTerminators = directoryTerminators;
        this.directoryTerminatorsRegex = "(" + directoryTerminators.stream().map(Pattern::quote).collect(Collectors.joining("|")) + ")";
    }

    @Override
    public List<RemoteFile> parse(String remotePath, byte[] data) {
        String textData = new String(data);
        if(!removePrependingRemotePath) {
            remotePath = "";
        }

        // TODO: maybe add (directoryTerminatorsRegex*) at the beginning?
        String regex = "^(" + Pattern.quote(remotePath) + ")?(" + directoryTerminatorsRegex + "*)(?<name>.*?)(" + directoryTerminatorsRegex + "*)$";
        return Arrays.stream(textData.replace("\r", "").split("\\n"))
                .map(fileName -> {
                    try {
                        if (fileName.isBlank()) {
                            return null;
                        }

                        return RemoteFile.newBuilder()
                                .setName(fileName.replaceAll(regex, "${name}"))
                                .setIsDirectory(directoryTerminators.stream().anyMatch(fileName::endsWith))
                                .build();
                    } catch (Exception e) {
                        if(eventProducer != null) {
                            eventProducer.sendWarning("Error parsing filename '" + fileName + "' in directory listing");
                        }
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .sorted(fileDirComparator)
                .collect(Collectors.toList());
    }
}

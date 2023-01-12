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

public class BasicListingParser extends FileListingParser {

    private List<String> directoryTerminators = Arrays.asList("/");
    private String directoryTerminatorsRegex = "/";

    private EventProducer eventProducer;

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("directoryTerminators", Spec.OptionType.LIST).withElementType(Spec.OptionType.STRING)
                .withDefault(Arrays.asList(":", "/", "\\"));
        return spec;
    }

    @Override
    public void init(String yamcsInstance, YConfiguration config) {
        super.init(yamcsInstance, config);
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance, "BasicListingParser", 10000);
        setDirectoryTerminators(config.getList("directoryTerminators"));
    }

    public void setDirectoryTerminators(List<String> directoryTerminators) {
        this.directoryTerminators = directoryTerminators;
        this.directoryTerminatorsRegex = "(" + directoryTerminators.stream().map(Pattern::quote).collect(Collectors.joining("|")) + ")";
    }

    @Override
    public List<RemoteFile> parse(String remotePath, String data) {
        String regex = "^(" + Pattern.quote(remotePath) + ")?(" + directoryTerminatorsRegex + "*)(?<name>.*?)(" + directoryTerminatorsRegex + "*)$";
        return Arrays.stream(data.replace("\r", "").split("\\n"))
                .map(fileName -> {
                    try {
                        if (fileName.strip().equals("")) {
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

package org.yamcs.parameter;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.yamcs.YamcsServer;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.AggregateParameterType;
import org.yamcs.xtce.Member;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.UnitType;

/**
 * Generates parameters containing information about the system disks: total space, available space and percentage used.
 * 
 */
public class FileStoreParameterProducer implements SystemParametersProducer {
    static final List<String> FILE_SYSTEM_TYPES = Arrays.asList("ext4", "ext3", "xfs");
    final static Log log = new Log(FileStoreParameterProducer.class);

    List<FileStoreParam> fileStores;

    private AggregateParameterType fileStoreAggrType;

    public FileStoreParameterProducer(SystemParametersService sysParamsService) throws IOException {
        UnitType kbunit = new UnitType("KB");
        UnitType pctunit = new UnitType("%");

        Member totalMember = new Member("total", sysParamsService.getBasicType(Type.SINT64, kbunit));
        totalMember.setShortDescription("Size of the file store");

        Member availableMember = new Member("available", sysParamsService.getBasicType(Type.SINT64, kbunit));
        availableMember.setShortDescription(
                "The number of bytes available to this Java Virtual Machine on the file store");

        Member percentageUseMember = new Member("percentageUse", sysParamsService.getBasicType(Type.FLOAT, pctunit));
        percentageUseMember.setShortDescription("Percentage of bytes used on the file store");

        fileStoreAggrType = new AggregateParameterType.Builder().setName("FileStore")
                .addMember(totalMember)
                .addMember(availableMember)
                .addMember(percentageUseMember)
                .build();

        fileStores = new ArrayList<>();

        if (isWindows()) {
            Path dataDirectory = YamcsServer.getServer().getDataDirectory().toAbsolutePath().getRoot();
            FileStore store = Files.getFileStore(dataDirectory);
            // store.name() returns the name of the drive, which can be empty, so prefer drive letter
            String displayName = dataDirectory.getRoot().toString();
            String driveLetter = displayName.replace(":\\", ""); // Change C:\ to C
            addFileStore(store, driveLetter, displayName, sysParamsService);
        } else if (isMac()) {
            FileStore store = Files.getFileStore(YamcsServer.getServer().getDataDirectory());
            addFileStore(store, store.name(), store.name(), sysParamsService);
        } else {
            for (FileStore store : FileSystems.getDefault().getFileStores()) {
                if (FILE_SYSTEM_TYPES.contains(store.type())) {
                    if (fileStores.stream()
                            .filter(fs -> fs.store.name().equals(store.name())).findFirst()
                            .isPresent()) {
                        // sometimes (e.g. docker) the same filesystem is mounted multiple times in different locations
                        log.debug("Not adding duplicate store '{}' to the file stores to be monitored", store);
                    } else {
                        addFileStore(store, store.name(), store.name(), sysParamsService);
                    }
                }
            }
        }
    }

    private void addFileStore(FileStore store, String name, String displayName,
            SystemParametersService sysParamsService) {
        log.debug("Adding store '{}' to the file stores to be monitored", store);
        Parameter p = sysParamsService.createSystemParameter("df/" + name, fileStoreAggrType,
                "Information about disk usage for the " + displayName + " file store of type " + store.type());
        fileStores.add(new FileStoreParam(store, p));
    }

    @Override
    public Collection<ParameterValue> getSystemParameters(long gentime) {
        List<ParameterValue> pvlist = new ArrayList<>();
        for (FileStoreParam storep : fileStores) {
            FileStore store = storep.store;
            try {
                long ts = store.getTotalSpace();
                long av = store.getUsableSpace();
                float perc = (float) (100 - av * 100.0 / ts);

                AggregateValue v = new AggregateValue(fileStoreAggrType.getMemberNames());
                v.setMemberValue("total", ValueUtility.getSint64Value(ts / 1024));
                v.setMemberValue("available", ValueUtility.getSint64Value(av / 1024));
                v.setMemberValue("percentageUse", ValueUtility.getFloatValue(perc));

                ParameterValue pv = new ParameterValue(storep.param);
                pv.setGenerationTime(gentime);
                pv.setAcquisitionTime(gentime);
                pv.setAcquisitionStatus(AcquisitionStatus.ACQUIRED);
                pv.setEngValue(v);

                pv.setExpireMillis((long) (1.9 * getFrequency() * 1000));
                pvlist.add(pv);
            } catch (NoSuchFileException e) {
                // Maybe drive became inaccessible. Don't be verbose about it,
                // value will eventually expire.
                log.trace("Failed to collect information about the file store {}", store, e);
            } catch (IOException e) {
                log.error("Failed to collect information about the file store {}", store, e);
            }
        }

        return pvlist;
    }

    @Override
    public int getFrequency() {
        return 60;
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win");
    }

    private static boolean isMac() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("mac");
    }

    static class FileStoreParam {
        final FileStore store;
        final Parameter param;

        public FileStoreParam(FileStore store, Parameter param) {
            this.store = store;
            this.param = param;
        }
    }

}

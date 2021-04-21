package org.yamcs.parameter;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.yamcs.logging.Log;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.AggregateParameterType;
import org.yamcs.xtce.Member;
import org.yamcs.xtce.Parameter;

/**
 * Generates parameters containing information about the system disks: total space, available space and percentage used.
 * 
 */
public class FileStoreParameterProducer implements SystemParametersProducer {
    static final List<String> FILE_SYSTEM_TYPES = Arrays.asList("ext4", "ext3", "xfs");
    final static Log log = new Log(FileStoreParameterProducer.class);

    List<FileStoreParam> fileStores;

    private AggregateParameterType fileStoreAggrType;

    public FileStoreParameterProducer(SystemParametersService sysParamsService) {
        fileStoreAggrType = new AggregateParameterType.Builder().setName("FileStore")
                .addMember(new Member("total", sysParamsService.getBasicType(Type.SINT64)))
                .addMember(new Member("available", sysParamsService.getBasicType(Type.SINT64)))
                .addMember(new Member("percentageUse", sysParamsService.getBasicType(Type.FLOAT)))
                .build();


        fileStores = new ArrayList<>();
        for (FileStore store : FileSystems.getDefault().getFileStores()) {
            if (FILE_SYSTEM_TYPES.contains(store.type())) {
                if (fileStores.stream()
                        .filter(fs -> fs.store.name().equals(store.name())).findFirst()
                        .isPresent()) {
                    // sometimes (e.g. docker) the same filesystem is mounted multiple times in different locations
                    log.debug("Do not adding duplicate store '{}' to the file stores to be monitored", store);
                } else {
                    log.debug("Adding store '{}' to the file stores to be monitored", store);
                    Parameter p = sysParamsService.createSystemParameter("df/" + store.name(), fileStoreAggrType,
                            "Information about disk usage");
                    fileStores.add(new FileStoreParam(store, p));
                }
            }
        }
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
                pv.setEngineeringValue(v);
                pvlist.add(pv);
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

    static class FileStoreParam {
        final FileStore store;
        final Parameter param;

        public FileStoreParam(FileStore store, Parameter param) {
            this.store = store;
            this.param = param;
        }
    }

}

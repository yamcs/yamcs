package org.yamcs.http.api;

import java.util.List;

import org.yamcs.Processor;
import org.yamcs.algorithms.AlgorithmManager;
import org.yamcs.api.Observer;
import org.yamcs.http.Context;
import org.yamcs.protobuf.AbstractMdbOverrideApi;
import org.yamcs.protobuf.AlgorithmTextOverride;
import org.yamcs.protobuf.GetAlgorithmOverridesRequest;
import org.yamcs.protobuf.GetAlgorithmOverridesResponse;
import org.yamcs.protobuf.ListMdbOverridesRequest;
import org.yamcs.protobuf.ListMdbOverridesResponse;
import org.yamcs.protobuf.MdbOverrideInfo;
import org.yamcs.protobuf.MdbOverrideInfo.OverrideType;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

public class MdbOverrideApi extends AbstractMdbOverrideApi<Context> {

    @Override
    public void listMdbOverrides(Context ctx, ListMdbOverridesRequest request,
            Observer<ListMdbOverridesResponse> observer) {
        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());

        ListMdbOverridesResponse.Builder responseb = ListMdbOverridesResponse.newBuilder();

        List<AlgorithmManager> l = processor.getServices(AlgorithmManager.class);
        if (l.size() == 1) {
            AlgorithmManager algorithmManager = l.get(0);
            for (CustomAlgorithm algorithm : algorithmManager.getAlgorithmOverrides()) {
                MdbOverrideInfo.Builder overrideb = MdbOverrideInfo.newBuilder()
                        .setType(OverrideType.ALGORITHM_TEXT)
                        .setAlgorithmTextOverride(toAlgorithmTextOverride(algorithm));

                responseb.addOverrides(overrideb);
            }
        }

        observer.complete(responseb.build());
    }

    @Override
    public void getAlgorithmOverrides(Context ctx, GetAlgorithmOverridesRequest request,
            Observer<GetAlgorithmOverridesResponse> observer) {
        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
        XtceDb xtcedb = XtceDbFactory.getInstance(processor.getInstance());
        Algorithm algorithm = MdbApi.verifyAlgorithm(xtcedb, request.getName());

        GetAlgorithmOverridesResponse.Builder responseb = GetAlgorithmOverridesResponse.newBuilder();

        List<AlgorithmManager> l = processor.getServices(AlgorithmManager.class);
        if (l.size() == 1) {
            AlgorithmManager algorithmManager = l.get(0);
            CustomAlgorithm override = algorithmManager.getAlgorithmOverride(algorithm);
            if (override != null) {
                responseb.setTextOverride(toAlgorithmTextOverride(override));
            }
        }

        observer.complete(responseb.build());
    }

    private AlgorithmTextOverride toAlgorithmTextOverride(CustomAlgorithm algorithm) {
        AlgorithmTextOverride.Builder b = AlgorithmTextOverride.newBuilder()
                .setAlgorithm(algorithm.getQualifiedName())
                .setText(algorithm.getAlgorithmText());
        return b.build();
    }
}

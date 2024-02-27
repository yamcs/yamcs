package org.yamcs.http.api;

import org.yamcs.api.Api;
import org.yamcs.api.Observer;
import org.yamcs.http.api.SubscribeParameterObserver.SubscribeParametersData;
import org.yamcs.protobuf.AlgorithmStatus;
import org.yamcs.protobuf.AlgorithmTrace;
import org.yamcs.protobuf.BatchGetParameterValuesRequest;
import org.yamcs.protobuf.BatchGetParameterValuesResponse;
import org.yamcs.protobuf.BatchSetParameterValuesRequest;
import org.yamcs.protobuf.SetParameterValueRequest;
import org.yamcs.protobuf.CreateProcessorRequest;
import org.yamcs.protobuf.DeleteProcessorRequest;
import org.yamcs.protobuf.EditAlgorithmTraceRequest;
import org.yamcs.protobuf.EditProcessorRequest;
import org.yamcs.protobuf.GetAlgorithmStatusRequest;
import org.yamcs.protobuf.GetAlgorithmTraceRequest;
import org.yamcs.protobuf.GetParameterValueRequest;
import org.yamcs.protobuf.GetProcessorRequest;
import org.yamcs.protobuf.ListProcessorTypesResponse;
import org.yamcs.protobuf.ListProcessorsRequest;
import org.yamcs.protobuf.ListProcessorsResponse;
import org.yamcs.protobuf.ProcessingProto;
import org.yamcs.protobuf.ProcessorInfo;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Statistics;
import org.yamcs.protobuf.SubscribeAlgorithmStatusRequest;
import org.yamcs.protobuf.SubscribeParametersRequest;
import org.yamcs.protobuf.SubscribeProcessorsRequest;
import org.yamcs.protobuf.SubscribeTMStatisticsRequest;

import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;
import com.google.protobuf.Empty;
import com.google.protobuf.Message;

@javax.annotation.processing.Generated(value = "org.yamcs.protoc.ServiceGenerator", date = "2024-02-28T22:05:13.856079043Z")
@SuppressWarnings("unchecked")
public abstract class AbstractProcessingApi<T> implements Api<T> {

    /**
     * <pre>
     *  List processor types
     * </pre>
     */
    public abstract void listProcessorTypes(T ctx, Empty request, Observer<ListProcessorTypesResponse> observer);

    /**
     * <pre>
     *  List processors
     * </pre>
     */
    public abstract void listProcessors(T ctx, ListProcessorsRequest request, Observer<ListProcessorsResponse> observer);

    /**
     * <pre>
     *  Get a processor
     * </pre>
     */
    public abstract void getProcessor(T ctx, GetProcessorRequest request, Observer<ProcessorInfo> observer);

    /**
     * <pre>
     *  Delete a processor
     * 
     *  Only replay processors can be removed.
     * </pre>
     */
    public abstract void deleteProcessor(T ctx, DeleteProcessorRequest request, Observer<Empty> observer);

    /**
     * <pre>
     *  Update a processor
     * </pre>
     */
    public abstract void editProcessor(T ctx, EditProcessorRequest request, Observer<Empty> observer);

    /**
     * <pre>
     *  Create a processor
     * </pre>
     */
    public abstract void createProcessor(T ctx, CreateProcessorRequest request, Observer<Empty> observer);

    /**
     * <pre>
     *  Get a parameter's value
     * </pre>
     */
    public abstract void getParameterValue(T ctx, GetParameterValueRequest request, Observer<ParameterValue> observer);

    /**
     * <pre>
     *  Set a parameter's value
     * 
     *  Only some type of parameters can be updated.
     * </pre>
     */
    public abstract void setParameterValue(T ctx, SetParameterValueRequest request, Observer<Empty> observer);

    /**
     * <pre>
     *  Batch get the value of multiple parameters
     * </pre>
     */
    public abstract void batchGetParameterValues(T ctx, BatchGetParameterValuesRequest request, Observer<BatchGetParameterValuesResponse> observer);

    /**
     * <pre>
     *  Batch set the value of multiple parameters
     * </pre>
     */
    public abstract void batchSetParameterValues(T ctx, BatchSetParameterValuesRequest request, Observer<Empty> observer);

    /**
     * <pre>
     *  Receive TM statistics updates
     * </pre>
     */
    public abstract void subscribeTMStatistics(T ctx, SubscribeTMStatisticsRequest request, Observer<Statistics> observer);

    /**
     * <pre>
     *  Receive parameter updates
     * 
     *  The input message can be sent multiple types, allowing to alter a
     *  subscription with the ``action`` field.
     * </pre>
     */
    public abstract Observer<SubscribeParametersRequest> subscribeParameters(T ctx,
            Observer<SubscribeParametersData> observer);

    /**
     * <pre>
     *  Receive processor updates
     * </pre>
     */
    public abstract void subscribeProcessors(T ctx, SubscribeProcessorsRequest request, Observer<ProcessorInfo> observer);

    /**
     * <pre>
     *  Get the algorithm status
     * </pre>
     */
    public abstract void getAlgorithmStatus(T ctx, GetAlgorithmStatusRequest request, Observer<AlgorithmStatus> observer);

    /**
     * <pre>
     *  Receive algorithm status updates
     * </pre>
     */
    public abstract void subscribeAlgorithmStatus(T ctx, SubscribeAlgorithmStatusRequest request, Observer<AlgorithmStatus> observer);

    /**
     * <pre>
     *  Get the algorithm trace
     * </pre>
     */
    public abstract void getAlgorithmTrace(T ctx, GetAlgorithmTraceRequest request, Observer<AlgorithmTrace> observer);

    /**
     * <pre>
     *  Enable/disable algorithm tracing
     * </pre>
     */
    public abstract void editAlgorithmTrace(T ctx, EditAlgorithmTraceRequest request, Observer<Empty> observer);

    @Override
    public final ServiceDescriptor getDescriptorForType() {
        return ProcessingProto.getDescriptor().getServices().get(0);
    }

    @Override
    public final Message getRequestPrototype(MethodDescriptor method) {
        if (method.getService() != getDescriptorForType()) {
            throw new IllegalArgumentException("Method not contained by this service.");
        }
        switch (method.getIndex()) {
        case 0:
            return Empty.getDefaultInstance();
        case 1:
            return ListProcessorsRequest.getDefaultInstance();
        case 2:
            return GetProcessorRequest.getDefaultInstance();
        case 3:
            return DeleteProcessorRequest.getDefaultInstance();
        case 4:
            return EditProcessorRequest.getDefaultInstance();
        case 5:
            return CreateProcessorRequest.getDefaultInstance();
        case 6:
            return GetParameterValueRequest.getDefaultInstance();
        case 7:
            return SetParameterValueRequest.getDefaultInstance();
        case 8:
            return BatchGetParameterValuesRequest.getDefaultInstance();
        case 9:
            return BatchSetParameterValuesRequest.getDefaultInstance();
        case 10:
            return SubscribeTMStatisticsRequest.getDefaultInstance();
        case 11:
            return SubscribeParametersRequest.getDefaultInstance();
        case 12:
            return SubscribeProcessorsRequest.getDefaultInstance();
        case 13:
            return GetAlgorithmStatusRequest.getDefaultInstance();
        case 14:
            return SubscribeAlgorithmStatusRequest.getDefaultInstance();
        case 15:
            return GetAlgorithmTraceRequest.getDefaultInstance();
        case 16:
            return EditAlgorithmTraceRequest.getDefaultInstance();
        default:
            throw new IllegalStateException();
        }
    }

    @Override
    public final Message getResponsePrototype(MethodDescriptor method) {
        if (method.getService() != getDescriptorForType()) {
            throw new IllegalArgumentException("Method not contained by this service.");
        }
        switch (method.getIndex()) {
        case 0:
            return ListProcessorTypesResponse.getDefaultInstance();
        case 1:
            return ListProcessorsResponse.getDefaultInstance();
        case 2:
            return ProcessorInfo.getDefaultInstance();
        case 3:
            return Empty.getDefaultInstance();
        case 4:
            return Empty.getDefaultInstance();
        case 5:
            return Empty.getDefaultInstance();
        case 6:
            return ParameterValue.getDefaultInstance();
        case 7:
            return Empty.getDefaultInstance();
        case 8:
            return BatchGetParameterValuesResponse.getDefaultInstance();
        case 9:
            return Empty.getDefaultInstance();
        case 10:
            return Statistics.getDefaultInstance();
        // case 11:
        // return SubscribeParametersData.getDefaultInstance();
        case 12:
            return ProcessorInfo.getDefaultInstance();
        case 13:
            return AlgorithmStatus.getDefaultInstance();
        case 14:
            return AlgorithmStatus.getDefaultInstance();
        case 15:
            return AlgorithmTrace.getDefaultInstance();
        case 16:
            return Empty.getDefaultInstance();
        default:
            throw new IllegalStateException();
        }
    }

    @Override
    public final void callMethod(MethodDescriptor method, T ctx, Message request, Observer<Message> future) {
        if (method.getService() != getDescriptorForType()) {
            throw new IllegalArgumentException("Method not contained by this service.");
        }
        switch (method.getIndex()) {
        case 0:
            listProcessorTypes(ctx, (Empty) request, (Observer<ListProcessorTypesResponse>)(Object) future);
            return;
        case 1:
            listProcessors(ctx, (ListProcessorsRequest) request, (Observer<ListProcessorsResponse>)(Object) future);
            return;
        case 2:
            getProcessor(ctx, (GetProcessorRequest) request, (Observer<ProcessorInfo>)(Object) future);
            return;
        case 3:
            deleteProcessor(ctx, (DeleteProcessorRequest) request, (Observer<Empty>)(Object) future);
            return;
        case 4:
            editProcessor(ctx, (EditProcessorRequest) request, (Observer<Empty>)(Object) future);
            return;
        case 5:
            createProcessor(ctx, (CreateProcessorRequest) request, (Observer<Empty>)(Object) future);
            return;
        case 6:
            getParameterValue(ctx, (GetParameterValueRequest) request, (Observer<ParameterValue>)(Object) future);
            return;
        case 7:
            setParameterValue(ctx, (SetParameterValueRequest) request, (Observer<Empty>)(Object) future);
            return;
        case 8:
            batchGetParameterValues(ctx, (BatchGetParameterValuesRequest) request, (Observer<BatchGetParameterValuesResponse>)(Object) future);
            return;
        case 9:
            batchSetParameterValues(ctx, (BatchSetParameterValuesRequest) request, (Observer<Empty>)(Object) future);
            return;
        case 10:
            subscribeTMStatistics(ctx, (SubscribeTMStatisticsRequest) request, (Observer<Statistics>)(Object) future);
            return;
        case 12:
            subscribeProcessors(ctx, (SubscribeProcessorsRequest) request, (Observer<ProcessorInfo>)(Object) future);
            return;
        case 13:
            getAlgorithmStatus(ctx, (GetAlgorithmStatusRequest) request, (Observer<AlgorithmStatus>)(Object) future);
            return;
        case 14:
            subscribeAlgorithmStatus(ctx, (SubscribeAlgorithmStatusRequest) request, (Observer<AlgorithmStatus>)(Object) future);
            return;
        case 15:
            getAlgorithmTrace(ctx, (GetAlgorithmTraceRequest) request, (Observer<AlgorithmTrace>)(Object) future);
            return;
        case 16:
            editAlgorithmTrace(ctx, (EditAlgorithmTraceRequest) request, (Observer<Empty>)(Object) future);
            return;
        default:
            throw new IllegalStateException();
        }
    }

    @Override
    public final Observer<Message> callMethod(MethodDescriptor method, T ctx, Observer<Message> future) {
        if (method.getService() != getDescriptorForType()) {
            throw new IllegalArgumentException("Method not contained by this service.");
        }
        switch (method.getIndex()) {
        case 11:
            return (Observer<Message>)(Object) subscribeParameters(ctx, (Observer<SubscribeParametersData>)(Object) future);
        default:
            throw new IllegalStateException();
        }
    }
}

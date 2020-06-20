package org.yamcs.client.base;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.yamcs.api.Observer;
import org.yamcs.client.Page;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;

public abstract class AbstractPage<RequestT extends Message, ResponseT extends Message, ItemT> implements Page<ItemT> {

    private static final String CONTINUATION_TOKEN = "continuationToken";
    private static final String NEXT = "next";

    private final RequestT originalRequest;
    private final String repeatableField;
    private final FieldDescriptor nextField;

    private CompletableFuture<ResponseT> future;

    // Only set when the future resolves
    private ResponseT response;
    private List<ItemT> items;
    private String continuationToken;

    public AbstractPage(RequestT request, String repeatableField) {
        originalRequest = request;
        this.repeatableField = repeatableField;

        Descriptor requestDescriptor = request.getDescriptorForType();
        nextField = requestDescriptor.findFieldByName(NEXT);
        if (nextField == null) {
            throw new IllegalArgumentException(String.format(
                    "Paging requires the request message to have a field '%s'", NEXT));
        }

        future = new CompletableFuture<>();
        fetch(request, new ResponseObserver<>(future));
    }

    public CompletableFuture<Page<ItemT>> future() {
        return future.thenApply(response -> {
            readResponse(response);
            return this;
        });
    }

    private void readResponse(ResponseT response) {
        this.response = response;
        Descriptor responseDescriptor = response.getDescriptorForType();
        FieldDescriptor continuationField = responseDescriptor.findFieldByName(CONTINUATION_TOKEN);
        if (continuationField == null) {
            throw new IllegalArgumentException(String.format(
                    "Paging requires the message to have a field '%s'", CONTINUATION_TOKEN));
        }

        if (response.hasField(continuationField)) {
            continuationToken = (String) response.getField(continuationField);
        }

        FieldDescriptor repeatableDescriptor = responseDescriptor.findFieldByName(repeatableField);
        items = mapRepeatableField(response.getField(repeatableDescriptor));
    }

    // Can be overriden to expose a page of non-protobuf messages
    @SuppressWarnings("unchecked")
    protected List<ItemT> mapRepeatableField(Object field) {
        return new ArrayList<>((List<ItemT>) field);
    }

    public ResponseT getResponse() {
        return response;
    }

    @Override
    public boolean hasNextPage() {
        return continuationToken != null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<Page<ItemT>> getNextPage() {
        if (continuationToken == null) {
            return CompletableFuture.completedFuture(null);
        }

        Message.Builder newRequest = originalRequest.toBuilder();
        newRequest.setField(nextField, continuationToken);
        RequestT continuationRequest = (RequestT) newRequest.build();
        return new ContinuationPage(continuationRequest, repeatableField).future();
    }

    protected abstract void fetch(RequestT request, Observer<ResponseT> observer);

    /**
     * Returns an iterator. This only iterates the current page.
     */
    @Override
    public Iterator<ItemT> iterator() {
        return items.iterator();
    }

    // Delegates the actual fetch to the subclass
    private class ContinuationPage extends AbstractPage<RequestT, ResponseT, ItemT> {

        public ContinuationPage(RequestT request, String repeatableField) {
            super(request, repeatableField);
        }

        @Override
        protected void fetch(RequestT request, Observer<ResponseT> observer) {
            AbstractPage.this.fetch(request, observer);
        }
    }
}

package org.yamcs.web.api;

import org.yamcs.api.FilterSyntaxException;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.api.EventFilterFactory;

public class ParseFilterObserver implements Observer<ParseFilterRequest> {

    private Observer<ParseFilterData> responseObserver;

    public ParseFilterObserver(Observer<ParseFilterData> responseObserver) {
        this.responseObserver = responseObserver;
    }

    @Override
    public void next(ParseFilterRequest request) {
        try {
            switch (request.getResource()) {
            case "events":
                EventFilterFactory.create(request.getFilter());
                break;
            default:
                throw new IllegalStateException("Unexpected resource: '" + request.getResource() + "'");
            }

            responseObserver.next(ParseFilterData.getDefaultInstance());
        } catch (BadRequestException e) {
            var detail = (FilterSyntaxException) e.getDetail();
            var responseb = ParseFilterData.newBuilder()
                    .setErrorMessage(e.getMessage())
                    .setBeginLine(detail.getBeginLine())
                    .setBeginColumn(detail.getBeginColumn())
                    .setEndLine(detail.getEndLine())
                    .setEndColumn(detail.getEndColumn());
            responseObserver.next(responseb.build());
        }
    }

    @Override
    public void completeExceptionally(Throwable t) {
        // NOP
    }

    @Override
    public void complete() {
        // NOP
    }
}

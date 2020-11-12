package org.yamcs.http.api;

import org.yamcs.api.Observer;
import org.yamcs.http.Context;
import org.yamcs.protobuf.AbstractTcoApi;
import org.yamcs.protobuf.GetCoefficientsRequest;
import org.yamcs.protobuf.SetCoefficientsRequest;
import org.yamcs.protobuf.TcoCoefficients;

import com.google.protobuf.Empty;

public class TcoApi extends AbstractTcoApi<Context> {

    @Override
    public void getCoefficients(Context ctx, GetCoefficientsRequest request, Observer<TcoCoefficients> observer) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setCoefficients(Context ctx, SetCoefficientsRequest request, Observer<Empty> observer) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void reset(Context ctx, Empty request, Observer<Empty> observer) {
        // TODO Auto-generated method stub
        
    }

}

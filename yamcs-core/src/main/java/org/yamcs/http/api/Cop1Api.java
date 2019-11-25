package org.yamcs.http.api;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.management.ManagementService;
import org.yamcs.management.ManagementService.LinkWithInfo;
import org.yamcs.protobuf.AbstractCop1Api;
import org.yamcs.protobuf.Cop1Config;
import org.yamcs.protobuf.Cop1Status;
import org.yamcs.protobuf.DisableRequest;
import org.yamcs.protobuf.GetConfigRequest;
import org.yamcs.protobuf.GetStatusRequest;
import org.yamcs.protobuf.InitializeRequest;
import org.yamcs.protobuf.ResumeRequest;
import org.yamcs.protobuf.SetConfigRequest;
import org.yamcs.tctm.Link;
import org.yamcs.tctm.ccsds.Cop1TcPacketHandler;
import org.yamcs.utils.ExceptionUtil;

import com.google.protobuf.Empty;

public class Cop1Api extends AbstractCop1Api<Context> {

    @Override
    public void initialize(Context ctx, InitializeRequest request, Observer<Empty> observer) {
        Cop1TcPacketHandler cop1Link = verifyCop1Link(request.getInstance(), request.getName());
        
        if(!request.hasType()) {
            throw new BadRequestException("No initialization type specified");
        }
        CompletableFuture<Void> cf; 
                
        switch(request.getType()) {
        case SET_VR:
            if(!request.hasVR()) {
                throw new BadRequestException("No vR specified for the SET_VR initialization request");
            }
            cf = cop1Link.initiateADWithVR(request.getVR());
            break;
        case UNLOCK:
            cf = cop1Link.initiateADWithUnlock();
            break;
        case WITH_CLCW_CHECK:
            
            if(request.hasClcwCheckInitializeTimeout()) {
                cf = cop1Link.initiateAD(true, request.getClcwCheckInitializeTimeout());
            } else {
                cf = cop1Link.initiateAD(true);    
            }
            break;
        case WITHOUT_CLCW_CHECK:
            cf = cop1Link.initiateAD(false);
            break;
        default:
            throw new IllegalStateException("Unknown request type "+request.getType());
        }
        sendEmptyResponse(observer, cf);
      
    }

   
   

    @Override
    public void resume(Context ctx, ResumeRequest request, Observer<Empty> observer) {
        Cop1TcPacketHandler cop1Link = verifyCop1Link(request.getInstance(), request.getName());
        sendEmptyResponse(observer, cop1Link.resume());
    }

    @Override
    public void disable(Context ctx, DisableRequest request, Observer<Empty> observer) {
        Cop1TcPacketHandler cop1Link = verifyCop1Link(request.getInstance(), request.getName());
        boolean bypassAll = request.hasSetBypassAll()?request.getSetBypassAll():true;
        cop1Link.disableCop1(bypassAll);
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void setConfig(Context ctx, SetConfigRequest request, Observer<Empty> observer) {
        Cop1TcPacketHandler cop1Link = verifyCop1Link(request.getInstance(), request.getName());
        if(!request.hasCop1Config()) {
            throw new BadRequestException("Cop1Config not specified");
        }
        sendEmptyResponse(observer, cop1Link.setConfig(request.getCop1Config()));
    }

    @Override
    public void getConfig(Context ctx, GetConfigRequest request, Observer<Cop1Config> observer) {
        Cop1TcPacketHandler cop1Link = verifyCop1Link(request.getInstance(), request.getName());
        CompletableFuture<Cop1Config> cf = cop1Link.getCop1Config();
        cf.whenComplete((v, error) -> {
            if (error == null) {
                observer.complete(v);
            } else {
                Throwable t = ExceptionUtil.unwind(error);
                observer.completeExceptionally(new InternalServerErrorException(t));
            }
        });
    }

    @Override
    public void getStatus(Context ctx, GetStatusRequest request, Observer<Cop1Status> observer) {
        Cop1TcPacketHandler cop1Link = verifyCop1Link(request.getInstance(), request.getName());
        CompletableFuture<Cop1Status> cf = cop1Link.getCop1Status();
        cf.whenComplete((v, error) -> {
            if (error == null) {
                observer.complete(v);
            } else {
                Throwable t = ExceptionUtil.unwind(error);
                observer.completeExceptionally(new InternalServerErrorException(t));
            }
        });

    }
    
    private Cop1TcPacketHandler verifyCop1Link(String instance, String name) {
        RestHandler.verifyInstance(instance);
        Optional<LinkWithInfo> o = ManagementService.getInstance().getLinkWithInfo(instance, name);
        if (!o.isPresent()) {
            throw new BadRequestException("There is no link named '" + name + "' in instance " + instance);
        }
        Link link = o.get().getLink();
        if(link instanceof Cop1TcPacketHandler) {
            return (Cop1TcPacketHandler) link;
        }
        throw new BadRequestException("Link '" + name + "' in instance " + instance+" does not support COP1");
    }

    private void sendEmptyResponse(Observer<Empty> observer, CompletableFuture<Void> cf) {
        cf.whenComplete((v, error) -> {
            if (error == null) {
                observer.complete(Empty.getDefaultInstance());
            } else {
                Throwable t = ExceptionUtil.unwind(error);
                observer.completeExceptionally(new InternalServerErrorException(t));
            }
        });
    }

}

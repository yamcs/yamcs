package org.yamcs.time;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsServer;
import org.yamcs.protobuf.Rest.SetSimulationTimeRequest;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.HttpException;
import org.yamcs.web.HttpServer;
import org.yamcs.web.NotFoundException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.Route;

import io.netty.channel.ChannelFuture;

/**
 * Simulation time model where the simulation starts at javaTime0
 * 
 * the simElapsedTime is the simulation elapsedTime counting from javaTime0
 * 
 * the speed is the simulation speed. If greater than 0, the time passes even without the update of the simElapsedTime
 * 
 * 
 * @author nm
 *
 */
public class SimulationTimeService implements TimeService {
    double speed;    
    long javaTime0;
    long javaTime; //this is the java time when the last simElapsedTime has been set
    long simElapsedTime;
    private static final Logger log=LoggerFactory.getLogger(SimulationTimeService.class);
    
    public SimulationTimeService(String yamcsInstance) {        
        javaTime0 = System.currentTimeMillis();
        javaTime = javaTime0 ;
        simElapsedTime = 0;
        speed = 1;
        
        HttpServer.getInstance().registerRouteHandler(yamcsInstance, new SimTimeRestHandler());
    }
    
    @Override
    public long getMissionTime() {
        long t;
        t= (long) (javaTime0 + simElapsedTime + speed*(System.currentTimeMillis()-javaTime));
        return t;
    }
    
    public void setSimElapsedTime(long simElapsedTime) {
        javaTime = System.currentTimeMillis();
        this.simElapsedTime = simElapsedTime;
    }
    
    public void setTime0(long time0) {
        javaTime0 = time0;
    }
    
    public void setSimSpeed(double simSpeed) {
        this.speed = simSpeed;
    }   

    /**
     * Handles incoming requests related to SimTime
     */
    public static class SimTimeRestHandler extends RestHandler {
        
        @Route(path = "/api/time/:instance", method = { "PUT", "POST"})
        public void setSimTime(RestRequest req) throws HttpException {
            String instance = verifyInstance(req, req.getRouteParam("instance"));
            TimeService ts = YamcsServer.getInstance(instance).getTimeService();
            if(!(ts instanceof SimulationTimeService)) {
                log.warn("Simulation time service requested for a non-simulation TimeService "+ts);
                throw new NotFoundException(req);
            }
            
            SimulationTimeService sts = (SimulationTimeService) ts;
            SetSimulationTimeRequest request = req.bodyAsMessage(SchemaRest.SetSimulationTimeRequest.MERGE).build();
            
            if(request.hasTime0()) {
                sts.setTime0(request.getTime0());
            } else if(request.hasTime0UTC()) {
                sts.setTime0(TimeEncoding.parse(request.getTime0UTC()));
            }
            
            if(request.hasSimSpeed()) {
                sts.setSimSpeed(request.getSimSpeed());
            }
            
            if(request.hasSimElapsedTime()) {
                sts.setSimElapsedTime(request.getSimElapsedTime());
            }
            
            completeOK(req);
        }
    }
}

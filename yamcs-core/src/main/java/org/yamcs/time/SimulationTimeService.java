package org.yamcs.time;

import org.yamcs.YConfiguration;
import org.yamcs.tctm.AbstractTmDataLink;
import org.yamcs.utils.TimeEncoding;

/**
 * Simulation time model. Can be used by configuring in the yamcs.instance.yaml:
 * 
 * <pre>
 * timeService:
 *     class: org.yamcs.time.SimulationTimeService
 *     args:
 *         time0: 2020-10-02T18:10:00.000Z
 *         speed: 1.0
 * </pre>
 * 
 * By default the time0 is initialised with the time at the instance startup and the speed is 1.
 * <p>
 * The service mantains a simulated time which is running based on the computer clock according to speed (e.g. speed = 2
 * means it runs two times realtime speed). If the speed is 0, the simulated time never advances and it has to be
 * advanced from other place (see below).
 * <p>
 * The simulation time service (as well as the speed) can be updated by various means:
 * <ul>
 * <li>Using the http time API; see
 * <a href="https://yamcs.org/docs/yamcs-http-api/time/">https://yamcs.org/docs/yamcs-http-api/time/</a> for
 * details.</li>
 * <li>Setting the option {@code updateSimulationTime: true} on a TM Data Link (this is implemented in
 * {@link AbstractTmDataLink}) and will cause the simulation time to be updated with the packet generation time each
 * time a packet is received on the corresponding link.
 * <li>By a custom service.
 * </ul>
 * <p>
 * Note: using a time0 configured with a fixed value will cause the Yamcs to start always with the same simulation time
 * and that might cause undesired effects such as packets overwriting eachother in the archive.
 * <p>
 * Such an option is better to be used in a template which
 * can then be used to create always new Yamcs instances (corresponding to test sessions) such that the data is
 * separated. See the <a href="https://yamcs.org/docs/yamcs-http-api/management/">instance and templates API</a> .
 *
 * @author nm
 *
 */
public class SimulationTimeService implements TimeService {
    double speed;
    long time0;
    long javaTime; // this is the java time when the last simElapsedTime has been set
    long simElapsedTime;

    public SimulationTimeService(String yamcsInstance, YConfiguration config) {
        if (config.containsKey("time0")) {
            time0 = TimeEncoding.parse(config.getString("time0"));
        } else {
            time0 = TimeEncoding.getWallclockTime();
        }
        speed = config.getLong("speed", 1);

        javaTime = System.currentTimeMillis();
        simElapsedTime = 0;
    }

    public SimulationTimeService(String yamcsInstance) {
        this(yamcsInstance, YConfiguration.emptyConfig());

    }

    /**
     * The mission time returned is:
     * <p>
     * {@code time0 + simElapsedTime + speed * (System.currentTimeMillis() - javaTime)}
     * <p>
     * where time0 is the value set with {@link #setTime0(long)}, simElapsedTime is the value set with
     * {@link #setSimElapsedTime(long)}
     * and the javaTime is the value returned by {@link System#currentTimeMillis()} last time when
     * {@link #setSimElapsedTime(long)} has been called.
     */
    @Override
    public long getMissionTime() {
        long t;
        t = (long) (time0 + simElapsedTime + speed * (System.currentTimeMillis() - javaTime));
        return t;
    }

    public void setSimElapsedTime(long simElapsedTime) {
        javaTime = System.currentTimeMillis();
        this.simElapsedTime = simElapsedTime;
    }

    /**
     * Set the time0
     * 
     * @param time0
     */
    public void setTime0(long time0) {
        this.time0 = time0;
    }

    /**
     * Set the simulation speed. If greater than 0, the time passes even without the update of the simElapsedTime.
     * 
     * @param simSpeed
     */
    public void setSimSpeed(double simSpeed) {
        this.speed = simSpeed;
    }
}

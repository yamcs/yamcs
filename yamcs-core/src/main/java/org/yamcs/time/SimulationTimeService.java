package org.yamcs.time;

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
    long javaTime; // this is the java time when the last simElapsedTime has been set
    long simElapsedTime;

    public SimulationTimeService(String yamcsInstance) {
        javaTime0 = System.currentTimeMillis();
        javaTime = javaTime0;
        simElapsedTime = 0;
        speed = 1;
    }

    @Override
    public long getMissionTime() {
        long t;
        t = (long) (javaTime0 + simElapsedTime + speed * (System.currentTimeMillis() - javaTime));
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
}

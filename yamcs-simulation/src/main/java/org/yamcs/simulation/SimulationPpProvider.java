package org.yamcs.simulation;

import java.io.FileReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Pvalue.RangeCondition;
import org.yamcs.simulation.generated.PpSimulation;
import org.yamcs.simulation.generated.PpSimulation.ParameterSequence;
import org.yamcs.tctm.ParameterDataLink;
import org.yamcs.tctm.ParameterSink;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

// Command line to generate xml classes:
//[...]/yamcs/yamcs-simulation/src/main/resources/org/yamcs/xsd$ xjc simulation_data.xsd -p org.yamcs.simulation.generated -d [...]/yamcs/yamcs-simulation/src/main/java/
public class SimulationPpProvider extends AbstractExecutionThreadService implements ParameterDataLink, Runnable {

    public Date simulationStartTime;
    public Date simulationRealStartTime;
    public int simulationStepLengthMs;
    public long simutationStep;
    public boolean loopSimulation;

    protected volatile long datacount = 0;
    protected volatile boolean disabled = false;

    private ParameterSink ppListener;

    private PpSimulation simulationData;
    // static String SIMULATION_DATA =
    // "/home/msc/development/git/yamcs/live/etc/simulation.xml";
    private static String simulationDataPath = "";

    private XtceDb xtceDb;

    private Random rand = new Random();

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    public SimulationPpProvider(String yamcsInstance, String name, LinkedHashMap<String, String> args)
            throws ConfigurationException {
        xtceDb = XtceDbFactory.getInstance(yamcsInstance);
        setSimulationData((String) args.get("simulationDataPath"));
        simulationData = loadSimulationData(simulationDataPath);
    }

    public SimulationPpProvider() {
    }

    @Override
    public Status getLinkStatus() {
        if (disabled) {
            return Status.DISABLED;
        } else {
            return Status.OK;
        }
    }

    @Override
    public String getDetailedStatus() {
        return getLinkStatus().toString();
    }

    @Override
    public void enable() {
        // reload simulation data and reset simulation parameters
        if (disabled) {
            simulationData = loadSimulationData(simulationDataPath);
        }
        disabled = false;

    }

    @Override
    public void disable() {
        disabled = true;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public long getDataInCount() {
        return 0;
    }

    @Override
    public long getDataOutCount() {
        return datacount;
    }

    @Override
    public void setParameterSink(ParameterSink ppListener) {
        this.ppListener = ppListener;

    }

    /**
     * Entry point to run the simulation
     */
    @Override
    public void run() {
        while (isRunning()) {
            try {
                if (!disabled) {
                    // run simulation
                    processSimulationData();
                } else {
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                log.warn("exception thrown when processing a parameter. Details:\n"
                        + e.toString());
                e.printStackTrace();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e1) {
                }
            }
        }
    }

    public void setSimulationData(String xmlFilePath) {
        simulationDataPath = xmlFilePath;
        simulationData = loadSimulationData(simulationDataPath);
    }

    /**
     * Processes the specified simulation scenario
     */
    public void processSimulationData() {

        // get simulation starting date
        if (simulationData.getStartDate() != null) {
            simulationStartTime = simulationData.getStartDate().toGregorianCalendar().getTime();
        } else {
            simulationStartTime = new Date();
        }
        simulationRealStartTime = new Date();
        simutationStep = 0;

        // get length of a simulation step
        simulationStepLengthMs = simulationData.getStepLengthMs();

        List<PpSimulation.ParameterSequence> pss = simulationData.getParameterSequence();
        for (ParameterSequence ps : pss) {
            processParameterSequence(ps);
        }

    }

    /**
     * Load simulation data from an XML file
     * 
     * @param fileName
     * @return simulation data
     */
    public PpSimulation loadSimulationData(String fileName) {
        try {
            final JAXBContext jc = JAXBContext.newInstance(PpSimulation.class);
            final Unmarshaller unmarshaller = jc.createUnmarshaller();

            final PpSimulation ppSimulation = (PpSimulation) unmarshaller
                    .unmarshal(new FileReader(fileName));
            return ppSimulation;

        } catch (Exception e) {
            log.error("Unable to load Simulation Data. Check the XML file is correct. Details:\n"
                    + e.toString());
            throw new ConfigurationException(
                    "Unable to load Simulation Data. Check the XML file is correct. Details:\n" + e.toString());
        }
    }

    /**
     * Processes a sequence of the simulation scenario
     * 
     * @param ps
     *            - sequence
     */
    private void processParameterSequence(ParameterSequence ps) {

        int repeatCount = 0;
        int maxRepeat = ps.getRepeat() != null ? ps.getRepeat() : 1;
        boolean loopSequence = ps.isLoop() != null && ps.isLoop();

        // repeat the sequence as specified
        while (loopSequence || repeatCount++ < maxRepeat) {

            if (stop()) {
                break;
            }

            // process step offset
            int stepOffset = ps.getStepOffset() == null ? 0 : ps.getStepOffset();
            processVoidStep(stepOffset);
            simutationStep += stepOffset;

            // initialize step count for this sequence
            List<ParameterSequence.Parameter> parameters = ps.getParameter();
            if (parameters.size() == 0) {
                return;
            }
            int lastSequenceStep = parameters.get(parameters.size() - 1)
                    .getAquisitionStep();

            int currentParameterIndex = 0;

            // process each step of the sequence
            for (int sequenceStep = 0; sequenceStep <= lastSequenceStep; sequenceStep++) {

                if (stop()) {
                    break;
                }

                ParameterSequence.Parameter currentParameter = parameters
                        .get(currentParameterIndex);

                // case where there is no parameter to send at this step
                if (currentParameter.getAquisitionStep() != sequenceStep) {
                    assert (currentParameter.getAquisitionStep() > sequenceStep);
                    processVoidStep(1);
                } else {
                    // there is at least 1 parameter to send at this step
                    List<ParameterSequence.Parameter> stepParameters = new ArrayList<>();
                    while (currentParameter.getAquisitionStep() == sequenceStep) {
                        stepParameters.add(currentParameter);
                        currentParameterIndex++;
                        // add next parameter if available
                        if (currentParameterIndex < parameters.size()) {
                            currentParameter = parameters
                                    .get(currentParameterIndex);
                        } else {
                            break;
                        }
                    }
                    processParameters(stepParameters);
                    stepParameters.clear();
                }
                simutationStep++;
            }
        }
    }

    /**
     * Used when no parameters need to be inserted at a given step of the simulation scenario
     * 
     * @param nbSteps
     */
    private void processVoidStep(int nbSteps) {
        try {
            log.trace("Processing {} void steps", nbSteps);
            Thread.sleep(simulationStepLengthMs * nbSteps);
        } catch (InterruptedException e) {
            log.error(e.toString());
        }
    }

    /**
     * Create a specified parameter and insert it in the Yamcs PP Listener
     * 
     * @param stepParameters
     */
    private void processParameters(List<ParameterSequence.Parameter> stepParameters) {

        String groupName = "simulation";

        List<ParameterValue> pvs = new LinkedList<>();
        for (ParameterSequence.Parameter sParameter : stepParameters) {

            if (stop()) {
                break;
            }

            // compute value
            Value engValue = getValue(sParameter.getValueType(), sParameter.getValue());
            Value rawValue = getValue(sParameter.getRawValueType(), sParameter.getRawValue());

            // create generationTime and acquisitionTime
            long acquisitionTime = simulationStartTime.getTime() + simutationStep * simulationStepLengthMs;
            long generationTime = acquisitionTime
                    - (sParameter.getAquisitionStep() - sParameter.getGenerationStep()) * simulationStepLengthMs;

            // convert time to 'instant'
            acquisitionTime = TimeEncoding.fromUnixTime(acquisitionTime);
            generationTime = TimeEncoding.fromUnixTime(generationTime);

            // get monitoring result
            String monitoringResult = sParameter.getMonitoringResult();

            ParameterValue pv = createPv(sParameter.getSpaceSystem(),
                    sParameter.getParaName(), generationTime, acquisitionTime,
                    engValue, rawValue, monitoringResult);

            if (pv != null) {
                pvs.add(pv);
            }
        }

        datacount += stepParameters.size();
        ppListener.updateParameters(TimeEncoding.getWallclockTime(), groupName, (int) datacount, pvs);

        long nextStepDate = simulationRealStartTime.getTime() + simulationStepLengthMs * simutationStep;
        long delayBeforeNextStep = nextStepDate - new Date().getTime();
        try {
            if (delayBeforeNextStep > 0) {
                Thread.sleep(delayBeforeNextStep);
            }
        } catch (Exception e) {
            log.error("", e);
        }
    }

    private Value getValue(String valueType, BigDecimal value) {
        if (valueType == null) {
            return null;
        }

        if (valueType.equals("random")) {
            return ValueUtility.getFloatValue(rand.nextFloat());
        } else if ("uint32".equals(valueType)) {
            return ValueUtility.getUint32Value(value.intValue());
        } else { // compatibility with old code that was always using floats
            return ValueUtility.getFloatValue(value.floatValue());
        }

    }

    /**
     * Creates parameter value
     * 
     * @param spaceSystem
     * @param paramName
     * @param generationTime
     * @param acquisitionTime
     * @param value
     * @param monitoringResult
     * @return parameter value object
     */
    private ParameterValue createPv(String spaceSystem, String paramName,
            long generationTime, long acquisitionTime, Value engValue, Value rawValue,
            String monitoringResult) {
        // create parameter definition
        String parameterFqn = spaceSystem + paramName;
        Parameter param;
        if (xtceDb != null) {
            param = xtceDb.getParameter(parameterFqn);
            if (param == null) {
                log.warn("Unable to get parameter " + parameterFqn + " from xtceDb.");
                param = new Parameter(parameterFqn);
            }

        } else {
            param = new Parameter(parameterFqn);
        }

        // create parameter value
        ParameterValue pv = new ParameterValue(param);
        pv.setEngineeringValue(engValue);
        pv.setRawValue(rawValue);

        // set monitoring result as specified in xml data (regardless of the
        // alarms ranges)
        String rangeCondition = null;
        if (monitoringResult != null && monitoringResult.contains("_")) {
            String[] parts = monitoringResult.split("_");
            monitoringResult = parts[0];
            rangeCondition = parts[1];
        }
        try {
            if (monitoringResult != null) {
                MonitoringResult mr = MonitoringResult
                        .valueOf(monitoringResult);
                pv.setMonitoringResult(mr);
            } else {
                pv.setMonitoringResult(MonitoringResult.DISABLED);
            }
            if (rangeCondition != null) {
                pv.setRangeCondition(RangeCondition.valueOf(rangeCondition));
            }
        } catch (Exception e) {
            log.error("Unable to set the specified monitoring result (\""
                    + monitoringResult
                    + "\". Please check that the value is one of the Enum MonitoringResult (DISABLED, IN_LIMITS, WATCH, WATCH_LOW, WATCH_HIGH,"
                    + " WARNING, WARNING_LOW, WARNING_HIGH, DISTRESS, DISTRESS_LOW, DISTRESS_HIGH, CRITICAL, CRITICAL_LOW, CRITICAL_HIGH, SEVERE, SEVERE_LOW, SEVERE_HIGH)");
        }

        pv.setGenerationTime(generationTime);
        pv.setAcquisitionTime(acquisitionTime);
        pv.setAcquisitionStatus(AcquisitionStatus.ACQUIRED);

        return pv;
    }

    protected boolean stop() {
        return !isRunning() || disabled;
    }

}

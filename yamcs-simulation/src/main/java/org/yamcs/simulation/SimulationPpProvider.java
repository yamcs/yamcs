package org.yamcs.simulation;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Pvalue.RangeCondition;
import org.yamcs.simulation.generated.PpSimulation;
import org.yamcs.simulation.generated.PpSimulation.ParameterSequence;
import org.yamcs.tctm.PpDataLink;
import org.yamcs.tctm.PpListener;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

// Command line to generate xml classes:
//[...]/yamcs/yamcs-simulation/src/main/resources/org/yamcs/xsd$ xjc simulation_data.xsd -p org.yamcs.simulation.generated -d [...]/yamcs/yamcs-simulation/src/main/java/
public class SimulationPpProvider extends AbstractExecutionThreadService implements PpDataLink, Runnable {
	protected volatile long datacount = 0;
	private PpListener ppListener;
	protected volatile boolean disabled = false;
	PpSimulation simulationData = null;
	// static String SIMULATION_DATA =
	// "/home/msc/development/git/yamcs/live/etc/simulation.xml";
	static String simulationDataPath = "";

	private Logger log = LoggerFactory.getLogger(this.getClass().getName());
	XtceDb xtceDb;

	Random rand = new Random();
	private ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(
			1);


	public SimulationPpProvider(String yamcsInstance, String name,
			LinkedHashMap args) throws ConfigurationException {
		xtceDb = XtceDbFactory.getInstance(yamcsInstance);
		SetSimulationData((String) args.get("simulationDataPath"));
		simulationData = LoadSimulationData(simulationDataPath);
	}

	public SimulationPpProvider() {
	}

	public void SetSimulationData(String xmlFilePath) {
		simulationDataPath = xmlFilePath;
		simulationData = LoadSimulationData(simulationDataPath);
	}

	@Override
	public String getLinkStatus() {
		if (disabled) {
			return "DISABLED";
		} else {
			return "OK";
		}
	}

	@Override
	public String getDetailedStatus() {
		return getLinkStatus();
	}

	@Override
	public void enable() {
		// reload simulation data and reset simulation parameters
		if (disabled) {
			simulationData = LoadSimulationData(simulationDataPath);
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
	public long getDataCount() {
		return datacount;
	}

	@Override
	public void setPpListener(PpListener ppListener) {
		this.ppListener = ppListener;

	}

	public boolean IsRunning() {
		return super.isRunning();
	}

	// ///
	// run()
	// Entry point to run the simulation
	//
	@Override
	public void run() {
		while (IsRunning()) {
			try {
				if (!disabled) {
					// run simulation
					ProcessSimulationData();

					// wait for the link to be disable / re-enabled
					WaitDisable();
				} else {
					Thread.sleep(500);
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

	// /
	// WaitDisable()
	// Wait for the user to restart the simulation
	//
	private void WaitDisable() {
		try {
			while (IsRunning() && !disabled) {
				Thread.sleep(500);
			}
		} catch (Exception e) {
		}
	}

	// ////
	// ProcessSimulationData()
	// Process the speceificed simulation scenario
	//
	public Date simulationStartTime;
	public Date simulationRealStartTime;
	public int simulationStepLengthMs;
	public long simutationStep = 0;
	public boolean loopSimulation = false;

	public void ProcessSimulationData() {

		// get simulation starting date
		if (simulationData.getStartDate() != null)
			simulationStartTime = simulationData.getStartDate()
					.toGregorianCalendar().getTime();
		else
			simulationStartTime = new Date();
		simulationRealStartTime = new Date();
		simutationStep = 0;

		// get length of a simulation step
		simulationStepLengthMs = simulationData.getStepLengthMs();

		// get loop status
		loopSimulation = simulationData.isLoop() != null
				&& simulationData.isLoop();

		// process each sequence
		do {
			List<PpSimulation.ParameterSequence> pss = simulationData
					.getParameterSequence();
			for (ParameterSequence ps : pss) {
				ProcessParameterSequence(ps);
			}
			if (!IsRunning() || disabled) {
				break;
			}
		} while (loopSimulation);
	}

	// ////
	// ProcessParameterSequence()
	// Process a sequence of the simulation scenario
	//
	private void ProcessParameterSequence(ParameterSequence ps) {

		int repeatCount = 0;
		int maxRepeat = ps.getRepeat() != null ? ps.getRepeat() : 1;
		boolean loopSequence = ps.isLoop() != null && ps.isLoop();

		// repeat the sequence as specified
		while (loopSequence || repeatCount++ < maxRepeat) {

			if (!IsRunning() || disabled)
				break;

			// process step offset
			int stepOffset = ps.getStepOffset() == null ? 0 : ps
					.getStepOffset();
			ProcessVoidStep(stepOffset);
			simutationStep += stepOffset;

			// initialize step count for this sequence
			List<ParameterSequence.Parameter> parameters = ps.getParameter();
			if(parameters.size() == 0)
				return;
			int lastSequenceStep = parameters.get(parameters.size() - 1)
					.getAquisitionStep();

			int currentParameterIndex = 0;

			// process each step of the sequence
			for (int sequenceStep = 0; sequenceStep <= lastSequenceStep; sequenceStep++) {

				if (!IsRunning() || disabled)
					break;

				ParameterSequence.Parameter currentParameter = parameters
						.get(currentParameterIndex);

				// case where there is no parameter to send at this step
				if (currentParameter.getAquisitionStep() != sequenceStep) {
					assert (currentParameter.getAquisitionStep() > sequenceStep);
					ProcessVoidStep(1);
				} else {
					// there is at least 1 parameter to send at this step
					List<ParameterSequence.Parameter> stepParameters = new ArrayList<ParameterSequence.Parameter>();
					while (currentParameter.getAquisitionStep() == sequenceStep) {
						stepParameters.add(currentParameter);
						currentParameterIndex++;
						// add next parameter if available
						if (currentParameterIndex < parameters.size())
							currentParameter = parameters
									.get(currentParameterIndex);
						else
							break;
					}
					ProcessParameters(stepParameters);
					stepParameters.clear();
				}
				simutationStep++;
			}
		}
	}

	// ///
	// ProcessParameters()
	// Create a specified parameter and insert it in the Yamcs PP Listener
	//
	private void ProcessParameters(
			List<ParameterSequence.Parameter> stepParameters) {

		String groupName = "simulation";

		List<ParameterValue> pvs = new LinkedList<ParameterValue>();
		for (ParameterSequence.Parameter sParameter : stepParameters) {

			if (!IsRunning() || disabled)
				break;

			// compute value
			float value;
			if (sParameter.getValueType().equals("random"))
				value = rand.nextFloat();
			else
				value = sParameter.getValue().floatValue();

			// create generationTime and acquisitionTime
			long acquisitionTime = simulationStartTime.getTime()
					+ simutationStep * simulationStepLengthMs;
			long generationTime = acquisitionTime
					- (sParameter.getAquisitionStep() - sParameter
							.getGenerationStep()) * simulationStepLengthMs;

			// convert time to 'instant'
			acquisitionTime = TimeEncoding.fromUnixTime(acquisitionTime);
			generationTime = TimeEncoding.fromUnixTime(generationTime);

			// get monitoring result
			String monitoringResult = sParameter.getMonitoringResult();

			ParameterValue pv = CreatePv(sParameter.getSpaceSystem(),
					sParameter.getParaName(), generationTime, acquisitionTime,
					value, monitoringResult);
			if (pv != null)
				pvs.add(pv);
		}

		datacount += stepParameters.size();
		ppListener.updatePps(new Date().getTime(), groupName, (int) datacount,
				pvs);


		Long nextStepDate = simulationRealStartTime.getTime() + simulationStepLengthMs * simutationStep;
		Long delayBeforeNextStep = nextStepDate - new Date().getTime();
		try {
            if(delayBeforeNextStep > 0)
			    Thread.sleep(delayBeforeNextStep);
		} catch (Exception e) {
			log.error("", e);
		}
	}

	// //
	// ProcessVoidStep()
	// Used when no parameters need to be inserted at a given step of the
	// simulation scenario
	private void ProcessVoidStep(int nbSteps) {
		try {
			log.trace("Processing " + nbSteps + " void steps");
			Thread.sleep(simulationStepLengthMs * nbSteps);
		} catch (InterruptedException e) {
			log.error(e.toString());
		}
	}

	// ///
	// LoadSimulationData()
	// Load data from an XML file
	//
	public PpSimulation LoadSimulationData(String fileName) {
		try {
			final JAXBContext jc = JAXBContext.newInstance(PpSimulation.class);
			final Unmarshaller unmarshaller = jc.createUnmarshaller();

			final PpSimulation ppSimulation = (PpSimulation) unmarshaller
					.unmarshal(new FileReader(fileName));
			return ppSimulation;

		} catch (Exception e) {
			log.error("Unable to load Simulation Data. Check the XML file is correct. Details:\n"
					+ e.toString());
			throw new ConfigurationException("Unable to load Simulation Data. Check the XML file is correct. Details:\n"+ e.toString());
		}
	}

	// ///
	// CreatePv()
	//
	private ParameterValue CreatePv(String spaceSystem, String paramName,
			long generationTime, long acquisitionTime, float value,
			String monitoringResult) {
		// create parameter definition
		String parameterName = spaceSystem + paramName;
		Parameter param = null;
		if (xtceDb != null) {
			param = xtceDb.getParameter(parameterName);
			if (param == null) {
				log.warn("Unable to get parameter " + parameterName
						+ " from xtceDb.");
				param = new Parameter(parameterName);
			}
		} else {
			param = new Parameter(parameterName);
		}

		// set float type by default
		ParameterType ptype = new FloatParameterType(paramName);
		param.setParameterType(ptype);

		// create parameter value
		ParameterValue pv = new ParameterValue(param);
		pv.setFloatValue((float) value);

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
			} else
				pv.setMonitoringResult(MonitoringResult.DISABLED);
		    if (rangeCondition != null)
		        pv.setRangeCondition(RangeCondition.valueOf(rangeCondition));
		} catch (Exception e) {
			log.error("Unable to set the specified monitoring result (\""
					+ monitoringResult
					+ "\". Please check that the value is one of the Enum MonitoringResult (DISABLED, IN_LIMITS, WATCH, WATCH_LOW, WATCH_HIGH, WARNING, WARNING_LOW, WARNING_HIGH, DISTRESS, DISTRESS_LOW, DISTRESS_HIGH, CRITICAL, CRITICAL_LOW, CRITICAL_HIGH, SEVERE, SEVERE_LOW, SEVERE_HIGH)");
		}

		// set generation and acquisition time
		pv.setGenerationTime(generationTime);
		pv.setAcquisitionTime(acquisitionTime);
		pv.setAcquisitionStatus(AcquisitionStatus.ACQUIRED);

		return pv;
	}

}

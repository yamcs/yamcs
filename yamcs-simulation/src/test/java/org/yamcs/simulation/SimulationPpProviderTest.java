package org.yamcs.simulation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Pvalue;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Pvalue.RangeCondition;
import org.yamcs.simulation.generated.ObjectFactory;
import org.yamcs.simulation.generated.PpSimulation;
import org.yamcs.tctm.PpListener;
import org.yamcs.utils.TimeEncoding;

public class SimulationPpProviderTest {

	private static final String DATA_SCENARIO1 = "src/test/resources/simulation_data.xml";
	private static final String DATA_SCENARIO2 = "src/test/resources/simulation_data2.xml";
	private static final String DATA_SCENARIO_DATE = "src/test/resources/simulation_data_date.xml";

	@BeforeClass
	public static void beforeClass() {
	    TimeEncoding.setUp();
	}
	@Test
	public void LoadSimulationData_loadOk() {

		// Arrange
		String fileName = DATA_SCENARIO1;
		SimulationPpProvider target = new SimulationPpProvider();

		// Act
		PpSimulation ppSimulation = target.LoadSimulationData(fileName);

		// Assert
		assertTrue(ppSimulation != null);

	}

	@Test
	public void CreateXml() throws JAXBException {
		try {
			// ARRANGE
			File file = new File("src/test/resources/genTest.xml");
			JAXBContext jaxbContext = JAXBContext
					.newInstance(PpSimulation.class);
			Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
			jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

			// ACT
			ObjectFactory of = new ObjectFactory();
			PpSimulation ppSimulation = of.createPpSimulation();
			PpSimulation.ParameterSequence paramSequence1 = new PpSimulation.ParameterSequence();
			PpSimulation.ParameterSequence.Parameter parameter1 = new PpSimulation.ParameterSequence.Parameter();
			parameter1.setSpaceSystem("APM");
			parameter1.setAquisitionStep(1);
			parameter1.setGenerationStep(2);
			parameter1.setParaName("SOLAR_toto");
			paramSequence1.getParameter().add(parameter1);
			ppSimulation.getParameterSequence().add(paramSequence1);
			jaxbMarshaller.marshal(ppSimulation, file);
			jaxbMarshaller.marshal(ppSimulation, System.out);
		} catch (Exception e) {
			// ASSERT
			assertFalse(e.toString(), true);
		}
	}

	@Test
	public void ProcessSimulationData_Scenario1() {
		// Arrange
		SimulationPpProvider target = new SimulationPpProvider() {
			@Override
			public boolean IsRunning() {
				return true;
			}
		};
		target.SetSimulationData(DATA_SCENARIO1);
		FakePpListener ppListener = new FakePpListener();
		target.setPpListener(ppListener);
		target.enable();

		// Act
		target.ProcessSimulationData();

		// Assert
		assertTrue(ppListener.receivedValue.size() == 21);
		// check values
		assertTrue(ppListener.receivedValue.get(0).getEngValue()
				.getFloatValue() == (float) 1.1);
		assertTrue(ppListener.receivedValue.get(1).getEngValue()
				.getFloatValue() == (float) 1.2);
		assertTrue(ppListener.receivedValue.get(2).getEngValue()
				.getFloatValue() == (float) 2.1);
		assertTrue(ppListener.receivedValue.get(3).getEngValue()
				.getFloatValue() == (float) 3.5);
		assertTrue(ppListener.receivedValue.get(4).getEngValue()
				.getFloatValue() == (float) 1.1);
		assertTrue(ppListener.receivedValue.get(5).getEngValue()
				.getFloatValue() == (float) 1.2);
		assertTrue(ppListener.receivedValue.get(6).getEngValue()
				.getFloatValue() == (float) 2.1);
		assertTrue(ppListener.receivedValue.get(7).getEngValue()
				.getFloatValue() == (float) 3.5);
		// check generation time
		assertTrue(ppListener.receivedValue.get(0).getGenerationTime() == ppListener.receivedValue
				.get(1).getGenerationTime());
		assertTrue(ppListener.receivedValue.get(4).getGenerationTime() == ppListener.receivedValue
				.get(5).getGenerationTime());
		assertTrue(ppListener.receivedValue.get(2).getGenerationTime()
				- ppListener.receivedValue.get(1).getGenerationTime() == 2);
		assertTrue(ppListener.receivedValue.get(2).getGenerationTime()
				- ppListener.receivedValue.get(1).getGenerationTime() == 2);
		assertTrue(ppListener.receivedValue.get(18).getGenerationTime()
				- ppListener.receivedValue.get(1).getGenerationTime() == 30);
		assertTrue(ppListener.receivedValue.get(20).getGenerationTime()
				- ppListener.receivedValue.get(1).getGenerationTime() == 42);

		// check monitoring result
		assertEquals(MonitoringResult.WARNING, ppListener.receivedValue.get(20).getMonitoringResult());
		assertEquals(RangeCondition.HIGH, ppListener.receivedValue.get(20).getRangeCondition());
	}

	@Test
	public void ProcessSimulationData_Scenario2() {

		// Arrange
		SimulationPpProvider target = new SimulationPpProvider() {
			@Override
			public boolean IsRunning() {
				return true;
			}
		};
		target.SetSimulationData(DATA_SCENARIO2);
		FakePpListener ppListener = new FakePpListener();
		target.setPpListener(ppListener);
		target.enable();

		// Act
		target.ProcessSimulationData();

		// Assert
		assertTrue(ppListener.receivedValue.size() == 52);
	}

	@Test
	public void ProcessSimulationData_Date() {

		// Arrange
		SimulationPpProvider target = new SimulationPpProvider() {
			@Override
			public boolean IsRunning() {
				return true;
			}
		};
		target.SetSimulationData(DATA_SCENARIO_DATE);
		FakePpListener ppListener = new FakePpListener();
		target.setPpListener(ppListener);
		target.enable();

		// Act
		long dateStart = TimeEncoding.getWallclockTime();
		target.ProcessSimulationData();
		long dateEnd = TimeEncoding.getWallclockTime();

		// Assert
		assertTrue(ppListener.receivedValue.size() == 2);
		assertTrue(ppListener.receivedValue.get(1).getGenerationTime() == ppListener.receivedValue.get(1).getAcquisitionTime() - 1500);

		long elapsedTimeGen0 = ppListener.receivedValue.get(0).getGenerationTime() - dateStart;
		long elapsedTimeAcqu0 = ppListener.receivedValue.get(0).getAcquisitionTime() - dateStart;
		
		
		assertTrue(0 <= elapsedTimeGen0 && elapsedTimeGen0 < 200);
		assertTrue(1300 < elapsedTimeAcqu0 && elapsedTimeAcqu0 < 1600);

		long elapsedTimeGen1 = ppListener.receivedValue.get(1).getGenerationTime() - dateStart;
		long elapsedTimeAcqu1 = ppListener.receivedValue.get(1).getAcquisitionTime() - dateStart;
		
		assertTrue(1900 < elapsedTimeGen1 && elapsedTimeGen1 < 2100);
		assertTrue(3400 < elapsedTimeAcqu1 && elapsedTimeAcqu1 < 3600);

		assertTrue((dateEnd - dateStart) >= elapsedTimeAcqu1);
	}
	
	

	class FakePpListener implements PpListener {
		public List<ParameterValue> receivedValue;

		public FakePpListener() {
			receivedValue = new ArrayList<ParameterValue>();
		}

		@Override
		public void updatePps(long gentime, String group, int seqNum,
				Collection<ParameterValue> params) {
			receivedValue.addAll(params);
		}

        @Override
        public void updateParams(long gentime, String group, int seqNum, Collection<Pvalue.ParameterValue> params) {
        }

		@Override
        public String toString() {
			String result = "";
			long firstGenerationTime = 0;
			for (ParameterValue pv : receivedValue) {
				if (firstGenerationTime == 0)
					firstGenerationTime = pv.getGenerationTime();
				result += "(" + pv.getEngValue().getFloatValue() + ", "
						+ (pv.getGenerationTime() - firstGenerationTime) + ") ";
			}
			return result;
		}

	}

}

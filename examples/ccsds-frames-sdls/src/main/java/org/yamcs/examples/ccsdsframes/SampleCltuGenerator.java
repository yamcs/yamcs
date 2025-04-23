package org.yamcs.examples.ccsdsframes;

import org.yamcs.YConfiguration;
import org.yamcs.tctm.ccsds.error.CltuGenerator;

/**
 * Implements a CLTU generator that pads the CLTU to a multiple of a specified
 * size. Note that the result is not a valid CCSDS TC CLTU. This processing is for
 * example purposes only.
 * <p>
 * The randomization option is ignored.
 */
public class SampleCltuGenerator extends CltuGenerator {

	private int frameMultiple;

	public SampleCltuGenerator() {
		this(YConfiguration.emptyConfig());
	}

	public SampleCltuGenerator(YConfiguration config) {
		super(null, null);
		frameMultiple = config.getInt("frameMultiple", 128);
	}

	@Override
        public byte[] makeCltu(byte[] data, boolean randomize) {
		int newLength = frameMultiple * ((data.length + frameMultiple - 1) / frameMultiple);
		byte[] result = new byte[newLength];
		System.arraycopy(data, 0, result, 0, data.length);
		return result;
	}

}

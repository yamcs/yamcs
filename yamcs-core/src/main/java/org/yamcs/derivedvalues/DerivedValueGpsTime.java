package org.yamcs.derivedvalues;


import org.yamcs.MdbDerivedValue;

import org.yamcs.utils.TimeEncoding;


public class DerivedValueGpsTime extends MdbDerivedValue {

	int packetIdAllowed;

	public DerivedValueGpsTime( String opsname ) {
		super(opsname);
	}

	public DerivedValueGpsTime( String opsname, String coarse_name ) {
		super(opsname, coarse_name);
		packetIdAllowed = 0;
	}

	public DerivedValueGpsTime( String opsname, String coarse_name, String fine_name ) {
		super(opsname, coarse_name, fine_name);
		packetIdAllowed = 0;
	}

	public DerivedValueGpsTime( String opsname, String coarse_name, String fine_name, String packetId_name, int packetIdAllowed ) {
		super(opsname, coarse_name, fine_name, packetId_name);
		this.packetIdAllowed = packetIdAllowed;
	}

	static public String getGPSTimeString( int coarseTime, byte fineTime ) {
		long instant=TimeEncoding.fromGpsCcsdsTime(coarseTime, fineTime);
		return TimeEncoding.toCombinedFormat(instant);
		
	}

	@Override
    public void updateValue() {
		if ( packetIdAllowed != 0 ) {
			int packetId = args[2].getEngValue().getUint32Value();
			if ( packetId != packetIdAllowed ) {
				updated = false;
				return;
			}
		}
		int coarseTime = args[0].getEngValue().getUint32Value();
		byte fineTime = (byte)(args.length > 1 ? args[1].getEngValue().getUint32Value() : 0);
		setStringValue(getGPSTimeString(coarseTime, fineTime));
		updated=true;
	}
}

package org.yamcs.simulator;

import java.net.*;
import java.io.*;
import java.util.Vector;

abstract class CSVHandler extends Thread
{
	abstract int getNumberOfEntries();
	abstract double getTimestampAtIndex(int index);
	abstract void processElement(int index);

	public void run()
	{
		try {

			if (getNumberOfEntries() > 0) {
				// have any entries at all

				double lastTimestamp = 0;

				for (int currentEntry = 0; ; ) {

					if (currentEntry >= getNumberOfEntries()) {
						currentEntry = 0;
						lastTimestamp = 0;
						Thread.sleep(1000);
						continue;
					}

					double timestamp = getTimestampAtIndex(currentEntry);
					long sleepMillis = (long)((timestamp - lastTimestamp)*1000);
					lastTimestamp = timestamp;

					processElement(currentEntry++);

					Thread.sleep(sleepMillis);
				}

			}

		} catch (InterruptedException e) {
//			System.out.println(e);
		}

		System.out.println("CSVHandler thread ended");
	}
}

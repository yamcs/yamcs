Time Correlation Service
========================

Correlates (synchronizes) time between a free running on-board clock and ground.

It receives samples ``(obt, ert)`` where:

* ``obt`` - onboard time considered to be a counter running based on an on-board computer clock.
* ``ert`` - Earth Reception Time - the time when the signal has been received on the ground - it is typically provided by a ground station.
 
It takes into account the parameters:

* ``onboardDelay``: Covers any delay happening on-board (sampling time, radiation time)
* ``tof``: Time of flight: the time it takes for the signal to reach the ground. This can be fixed or computed by dynamically interpolating from data provided by a flight dynamics system.

Assuming that:
 ob_time = ert - (tof + onboardDelay)

the service will compute ``m`` = gradient and ``c`` = offset such that:
 ob_time = m * obt + c
 
Using the computed gradient and offset, the free running obt can be correlated  to the ground time. The process has to be repeated each time the on-board computer resets to 0 (this typically happens when the computer reboots). This method can compensate for a linear drift of the on-board clock.

The determination of the gradient and offset is done using the least squares method.
 
The number of samples used for computing the coefficients is configurable and has to be minimum 2.

The ground time ``ert`` being provided by a ground station (and not by Yamcs), is considered to be accurate enough for the required purpose.

*Note about accuracy*: the main usage of this service is to timestamp the telemetry received from the on-board system. Yamcs keeps such timestamps at milliseconds resolution. However the service keeps internally the time at picosecond resolution so theoretically it can be used to achieve better than millisecond accuracy. In practice this is not so easy: it requires an accurate on-board clock, an accurate ground-station clock, a good time of flight estimation taking into account various effects (ionospheric, tropospheric delays, etc). All the dynamic delays have to be incorporated into the time of flight estimation.
  
 
Accuracy and validity
---------------------

Once the coefficients have been calculated, for each new sample received a deviation is calculated as the delta between the OBT computed using the coefficients and the OBT which is part of the sample (after adjusting for delays). The deviation is compared with the accuracy and validity parameters:
 
* If the deviation is greater than ``accuracy`` but smaller than ``validity``, then a recalculation of the coefficients is performed based on the last received samples.
* If the deviation is greater than ``validity`` then the coefficients are declared as invalid and all the samples from the buffer except the last one are dropped. The time returned by ``getTime()`` will be invalid until the required number of new samples is received and the next recalculation is performed.

 
Verify Only Mode
----------------

If the on-board clock is synchronized via a different method, this service can still be used to verify the synchronization.
 
  
The method ``verify(TmPacket pkt)`` will check the difference between the packet generation time and the expected generation time (using ``ert - delays``) and in case the difference is greater than the validity, the packet will be changed with the local computed time and the flag {@link TmPacket#setLocalGenTime()} will also be set.


Usage
-----
  
To use this service the preprocessor (or other mission specific service) will adds samples using the ``addSample(long, Instant)`` each time it receives a correlation sample from on-board. How the on-board system will send such samples is mission specific (for  example the PUS protocol defines some specific time packets for this purpose).

The preprocessor can then use the method ``getTime(long obt)`` to get the time corresponding to the ``obt`` or call ``timestamp(long obt, TmPacket pkt)`` to timestamp the packet. 
The second method will timestamp the packet with a time derived from the ``ert`` if the service is not synchronized. A corresponding flag will be set on the packet so it can be distinguished in the archive.


Time of flight estimation
-------------------------

As explained above, the correlation process requires the estimation of the time of flight between the spacecraft and the ground station. This can be configured to a static value or dynamically computed based on the user supplied polynomials on time intervals. The :apidoc:`HTTP API <time-correlation/add-time-of-flight-intervals/>` can be used to add the intervals and corresponding polynomials. 


Class Name
----------

:javadoc:`org.yamcs.time.TimeCorrelationService`


Configuration
-------------

This service is defined in :file:`etc/yamcs.{instance}.yaml`. Example:

.. code-block:: yaml

  services:
      - class: org.yamcs.time.TimeCorrelationService
        name: tco0
        args:            
            onboardDelay: 0.0
            useTofEstimator: false
            defaultTof: 0.0
            accuracy: 0.1
            validity: 0.2
            numSamples: 3            


Configuration Options
---------------------

onboardDelay  (double)
    The on-board delay in seconds. This is a fixed value estimating the time it takes for the time packet to leave the spacecraft. The default value is 0 seconds.

useTofEstimator (boolean)
    Flag to enable or disable time of flight estimator service. The default value is false. Enable time of flight estimator service when it is required to dynamically compute the time of flight.

defaultTof (double)
    The default time of flight in seconds. This value is used if the tof estimator does not return a value because no interval has been configured.

accuracy (double)
    The accuracy in seconds. See above for an explanation on how this value is used. Default: 0.1 (100 milliseconds). 
 
validity (double)
    The validity in seconds. See above for an explanation on how this value is used. Default: 0.2 (200 milliseconds). 

numSamples (integer)
    How many samples to collect before computing the correlation coefficients. It has to be minimum 2. Default: 3.

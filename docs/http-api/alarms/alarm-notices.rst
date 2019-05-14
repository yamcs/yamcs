Alarm Notices
=============

Subscribe to alarm notices:

.. code-block:: json

    [ 1, 1, 1, { "alarms": "subscribe" } ]


.. rubric:: Response

You first get an empty reply message confirming the positive receipt of your request:

.. code-block:: json

    [ 1, 2, 789 ]

Further messages are marked as type ``ALARM_DATA``. Directly after you subscribe, you will receive get the active set of alarms -- if applicable.

.. code-block:: json

    [1,4,0,{"dt":"ALARM_DATA","data":{"id":0,"type":1,"triggerValue":{"id":{"name":"/YSS/SIMULATOR/O2TankTemp"},"rawValue":{"type":2,"uint32Value":227},"engValue":{"type":2,"uint32Value":227},"acquisitionTime":1440576556724,"generationTime":1440576539714,"acquisitionStatus":0,"processingStatus":true,"monitoringResult":21,"acquisitionTimeUTC":"2015-08-26T08:08:40.724Z","generationTimeUTC":"2015-08-26T08:08:23.714Z","watchLow":10.0,"watchHigh":12.0,"warningLow":30.0,"warningHigh":32.0,"distressLow":40.0,"distressHigh":42.0,"criticalLow":60.0,"criticalHigh":62.0,"severeLow":80.0,"severeHigh":82.0,"expirationTime":1440576558224,"expirationTimeUTC":"2015-08-26T08:08:42.224Z"},"mostSevereValue":{"id":{"name":"/YSS/SIMULATOR/O2TankTemp"},"rawValue":{"type":2,"uint32Value":227},"engValue":{"type":2,"uint32Value":227},"acquisitionTime":1440576556724,"generationTime":1440576539714,"acquisitionStatus":0,"processingStatus":true,"monitoringResult":21,"acquisitionTimeUTC":"2015-08-26T08:08:40.724Z","generationTimeUTC":"2015-08-26T08:08:23.714Z","watchLow":10.0,"watchHigh":12.0,"warningLow":30.0,"warningHigh":32.0,"distressLow":40.0,"distressHigh":42.0,"criticalLow":60.0,"criticalHigh":62.0,"severeLow":80.0,"severeHigh":82.0,"expirationTime":1440576558224,"expirationTimeUTC":"2015-08-26T08:08:42.224Z"},"currentValue":{"id":{"name":"/YSS/SIMULATOR/O2TankTemp"},"rawValue":{"type":2,"uint32Value":258},"engValue":{"type":2,"uint32Value":258},"acquisitionTime":1440576955780,"generationTime":1440576938777,"acquisitionStatus":0,"processingStatus":true,"monitoringResult":21,"acquisitionTimeUTC":"2015-08-26T08:15:19.780Z","generationTimeUTC":"2015-08-26T08:15:02.777Z","watchLow":10.0,"watchHigh":12.0,"warningLow":30.0,"warningHigh":32.0,"distressLow":40.0,"distressHigh":42.0,"criticalLow":60.0,"criticalHigh":62.0,"severeLow":80.0,"severeHigh":82.0,"expirationTime":1440576957280,"expirationTimeUTC":"2015-08-26T08:15:21.280Z"},"violations":65}}]
    [1,4,1,{"dt":"ALARM_DATA","data":{"id":0,"type":4,"triggerValue":{"id":{"name":"/YSS/SIMULATOR/O2TankTemp"},"rawValue":{"type":2,"uint32Value":227},"engValue":{"type":2,"uint32Value":227},"acquisitionTime":1440576556724,"generationTime":1440576539714,"acquisitionStatus":0,"processingStatus":true,"monitoringResult":21,"acquisitionTimeUTC":"2015-08-26T08:08:40.724Z","generationTimeUTC":"2015-08-26T08:08:23.714Z","watchLow":10.0,"watchHigh":12.0,"warningLow":30.0,"warningHigh":32.0,"distressLow":40.0,"distressHigh":42.0,"criticalLow":60.0,"criticalHigh":62.0,"severeLow":80.0,"severeHigh":82.0,"expirationTime":1440576558224,"expirationTimeUTC":"2015-08-26T08:08:42.224Z"},"mostSevereValue":{"id":{"name":"/YSS/SIMULATOR/O2TankTemp"},"rawValue":{"type":2,"uint32Value":227},"engValue":{"type":2,"uint32Value":227},"acquisitionTime":1440576556724,"generationTime":1440576539714,"acquisitionStatus":0,"processingStatus":true,"monitoringResult":21,"acquisitionTimeUTC":"2015-08-26T08:08:40.724Z","generationTimeUTC":"2015-08-26T08:08:23.714Z","watchLow":10.0,"watchHigh":12.0,"warningLow":30.0,"warningHigh":32.0,"distressLow":40.0,"distressHigh":42.0,"criticalLow":60.0,"criticalHigh":62.0,"severeLow":80.0,"severeHigh":82.0,"expirationTime":1440576558224,"expirationTimeUTC":"2015-08-26T08:08:42.224Z"},"currentValue":{"id":{"name":"/YSS/SIMULATOR/O2TankTemp"},"rawValue":{"type":2,"uint32Value":280},"engValue":{"type":2,"uint32Value":280},"acquisitionTime":1440576962013,"generationTime":1440576945011,"acquisitionStatus":0,"processingStatus":true,"monitoringResult":21,"acquisitionTimeUTC":"2015-08-26T08:15:26.013Z","generationTimeUTC":"2015-08-26T08:15:09.011Z","watchLow":10.0,"watchHigh":12.0,"warningLow":30.0,"warningHigh":32.0,"distressLow":40.0,"distressHigh":42.0,"criticalLow":60.0,"criticalHigh":62.0,"severeLow":80.0,"severeHigh":82.0,"expirationTime":1440576963513,"expirationTimeUTC":"2015-08-26T08:15:27.513Z"},"violations":66}}]

Notice how we are first getting an alarm of type ``ACTIVE`` that triggered somewhere before we connected, and only then a further update on that alarm of type ``PVAL_UPDATED``.

.. rubric:: Unsubscribe

Unsubscribe from all further alarm updates:

.. code-block:: json

    [ 1, 1, 790, { "alarms": "unsubscribe" } ]

This will be confirmed with an empty reply message:

.. code-block:: json

    [ 1, 2, 790 ]

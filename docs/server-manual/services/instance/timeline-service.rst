Timeline Service
================

This services enables :doc:`Timeline <../../timeline/index>`-related functionalities.

The Yamcs Timeline provides a visual, chronological overview of mission events. It can also be used to schedule activities for future execution.


Class Name
----------

:javadoc:`org.yamcs.timeline.TimelineService`


Configuration
-------------

This service is defined in :file:`etc/yamcs.{instance}.yaml`. Example:

.. code-block:: yaml

    services:
      - class: org.yamcs.timeline.TimelineService

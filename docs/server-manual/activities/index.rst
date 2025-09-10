Activities
==========

Yamcs can run background activities whose execution state are tracked in the UI.

.. image:: _images/activities-page.png
    :alt: Activities Page
    :align: center

Yamcs includes a core set of activity types that cover command execution, stack execution, script execution and manual execution. Yamcs plugins may add more types.

To enable activities you must enable the Timeline service, see the :doc:`configuration reference <../services/instance/timeline-service>`.

.. note::
    In a future release we expect to transform activities as a core service of Yamcs, which will then exist independently from the Timeline service. At its core the activity service is only concerned with immediate execution, not with planning or scheduling.


.. rubric:: Status

An activity can assume one of these statuses:

* **RUNNING:** The activity is ongoing
* **CANCELLED:** The activity was cancelled
* **SUCCESSFUL:** The activity completed successfully
* **FAILED:** An error occurred while running the activity


.. rubric:: Relation to Timeline

When combining with the :doc:`../timeline/index`, you can plan activities for future execution:

.. image:: _images/planned-activities.png
    :alt: Planned Activities
    :align: center

Planned activities are submitted via the UI or API to the Timeline (saved as activity *items*). An activity scheduler monitors all activity items in the Timeline, and submits them to the activity service when due, for immediate execution.

.. toctree::
    :maxdepth: 1
    :caption: Table of Contents

    commands
    stacks
    scripts
    manual

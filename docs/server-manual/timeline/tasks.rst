Timeline Tasks
==============

Timeline Tasks are for keeping tracks of things that you need to yourself, outside of Yamcs.

Yamcs will remind you when the task was planned to start, and you can notify when it has stopped.

Tasks do not stop automatically. You are expected to stop them yourself by choosing one of the options :guilabel:`Set successful` or :guilabel:`Set failed`. In the latter case, you will be prompted to provide a reason for failure.


Yamcs UI
--------

To create a timeline task, go to :menuselection:`Timeline`, then select :menuselection:`Create task`.

You need to provide at least a **Title** and the **Planned start**.

The **Expected duration** field can be used to indicate how much time you think task will take. This information is used to determine the task's size on the Timeline chart, as well as to show a percentual progress indicator. If you leave the duration to zero, than the progress is considered indeterminate, and the Timeline chart will show a box of minimal size (a few pixels wide).

The **Start automatically** field indicates whether the task will automatically start at the planned start time. If unchecked it will instead go into a ``READY`` mode, waiting for the user to indicate that the task is actually underway.

Optionally you can associate tags with this task. Tags are free-form text fields. This information is used by :doc:`bands/item-band` to select which tasks are rendered: any task that has at least one tag that matches one of the band's tags will be displayed on that specific band.

If you don't associate any tags, the task will only be displayed on bands that do not have any tags defined.

.. note::
    Tasks do not follow all style preferences of the band (e.g. background color). Their appearance is controlled by the Yamcs UI and depends on their execution status.


Python Yamcs Client
-------------------

Run a task one minute from now:

.. code-block:: python

    from datetime import datetime, timedelta, timezone

    from yamcs.client import TimelineTask, YamcsClient

    client = YamcsClient("http://localhost:8090")
    timeline = client.get_timeline_client("simulator")

    now = datetime.now(tz=timezone.utc)

    item = TimelineTask(
        name="Start simulator",
        start=now + timedelta(minutes=1),
        duration=timedelta(minutes=5),  # Planned duration
    )
    timeline.save_item(item)

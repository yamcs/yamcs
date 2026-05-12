Dependencies
============

When planning a task or activity, you can have it start on two conditions:

* Start at an absolute time, OR.
* Start based on the specific condition of one or more other items (called predecessors).

The latter option allows to chain items together. Such dependencies cannot be defined through the Yamcs UI, only through scripting.

In the following example there are three items: a pass event and two activities. Only the pass event has a absolute start time. The two activities derive their start time based on their predecessor's condition.

.. code-block:: python

    from datetime import datetime, timedelta

    from yamcs.client import (
        OnStart,
        OnSuccess,
        ScriptActivity,
        StackActivity,
        TimelineActivity,
        TimelineEvent,
        YamcsClient,
    )

    client = YamcsClient("localhost:8090")
    timeline = client.get_timeline_client("simulator")

    # Mark a pass (The Predecessor)
    pass_event = TimelineEvent(
        name="Pass",
        start=datetime.now(),
        duration=timedelta(minutes=7),
    )

    # Define an activity A, triggered when the pass starts
    activity_a = TimelineActivity(
        name="Download data",
        start=OnStart(pass_event),
        activity=ScriptActivity(script="download_data.sh"),
    )

    # Define an activity B, triggered when the A was successful
    activity_b = TimelineActivity(
        name="Run commands",
        start=OnSuccess(activity_a),
        activity=StackActivity(bucket="stacks", stack="my_stack.ycs"),
    )

    # Save all to the Timeline
    timeline.save_item(pass_event)
    timeline.save_item(activity_a)
    timeline.save_item(activity_b)

For more on this topic see the `Python Yamcs Client <https://docs.yamcs.org/python-yamcs-client/timeline/>`_ documentation.

Timeline
========

This supports the timeline functionality in yamcs-web. Essentially the timeline in yamcs-web shows items on bands.

The following item types are supported:

events
    general items which do not have execution state: for example can store information about day/night, ground station coverage, milestones (e.g. launch, a deadline), other items covering large time intervals (e.g. mission phase).
activities
    used for items for which Yamcs keeps execution state. The following execution states are supported:  PLANNED, IN_PROGRESS, COMPLETED, ABORTED, FAILED. Activities can depend on each other meaning that one activity is started after a previous one has finished.

The activities are further subdivided in:

manual activities
    represent manual actions performed by an operator who also updates the execution state. The yamcs-web informs the operator when an activity is due.
automated activities
    execute things on the server. They can be automatically started when their time is due or can be started manually by the operator.

Items can be grouped into groups, and the groups themselves are timeline items. Groups composed only of activities are themselves activities (i.e. they have an execution state).

The start/stop of the timeline items can be specified as absolute dates or relative to another item. Typically (but not mandatory) the items which are part of a group will have the time specified as relative to the group start.

The service supports multiple data sources:

* rdb - is the internal RocksDB general purpose source for storing and retrieving items. This is supported by an automated activity executor.
* parameter - provides a timeline view of enumerated parameter ranges
* commands - provides a timeline view of the commands sent

Other mission specific sources (e.g. connection to a shift planner or to an external ground station scheduling system) can be implemented as plugins.
External sources do not need to support all features of the rdb source, for example they can be read-only or support only events (no activities).

TBD: the service supports crontab like functionality. 

Item templates can be placed into the crontab and they will be automatically instantiated at the set time. This allows for example automatically creating regular occurring activities.

The service does not support complex substantiations of item or activity groups (e.g. items that depend on each other) - if such functionality is desired, the user can script the creation as part of one activity.


Bands
-----

The bands are the horizontal areas covering a time interval and containing a set of items. The band type determines how the items are displayed. A band displays only items from a single source (or no source at all).

There are different types of bands:

* TIME_RULER displays time in user chosen timezone
* SPACER is used to insert a horizontal space in the view


Views
-----

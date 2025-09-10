Scheduling
==========

At various places, the Yamcs UI allows to schedule a background activity. For example you can run a command at a later time. This information is stored to the Timeline in the form of an item with an associated activity definition.

The Timeline service includes a scheduler that will find such planned items, and start corresponding activities at the appropriate time.

Rather than specifying an absolute start time, you may instead want to specify a dependency to another activity item.

Through API you can also express dependencies between activities.

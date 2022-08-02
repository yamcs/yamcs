Activity Service
================

The activity service is responsible for starting/stopping/controling activities. It works in conjuction with the timeline service whereas the timeline service serves as a storage(database) of activities and the activity server is in charge of executing them.

There are three types of activities, Manual Activities, Automated Activities and  Activities Groups.

Manual Activities
=================

These are activities which correspond with a human operator (or in any case an entity outside Yamcs) performs. They do not have a software executor; Yamcs only keeps track of execution status. Starting an activity means ackowledging that the activity has been started. Similarily, stopping it means acknowledging it has been stopped. The start/stop events may trigger however other dependent activities. 


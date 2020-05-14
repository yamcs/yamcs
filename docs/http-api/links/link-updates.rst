Link Updates
============

Subscribe to link updates on a :doc:`../websocket` connection using the topic ``links``. This will make your WebSocket connection receive instance-wide statistics on link activity.


.. rubric:: Request Schema (protobuf)
.. code-block:: proto

    message SubscribeLinksRequest {
      optional string instance = 1;
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message LinkEvent {
      enum Type {
        // A new link was registered. You also receive this event directly after you subscribe,
        //  for every link that is registered at that time.
        REGISTERED = 1;
        // A link was unregistered.
        UNREGISTERED = 2;
        // A link was updated in one of its attributes, for example the dataCount has increased,
        // or the status has changed.
        UPDATED = 3;
      }
      optional Type type = 1;
      optional LinkInfo linkInfo = 2;
    }

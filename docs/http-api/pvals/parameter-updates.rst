Parameter Updates
=================

Subscribe to parameter updates on a :doc:`../websocket` connection using the topic ``parameters``.

This topic supports client-streaming allowing you to alter an ongoing subscription as specified by the property ``action`` in request messages. Just make sure to specify the assigned ``call`` identifier in follow-up messages, otherwise Yamcs will assume that you are making a new call.


.. rubric:: Request Schema (protobuf)
.. code-block:: proto

    message SubscribeParametersRequest {
      enum Action {
        REPLACE = 0;
        ADD = 1;
        REMOVE = 2;
      }
      
      optional string instance = 1;
      optional string processor = 2;
      repeated NamedObjectId id = 3;
      
      // Send an error message if any parameter is invalid.
      // Default: false
      optional bool abortOnInvalid = 4;
    
      // Send parameter updates when parameters expire.
      // The update will have the same value and timestamp like
      // the preceding update, but with acquisition status set to
      // EXPIRED (instead of ACQUIRED)
      // Default: false
      optional bool updateOnExpiration = 5;
    
      // If available, send immediately the last cached value
      // of each subscribed parameter.
      // Default: true
      optional bool sendFromCache = 6;
      
      // How to interpret the submitted parameter ids. Default
      // is to replace an exising subscription with the newly
      // submitted list.
      optional Action action = 7;
    }

.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message SubscribeParametersData {
      // mapping between numeric and subscribed id
      map<uint32, NamedObjectId> mapping = 1;
      
      repeated NamedObjectId invalid = 2;
      
      repeated pvalue.ParameterValue values = 3;
    }

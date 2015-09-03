curl -XPOST http://localhost:8090/obcp/api/processor/replay6/?pretty -d '
  {
     "operation": 5,
     "replaySpeed" : {
          "type": 3,
           "param": 1000.0
      }
     
  }'

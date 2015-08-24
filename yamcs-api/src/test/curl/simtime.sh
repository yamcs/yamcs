curl -XPOST http://localhost:8090/obcp/api/simTime/set?pretty -d '
  {
    "time0UTC": "2018-04-01T00:00:30",
    "simElapsedTime": 0,
    "simSpeed": 3
  }'

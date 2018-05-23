curl -XPOST http://localhost:8090/api/processors/simulator/realtime/commands/YSS/SIMULATOR/SWITCH_VOLTAGE_OFF -d '{
  "sequenceNumber" : 1,
  "origin" : "nico",
  "assignment" : [ {
    "name": "voltage_num",
    "value": "3"
  } ]
}'

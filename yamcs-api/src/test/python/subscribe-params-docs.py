import websocket
ws = websocket.WebSocket()
wsaddr = "ws://localhost:8090/_websocket/simulator"
print "Connecting to "+wsaddr + "...",
ws.connect(wsaddr)
print "connected"

print " subcribing to parameters...",

ws.send('[1,1,3, {"parameter": "subscribe", "data": { "id" : [ \
           {"name": "/YSS/SIMULATOR/BatteryVoltage1"},\
           { "namespace": "MDB:OPS Name", "name": "SIMULATOR_BatteryVoltage2" }\
        ], "updateOnExpiration": true }}]')
print "subscribed"
while True:
    result =  ws.recv()
    print "Received '%s'" % result


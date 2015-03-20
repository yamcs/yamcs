import websocket
ws = websocket.WebSocket()
wsaddr = "ws://localhost:8090/simulator/_websocket"
print "Connecting to "+wsaddr + "...",
ws.connect(wsaddr)
print "connected"

print " subcribing to parameters...",

ws.send('[1,1,3, {"parameter": "subscribe", "data": { "list" : [ \
           {"name": "/SIMULATOR/SIMULATOR/Alpha"},\
           {"name": "/SIMULATOR/SIMULATOR/Heading"} \
        ]}}]')
print "subscribed"
while True:
    result =  ws.recv()
    print "Received '%s'" % result


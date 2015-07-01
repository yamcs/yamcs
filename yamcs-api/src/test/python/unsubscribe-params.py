import websocket
ws = websocket.WebSocket()
wsaddr = "ws://localhost:8090/simulator/_websocket"
print "Connecting to "+wsaddr + "...",
ws.connect(wsaddr)
print "connected"


print "current timeout", ws.gettimeout()
ws.settimeout(3)
print "new timeout", ws.gettimeout()


print "Receiving while not subscribed..."
try:
    result = ws.recv()
    print "Bad, received '%s'" % result    
except:
    print "Good, nothing received"
    pass



print "Subscribing parameters...",
ws.send('[1,1,3, {"parameter": "subscribe", "data": { "list" : [ \
           {"name": "/YSS/SIMULATOR/Alpha"},\
           {"name": "/YSS/SIMULATOR/Heading"}, \
           {"name": "SIMULATOR_PrimBusVoltage1", "namespace": "MDB:OPS Name"}\
        ]}}]')
print "subscribed"
i = 0
while i < 3:
    i = i+1
    result =  ws.recv()
    print "Received '%s'" % result


print "Unsubcribing parameters...",
ws.send('[1,1,3, {"parameter": "unsubscribe", "data": { "list" : [ \
           {"name": "/YSS/SIMULATOR/Alpha"},\
           {"name": "/YSS/SIMULATOR/Heading"}, \
           {"name": "SIMULATOR_PrimBusVoltage1", "namespace": "MDB:OPS Name"}\
        ]}}]')
print "unsubscribed, listening for data"

result =  ws.recv()
print "Ok, received '%s'" % result

i = 0
while i < 3:
    i = i+1
    try:
        result =  ws.recv()
    except:
        print "Good, nothing received"
        break
    print "Bad, Received '%s'" % result

ws.close();

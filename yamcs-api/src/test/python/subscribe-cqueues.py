import websocket
ws = websocket.WebSocket()
wsaddr = "ws://localhost:8090/_websocket/aces-sim"
print "Connecting to "+wsaddr + "...",
ws.connect(wsaddr)
print "connected"

print " subcribing to cqueue updates...",

ws.send('[1,1,3, {"cqueues": "subscribe"}]'); 
print "subscribed"
while True:
    result =  ws.recv()
    print "Received '%s'" % result


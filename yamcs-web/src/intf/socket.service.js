(function () {
    'use strict';

    /*
     * Web Socket Protocol Handling.
     * Each message sent is a list [1=protoversion, 1=request, request id, request object]
     * The message received back has to contain the same id and can be of type reply or exception
     */

    angular
        .module('yamcs.intf')
        .factory('socket', WebSocketClient);

    var PROTOCOL_VERSION = 1;
    var MESSAGE_TYPE_REQUEST = 1;
    var MESSAGE_TYPE_REPLY = 2;
    var MESSAGE_TYPE_EXCEPTION = 3;
    var MESSAGE_TYPE_DATA = 4;

    var requestSeqCount = -1;
    var wsproto = "ws";
    if (location.protocol == 'https:') {
        wsproto = "wss"
    }

    var dataCallbacks = {};
    var exceptionHandlers = {};
    var replyHandlers = {};

    var conn;

    /* @ngInject */
    function WebSocketClient($rootScope, $log, yamcsInstance) {
        $log.info('connecting websocket');

        conn = new WebSocket(wsproto+'://'+location.host+'/'+yamcsInstance+'/_websocket');
        conn.onmessage = function(msg) {
            var json = JSON.parse(msg.data);

            switch(json[1]) {
            case MESSAGE_TYPE_REPLY:
                //$log.info('get back reply', json);
                dispatchReply(json[2], json[3]);
                break;
            case MESSAGE_TYPE_EXCEPTION:
                //$log.error('get back exc', json);
                dispatchException(json[2], json[3]);
                break;
            case MESSAGE_TYPE_DATA:
                var data=json[3];
                dispatchData(data.dt, data.data);
                break;
            }
        };
        conn.onclose = function (event) {
            $log.error('websocket closed');
            dispatchData('close', event);
        };
        conn.onopen = function () {
            //$log.success('websocket open');
            dispatchData('open', null);

            $rootScope.$apply(function () {
                $rootScope.wsconnected = true;
            });
        };

        return {
            on: function (eventName, callback) {
                dataCallbacks[eventName] = dataCallbacks[eventName] || [];
                dataCallbacks[eventName].push(function (message) {
                    $rootScope.$apply(function () {
                        callback(message);
                    });
                });
                //return this;// chainable
            },
            emit: function (resource, request, data, replyHandler, exceptionHandler) {
                requestSeqCount++;
                if (replyHandler) {
                    replyHandlers[requestSeqCount] = replyHandler;
                }
                if (exceptionHandler) {
                    exceptionHandlers[requestSeqCount] = exceptionHandler;
                }
                var t = {};
                t[resource] = request;
                t['data'] = data;
                var payload = JSON.stringify([
                    PROTOCOL_VERSION,
                    MESSAGE_TYPE_REQUEST,
                    requestSeqCount,
                    t //{request: request, data: data}
                ]);
                conn.send(payload);
                //return this;
            },
            isConnected: function () {
                return conn.readyState == WebSocket.OPEN;
            }
        };
    }

    function dispatchException(requestId, data) {
        var h = exceptionHandlers[requestId];
        delete exceptionHandlers[requestId];
        delete replyHandlers[requestId];

        if (h === undefined) {
            console.log("Exception received for request id "+requestId+", and no handler available. Exception data: ", data);
            return;
        }
        h(data.et, data.msg);
    }

   function dispatchReply(requestId, data) {
       var h = replyHandlers[requestId];
       delete exceptionHandlers[requestId];
       delete replyHandlers[requestId];

       if (h === undefined)  return;
       h(data);
    }

    function dispatchData(eventName, message) {
        var chain = dataCallbacks[eventName];
        if (chain == undefined) return; // no callbacks for this event

        for (var i = 0; i < chain.length; i++) {
            chain[i](message);
        }
    }
})();

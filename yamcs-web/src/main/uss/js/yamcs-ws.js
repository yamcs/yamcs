var YamcsWebSocket = function(instance) {
    var PROTOCOL_VERSION=1;
    var MESSAGE_TYPE_REQUEST=1;
    var MESSAGE_TYPE_REPLY=2;
    var MESSAGE_TYPE_EXCEPTION=3;
    var MESSAGE_TYPE_DATA=4;
    
    var requestSeqCount=-1;    
    var wsproto = "ws";

    if(window.location.protocol=='https:') {
        wsproto = "wss"   
    }
    var conn = new WebSocket(wsproto+"://"+window.location.host+"/"+instance+"/_websocket");

    var dataCallbacks = {};
    var exceptionHandlers = {};
    var replyHandlers = {};
    var invalidDataBindings={};



    /*
     * Web Socket Protocol Handling.
     * Each message sent is a list [1=protoversion, 1=request, request id, request object]
     * The message received back has to contain the same id and can be of type reply or exception
     */

    conn.onmessage = function(msg) {
        var json = JSON.parse(msg.data)
	
        switch(json[1]) {
        case MESSAGE_TYPE_REPLY:
            dispatchReply(json[2], json[3]); 
            break;
        case MESSAGE_TYPE_EXCEPTION:
            dispatchException(json[2], json[3]); 
            break;
        case MESSAGE_TYPE_DATA:
            var data=json[3];
            dispatchData(data.dt, data.data);
            break;
        } 
    };

    conn.onclose = function(event){dispatchData('close',event)}
    conn.onopen = function(){dispatchData('open',null)}



    var sendRequest = function(requestName, requestData, replyHandler, exceptionHandler){
        requestSeqCount++;
        if(replyHandler) replyHandlers[requestSeqCount]=replyHandler;
        if(exceptionHandler) exceptionHandlers[requestSeqCount]=exceptionHandler;
        var payload = JSON.stringify([PROTOCOL_VERSION, MESSAGE_TYPE_REQUEST, requestSeqCount, {request:requestName, data: requestData}]);
        conn.send( payload ); // <= send JSON data to socket server
        return this;
    };
    
    var dispatchException = function(requestId, data) {
       var h = exceptionHandlers[requestId];
       delete exceptionHandlers[requestId];
       delete replyHandlers[requestId];

       if(h === undefined) {
           console.log("Exception received for request id "+requestId+", and no handler available. Exception data: ", data);
           return;
       } 
       h(data.et, data.msg);
   }

   var dispatchReply = function(requestId, data) {
       var h = replyHandlers[requestId];
       delete exceptionHandlers[requestId];
       delete replyHandlers[requestId];

       if(h === undefined)  return;
       h(data);
   }

    this.bindDataHandler = function(dataType, callback){
        dataCallbacks[dataType] = dataCallbacks[dataType] || [];
        dataCallbacks[dataType].push(callback);
        return this;// chainable
    };

    var dispatchData = function(dataType, message) {
        
        var chain = dataCallbacks[dataType];

        if(chain == undefined) return; // no callbacks for this event
     
        for(var i = 0; i < chain.length; i++){
            chain[i]( message )
        }
    }
    
    this.isConnected = function() {
        return conn.readyState == WebSocket.OPEN;
    }

/************** Parameter subscription (could be moved to its own 'class' *************/
    var subscribedParameters = {}; //this collects the databindings from all the open displays

    var addSubscribedParameter = function(paraname, p) {
       var dbs=subscribedParameters[paraname];
       if(!dbs) {
           dbs = [];
           subscribedParameters[paraname]=dbs;
       }
       var pdb = p.bindings;
       for(var j = 0; j < pdb.length; j++){
           dbs.push(pdb[j]);
       }
    }

    var doSubscribeParameters = function(parameters, addToList) {
        var paraList=[];
        for(var paraname in parameters) {
            var p=parameters[paraname];
            if (p.type=='ExternalDataSource') {
                if(addToList) addSubscribedParameter(paraname, p);
                paraList.push({name: paraname, namespace: 'MDB:OPS Name'});
            }
        }
        if(paraList.length==0) return;
        //console.log(paraList);
        var that=this;
        sendRequest("subscribe", {list: paraList}, null,
            function(exceptionType, exceptionMsg) { //exception handler
                if(exceptionType == 'InvalidIdentification') {
                    var invalidParams=exceptionMsg.list;
                    console.log('The following parameters are invalid: ', invalidParams);
                    for(var i=0; i<invalidParams.length; i++) {
                        var name=invalidParams[i].name;
                        var db=parameters[name];
                        delete parameters[name];
                        invalidDataBindings[name]=db;
                    }
                    console.log('retrying without them');
                    doSubscribeParameters(parameters, false, false);
            } else {
                console.log('got exception from subscription: ',exceptionType, exceptionMsg);
            }
        });
    }

    this.subscribeParameters = function(parameters) {
        doSubscribeParameters(parameters, true);
    }

    var doSubscribeComputations = function(parameters, addToList) {
        var compDefList=[];
        for(var paraname in parameters) {
            var p=parameters[paraname];
            if (p.type=='Computation') {
                var cdef={name: paraname, expression: p.expression, argument: [], language: 'jformula'};
                var args=p.args;
                for(var i=0;i<args.length;i++) {
                    var a=args[i];
                    cdef.argument.push({name: a.Opsname, namespace: 'MDB:OPS Name'});
                }
                compDefList.push(cdef);
                if(addToList) addSubscribedParameter(paraname, p);
            }
        }
        if(compDefList.length == 0) return;
        //console.log(paraList);
        //console.log('compDefList: ', compDefList);
        var that=this;
        sendRequest("subscribeComputations", {compDef: compDefList}, null,
            function(exceptionType, exceptionMsg) { //exception handler
                if(exceptionType == 'InvalidIdentification') {
                    var invalidParams=exceptionMsg.list;
                    console.log('The following parameters are invalid: ', invalidParams);
                    //remove computations that have as arguments the invalid parameters
                    for(var i=0; i<invalidParams.length; i++) {
                        var invParaName=invalidParams[i].name;
                        var cnameToRemove=null;
                        for(var paraname in parameters) {
                            var p=parameters[paraname];
                            if (p.type=='Computation') {
                                var args=p.args;
                                for(var k=0; k<args.length; k++) {
                                    var a=args[k];
                                    if(invParaName == a.Opsname) {
                                        cnameToRemove = paraname;
                                        break;
                                    }
                                }
                            if(cnameToRemove) break;
                            }
                        }
                        if(cnameToRemove) { 
                            var db=parameters[cnameToRemove];
                            delete parameters[cnameToRemove];
                            invalidDataBindings[cnameToRemove]=db;
                        }
                    }
                    console.log('retrying without them');
                    doSubscribeComputations(parameters, false);
                } else {
                    console.log('got exception from subscription: ',exceptionType, exceptionMsg);
                }
            });
    }

    this.subscribeComputations = function(parameters) {
        doSubscribeComputations(parameters,true)
    }

    this.bindDataHandler('PARAMETER', function(pdata){
        var params=pdata.parameter;
        for(var i=0; i<params.length; i++) {
            var p=params[i];
            var dbs = subscribedParameters[p.id.name];
            if(!dbs) {
                console.log("cannot find bindings for "+ p.id.name, subscribedParameters);
            }
            for(var j = 0; j < dbs.length; j++){
                USS.updateWidget(dbs[j], p);
            }
        }
    });  
};

(function () {
    'use strict';

    angular
        .module('yamcs.intf')
        .factory('tmService', tmService);

    // These should be renamed once the below todo is resolved
    var parameterListeners = [];
    var parameterListenersBySubscriptionId = {};
    var subscriptionId = 0;


    /*
     * TODO USS only
     * Collects databindings (from displays or not)
     */
    var subscribedParameters = {};
    var invalidDataBindings = {};

    function idsMatch(a, b) {
        var aHas = a.hasOwnProperty('namespace');
        var bHas = b.hasOwnProperty('namespace');
        if (aHas !== bHas) {
            return false;
        } else if (aHas) {
            return (a.name === b.name) && (a.namespace === b.namespace);
        } else {
            return (a.name === b.name);
        }
    }

    /* @ngInject */
    function tmService($http, $log, socket, yamcsInstance) {

        socket.on('PARAMETER', function(pdata) {
            var params = pdata['parameter'];
            for(var i=0; i<params.length; i++) {
                var p = params[i];

                for (var j = 0; j < parameterListeners.length; j++) {
                    var requestId = parameterListeners[j].id;
                    if (idsMatch(requestId, p.id)) {
                        parameterListeners[j]['callback'](p);
                    }
                }
            }
        });

        return {
            getParameter: getParameter,
            getParameterSamples: getParameterSamples,
            getParameterHistory: getParameterHistory,
            subscribeParameter: subscribeParameter,
            unsubscribeParameter: unsubscribeParameter,

            // TODO USS only. Should be unsplit and partially moved
            subscribeParameters: subscribeParameters,
            // TODO USS only. Should be unsplit and partially moved
            subscribeComputations: subscribeComputations,

            // TEMP should not be exposed, but need to understand 'bindings' first
            subscribedParameters: subscribedParameters
        };

        function getParameter(qname, options) {
            var targetUrl = '/api/processors/' + yamcsInstance + '/realtime/parameters' + qname;
            targetUrl += toQueryString(options);
            return $http.get(targetUrl).then(function (response) {
                return response.data;
            }).catch(function (e) {
                $log.error('XHR failed', e);
                throw messageToException(message);
            });
        }

        function getParameterSamples(qname, options) {
            var targetUrl = '/api/archive/' + yamcsInstance + '/parameters' + qname + '/samples';
            targetUrl += toQueryString(options);
            return $http.get(targetUrl).then(function (response) {
                    return response.data;
            }).catch(function (message) {
                $log.error('XHR failed', message);
                throw messageToException(message);
            });
        }

        function getParameterHistory(qname, options) {
            var targetUrl = '/api/archive/' + yamcsInstance + '/parameters' + qname;
            targetUrl += toQueryString(options);
            return $http.get(targetUrl).then(function (response) {
                return response.data;
            }).catch(function (message) {
                $log.error('XHR failed', message);
                throw messageToException(message);
            });
        }

        function subscribeParameter(id, callback) {
            //console.log('sub request for ', id);

            socket.on('open', function () {
                doSingleSubscribe(id, callback);
            });
            if (socket.isConnected()) {
                doSingleSubscribe(id, callback);
            }
        }

        function doSingleSubscribe(parameterId, callback) {
            var subId = ++subscriptionId;
            parameterListeners.push({id: parameterId, callback: callback});
            parameterListenersBySubscriptionId[subId] = callback;
            socket.emit('parameter', 'subscribe', {list:[parameterId]}, null, function (et, msg) {
                console.log('got exception from subscription: ', et, msg);
            });
        }

        function unsubscribeParameter(subscriptionId) {
            delete parameterListenersBySubscriptionId[subscriptionId];
        }

        function subscribeParameters(parameters, callback) {
            socket.on('open', function () {
                doSubscribeParameters(parameters, true);
            });
            if (socket.isConnected()) {
                doSubscribeParameters(parameters, true);
            }
        }

        function subscribeComputations(parameters, callback) {
            socket.on('open', function () {
                doSubscribeComputations(parameters, true);
            });
            if (socket.isConnected()) {
                doSubscribeComputations(parameters, true);
            }
        }

        function addSubscribedParameter(paraname, p) {
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

        function doSubscribeParameters(parameters, addToList) {
            var paraList=[];
            for(var paraname in parameters) {
                var p=parameters[paraname];
                if (p.type=='ExternalDataSource') {
                    if(addToList) addSubscribedParameter(paraname, p);
                    paraList.push({name: p.name, namespace: p.namespace});
                }
            }

            if(paraList.length==0) return;
            //console.log(paraList);
            var that=this;
            socket.emit('parameter', 'subscribe', {list: paraList}, null,
                function(exceptionType, exceptionMsg) { //exception handler
                    if (exceptionType == 'InvalidIdentification') {
                        var invalidParams=exceptionMsg.list;
                        console.log('The following parameters are invalid: ', invalidParams);
                        for(var i=0; i<invalidParams.length; i++) {
                            var name=invalidParams[i].name;
                            var db=parameters[name];
                            delete parameters[name];
                            invalidDataBindings[name]=db;
                        }
                        console.log('retrying without them');
                        doSubscribeParameters(parameters, false);
                } else {
                    console.log('got exception from subscription: ', exceptionType, exceptionMsg);
                }
            });
        }

        function doSubscribeComputations(parameters, addToList) {
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
            socket.emit('parameter', 'subscribeComputations', {compDef: compDefList}, null,
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
                        console.log('got exception from subscription: ', exceptionType, exceptionMsg);
                    }
                });
        }

        function toQueryString(options) {
            if (!options) return '?nolink';
            var result = '?nolink';
            for (var opt in options) {
                if (options.hasOwnProperty(opt)) {
                    result += '&' + opt + '=' + options[opt];
                }
            }
            return result;
        }

        function messageToException(message) {
            return {
                name: message['data']['type'],
                message: message['data']['msg']
            };
        }
    }
})();

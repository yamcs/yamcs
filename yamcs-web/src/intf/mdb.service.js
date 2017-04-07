(function () {
    'use strict';

    angular
        .module('yamcs.intf')
        .factory('mdbService', mdbService);

    /* @ngInject */
    function mdbService($http, $log, yamcsInstance) {

        return {
            getSummary: getSummary,

            listParameters: listParameters,
            getParameterInfo: getParameterInfo,

            listContainers: listContainers,
            getContainerInfo: getContainerInfo,

            listCommands: listCommands,
            getCommandInfo: getCommandInfo,

            listAlgorithms: listAlgorithms,
            getAlgorithmInfo: getAlgorithmInfo,
            
            sendCommand: sendCommand
        };

        function getSummary() {
            var targetUrl = '/api/mdb/' + yamcsInstance;

            return $http.get(targetUrl).then(function (response) {
                var mdb = response.data;
                mdb['flatSpaceSystems'] = []; // Flatten the nested structure, for better UI
                if (mdb.hasOwnProperty('spaceSystem')) {
                    for (var i = 0; i < mdb['spaceSystem'].length; i++) {
                        var ss = mdb['spaceSystem'][i];
                        if (ss['qualifiedName'] === '/yamcs') {
                            var compacted = compactSpaceSystem(ss);
                            mdb['flatSpaceSystems'].push(compacted);
                        } else {
                            var flattened = flattenSpaceSystem(ss);
                            for (var j = 0; j < flattened.length; j++) {
                                mdb['flatSpaceSystems'].push(flattened[j]);
                            }
                        }
                    }
                }
                return mdb;
            }).catch(function (message) {
                $log.error('XHR failed', message);
                throw messageToException(message);
            });
        }

        function listParameters(options) {
            var targetUrl = '/api/mdb/' + yamcsInstance + '/parameters';

            targetUrl += toQueryString(options);

            return $http.get(targetUrl).then(function (response) {
                return response.data['parameter'];
            }).catch(function (message) {
                $log.error('XHR failed', message);
                throw messageToException(message);
            });
        }

        function listContainers(options) {
            var targetUrl = '/api/mdb/' + yamcsInstance + '/containers';
            targetUrl += toQueryString(options);

            return $http.get(targetUrl).then(function (response) {
                return response.data['container'];
            }).catch(function (message) {
                $log.error('XHR failed', message);
                throw messageToException(message);
            });
        }

        function getContainerInfo(urlInfo,options){
            var targetUrl = '/api/mdb/'+yamcsInstance+'/containers/'+urlInfo;
            targetUrl+=toQueryString(options);
            return $http.get(targetUrl).then( function(response){
                return response;
            })
        }

        function listCommands(options) {
            var targetUrl = '/api/mdb/' + yamcsInstance + '/commands';
            targetUrl += toQueryString(options);

            return $http.get(targetUrl).then(function (response) {
                return response.data['command'];
            }).catch(function (message) {
                $log.error('XHR failed', message);
                throw messageToException(message);
            });
        }

        function getCommandInfo(urlname, options){
            var targetUrl = '/api/mdb/'+yamcsInstance+'/commands'+urlname;
            targetUrl += toQueryString(options);
            return $http.get(targetUrl).then(function (response){
                $log.log('DATA FOR COMMAND', response.data);
                return response.data;
            }).catch(function(message){
                $log.error('XHR failed', message);
                throw messageToException(message);
            });
        }

        function listAlgorithms(options) {
            var targetUrl = '/api/mdb/' + yamcsInstance + '/algorithms';
            targetUrl += toQueryString(options);

            return $http.get(targetUrl).then(function (response) {
                return response.data['algorithm'];
            }).catch(function (message) {
                $log.error('XHR failed', message);
                throw messageToException(message);
            });
        }

        function getParameterInfo(urlname, options) {
            var targetUrl = '/api/mdb/' + yamcsInstance + '/parameters' + urlname;
            targetUrl += toQueryString(options);
            return $http.get(targetUrl).then(function (response) {
                return response.data;
            }).catch(function (message) {
                $log.error('XHR failed', message);
                throw messageToException(message);
            });
        }

        function getAlgorithmInfo(urlname, options) {
            var targetUrl = '/api/mdb/' + yamcsInstance + '/algorithms' + urlname;
            targetUrl += toQueryString(options);
            return $http.get(targetUrl).then(function (response) {
                return response.data;
            }).catch(function (message) {
                $log.error('XHR failed', message);
                throw messageToException(message);
            });
        }

        function sendCommand(urlname, assignements){
            var targetUrl= '/api/processors/'+yamcsInstance+'/realtime/commands'+urlname;
            
            var query = {
                "sequenceNumber" : 1,
                "origin" : "user@my-machine",
                "assignment" : assignements,
                "dryRun" : false
            }
            $log.log('TRIGGER COMMAND', targetUrl, query);
            return $http.post(targetUrl, query).then( function(response){
                return response;
            }).catch(function (msg){
                $log.error("XHR failed", msg);
                throw messageToException(msg);
            });
        }
        /*
            Returns an array of a space system and all of its nested children
         */
        function flattenSpaceSystem(ss) {
            var flattened = [ ss ];
            if (ss.hasOwnProperty('sub')) {
                for (var i = 0; i < ss.sub.length; i++) {
                    var flatsub = flattenSpaceSystem(ss.sub[i]);
                    for (var j = 0; j < flatsub.length; j++) {
                        flattened.push(flatsub[j]);
                    }
                }
            }
            return flattened;
        }

        /*
            Returns a single space system, with all nested elements attached directly to it
         */
        function compactSpaceSystem(ss) {
            if (ss.hasOwnProperty('sub')) {
                for (var i = 0; i < ss.sub.length; i++) {
                    var flatsub = flattenSpaceSystem(ss.sub[i]);
                    for (var j = 0; j < flatsub.length; j++) {
                        ss['parameterCount'] += flatsub[j]['parameterCount'];
                        ss['containerCount'] += flatsub[j]['containerCount'];
                        ss['commandCount'] += flatsub[j]['commandCount'];
                        ss['algorithmCount'] += flatsub[j]['algorithmCount'];
                    }
                }
            }
            return ss;
        }

        function toQueryString(options) {
            if (!options) return '?nolink';
            var result = '?nolink&pretty=no';
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

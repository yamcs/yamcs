(function () {
    'use strict';

    angular.module('yamcs.intf').factory('tmService', tmService);

    /* @ngInject */
    function tmService($http, $log, $rootScope, socket, yamcsInstance, remoteConfig) {

        socket.on('PARAMETER', function(pdata) {
            var params = pdata['parameter'];
            $rootScope.$broadcast('yamcs.tm.pvals', params);
        });

        return {
            getParameter: getParameter,
            getParameterSamples: getParameterSamples,
            getParameterHistory: getParameterHistory,
            subscribeParameters: subscribeParameters,
            subscribeComputations: subscribeComputations
        };

        function getParameter(qname, options) {
            var targetUrl = '/api/processors/' + yamcsInstance + '/realtime/parameters' + qname;
            targetUrl += toQueryString(options);
            return $http.get(targetUrl).then(function (response) {
                return response.data;
            }).catch(function (message) {
                $log.error('XHR failed', message);
                throw messageToException(message);
            });
        }

        function getParameterSamples(qname, options, canceler) {
            if(!!remoteConfig['useParameterArchive']) {
                var targetUrl = '/api/archive/' + yamcsInstance + '/parameters2' + qname + '/samples';
            } else {
                var targetUrl = '/api/archive/' + yamcsInstance + '/parameters' + qname + '/samples';
            }
            targetUrl += toQueryString(options);
            var ngOpts = {};
            if (canceler) {
                ngOpts['timeout'] = canceler.promise;
            }
            return $http.get(targetUrl, ngOpts).then(function (response) {
                return response.data;
            }).catch(function (message) {
                if (message['data']) {
                    $log.error('XHR failed', message);
                    throw messageToException(message);
                } else {
                    $log.info('Canceled a pending request');
                }
            });
        }

        function getParameterHistory(qname, options) {
            if(!!remoteConfig['useParameterArchive']) {
                var targetUrl = '/api/archive/' + yamcsInstance + '/parameters2' + qname;
            } else {
                var targetUrl = '/api/archive/' + yamcsInstance + '/parameters' + qname;
            }
            targetUrl += toQueryString(options);
            return $http.get(targetUrl).then(function (response) {
                return response.data;
            }).catch(function (message) {
                $log.error('XHR failed', message);
                throw messageToException(message);
            });
        }
        function subscribeParameters(parameters) {
            if (parameters.length == 0) return;
            var msg = {
                abortOnInvalid: false,
                id: parameters
            };

            socket.on('open', function () {
                socket.emit('parameter', 'subscribe2', msg);
            });
            if (socket.isConnected()) {
                socket.emit('parameter', 'subscribe2', msg);
            }
        }

        function subscribeComputations(computations) {
            if(computations.length == 0) return;
            var msg = {
                abortOnInvalid: false,
                computation: computations
            };

            socket.on('open', function () {
                socket.emit('parameter', 'subscribeComputations', msg);
            });
            if (socket.isConnected()) {
                socket.emit('parameter', 'subscribeComputations', msg);
            }
        }

        function toQueryString(options) {
            if (!options) return '?nolink&pretty=no';
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
    }
})();

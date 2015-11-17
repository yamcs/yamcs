(function () {
    'use strict';

    angular
        .module('app.yamcs')
        .factory('mdbService', mdbService);

    /* @ngInject */
    function mdbService($http, $log, yamcsInstance) {

        return {
            getSummary: getSummary,

            listParameters: listParameters,
            getParameterInfo: getParameterInfo,

            listContainers: listContainers,

            listCommands: listCommands,

            listAlgorithms: listAlgorithms,
            getAlgorithmInfo: getAlgorithmInfo
        };

        function getSummary() {
            var targetUrl = '/api/mdb/' + yamcsInstance;

            return $http.get(targetUrl).then(function (response) {
                var mdb = response.data;
                mdb['flatSpaceSystems'] = []; // Flatten the nested structure, for better UI
                if (mdb.hasOwnProperty('spaceSystem')) {
                    for (var i = 0; i < mdb.spaceSystem.length; i++) {
                        var flattened = flattenSpaceSystem(mdb.spaceSystem[i]);
                        for (var j = 0; j < flattened.length; j++) {
                            mdb['flatSpaceSystems'].push(flattened[j]);
                        }
                    }
                }
                return mdb;
            }).catch(function (message) {
                $log.error('XHR failed', message);
            });
        }

        function listParameters(qname) {
            var targetUrl = '/api/mdb/' + yamcsInstance + '/parameters';
            if (qname) {
                targetUrl += qname;
            }

            return $http.get(targetUrl).then(function (response) {
                return response.data['parameter'];
            }).catch(function (message) {
                $log.error('XHR failed', message);
            });
        }

        function listContainers(qname) {
            var targetUrl = '/api/mdb/' + yamcsInstance + '/containers';
            if (qname) {
                targetUrl += qname;
            }

            return $http.get(targetUrl).then(function (response) {
                return response.data['container'];
            }).catch(function (message) {
                $log.error('XHR failed', message);
            });
        }

        function listCommands(qname) {
            var targetUrl = '/api/mdb/' + yamcsInstance + '/commands';
            if (qname) {
                targetUrl += qname;
            }

            return $http.get(targetUrl).then(function (response) {
                return response.data['command'];
            }).catch(function (message) {
                $log.error('XHR failed', message);
            });
        }

        function listAlgorithms(qname) {
            var targetUrl = '/api/mdb/' + yamcsInstance + '/algorithms';
            if (qname) {
                targetUrl += qname;
            }

            return $http.get(targetUrl).then(function (response) {
                return response.data['algorithm'];
            }).catch(function (message) {
                $log.error('XHR failed', message);
            });
        }

        function getParameterInfo(urlname) {
            var targetUrl = '/api/mdb/' + yamcsInstance + '/parameters' + urlname;
            return $http.get(targetUrl).then(function (response) {
                return response.data;
            }).catch(function (message) {
                $log.error('XHR failed', message);
            });
        }

        function getAlgorithmInfo(urlname) {
            var targetUrl = '/api/mdb/' + yamcsInstance + '/algorithms' + urlname;
            return $http.get(targetUrl).then(function (response) {
                return response.data;
            }).catch(function (message) {
                $log.error('XHR failed', message);
            });
        }

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
    }
})();

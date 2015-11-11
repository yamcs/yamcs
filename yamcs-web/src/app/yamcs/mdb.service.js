(function () {
    'use strict';

    angular
        .module('app.yamcs')
        .factory('mdbService', mdbService);

    /* @ngInject */
    function mdbService($http, exception) {

        return {
            getSummary: getSummary,
            listParameters: listParameters,
            listContainers: listContainers,
            listCommands: listCommands,
            getParameterInfo: getParameterInfo
        };

        function getSummary() {
            var yamcsInstance = location.pathname.match(/\/([^\/]*)\//)[1];
            var targetUrl = '/api/mdb/' + yamcsInstance;

            return $http.get(targetUrl).then(function (response) {
                return response.data;
            }).catch(function (message) {
                exception.catcher('XHR Failed')(message);
            });
        }

        function listParameters(qname) {
            var yamcsInstance = location.pathname.match(/\/([^\/]*)\//)[1];
            var targetUrl = '/api/mdb/' + yamcsInstance + '/parameters';
            if (qname) {
                targetUrl += qname;
            }

            return $http.get(targetUrl).then(function (response) {
                return response.data['parameter'];
            }).catch(function (message) {
                exception.catcher('XHR Failed')(message);
            });
        }

        function listContainers(qname) {
            var yamcsInstance = location.pathname.match(/\/([^\/]*)\//)[1];
            var targetUrl = '/api/mdb/' + yamcsInstance + '/containers';
            if (qname) {
                targetUrl += qname;
            }

            return $http.get(targetUrl).then(function (response) {
                return response.data['container'];
            }).catch(function (message) {
                exception.catcher('XHR Failed')(message);
            });
        }

        function listCommands(qname) {
            var yamcsInstance = location.pathname.match(/\/([^\/]*)\//)[1];
            var targetUrl = '/api/mdb/' + yamcsInstance + '/commands';
            if (qname) {
                targetUrl += qname;
            }

            return $http.get(targetUrl).then(function (response) {
                return response.data['command'];
            }).catch(function (message) {
                exception.catcher('XHR Failed')(message);
            });
        }

        function getParameterInfo(urlname) {
            var yamcsInstance = location.pathname.match(/\/([^\/]*)\//)[1];
            var targetUrl = '/api/mdb/' + yamcsInstance + '/parameters' + urlname;
            return $http.get(targetUrl).then(function (response) {
                return response.data;
            }).catch(function (message) {
                exception.catcher('XHR Failed')(message);
            });
        }
    }
})();

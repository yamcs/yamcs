(function () {
    angular
        .module('yamcs.intf')
        .factory('displaysService', displaysService);

    /* @ngInject */
    function displaysService($http, $log, yamcsInstance) {

        return {
            listDisplays: listDisplays,
            getDisplay: getDisplay
        };

        function listDisplays() {
            var targetUrl = '/api/displays/' + yamcsInstance;
            return $http.get(targetUrl, {cache: true}).then(function (response) {
                var data = response.data;
                // Array of arrays, but translate it for easier processing
                var displays = [];
                for (var i = 0; i < data.length; i++) {
                    displays.push(addDisplay('', data[i]));
                }
                return displays;
            }).catch(function (message) {
                $log.error('XHR failed', message);
                throw messageToException(message);
            });
        }

        function getDisplay(filename) {
            var targetUrl = '/_static/' + yamcsInstance + '/displays/' + filename;
            return $http.get(targetUrl, {
                cache: true,
                transformResponse : function(data) {
                    return $.parseXML(data);
                }
            }).then(function (response) {
                return response.data;
            }).catch(function (message) {
                $log.error('XHR failed', message);
                throw messageToException(message);
            });
        }

        function addDisplay(path, d) {
            if (d instanceof Array) {
                var group = { 'group': d[0], 'displays': [] };
                for (var i = 1; i < d.length; i++) {
                    var d1 = d[i];
                    var child = addDisplay(path + d[0] + '/', d1);
                    group['displays'].push(child);
                }
                return group;
            } else {
                return { 'name': d, 'display': path + d };
            }
        }

        function messageToException(message) {
            return {
                name: message['data']['type'],
                message: message['data']['msg']
            };
        }
    }
})();

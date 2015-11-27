(function () {
    angular
        .module('yamcs.intf')
        .factory('displaysService', displaysService);

    /* @ngInject */
    function displaysService($http, socket, tmService, $log, yamcsInstance) {

        socket.on('PARAMETER', function(pdata) {
            var params = pdata.parameter;
            for(var i = 0; i < params.length; i++) {
                var p = params[i];
                var dbs = tmService.subscribedParameters[p.id.name];
                if (!dbs) {
                    //$log.error('Cannot find bindings for '+ p.id.name, tmService.subscribedParameters);
                    continue;
                }
                for (var j = 0; j < dbs.length; j++) {
                    // TODO refactor this, should not know about uss
                    USS.updateWidget(dbs[j], p);
                }
            }
        });

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

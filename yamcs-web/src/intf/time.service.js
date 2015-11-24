(function () {
    'use strict';

    angular
        .module('yamcs.intf')
        .factory('timeService', timeService);

    /* @ngInject */
    function timeService(socket, $rootScope) {

        var latestTime;

        socket.on('open', function () {
            subscribeUpstream();
        });
        if (socket.isConnected()) {
            subscribeUpstream();
        }

        return {
        };

        function subscribeUpstream() {
            socket.on('TIME_INFO', function (data) {
                latestTime = data;
                $rootScope.$broadcast('yamcs.time', latestTime);
            });
            socket.emit('time', 'subscribe', {}, null, function (et, msg) {
                console.log('failed subscribe', et, ' ', msg);
            });
        }
    }
})();

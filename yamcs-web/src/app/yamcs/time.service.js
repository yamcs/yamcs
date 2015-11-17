(function () {
    'use strict';

    angular
        .module('app.yamcs')
        .factory('timeService', timeService);

    var latestTime;
    var timeWatchers = {};
    var subscriptionId = 0;

    /* @ngInject */
    function timeService(socket) {

        socket.on('open', function () {
            subscribeUpstream();
        });
        if (socket.isConnected()) {
            subscribeUpstream();
        }

        return {
            watchTime: watchTime,
            unwatchTime: unwatchTime
        };

        function subscribeUpstream() {
            socket.on('TIME_INFO', function (data) {
                latestTime = data;
                for (var sid in timeWatchers) {
                    if (timeWatchers.hasOwnProperty(sid)) {
                        timeWatchers[sid](data);
                    }
                }
            });
            socket.emit('time', 'subscribe', {}, null, function (et, msg) {
                console.log('failed subscribe', et, ' ', msg);
            });
        }

        function watchTime(callback) {
            var id = ++subscriptionId;
            timeWatchers[id] = callback;
            if (typeof latestTime !== 'undefined') {
                callback(latestTime);
            }
            return id;
        }

        function unwatchTime(subscriptionId) {
            delete timeWatchers[subscriptionId];
        }
    }
})();

(function () {
    'use strict';

    angular
        .module('yamcs.intf')
        .factory('eventsService', eventsService);

    /* @ngInject */
    function eventsService(socket, $rootScope) {

        var unreadCount = 0; // well, sort of
        var urgent = false;
        var events = [];

        socket.on('open', function () {
            subscribeUpstream();
        });
        if (socket.isConnected()) {
            subscribeUpstream();
        }

        return {
            getEvents: getEvents,
            resetUnreadCount: resetUnreadCount
        };

        function getEvents() {
            return events;
        }

        function resetUnreadCount() {
            unreadCount = 0;
            urgent = false;
            broadcastEventStats();
        }

        function subscribeUpstream() {
            socket.on('EVENT', function (data) {
                unreadCount++;
                events.push(data);
                if (data['severity'] === 'ERROR') {
                    urgent = true;
                }
                broadcastEventStats();
                $rootScope.$broadcast('yamcs.event', data);
            });
            socket.emit('events', 'subscribe', {}, null, function (et, msg) {
                console.log('failed subscribe', et, ' ', msg);
            });
        }

        function broadcastEventStats() {
            $rootScope.$broadcast('yamcs.eventStats', {
                unreadCount: unreadCount,
                count: events.length,
                urgent: urgent
            });
        }

        function messageToException(message) {
            return {
                name: message['data']['type'],
                message: message['data']['msg']
            };
        }
    }
})();

(function () {
    'use strict';

    angular
        .module('yamcs.intf')
        .factory('eventsService', eventsService);

    /* @ngInject */
    function eventsService($http, $log, socket, $filter, yamcsInstance, $rootScope) {

        var unreadCount = 0; // well, sort of
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
            broadcastEventStats();
        }

        function subscribeUpstream() {
            socket.on('EVENT', function (data) {
                unreadCount++;
                events.push(data);
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
                count: events.length
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

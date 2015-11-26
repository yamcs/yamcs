(function () {
    'use strict';

    angular
        .module('yamcs.intf')
        .factory('eventsService', eventsService);

    /* @ngInject */
    function eventsService($rootScope, $http, socket, yamcsInstance) {

        var stats = {
            unreadCount: 0, // well, sort of
            urgent: false
        };

        socket.on('open', function () {
            subscribeUpstream();
        });
        if (socket.isConnected()) {
            subscribeUpstream();
        }

        return {
            getEvents: getEvents,
            listEvents: listEvents,
            resetUnreadCount: resetUnreadCount
        };

        function getEvents() {
            return events;
        }

        function listEvents(options) {
            var targetUrl = '/api/archive/' + yamcsInstance + '/events';
            targetUrl += toQueryString(options);
            return $http.get(targetUrl).then(function (response) {
                return response.data['event'];
            }).catch(function (message) {
                $log.error('XHR failed', message);
                throw messageToException(message);
            });
        }

        function resetUnreadCount() {
            stats.unreadCount = 0;
            stats.urgent = false;
            $rootScope.$broadcast('yamcs.eventStats', stats);
        }

        function subscribeUpstream() {
            socket.on('EVENT', function (data) {
                stats.unreadCount++;
                if (data['severity'] === 'ERROR') {
                    stats.urgent = true;
                }
                $rootScope.$broadcast('yamcs.eventStats', stats);
                $rootScope.$broadcast('yamcs.event', data);
            });
            socket.emit('events', 'subscribe', {}, null, function (et, msg) {
                console.log('failed subscribe', et, ' ', msg);
            });
        }

        function toQueryString(options) {
            if (!options) return '?nolink';
            var result = '?nolink';
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

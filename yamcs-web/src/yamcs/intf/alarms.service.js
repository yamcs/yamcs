(function () {
    'use strict';

    angular
        .module('yamcs.intf')
        .factory('alarmService', alarmService);

    // Aggregated alarm data
    var activeAlarmsById = {};

    var alarmWatchers = {};
    var subscriptionId = 0;

    /* @ngInject */
    function alarmService($http, $log, socket, $filter, yamcsInstance) {

        socket.on('open', function () {
            subscribeUpstream();
        });
        if (socket.isConnected()) {
            subscribeUpstream();
        }

        return {
            listAlarms: listAlarms,
            watch: watch,
            unwatch: unwatch,
            patchParameterAlarm: patchParameterAlarm
        };

        function listAlarms() {
            var targetUrl = '/api/processors/' + yamcsInstance + '/realtime/alarms';
            //$log.info('Fetching alarm defs');
            return $http.get(targetUrl).then(function (response) {
                return response.data.alarm;
            }).catch(function (message) {
                $log.error('XHR failed', message);
            });
        }

        function subscribeUpstream() {
            socket.on('ALARM_INFO', function (data) {
                if (data['type'] == 'CLEARED') {
                    delete activeAlarmsById[data['id']];
                } else {
                    activeAlarmsById[data['id']] = enrichAlarm(data);
                }
                console.log('data is now ', activeAlarmsById);

                var alarmList = [];
                for (var aid in activeAlarmsById) {
                    if (activeAlarmsById.hasOwnProperty(aid)) {
                        alarmList.push(activeAlarmsById[aid]);
                    }
                }

                for (var sid in alarmWatchers) {
                    if (alarmWatchers.hasOwnProperty(sid)) {
                        alarmWatchers[sid](alarmList);
                    }
                }
            });
            socket.emit('alarms', 'subscribe', {}, null, function (et, msg) {
                console.log('failed subscribe', et, ' ', msg);
            });
        }

        function watch(callback) {
            var id = ++subscriptionId;
            alarmWatchers[id] = callback;
            callback(activeAlarmsById);
            return id;
        }

        function unwatch(subscriptionId) {
            delete alarmWatchers[subscriptionId];
        }

        function patchParameterAlarm(parameterId, alarmId, options) {
            var targetUrl = '/api/processors/' + yamcsInstance + '/realtime/parameters' + parameterId.name + '/' + 'alarms/' + alarmId;
            return $http.patch(targetUrl, options).then(function (response) {
                return response.data;
            }).catch(function (message) {
                $log.error('XHR failed', message);
            });
        }

        function enrichAlarm(alarm) {
            alarm.msg = $filter('name')(alarm.triggerValue.id.name);

            var prefix = '';
            switch (alarm.triggerValue.monitoringResult) {
            case 'WATCH_LOW':
            case 'WARNING_LOW':
            case 'DISTRESS_LOW':
            case 'CRITICAL_LOW':
            case 'SEVERE_LOW':
                prefix = 'OOL ';
                break;
            case 'WATCH_HIGH':
            case 'WARNING_HIGH':
            case 'DISTRESS_HIGH':
            case 'CRITICAL_HIGH':
            case 'SEVERE_HIGH':
                prefix = 'OOL ';
            }

            // TODO should not be done in service, and really only in template
            alarm.msg = prefix + '<a href="#/mdb' + alarm.triggerValue.id.name + '">' + alarm.msg + '</a>';

            alarm.mostSevereLevel = toNumericLevel(alarm.mostSevereValue.monitoringResult);
            alarm.currentLevel = toNumericLevel(alarm.currentValue.monitoringResult);
            alarm.triggerLevel = toNumericLevel(alarm.triggerValue.monitoringResult);
            return alarm;
        }

        function toNumericLevel(monitoringResult) {
            switch (monitoringResult) {
            case 'WATCH_HIGH':
            case 'WATCH_LOW':
            case 'WATCH':
                return 1;
            case 'WARNING_HIGH':
            case 'WARNING_LOW':
            case 'WARNING':
                return 2;
            case 'DISTRESS_HIGH':
            case 'DISTRESS_LOW':
            case 'DISTRESS':
                return 3;
            case 'CRITICAL_HIGH':
            case 'CRITICAL_LOW':
            case 'CRITICAL':
                return 4;
            case 'SEVERE_HIGH':
            case 'SEVERE_LOW':
            case 'SEVERE':
                return 5;
            default:
                return 0;
            }
        }
    }
})();

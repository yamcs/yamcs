(function () {
    'use strict';

    angular
        .module('yamcs.intf')
        .factory('alarmsService', alarmsService);

    // Aggregated alarm data
    var activeAlarms = [];

    /* @ngInject */
    function alarmsService($rootScope, $http, $log, socket, yamcsInstance) {

        var stats = {
            activeCount: 0,
            urgent: false
        };

        socket.on('open', function () {
            subscribeUpstream();
        });
        if (socket.isConnected()) {
            subscribeUpstream();
        }

        return {
            getKey: getKey,
            getActiveAlarms: getActiveAlarms,
            getActiveAlarmForParameter: getActiveAlarmForParameter,
            listAlarms: listAlarms,
            listAlarmsForParameter: listAlarmsForParameter,
            patchParameterAlarm: patchParameterAlarm
        };

        /**
         * Returns a singular value combining the effective key of an alarm:
         * (triggerTime, parameter, seqnum)
         */
        function getKey(alarm) {
            var triggerTime = alarm['triggerValue']['generationTime'];
            var qname = alarm['triggerValue']['id']['name'];
            return triggerTime + qname + alarm['seqNum'];
        }

        /**
         * Active alarms sorted by key
         */
        function getActiveAlarms() {
            return activeAlarms;
        }

        function getActiveAlarmForParameter(qname) {
            for (var i = 0; i < activeAlarms.length; i++) {
                if (activeAlarms[i]['triggerValue']['id']['name'] === qname) {
                    return activeAlarms[i];
                }
            }
        }

        function listAlarms() {
            var targetUrl = '/api/archive/' + yamcsInstance + '/alarms';
            return $http.get(targetUrl).then(function (response) {
                var alarms = [];
                if (response.data.hasOwnProperty('alarm')) {
                    for (var i = 0; i < response.data['alarm'].length; i++) {
                        var alarm = enrichAlarm(response.data['alarm'][i]);
                        var stillOngoing = false;
                        for (var j = 0; j < activeAlarms.length; j++) {
                            if (activeAlarms[j]['key'] === alarm['key']) {
                                stillOngoing = true;
                                break;
                            }
                        }
                        if (!stillOngoing) {
                            alarms.push(alarm);
                        }
                    }
                }
                return alarms;
            }).catch(function (message) {
                $log.error('XHR failed', message);
                throw messageToException(message);
            });
        }

        function listAlarmsForParameter(qname) {
            var targetUrl = '/api/archive/' + yamcsInstance + '/alarms' + qname;
            return $http.get(targetUrl).then(function (response) {
                if (response.data.hasOwnProperty('alarm')) {
                    for (var i = 0; i < response.data['alarm'].length; i++) {
                        enrichAlarm(response.data['alarm'][i]);
                    }
                }
                return response.data['alarm'];
            }).catch(function (message) {
                $log.error('XHR failed', message);
                throw messageToException(message);
            });
        }

        function subscribeUpstream() {
            socket.on('ALARM_DATA', function (data) {
                data = enrichAlarm(data);
                if (data['type'] === 'CLEARED') {
                    for (var i = 0; i < activeAlarms.length; i++) {
                        if (activeAlarms[i]['key'] === data['key']) {
                            activeAlarms.splice(i, 1);
                            break;
                        }
                    }
                } else {
                    var match = false;
                    for (var i = 0; i < activeAlarms.length; i++) {
                        if (activeAlarms[i]['key'] === data['key']) {
                            activeAlarms[i] = data;
                            match = true;
                            break;
                        }
                    }
                    if (!match) {
                        var insertIndex = _.sortedIndex(activeAlarms, data, 'key');
                        activeAlarms.splice(insertIndex, 0, data);
                    }
                }

                updateStats();
                $rootScope.$broadcast('yamcs.alarm', data);
            });

            socket.emit('alarms', 'subscribe', {}, null, function (et, msg) {
                console.log('failed subscribe', et, ' ', msg);
            });
        }

        function patchParameterAlarm(parameterId, alarmId, options) {
            var targetUrl = '/api/processors/' + yamcsInstance + '/realtime/parameters' + parameterId.name + '/' + 'alarms/' + alarmId;
            return $http.patch(targetUrl, options).then(function (response) {
                return response.data;
            }).catch(function (message) {
                $log.error('XHR failed', message);
                throw messageToException(message);
            });
        }

        function updateStats() {
            var urgent = false;
            for (var i = 0; i < activeAlarms.length; i++) {
                if (!activeAlarms[i].hasOwnProperty('acknowledgeInfo')) {
                    urgent = true;
                    break;
                }
            }
            stats['activeCount'] = activeAlarms.length;
            stats['urgent'] = urgent;
            $rootScope.$broadcast('yamcs.alarmStats', stats);
        }

        function enrichAlarm(alarm) {
            alarm['key'] = getKey(alarm);
            alarm['triggerLevel'] = toNumericLevel(alarm['triggerValue']['monitoringResult']);
            if (alarm.hasOwnProperty('mostSevereValue')) {
                alarm['mostSevereLevel'] = toNumericLevel(alarm['mostSevereValue']['monitoringResult']);
            } else {
                alarm['mostSevereValue'] = alarm['triggerValue'];
                alarm['mostSevereLevel'] = alarm['triggerLevel'];
            }
            if (alarm.hasOwnProperty('currentValue')) {
                alarm['currentLevel'] = toNumericLevel(alarm['currentValue']['monitoringResult']);
            } else {
                alarm['currentValue'] = alarm['triggerValue'];
                alarm['currentLevel'] = alarm['triggerLevel'];
            }
            return alarm;
        }

        function toNumericLevel(monitoringResult) {
            switch (monitoringResult) {
            case 'WATCH':
                return 1;
            case 'WARNING':
                return 2;
            case 'DISTRESS':
                return 3;
            case 'CRITICAL':
                return 4;
            case 'SEVERE':
                return 5;
            default:
                return 0;
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

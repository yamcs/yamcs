(function() {
    'use strict';

    angular
        .module('yamcs.mdb')
        .controller('MDBParameterDetailController', MDBParameterDetailController);

    /* @ngInject */
    function MDBParameterDetailController($rootScope, $scope, $routeParams, $filter, $uibModal, tmService, mdbService, configService, alarmsService) {

        // Will be augmented when passed into directive
        $scope.plotController = {};

        $scope.plotctx = {
            range: configService.get('initialPlotRange', '1h')
        };
        $rootScope.pageTitle = $routeParams.name + ' | Yamcs';

        var urlname = '/' + $routeParams['ss'] + '/' + $routeParams.name;

        $scope.pdata = [{
            name: null,
            points: [],
            min: null,
            max: null
        }];

        var loadingHistory = false;

        $scope.alarms = [];
        mdbService.getParameterInfo(urlname).then(function (data) {

            $scope.info = mapAlarmRanges(data);
            var qname = $scope.info['qualifiedName'];

            alarmsService.listAlarmsForParameter(qname).then(function (alarms) {
                $scope.alarms = alarms;

                // Both dependencies are now fetched (could improve towards parallel requests though)
                // So continue on

                // Live data is added to the plot, except when we are loading a chunk of historic
                // data. This may mean that we miss a few points though, but that's acceptable for now.
                var subscriptionId = tmService.subscribeParameter({name: qname}, function (data) {
                    $scope.para = data;
                    if (!loadingHistory && $scope.isNumeric()) {
                        appendPoint($scope, data, $filter);
                    } else {
                        //console.log('ignoring a point');
                    }
                });
                $scope.$on('$destroy', function() {
                    tmService.unsubscribeParameter(subscriptionId);
                });

                tmService.getParameterHistory(qname, {
                    norepeat: true,
                    limit: 10
                }).then(function (historyData) {
                    $scope.values = historyData['parameter'];
                });

                $scope.activeAlarms = alarmsService.getActiveAlarms(); // Live collection
                $scope.activeAlarm = alarmsService.getActiveAlarmForParameter(qname);
                $scope.$watchCollection('activeAlarms', function (activeAlarms) {
                    var match = false;
                    for (var i = 0; i < activeAlarms.length; i++) {
                        var alarm = activeAlarms[i];
                        if (alarm['triggerValue']['id']['name'] === qname) {
                            $scope.activeAlarm = alarm;
                            match = true;
                            break;
                        }
                    }
                    if (!match) $scope.activeAlarm = null;
                    // TODO should maybe update alarm history table
                });


                return $scope.alarms;
            });

            $scope.$watchGroup(['plotctx.range', 'plotController.initialized'], function (values) {
                var mode = values[0];
                if ($scope.plotController.initialized) {
                    $scope.plotController.startSpinner(); // before emptyPlot, so effects get considered in empty redraw
                    loadingHistory = true;
                    $scope.plotController.emptyPlot();
                    loadHistoricData(tmService, qname, mode).then(function (data) {
                        $scope.plotController.stopSpinner();
                        $scope.pdata = [ data ];
                        loadingHistory = false;
                    });
                }
            });

            $scope.openAcknowledge = function(alarm) {
                var form = {
                    comment: undefined
                };
                $uibModal.open({
                  animation: true,
                  templateUrl: 'acknowledgeAlarmModal.html',
                  controller: 'AcknowledgeAlarmModalController',
                  size: 'lg',
                  resolve: {
                    alarm: function () {
                        return alarm;
                    },
                    form: function () {
                        return form;
                    }
                  }
                });
            };

            return $scope.info;
        });

        $scope.openEnumValuesModal = function() {
            $uibModal.open({
                animation: true,
                templateUrl: 'enumValuesModal.html',
                controller: 'ValueEnumerationModalInstanceController',
                size: 'lg',
                resolve: {
                    info: function () {
                        return $scope.info;
                    }
                }
            });
        };
        $scope.addParameterModal = function() {
            $uibModal.open({
                animation: true,
                templateUrl: 'addParameterModal.html',
                controller: 'AddParameterModalInstanceController',
                size: 'md'
            });
        };

        $scope.expandAlarms = function() {
            for (var i = 0; i < $scope.alarms.length; i++) {
                $scope.alarms[i].expanded = true;
            }
        };

        $scope.collapseAlarms = function() {
            for (var i = 0; i < $scope.alarms.length; i++) {
                $scope.alarms[i].expanded = false;
            }
        };

        $scope.isNumeric = function() {
            if ($scope.hasOwnProperty('info') && $scope.info.hasOwnProperty('type') && $scope.info.type.hasOwnProperty('engType')) {
                return $scope.info.type.engType === 'float' || $scope.info.type.engType === 'integer';
            } else {
                return false;
            }
        }
    }

    function appendPoint(scope, pval, filter) {
        if (pval && pval.hasOwnProperty('engValue')) {
            var val = filter('stringValue')(pval);

            var t = new Date();
            t.setTime(Date.parse(pval['generationTimeUTC']));
            scope.pdata[0]['points'].push([t, [val,val,val]]);
            if (scope.pdata[0]['min'] === null || scope.pdata[0]['min'] > val) {
                scope.pdata[0]['min'] = val;
            }
            if (scope.pdata[0]['max'] === null || scope.pdata[0]['max'] < val) {
                scope.pdata[0]['max'] = val;
            }
            if (scope.hasOwnProperty('plotController')) {
                scope.plotController.repaint();
            }
        }
    }

    function loadHistoricData(tmService, qname, period) {
        var now = new Date();
        var nowIso = now.toISOString();
        var before = new Date(now.getTime());
        var beforeIso = nowIso;
        if (period === '15m') {
            before.setMinutes(now.getMinutes() - 15);
            beforeIso = before.toISOString();
        } else if (period === '30m') {
            before.setMinutes(now.getMinutes() - 30);
            beforeIso = before.toISOString();
        } else if (period === '1h') {
            before.setHours(now.getHours() - 1);
            beforeIso = before.toISOString();
        } else if (period === '5h') {
            before.setHours(now.getHours() - 5);
            beforeIso = before.toISOString();
        } else if (period === '1d') {
            before.setDate(now.getDate() - 1);
            beforeIso = before.toISOString();
        } else if (period === '1w') {
            before.setDate(now.getDate() - 7);
            beforeIso = before.toISOString();
        } else if (period === '1m') {
            before.setDate(now.getDate() - 31);
            beforeIso = before.toISOString();
        } else if (period === '3m') {
            before.setDate(now.getDate() - (3*31));
            beforeIso = before.toISOString();
        } else if (period === '1y') {
            before.setDate(now.getDate() - 365);
            beforeIso = before.toISOString();
        }

        return tmService.getParameterSamples(qname, {
            start: beforeIso.slice(0, -1),
            stop: nowIso.slice(0, -1)
        }).then(function (incomingData) {
            var rangeMin, rangeMax;
            var points = [];
            if (incomingData['sample']) {
                for (var i = 0; i < incomingData['sample'].length; i++) {
                    var sample = incomingData['sample'][i];
                    var t = new Date();
                    t.setTime(Date.parse(sample['time']));
                    var v = sample['avg'];
                    var min = sample['min'];
                    var max = sample['max'];

                    if (typeof rangeMin === 'undefined') {
                        rangeMin = min;
                        rangeMax = max;
                    } else {
                        if (rangeMin > min) rangeMin = min;
                        if (rangeMax < max) rangeMax = max;
                    }
                    points.push([t, [min, v, max]]);
                }
            }

            return {
                name: qname,
                points: points,
                min: rangeMin,
                max: rangeMax
            };
        });
    }

    function mapAlarmRanges(info) {
        if (info.hasOwnProperty('type')) {
            var type = info.type;
            if (type.hasOwnProperty('defaultAlarm')) {
                var defaultAlarm = type['defaultAlarm'];
                if (defaultAlarm.hasOwnProperty('staticAlarmRange')) {
                    var ranges = defaultAlarm['staticAlarmRange'];
                    for (var i = 0; i < ranges.length; i++) {
                        var range = ranges[i];
                        switch (range['level']) {
                            case 'WATCH': info.watch = range; break;
                            case 'WARNING': info.warning = range; break;
                            case 'DISTRESS': info.distress = range; break;
                            case 'CRITICAL': info.critical = range; break;
                            case 'SEVERE': info.severe = range;
                        }
                    }
                }
            }
        }
        return info;
    }
})();

(function() {
    'use strict';

    angular.module('yamcs.mdb').controller('MDBParameterDetailController', MDBParameterDetailController);

    /* @ngInject */
    function MDBParameterDetailController($rootScope, $scope, $routeParams, $q, $uibModal, tmService, mdbService, configService, alarmsService) {
        $rootScope.pageTitle = $routeParams.name + ' | Yamcs';

        // Will be augmented when passed into directive
        $scope.plotController = {};

        $scope.plotctx = {
            range: configService.get('initialPlotRange', '1h')
        };

        $scope.samples = [{
            name: null,
            points: [],
            min: null,
            max: null
        }];

        var loadingHistory = false;
        var apparentlyNumericParameter = false;
        var lastSamplePromiseCanceler;

        $scope.alarms = [];
        mdbService.getParameterInfo('/' + $routeParams['ss'] + '/' + $routeParams.name).then(function (data) {

            $scope.info = mapAlarmRanges(data);
            var qname = $scope.info['qualifiedName'];

            // Called by plot directive when user zooms. Load detailed samples.
            $scope.onZoom = function(startDate, stopDate) {
                if (lastSamplePromiseCanceler) {
                    lastSamplePromiseCanceler.resolve();
                }
                lastSamplePromiseCanceler = $q.defer();
                tmService.getParameterSamples(qname, {
                    start: startDate.toISOString().slice(0, -1),
                    stop: stopDate.toISOString().slice(0, -1)
                }, lastSamplePromiseCanceler).then(function (data) {
                    $scope.plotController.spliceDetailSamples(data);
                });
            };

            $scope.$on('yamcs.tm.pvals', function(event, pvals) {
                for(var i = 0; i < pvals.length; i++) {
                    var pval = pvals[i];
                    if (pval.id.name === qname) {
                        $scope.para = pval;
                        // Live data is added to the plot, except when we are loading a chunk of historic
                        // data. This may mean that we miss a few points though, but that's acceptable for now.
                        if (!loadingHistory && $scope.isNumeric()) {
                            $scope.plotController.appendPoint(pval);
                        } else {
                            console.log('ignoring a point');
                        }
                    }
                }
            });

            alarmsService.listAlarmsForParameter(qname).then(function (alarms) {
                $scope.alarms = alarms;

                // Both dependencies are now fetched (could improve towards parallel requests though)
                // So continue on

                tmService.subscribeParameters([{name: qname}]);

                $scope.$on('$destroy', function() {
                    // TODO tmService.unsubscribeParameter(subscriptionId);
                });

                tmService.getParameterHistory(qname, {
                    norepeat: true,
                    limit: 10
                }).then(function (historyData) {
                    $scope.values = historyData['parameter'];
                    
                    // additional checks for system parameters which don't have a type :(
                    if ($scope.values && $scope.values.length > 0) {
                        var valType = $scope.values[0]['engValue']['type'];
                        if (valType === 'SINT64'
                                || valType === 'UINT64'
                                || valType === 'SINT32'
                                || valType === 'UINT32'
                                || valType === 'FLOAT'
                                || valType === 'DOUBLE') {
                            apparentlyNumericParameter = true;
                        }
                    }
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
                    $scope.plotController.startSpinner(); // before setting samples to null, so effects get considered in empty redraw
                    loadingHistory = true;
                    $scope.samples = null;
                    loadHistoricData(qname, mode).then(function (data) {
                        $scope.plotController.stopSpinner();
                        $scope.samples = data;
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
            } else if (apparentlyNumericParameter) {
                return true;
            }
            return false;
        };

        function loadHistoricData(qname, period) {
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

            if (lastSamplePromiseCanceler) {
                lastSamplePromiseCanceler.resolve();
            }
            lastSamplePromiseCanceler = $q.defer();
            return tmService.getParameterSamples(qname, {
                start: beforeIso.slice(0, -1),
                stop: nowIso.slice(0, -1)
            }, lastSamplePromiseCanceler);
        }
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

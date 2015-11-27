(function() {
    'use strict';

    angular
        .module('yamcs.mdb')
        .controller('MDBParameterDetailController', MDBParameterDetailController);

    /* @ngInject */
    function MDBParameterDetailController($rootScope, $routeParams, tmService, mdbService, $scope, $uibModal, configService, alarmsService) {
        var vm = this;

        // Will be augmented when passed into directive
        $scope.plotController = {};

        $scope.plotctx = {
            range: configService.get('initialPlotRange', '1h')
        };
        $rootScope.pageTitle = $routeParams.name + ' | Yamcs';

        var urlname = '/' + $routeParams['ss'] + '/' + $routeParams.name;
        vm.urlname = urlname;

        $scope.pdata = [{
            name: null,
            points: [],
            min: null,
            max: null
        }];
        vm.alarms = [];
        mdbService.getParameterInfo(urlname).then(function (data) {

            vm.info = mapAlarmRanges(data);
            var qname = vm.info['qualifiedName'];

            alarmsService.listAlarmsForParameter(qname).then(function (alarms) {
                vm.alarms = alarms;

                // Both dependencies are now fetched (could improve towards parallel requests though)
                // So continue on

                var subscriptionId = tmService.subscribeParameter({name: qname}, function (data) {
                    vm.para = data;
                });
                $scope.$on('$destroy', function() {
                    tmService.unsubscribeParameter(subscriptionId);
                });

                tmService.getParameterHistory(qname, {
                    noRepeat: true,
                    limit: 10
                }).then(function (historyData) {
                    vm.values = historyData['parameter'];
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


                return vm.alarms;
            });

            $scope.$watchGroup(['plotctx.range', 'plotController.initialized'], function (values) {
                var mode = values[0];
                if ($scope.plotController.initialized) {
                    $scope.plotController.startSpinner(); // before emptyPlot, so effects get considered in empty redraw
                    $scope.plotController.emptyPlot();
                    loadHistoricData(tmService, qname, mode).then(function (data) {
                        $scope.plotController.stopSpinner();
                        $scope.pdata = [ data ];
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

            return vm.info;
        });

        vm.openEnumValuesModal = function() {
            $uibModal.open({
                animation: true,
                templateUrl: 'enumValuesModal.html',
                controller: 'ValueEnumerationModalInstanceController',
                size: 'lg',
                resolve: {
                    info: function () {
                        return vm.info;
                    }
                }
            });
        };
        vm.addParameterModal = function() {
            $uibModal.open({
                animation: true,
                templateUrl: 'addParameterModal.html',
                controller: 'AddParameterModalInstanceController',
                size: 'md'
            });
        };

        vm.expandAlarms = function() {
            for (var i = 0; i < vm.alarms.length; i++) {
                vm.alarms[i].expanded = true;
            }
        };

        vm.collapseAlarms = function() {
            for (var i = 0; i < vm.alarms.length; i++) {
                vm.alarms[i].expanded = false;
            }
        };

        vm.isNumeric = function() {
            if (vm.hasOwnProperty('info') && vm.info.hasOwnProperty('type') && vm.info.type.hasOwnProperty('engType')) {
                return vm.info.type.engType === 'float' || vm.info.type.engType === 'integer';
            } else {
                return false;
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
        }

        return tmService.getParameterSamples(qname, {
            start: beforeIso.slice(0, -1),
            stop: nowIso.slice(0, -1)
        }).then(function (incomingData) {
            var min, max;
            var points = [];
            if (incomingData['sample']) {
                for (var i = 0; i < incomingData['sample'].length; i++) {
                    var sample = incomingData['sample'][i];
                    var t = new Date();
                    t.setTime(Date.parse(sample['averageGenerationTimeUTC']));
                    var v = sample['averageValue'];
                    var lo = sample['lowValue'];
                    var hi = sample['highValue'];

                    if (typeof min === 'undefined') {
                        min = lo;
                        max = hi;
                    } else {
                        if (min > lo) min = lo;
                        if (max < hi) max = hi;
                    }
                    points.push([t, [lo, v, hi]]);
                }
            }

            return {
                name: qname,
                points: points,
                min: min,
                max: max
            };

            // before updating graph, so 'no data' text is not rendered
            //ctx['archiveFetched'] = true;

            /*if (data.length > 0) {
                updateGraph(g, data);
            } else {
                // Ensures that the 'no data' message is shown
                updateGraph(g, 'x\n');
            }

            spinner.stop();*/
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

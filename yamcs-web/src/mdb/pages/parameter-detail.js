(function() {
    'use strict';

    angular
        .module('yamcs.mdb')
        .controller('MDBParameterDetailController', MDBParameterDetailController);

    /* @ngInject */
    function MDBParameterDetailController($rootScope, $routeParams, tmService, mdbService, $scope, $uibModal, configService, alarmsService) {
        var vm = this;
        vm.isNumeric = isNumeric;

        $scope.plotctx = {
            range: configService.get('initialPlotRange', '1h')
        };
        $rootScope.pageTitle = $routeParams.name + ' | Yamcs';

        var urlname = '/' + $routeParams['ss'] + '/' + $routeParams.name;
        vm.urlname = urlname;

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

                return vm.alarms;
            });

            return vm.info;
        });

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

        function isNumeric() {
            if (vm.hasOwnProperty('info') && vm.info.hasOwnProperty('type') && vm.info.type.hasOwnProperty('engType')) {
                return vm.info.type.engType === 'float' || vm.info.type.engType === 'integer';
            } else {
                return false;
            }
        }
    }

    function mapAlarmRanges(info) {
        if (info.hasOwnProperty('type')) {
            var type = info.type;
            if (type.hasOwnProperty('defaultAlarm')) {
                var defaultAlarm = type.defaultAlarm;
                if (defaultAlarm.hasOwnProperty('staticAlarmRange')) {
                    var ranges = defaultAlarm.staticAlarmRange;
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

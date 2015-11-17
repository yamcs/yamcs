(function() {
    'use strict';

    angular
        .module('app.tm')
        .controller('MDBParameterDetailController', MDBParameterDetailController);

    /* @ngInject */
    function MDBParameterDetailController($routeParams, tmService, mdbService, $scope, $uibModal, configService) {
        var vm = this;

        $scope.plotmode = '1h';

        var urlname = '/' + $routeParams.name;
        if ($routeParams.hasOwnProperty('ss2')) {
            urlname = '/' + $routeParams['ss2'] + urlname;
        }
        if ($routeParams.hasOwnProperty('ss1')) {
            urlname = '/' + $routeParams['ss1'] + urlname;
        }
        vm.urlname = urlname;

        mdbService.getParameterInfo(urlname).then(function (data) {
            vm.info = mapAlarmRanges(data);

            var qname = vm.info.qualifiedName;
            var subscriptionId = tmService.subscribeParameter({name: qname}, function (data) {
                vm.para = data;
            });
            $scope.$on('$destroy', function() {
                tmService.unsubscribeParameter(subscriptionId);
            });

            vm.openEnumValuesModal = function () {
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

            return vm.info;
        });
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

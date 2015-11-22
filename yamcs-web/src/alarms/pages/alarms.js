(function () {
    'use strict';

    angular
        .module('yamcs.alarms')
        .controller('AlarmsController',  AlarmsController)
        .controller('AcknowledgeAlarmModalController',  AcknowledgeAlarmModalController);

    /* @ngInject */
    function AlarmsController($rootScope, alarmsService, $uibModal, $scope, $log) {
        var vm = this;

        $rootScope.pageTitle = 'Alarms | Yamcs';

        vm.openAcknowledge = openAcknowledge;
        vm.alarms = [];

        /*alarmsService.listAlarms().then(function (data) {
            vm.alarms = data;
            return vm.alarms;
        });*/

        /*
            vm.alarms is a live collection
         */
        var alarmSubscriptionId = alarmsService.watch(function (alarms) {
            vm.alarms = alarms;
        });

        $scope.$on('$destroy', function () {
            console.log('alarm/unsubscribe of alarms page controller');
            alarmsService.unwatch(alarmSubscriptionId)
        });



        function openAcknowledge(alarm) {
            var form = {
                comment: undefined
            };
            var modalInstance = $uibModal.open({
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

            modalInstance.result.then(function() {
                console.log('closed form is ', form);
            }, function () {
                $log.info('Modal dismissed at: ' + new Date());
            });
        }
    }

    /* @ngInject */
    function AcknowledgeAlarmModalController($scope, $uibModalInstance, alarm, form, alarmsService) {
        $scope.alarm = alarm;
        $scope.form = form;

        $scope.ok = function () {
            alarmsService.patchParameterAlarm(alarm.triggerValue.id, alarm.id, {
                state: 'acknowledged',
                comment: form.comment
            });
            $uibModalInstance.close($scope.form);
        };

        $scope.cancel = function () {
            $uibModalInstance.dismiss('cancel');
        };
    }
})();

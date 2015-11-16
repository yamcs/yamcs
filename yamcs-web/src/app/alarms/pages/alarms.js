(function () {
    'use strict';

    angular
        .module('app.alarms')
        .controller('AlarmController',  AlarmController)
        .controller('AcknowledgeAlarmModalController',  AcknowledgeAlarmModalController);

    /* @ngInject */
    function AlarmController(alarmService, $uibModal, $scope, $log) {
        var vm = this;
        vm.openAcknowledge = openAcknowledge;
        vm.alarms = [];

        /*alarmService.listAlarms().then(function (data) {
            vm.alarms = data;
            return vm.alarms;
        });*/

        /*
            vm.alarms is a live collection
         */
        var alarmSubscriptionId = alarmService.watch(function (alarms) {
            vm.alarms = alarms;
        });

        $scope.$on('$destroy', function () {
            console.log('alarm/unsubscribe of alarms page controller');
            alarmService.unwatch(alarmSubscriptionId)
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
    function AcknowledgeAlarmModalController($scope, $uibModalInstance, alarm, form, alarmService) {
        $scope.alarm = alarm;
        $scope.form = form;

        $scope.ok = function () {
            alarmService.patchParameterAlarm(alarm.triggerValue.id, alarm.id, {
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

(function () {
    'use strict';

    angular
        .module('yamcs.alarms')
        .controller('AlarmsController',  AlarmsController)
        .controller('AcknowledgeAlarmModalController',  AcknowledgeAlarmModalController);

    /* @ngInject */
    function AlarmsController($rootScope, alarmsService, $uibModal, $log) {
        $rootScope.pageTitle = 'Alarms | Yamcs';

        var vm = this;


        vm.openAcknowledge = openAcknowledge; // Opens the acknowledge dialog
        vm.alarmTab = 'activeAlarms';
        vm.alarms = alarmsService.getActiveAlarms(); // Live collection

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
            alarmsService.patchParameterAlarm(alarm['triggerValue']['id'], alarm['seqNum'], {
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

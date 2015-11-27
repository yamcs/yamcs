(function() {
    'use strict';

    angular
        .module('yamcs.mdb').controller('AddParameterModalInstanceController', AddParameterModalInstanceController);

    /* @ngInject */
    function AddParameterModalInstanceController($scope, $uibModalInstance, mdbService) {
        mdbService.listParameters().then(function(parameters) {
            $scope.parameters = parameters;
        });

        $scope.close = function () {
          $uibModalInstance.close();
        };

        $scope.ok = function () {
            /*alarmsService.patchParameterAlarm(alarm['triggerValue']['id'], alarm['seqNum'], {
                state: 'acknowledged',
                comment: form.comment
            });*/
            $uibModalInstance.close($scope.form);
        };

        $scope.cancel = function () {
            $uibModalInstance.dismiss('cancel');
        };

        $scope.data = { chosenParameters: []};
    }
})();

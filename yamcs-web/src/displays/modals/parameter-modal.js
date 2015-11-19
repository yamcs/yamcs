(function() {
    'use strict';

    angular
        .module('yamcs.displays').controller('ParameterModalInstanceController', ParameterModalInstanceController);

    /* @ngInject */
    function ParameterModalInstanceController($scope, $uibModalInstance, items) {
        $scope.items = items;
        $scope.selected = {
          item: $scope.items[0]
        };

        $scope.ok = function () {
          $uibModalInstance.close($scope.selected.item);
        };

        $scope.cancel = function () {
          $uibModalInstance.dismiss('cancel');
        };
    }
})();

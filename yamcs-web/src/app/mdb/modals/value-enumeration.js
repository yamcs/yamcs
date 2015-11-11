(function() {
    'use strict';

    angular
        .module('app.mdb').controller('ValueEnumerationModalInstanceController', ValueEnumerationModalInstanceController);

    /* @ngInject */
    function ValueEnumerationModalInstanceController($scope, $uibModalInstance, info) {
        $scope.info = info;

        $scope.close = function () {
          $uibModalInstance.close();
        };
    }
})();

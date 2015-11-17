(function () {
    angular
        .module('app.displays')
        .controller('DisplayController', DisplayController);

    /* @ngInject */
    function DisplayController($rootScope, $routeParams, $uibModal, $scope) {
        var vm = this;

        var displayName = $routeParams.display;
        if ($routeParams.hasOwnProperty('group')) {
            displayName = $routeParams.group + '/' + displayName;
        }

        $rootScope.pageTitle = displayName + ' | Yamcs';
        vm.displayName = displayName;

        vm.items = ['item1', 'item2', 'item3'];

        $scope.openParameterModal = function () {
            var modalInstance = $uibModal.open({
               animation: true,
               templateUrl: '/_static/app/displays/parameter-modal.html',
               controller: 'ParameterModalInstanceController',
               size: 'lg',
               resolve: {
                 items: function () {
                   return vm.items;
                 }
               }
             });

             modalInstance.result.then(function (selectedItem) {
               vm.selected = selectedItem;
             }, function () {
               //$log.info('Modal dismissed at: ' + new Date());
             });
        };
    }
})();

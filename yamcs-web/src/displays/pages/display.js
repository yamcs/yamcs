(function () {
    angular
        .module('yamcs.displays')
        .controller('DisplayController', DisplayController);

    /* @ngInject */
    function DisplayController($rootScope, $routeParams, $scope) {
        var vm = this;

        var displayName = $routeParams.display;

        $rootScope.pageTitle = displayName + ' | Yamcs';
        vm.displayName = displayName;

        $scope.$on('$destroy', function() {
            console.log('destroy on display');
        });
    }
})();

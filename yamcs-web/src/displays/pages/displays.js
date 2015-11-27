(function () {
    angular
        .module('yamcs.displays')
        .controller('DisplaysController',  DisplaysController);

    /* @ngInject */
    function DisplaysController($rootScope, displaysService) {
        var vm = this;

        $rootScope.pageTitle = 'Displays | Yamcs';

        vm.displays = [];
        displaysService.listDisplays().then(function (data) {
            vm.displays = data;
            return vm.displays;
        });
    }
})();

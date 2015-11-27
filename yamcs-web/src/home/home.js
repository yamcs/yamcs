(function () {
    angular
        .module('yamcs.home')
        .controller('HomeController',  HomeController);

    /* @ngInject */
    function HomeController($rootScope, configService, displaysService) {
        var vm = this;

        vm.appTitle = configService.get('title');
        $rootScope.pageTitle = vm.appTitle;

        vm.displays = [];
        displaysService.listDisplays().then(function (data) {
            vm.displays = data;
            return vm.displays;
        });
    }
})();

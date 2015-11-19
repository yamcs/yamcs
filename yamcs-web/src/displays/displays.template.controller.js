(function () {
    angular
        .module('yamcs.displays')
        .controller('DisplaysTemplateController',  DisplaysTemplateController);

    /* @ngInject */
    function DisplaysTemplateController(displaysService) {
        var vm = this;
        vm.displays = [];
        displaysService.listDisplays().then(function (data) {
            vm.displays = data;
            return vm.displays;
        });
    }
})();

(function () {
    angular
        .module('app.displays')
        .controller('DisplaysController',  DisplaysController);

    /* @ngInject */
    function DisplaysController(displaysService) {
        var vm = this;
        vm.displays = [];

        displaysService.listDisplays().then(function (data) {
            vm.displays = data;
            return vm.displays;
        });
    }

    function addDisplay(path, d) {
        if (d instanceof Array) {
            var group = { 'group': d[0], 'displays': [] };
            for (var i = 1; i < d.length; i++) {
                var d1 = d[i];
                var child = addDisplay(path + d[0] + '/', d1);
                group['displays'].push(child);
            }
            return group;
        } else {
            return { 'name': d, 'display': path + d };
        }
    }
})();

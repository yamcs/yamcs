(function () {
    'use strict';

    angular.module('yamcs.mdb').controller('MDBCommandsController',  MDBCommandsController);

    /* @ngInject */
    function MDBCommandsController($rootScope, mdbService, $routeParams) {
        var vm = this;

        var qname = '/' + $routeParams['ss'];

        vm.qname = qname;
        vm.title = qname;
        vm.commandsLoaded = false;
        vm.mdbType = 'commands';

        $rootScope.pageTitle = 'Commands | Yamcs';

        mdbService.listCommands({
            namespace: qname,
            recurse: (qname === '/yamcs')
        }).then(function (data) {
            vm.commands = data;
            vm.commandsLoaded = true;
            return vm.commands;
        });
    }
})();

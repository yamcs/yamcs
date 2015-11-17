(function () {
    'use strict';

    angular
        .module('app.mdb')
        .controller('MDBCommandsController',  MDBCommandsController);

    /* @ngInject */
    function MDBCommandsController($rootScope, mdbService, $routeParams) {
        var vm = this;

        var qname = '/' + $routeParams['ss1'];
        if ($routeParams.hasOwnProperty('ss2')) {
            qname += '/' + $routeParams['ss2'];
        }
        if ($routeParams.hasOwnProperty('ss3')) {
            qname += '/' + $routeParams['ss3'];
        }

        vm.qname = qname;
        vm.title = qname;
        vm.mdbType = 'commands';

        $rootScope.pageTitle = 'Commands | Yamcs';

        mdbService.listCommands(qname).then(function (data) {
            vm.commands = data;
            return vm.commands;
        });
    }
})();

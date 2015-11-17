(function () {
    'use strict';

    angular
        .module('app.mdb')
        .controller('MDBParametersController',  MDBParametersController);

    /* @ngInject */
    function MDBParametersController($rootScope, mdbService, $routeParams) {
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
        vm.mdbType = 'parameters';

        $rootScope.pageTitle = 'Parameters | Yamcs';

        mdbService.listParameters(qname).then(function (data) {
            vm.parameters = data;
            return vm.parameters;
        });
    }
})();

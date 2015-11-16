(function () {
    'use strict';

    angular
        .module('app.mdb')
        .controller('MDBAlgorithmsController',  MDBAlgorithmsController);

    /* @ngInject */
    function MDBAlgorithmsController(mdbService, $routeParams) {
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
        vm.mdbType = 'algorithms';

        mdbService.listAlgorithms(qname).then(function (data) {
            vm.algorithms = data;
            return vm.algorithms;
        });
    }
})();

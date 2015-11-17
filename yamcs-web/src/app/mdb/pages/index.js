(function () {
    'use strict';

    angular
        .module('app.mdb')
        .controller('MDBIndexController',  MDBIndexController);

    /* @ngInject */
    function MDBIndexController($rootScope) {
        var vm = this;
        vm.title = 'Mission Database';

        $rootScope.pageTitle = vm.title + ' | Yamcs';
    }
})();

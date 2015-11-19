(function () {
    'use strict';

    angular
        .module('yamcs.mdb')
        .controller('MDBIndexController',  MDBIndexController);

    /* @ngInject */
    function MDBIndexController($rootScope, mdbService) {
        var vm = this;
        vm.title = 'Mission Database';
        $rootScope.pageTitle = vm.title + ' | Yamcs';

        mdbService.getSummary().then(function (mdb) {
            vm.mdb = mdb;
            return vm.mdb;
        });
    }
})();

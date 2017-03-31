(function(){
    'use strict';

    angular
        .module('yamcs.mdb')
        .controller('MDBContainerDetailController', MDBContainerDetailController);

    /* @ngInject */
    function MDBContainerDetailController($rootScope, $log, $routeParams, mdbService){
        var vm = this;
        $log.log('Got into containers');
       // $rootScope.pageTitle = $routeParams.name + ' | Yamcs';
    };
})();
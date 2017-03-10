(function(){
    'use strict';

    angular.module('yamcs.mdb').controller('MDBCommandDetailController', MDBCommandDetailController);

    /* @ngInject */
    function MDBCommandDetailController($rootScope, mdbService, $routeParams, $log){
        $rootScope.pageTitle = $routeParams.name + ' | Yamcs';
        var vm = this;


        $log.log('Log of :', $routeParams['ss'])
        var urlname = '/'+$routeParams['ss']+'/'+encodeURIComponent($routeParams.name);
        vm.urlname = urlname;
        vm.has_comparison = false;
       mdbService.getCommandInfo(urlname).then( function(data){
            vm.info = data;
            if(data.constraint != undefined){
                vm.has_comparison = true;
            };
        })
        
        
    }
})();
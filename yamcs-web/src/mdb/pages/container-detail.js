(function(){
    'use strict';
    angular
        .module('yamcs.mdb')
        .controller('MDBContainerDetailController', MDBContainerDetailController);

    /* @ngInject */
    function MDBContainerDetailController($rootScope, $log, $routeParams, mdbService){
        var vm = this;
        vm.info = {};
        $log.log('Got into containers');
        var containerName = $routeParams['ss'];
        mdbService.getContainerInfo(containerName).then(function(response){
            vm.containerInfo = response.data;
            vm.info.qualifiedName = response.data.qualifiedName;
            vm.info.name = response.data.name;
        })
        $log.log(containerName);
    };
})();
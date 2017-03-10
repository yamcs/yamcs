(function(){
    'use strict';

    angular
        .module('yamcs.services')
        .controller('SERVICESIndexController', SERVICESIndexController);
        
    /* @ngInject */
    function SERVICESIndexController($rootScope, servicesService, $log, $http){
        var vm = this;
        vm.title = 'Available Services';
        $rootScope.pageTitle = vm.title + ' | Yamcs';

        vm.show = 'global';
  
        vm.sendPatch = function(service){
            $log.log( service.state);
            var state =  (service.state == "RUNNING") ? "stopped" : "running";
            
            $log.log('Strying to send patch');
            servicesService.patchService(state, service.name).then( function(resp){
                $log.info('The service got patched');
                showServicesStatus();
            })
        }

        var showServicesStatus = function(){
            servicesService.getServices().then( function( servs){
            $log.log("var", servs)
            vm.services = servs.service;
            return vm.services;
            });
        };
        
        showServicesStatus();

        servicesService.getGlobalServices().then( function( servs){
            $log.log("Got global services", servs);
            vm.global_services = servs.service;
        });

        
    };
})();
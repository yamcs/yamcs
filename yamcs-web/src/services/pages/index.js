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
            
            servicesService.patchService(state, service.name).then( function(resp){
                showServicesStatus();
            })
        }

        var showServicesStatus = function(){
            servicesService.getServices().then( function( servs){
            vm.services = servs.service;
            return vm.services;
            });
        };
        
        showServicesStatus();

        servicesService.getGlobalServices().then( function( servs){
            vm.global_services = servs.service;
        });

        
    };
})();
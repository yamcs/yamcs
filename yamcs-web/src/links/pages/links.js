(function(){
    'use strict';

    angular
        .module('yamcs.links')
        .controller('LINKSIndexController', LINKSIndexController);

    /* @ngInject */

    function LINKSIndexController($rootScope, linksService, $log, $http ,$interval, $scope){
        var vm = this;
        vm.title = 'Links';
        $rootScope.pageTitle = vm.title + ' | Yamcs';
        
        //Websocket subscription
        vm.active_links = []; 
        vm.active_links = linksService.getActiveLinks();
        // Memorize the packets from previous interval
        var _memoryPackets = {};

        vm.patchLink = function(name, currentStatus){
            var status = (currentStatus == 'OK') ?  'disabled': 'enabled';
            linksService.patchLinks(name, status).then( function(response){
                //$log.info('Update status', response)
            })
        }

        var checkPackets = function(){
            var name = null;
            var data = null;
            var delta = null;
            for(var i=0; i < vm.active_links.length; i++){
        //Verify the name in the dictionary for old packets value
                name = vm.active_links[i].name;
                data = vm.active_links[i].dataCount;
                delta = 0;
                if( _memoryPackets[name] == undefined){
                    _memoryPackets[name] = data;
                }else
        //Verify the dif in packets, if there is one, highlight, if no, unhighlight
                {
                    delta = data - _memoryPackets[name];
                    if(delta > 0){
                       vm.active_links[i].highlight = true;
                       _memoryPackets[name] = data;                       
                    }else{
                        vm.active_links[i].highlight = false;
                    }
                }
            };
        };

        vm.highlightTimer = $interval( checkPackets,1500);

        //Cancel subscription and interval on route change
        $scope.$on('$destroy', function(){
            vm.active_links.length=0;
            linksService.unsubscribeUpstream();
            $interval.cancel(vm.highlightTimer);
            
        });
    }
})();
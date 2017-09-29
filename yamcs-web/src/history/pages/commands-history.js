(function(){
    'use strict';

    angular.module('yamcs.history').controller('HistoryController', HistoryController);

    /* @ngInject */
    function HistoryController($rootScope, $log, historyService){

        var vm= this;
        vm.history = historyService.getHistory();
        vm.headers = [];

        vm.scrollOptions= {
            disable:false,
            use_bottom: true,
            show_loader: false
        }
        historyService.downloadHistory({
                limit: 20
            }).then(function(resp){
                
            })
            
        vm.verifyStatusClass = function(data){
            if(data == undefined){
                return false;
            }
            if(data.status != undefined && data.time != undefined){
                if(data.status.search(/OK/i)>-1){
                    return true;
                }else{
                    return false;
                }
            }else{
                return false;
            }

        }
        vm.getDelay = function(t1, t2){
            if(t1==undefined || t2==undefined ){
                return 'N/A';
            }
            var delta = t1-t2-36000;
            if(delta >= 0){
                return delta;
            }
        }
        
        vm.loadMore = function(){
            vm.scrollOptions.show_loader = true;
            if(vm.history != undefined){
                if( vm.history.data != undefined){
                    if((vm.history.data.length-1)>0){
                        var finalCommand = vm.history.data[vm.history.data.length - 1];
                        historyService.downloadHistory({
                                limit:5,
                                stop: finalCommand.info['time']
                        }).then(function(res){
                            vm.scrollOptions.show_loader = false;
                        })
                    }
                }

            }
            $log.log('HISTORY LENGTH', vm.history.data.length);
            vm.scrollOptions.disable = true;
        }
    
        
    }
})();
(function(){
    'use strict';

    angular.module('yamcs.history').controller('HistoryController', HistoryController);

    /* @ngInject */
    function HistoryController($rootScope, $log, historyService){

        var vm= this;
        vm.history = historyService.getHistory();

        vm.verifyStatusClass = function(data){
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
        $log.log('Loaded history controller', vm.history);
    }
})();
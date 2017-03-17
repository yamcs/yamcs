(function(){
    'use strict';

    angular.module('yamcs.mdb').controller('MDBCommandDetailController', MDBCommandDetailController);

    /* @ngInject */
    function MDBCommandDetailController($rootScope, mdbService, $routeParams, $log){
        $rootScope.pageTitle = $routeParams.name + ' | Yamcs';
        var vm = this;
        
        var urlname = '/'+$routeParams['ss']+'/'+encodeURIComponent($routeParams.name);
        vm.urlname = urlname;
        vm.has={
            _comparison:false,
            _baseCommand:false
        };

       mdbService.getCommandInfo(urlname).then( function(data){
            vm.info = data;
            if(data.constraint != undefined){
                vm.has._comparison = true;
            };
            if(data.baseCommand != undefined){
                vm.has._baseCommand = true;
                vm.baseCommand = toFlatToList(data, 'baseCommand');
                vm.sendCommand = constructCommand();
            }
        });

        vm.stringToOperator = function(string_op){
                var operator = '';
                switch( string_op){
                    case 'EQUAL_TO':
                        operator = '=';
                        break;
                    case 'NOT_EQUAL_TO':
                        operator = '!=';
                        break;
                    case 'GREATER_THAN':
                        operator = ">";
                        break;
                    case 'GREATER_THAN_OR_EQUAL_TO':
                        operator = ">=";
                        break;
                    case 'SMALLER_THAN':
                        operator = "<";
                        break;
                    case 'SMALLER_THAN_OR_EQUAL_TO':
                        operator = "<=";
                        break;
                    default:
                        operator = "#?"
                        $log.error('Not such operator', string_op);
                        break;
                }
                return operator;
        };        
        
        vm.getArgumentValue = function(argName){
            var cmds = vm.baseCommand;
            var list = [];
            for(var i=0; i<cmds.length; i++){
                if(cmds[i]['argumentAssignment'] != undefined){
                    list = list.concat(cmds[i]['argumentAssignment']);
                }
            };
            for(var i=0; i<list.length; i++){
                if(argName === list[i].name){
                    return list[i].value;
                }
            }
            return 'N/A'
        };
        var toFlatToList = function( input, objectToFlat ){
            var flatList = [];
            var toFlat = input;
            var hasChild = true;
            var depth = 0;
            do{
                var tempItem = {};
                for(var key in toFlat){
                    if (key != objectToFlat){
                        tempItem[key] = toFlat[key];
                    }
                }
                flatList.push(tempItem);
                if(toFlat[objectToFlat] == undefined){
                    hasChild = false;
                }else{
                    toFlat = toFlat[objectToFlat];
                };
            }while(hasChild);

            return flatList;
        };

        var constructCommand = function(){
            var commands = [];
            for(var i=0; i < vm.baseCommand.length; i++){
                if(vm.baseCommand[i].abstract == false){
                    commands = commands.push(vm.baseCommand[i].argument);
                }
            }
            return commands;            
        };

        vm.triggerCommand = function(){
            var url = vm.urlname;
            mdbService.sendCommand(url, vm.sendCommand).then( function(data){
                $log.log('Success', data);
            }).catch( function(msg){
                $log.error("XHR Error");
            });
        };
    }
})();
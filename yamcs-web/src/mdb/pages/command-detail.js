(function(){
    'use strict';

    angular.module('yamcs.mdb').controller('MDBCommandDetailController', MDBCommandDetailController);

    /* @ngInject */
    function MDBCommandDetailController($rootScope, mdbService, $routeParams, $log){
        $rootScope.pageTitle = $routeParams.name + ' | Yamcs';
        var vm = this;


        vm.commands = [];

        var urlname = '/'+$routeParams['ss']+'/'+encodeURIComponent($routeParams.name);
        vm.urlname = urlname;
        vm.has = {
            _comparison:false,
            _baseCommand:false,
            _trigger:true,
            _sentMsg:false
        };

       mdbService.getCommandInfo(urlname).then( function(data){
            vm.info = data;
            if(data.constraint != undefined){
                vm.has._comparison = true;
            };
            if(data.baseCommand != undefined){
                vm.has._baseCommand = true;
                vm.baseCommand = toFlatToList(data, 'baseCommand');
                constructCommand();
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
            for(var i=0; i < vm.baseCommand.length; i++){
                var tempCommand = {};
                var commandFrom = null;
                if(vm.baseCommand[i].abstract == false && vm.baseCommand[i].argument != undefined){
                    for(var j = 0; j<vm.baseCommand[i].argument.length; j++){
                        
                    commandFrom = vm.baseCommand[i].argument[j];
                    tempCommand = {
                        'name':commandFrom.name,
                        'desc':commandFrom.description,
                        'type':commandFrom.type.engType,
                        'value':'',
                        'options':[]
                    }
                    if( commandFrom.type.rangeMin != undefined){
                        tempCommand['options']['min']= commandFrom.type.rangeMin;
                    }
                    if( commandFrom.type.rangeMax != undefined){
                        tempCommand['options']['max']= commandFrom.type.rangeMax;
                    }
                    if( commandFrom.type.engType === 'enumeration'){
                        $log.info('COMMAND FROM', commandFrom);
                        tempCommand['options']['range'] = commandFrom.type.enumValue;
                    }
                    vm.commands.push(tempCommand);
                    }
                }
            } 
            if( vm.commands.length ==0 ){
                vm.has._trigger=false;
            }   
        };

        vm.triggerCommand = function(){
            vm.has._sentMsg = false;
            var url = vm.urlname;
            mdbService.sendCommand(url, generateRequests(vm.commands)).then( function(data){
                vm.has._sentMsg = true;
                vm.sentMsg= data;
            }).catch( function(msg){
                $log.error("XHR Error");
            });

        };

        var generateRequests = function(commands){
            var requests = []
            for(var i=0; i<commands.length; i++){
                var tempRequest = {};
                tempRequest.name = commands[i].name;
                $log.log('Type of value', typeof(commands[i]))
                if( typeof (commands[i].value) === 'string'){
                    tempRequest.value = commands[i].value;                  
                }else{
                    tempRequest.value =JSON.stringify(commands[i].value) ; 
                }               
                
                if(tempRequest.value === ''){
                    $log.error('Empty value to send');
                }else 
                    requests.push(tempRequest);
            }
            return requests;
        }

        vm.getRequestType = function(type){
            var supportedType= {
                'string':'text',
                'integer':'number'
            }
            if(supportedType[type] == undefined){
                return 'hidden'
            }else
                return supportedType[type];
        };


        vm.testFunction = function(){
            $log.log('FUNCTION TESTED ####');
        };
    }
})();
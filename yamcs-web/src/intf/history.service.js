(function(){
    'use strict';

    angular.module('yamcs.intf').factory('historyService',historyService);

    /* @ngInject */
    function historyService($rootScope, $http, socket, yamcsInstance, $log){
        var history={status:'', data:[], dynamic_hops:{}};
        var historyIn = {};
        var oldData= [];
        socket.on('open', function () {
            subscribeUpstream();
        });
        if (socket.isConnected()) {
            subscribeUpstream();
        }        
        
        return {
            getHistory : getHistory,
            getOldHistory : getOldHistory
        };

        function getHistory(){
            return history;
        }

        function subscribeUpstream(){
            socket.on('CMD_HISTORY', function(data){
                history.status = 'Initiated';
                var oldData = false;
                verifyData(data ,oldData);
            });

            socket.emit("cmdhistory", 'subscribe', {}, null, function(et, msg){
                $log.log('Failed subscribe ', et, ' ', msg);
            });
        }
        function verifyData(data, oldData){
                var id = data.commandId.generationTime+data.commandId.origin+data.commandId.sequenceNumber+data.commandId.commandName;
                var id_hash = hashCode(id);
                if( historyIn[id_hash] == undefined){
                    historyIn[id_hash] = true;  
                                            
                    var historyData = {
                        'id':id_hash,
                        'info':{
                            time: data.commandId.generationTime-36000,
                            source:data.commandId.origin,
                            name:data.commandId.commandName,
                            sequence:data.commandId.sequenceNumber
                        },
                        'hops':{}
                    }

                    history.data.push(historyData);
                    if(oldData){
                        addHop(data, id_hash);
                    }
                }else{
                    addHop(data,id_hash);
                }
        }
        
        function hashCode(str){
            var hash = 0;
            if (str.length == 0) return hash;
            for (var i = 0; i < str.length; i++) {
                var char = str.charCodeAt(i);
                hash = ((hash<<5)-hash)+char;
                hash = hash & hash; // Convert to 32bit integer
            }
            return hash;
        }

        function addHop(data,hash){
            //Goes through the currently available commands history, if ID matched adds the hop to it
            for(var i=0; i<history.data.length; i++ ){
               if(hash == history.data[i].id ){
                   for(var aIndex = 0; aIndex <data.attr.length; aIndex++){
                    var name = parseHopName(data.attr[aIndex].name);
                    if(name != undefined){
                        if(history.data[i].hops[name] == undefined){
                            history.data[i].hops[name] ={};
                        }
                            if( data.attr[aIndex].value.stringValue != undefined){
                                history.data[i].hops[name].status = data.attr[aIndex].value.stringValue;
                            }else if( data.attr[aIndex].value.timestampValue != undefined){
                                history.data[i].hops[name].time = data.attr[aIndex].value.timestampValue;
                            }                
                    }  
                   }           

               }
            }
        }
        
        function parseHopName(name){
            // If the name is not a static name from dictionary, verifies if the hop name is between underscores
            var not_parsed = {
                TransmissionConstraints:true,
                Final_Sequence_Count:true,
                username:true,
                binary:true,
                source:true
            }
            if(not_parsed[name] == true){
                return undefined;
            }else{                
                var reg_ex = /_(.*?)_/g;
                var hop = reg_ex.exec(name);
                //In case it was a static one
                if(hop[1] == undefined){
                    return undefined
                }else{
                    history.dynamic_hops[hop[1]]=hop[1];
                    return hop[1]; 
                }
            }
        }
        
        function getOldHistory(){
            downloadHistory().then(function(data){
                var oldData = true;
                for(var i=0; i < data.length; i++){
                    verifyData(data[i], oldData);
                }
            })
        }
        function downloadHistory(){

        //->:: TO CHANGE
        //->:: /api/mdb/simulator/commands/YSS/SIMULATOR/DUMP_RECORDING
        var str = '/YSS/SIMULATOR/DUMP_RECORDING';
            var targetUrl = '/api/archive/'+yamcsInstance+'/commands'+str;
            return $http.get(targetUrl).then( function( oldCommands){
                return oldCommands.data.entry; 
            }).catch(function (message){
                $log.error('XHR failed', message);
            });
        };
        
    }
})();
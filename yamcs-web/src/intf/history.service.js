(function(){
    'use strict';

    angular.module('yamcs.intf').factory('historyService',historyService);

    /* @ngInject */
    function historyService($rootScope, $http, socket, yamcsInstance, $log){
        var history={status:'', data:[] };
        var historyIn = {};

        socket.on('open', function () {
            subscribeUpstream();
        });
        if (socket.isConnected()) {
            subscribeUpstream();
        }        
        
        return {
            getHistory:getHistory
        };

        function getHistory(){
            return history;
        }

        function subscribeUpstream(){
            socket.on('CMD_HISTORY', function(data){
                history.status = 'Initiated';
                var id = data.commandId.generationTime+data.commandId.origin+data.commandId.sequenceNumber+data.commandId.commandName;
                var id_hash = hashCode(id);
                $log.log(id_hash);
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

                    $log.log('Delta time ', historyData.info.time- data.commandId.generationTime);
                    history.data.push(historyData);
                }else{
                    addHop(data,id_hash);
                }
            });

            socket.emit("cmdhistory", 'subscribe', {}, null, function(et, msg){
                $log.log('Failed subscribe ', et, ' ', msg);
            });
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
            for(var i=0; i<history.data.length; i++ ){
               if(hash == history.data[i].id ){
                   var name = parseHopName(data.attr[0].name);
                   if(name !=''){
                       if(history.data[i].hops[name] == undefined){
                           history.data[i].hops[name] ={};
                       }
                        if( data.attr[0].value.stringValue != undefined){
                           history.data[i].hops[name].status = data.attr[0].value.stringValue;
                        }else if( data.attr[0].value.timestampValue != undefined){
                           history.data[i].hops[name].time = data.attr[0].value.timestampValue;
                        }                
                   }                

               }
            }
        }

        function parseHopName(name){
            var not_parsed = {
                TransmissionConstraints:true,
                Final_Sequence_Count:true
            }
            if(not_parsed[name] == true){
                return '';
            }else{                
                var reg_ex = /_(.*?)_/g;
                var hop = reg_ex.exec(name);
                return hop[1];
            }
        }
        
    }
})();
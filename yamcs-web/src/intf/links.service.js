(function (){
    'use strict';

    angular
        .module('yamcs.intf')
        .factory('linksService', linksService);

        var activeLinks = [];
        /* @ngInject */
        function linksService($rootScope, $http, $log, socket, yamcsInstance){
            socket.on('open', function(){
                        subscribeUpstream();
                });
            if (socket.isConnected()){
                    subscribeUpstream();
                };

        return {
            getAllListLinks: getAllListLinks,
            getActiveLinks: getActiveLinks,
            patchLinks: patchLinks,
            unsubscribeUpstream: unsubscribeUpstream

        }
        function getActiveLinks(){
                return activeLinks;
        };

        function getAllListLinks(){
            var targetUrl = '/api/links';

            return $http.get(targetUrl).then( function(response){
                $log.info('all links', response);
                return response.data;
            }).catch( function(message){
                $log.error('XHR failed', message);
            });
        };


        function subscribeUpstream(){
            socket.on('LINK_EVENT', function(data){
                //$log.log('Getting some data', data);
                var linkInfo = data["linkInfo"];
                if(data.type == 'REGISTERED'){
                    $log.log('The socket is registered', data);
                    linkInfo.highlight = false;
                    activeLinks.push(linkInfo);
                }
                else if(data.type == 'UPDATED'){
                    for(var i = 0; i < activeLinks.length; i++){
                        if( activeLinks[i].name == linkInfo.name){
                            activeLinks[i].dataCount = linkInfo.dataCount;
                            activeLinks[i].detailedStatus = linkInfo.detailedStatus;
                            activeLinks[i].status = linkInfo.status;
                            activeLinks[i].disabled = linkInfo.disabled;
                        }
                    }
                }
                else if(data.type =='UNREGISTERED'){
                        for (var i=0; i< activeLinks.length; i++){
                            if( activeLinks[i].name == linkInfo.name){
                                activeLinks.splice(i,1);
                            }
                        };
                }                
            })

            socket.emit('links', 'subscribe', {}, null, function(et, msg){
                $log.log('Failed Subscribe', et, '', msg);
            });
        };

        function patchLinks(name, status){
            var targetUrl = '/api/links/' + yamcsInstance + '/link/' + name;
            var update = {"state" : status};
            return $http.patch(targetUrl, update).then( function(response){
                return response.data;
            }).catch( function(message){
                $log.error('XHR failed', message);
            });
        };

        function unsubscribeUpstream(){
            $log.log("Unsubscribing from the websocket");
            socket.emit('links', 'unsubscribe', {}, null, function(et, msg){
                $log.log('Failed Unsubscribing from the event', et, '', msg);
            });
        };
    }
})();
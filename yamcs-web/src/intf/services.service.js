(function (){
    'use strict';

    angular
        .module('yamcs.intf')
        .factory('servicesService', servicesService);

    /* @ngInject */
    function servicesService($http, yamcsInstance, $log){
        return{
            getServices: getServices,
            getGlobalServices: getGlobalServices,
            patchService: patchService
        };

    function getServices(){
        var targetUrl = '/api/services/'+yamcsInstance;

       return $http.get(targetUrl).then(function (response){
            var services = response.data;
            return services;
        }).catch( function(msg){
            $log.error('XHR failed', msg);
        })
    };
    function patchService(state, name, global){
            var targetUrl='';

             targetUrl = '/api/services/' + yamcsInstance+'/'+name;
            

            var data = {
                "state":state
            }
            return $http.patch(targetUrl, data).then(function (resp){
            }).catch( function(msg){
                $log.error('XHR failed', msg);
            })
    };
    function getGlobalServices(){
        var targetUrl = '/api/services/_global'

        return $http.get(targetUrl).then(function(response){
            var services = response.data;
            return services;
        }).catch( function(msg){
            $log.error('XHR failed', msg);
        })
    };
}

})();
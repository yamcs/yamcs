(function(){
    'use strict';

    angular.module('yamcs.history', []).config(configure);

    /* @ngInject */
    function configure($routeProvider){
        $routeProvider.when('/:instance/history',{
            templateUrl: '/_static/_site/history/pages/commands-history.html',
            controller: 'HistoryController',
            controllerAs: 'vm'
        });
    }
} )();
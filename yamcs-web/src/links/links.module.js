(function() {
    'use strict';

    angular
        .module('yamcs.links', ['yamcs.core'])
        .config(configure);

    /* @ngInject */
    function configure($routeProvider){
        $routeProvider.when('/:instance/links',{
            templateUrl: '/_static/_site/links/pages/links.html',
            controller: 'LINKSIndexController',
            controllerAs: 'vm'
        });
    }

})();
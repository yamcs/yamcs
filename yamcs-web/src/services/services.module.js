(function()
    {'use strict';

    angular
        .module('yamcs.services', ['yamcs.core'])
        .config(configure);

    /* @ngInject */
    function configure($routeProvider){
        $routeProvider.when('/:instance/services', {
            templateUrl: '/_static/_site/services/pages/index.html',
            controller: 'SERVICESIndexController',
            controllerAs: 'vm'
        });
    }
})();
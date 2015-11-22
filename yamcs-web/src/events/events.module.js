(function() {
    'use strict';

    angular
        .module('yamcs.events', [])
        .config(configure);

    /* @ngInject */
    function configure($routeProvider) {
        $routeProvider.when('/:instance/events', {
            templateUrl: '/_static/_site/events/pages/events.html',
            controller: 'EventsController',
            controllerAs: 'vm'
        });
    }
})();

(function() {
    'use strict';

    angular
        .module('yamcs.displays', ['yamcs.core', 'yamcs.uss'])
        .directive('displaysPane', displaysPane)
        .directive('embeddedDisplay', embeddedDisplay)
        .config(configure);

    /* @ngInject */
    function displaysPane() {
        return {
            restrict: 'E',
            transclude: true,
            scope: {
                activePane: '@',
                headerTitle: '@',
                yamcsInstance: '=',
                standalone: '=',
                shell: '='
            },
            templateUrl: '/_static/_site/displays/displays.template.html'
        };
    }

    /* @ngInject */
    function embeddedDisplay(displaysService, tmService, ussService) {
        return {
            restrict: 'A',
            scope: { ref: '@' },
            link: function(scope, elem, attrs) {
                if (!elem.data('spinner')) {
                    elem.data('spinner', new Spinner());
                }
                elem.data('spinner').spin(elem[0]);

                displaysService.getDisplay(attrs['ref']).then(function(sourceCode) {
                    ussService.drawDisplay(sourceCode, elem, function(display) {

                        tmService.subscribeParameters(display.parameters);
                        tmService.subscribeComputations(display.parameters);

                        // 'Leak' canvas color
                        // This should not be done here. But I'm not yet fully understanding angular
                        // intricacities when it comes to scopes, directives and DOM manipulations.
                        //elem.parents('.main').css('background-color', display.bgcolor);
                        $('body').css('background-color', display.bgcolor);
                        elem.data('spinner').stop();
                    });
                });

                scope.$on('yamcs.tm.pvals', function(event, data) {
                    for(var i = 0; i < data.length; i++) {
                        var p = data[i];
                        var dbs = tmService.subscribedParameters[p.id.name];
                        if (!dbs) {
                            //$log.error('Cannot find bindings for '+ p.id.name, tmService.subscribedParameters);
                            continue;
                        }
                        for (var j = 0; j < dbs.length; j++) {
                            dbs[j].updateWidget(p);
                        }
                    }
                });

                scope.$on('$destroy', function() {
                    console.log('destroy on embedded display');

                    // Return intentionally leaked background color to the default
                    $('body').css('background-color', '');

                    // TODO
                    //unsubscribe
                });
            }
        }
    }

    /* @ngInject */
    function configure($routeProvider) {
        $routeProvider.when('/:instance/displays', {
            templateUrl: '/_static/_site/displays/pages/displays.html',
            controller: 'DisplaysController',
            controllerAs: 'vm'
        }).when('/:instance/displays/:display*', {
            templateUrl: '/_static/_site/displays/pages/display.html',
            controller: 'DisplayController',
            controllerAs: 'vm'
        });
    }
})();

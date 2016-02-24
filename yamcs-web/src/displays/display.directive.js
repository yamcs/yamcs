(function() {
    'use strict';

    /*
        The embedded display directive creates a display instance that holds its
        own state (bindings) in local angular scope.
        Currently still TODO is to unsubscribe in a way that would not impact
        potential other parallel subscriptions
     */
    angular.module('yamcs.displays').directive('display', displayDirective);

    /* @ngInject */
    function displayDirective(displaysService, tmService, ussService) {
        return {
            restrict: 'A',
            scope: { ref: '@' },
            link: function(scope, elem, attrs) {

                var displayWidget;

                if (!elem.data('spinner')) {
                    elem.data('spinner', new Spinner({color: '#ccc'}));
                }
                elem.data('spinner').spin(elem[0]);

                displaysService.getDisplay(attrs['ref']).then(function(sourceCode) {
                    ussService.drawDisplay(sourceCode, elem, function(display) {
                        displayWidget = display;

                        tmService.subscribeParameters(display.getParameters());
                        tmService.subscribeComputations(display.getComputations());

                        // 'Leak' canvas color
                        // This should not be done here. But I'm not yet fully understanding angular
                        // intricacities when it comes to scopes, directives and DOM manipulations.
                        //elem.parents('.main').css('background-color', display.bgcolor);
                        $('body').css('background-color', display.bgcolor);
                        elem.data('spinner').stop();
                    });
                });

                scope.$on('yamcs.tm.pvals', function(event, data) {
                    if (displayWidget) {
                        displayWidget.updateBindings(data);
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
})();

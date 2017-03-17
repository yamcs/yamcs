(function (){
    'use strict';

    angular
        .module('yamcs.links')
        .directive('linksPane', function(){
            return{
            retrict: 'E',
            transclude: true,
            scope: {
                activePane:'@',
                yamcsInstance: '=',
                standalone: '=',
                shell: '='
            }, 
            templateUrl: '/_static/_site/mdb/links.template.html'
            };
        });

})();
(function (){
    'use strict';

    angular.module('yamcs.mdb').directive('dynamicForm', function(){
        var supported = { 
            'test':{element: 'input', type: 'text', editable:true, textBase:true}
        };

        return{
                    restrict:"E",
                    require:'^ngModel',                   
                    controller: 'MDBFormController',
                    scope:{
                        ngModel:'=',
                        request:'@',
                        request2:'=',
                        send:'&'
                    },                   
                    templateUrl: '/_static/_site/mdb/mdb.dynamics.html'
                }
    });
})();
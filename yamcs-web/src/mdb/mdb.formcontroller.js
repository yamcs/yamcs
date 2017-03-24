(function (){
    'use strict';

    angular.module('yamcs.mdb')
    .controller('MDBFormController', MDBFormController);

    /* @ngInject */
    function MDBFormController($log, $scope){
        

        console.log('LOG' , $scope.request);
    }

})();
(function() {
    'use strict';

    angular
        .module('yamcs.core')
        .factory('configService', configService);

    /* @ngInject */
    function configService(yamcsInstance, remoteConfig) {

        var config = remoteConfig;
        config['title'] = remoteConfig['title'] || 'Untitled';
        config['yamcsInstance'] = yamcsInstance;

        if (config.hasOwnProperty('brandImage')) {
            config['brandImage'] = '/_static/' + yamcsInstance + '/' + config['brandImage'];
        }

        return {
            getYamcsInstance: getYamcsInstance,
            get: get
        };

        function getYamcsInstance() {
            return yamcsInstance;
        }

        function get(key, defaultValue) {
            if (config.hasOwnProperty(key)) {
                return config[key];
            } else {
                return defaultValue;
            }
        }
    }
})();

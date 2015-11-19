(function () {
    angular.module('yamcs.core')

    .filter('cut', function () {
        return function (value, wordwise, max, tail) {
            if (!value) return '';

            max = parseInt(max, 10);
            if (!max) return value;
            if (value.length <= max) return value;

            value = value.substr(0, max);
            if (wordwise) {
                var lastspace = value.lastIndexOf(' ');
                if (lastspace != -1) {
                    value = value.substr(0, lastspace);
                }
            }

            return value + (tail || ' …');
        };
    })

    .filter('slice', function () {
        return function (value, start, end) {
            if (!value) return '';
            return value.slice(start, end);
        };
    })

    .filter('capitalize', function () {
        return function (value) {
            if (!value) return '';
            return value.charAt(0).toUpperCase() + value.slice(1).toLowerCase();
        };
    })

    .filter('nvl', function () {
        return function (value, ifNoValue) {
            if (!value) return ifNoValue;
            return value;
        };
    })

    .filter('stripHTML', function() {
        return function(text) {
            if (!text) return '';
            return String(text).replace(/<[^>]+>/gm, '');
        }
     })

    .filter('startsWith', function() {
        return function(haystack, needle) {
            if (!haystack || !needle) return false;
            return haystack.slice(0, needle.length) === needle;
        }
     })

    .filter('join', function () {
        return function (arr, sep) {
            sep = sep || ' ';
            if (!arr) return '';
            var result = '';
            for (var i = 0; i < arr.length; i++) {
                if (i != 0) result += sep;
                result += arr[i];
            }
            return result;
        };
    })

    .filter('joinBy', function () {
        return function (arr, key, sep) {
            sep = sep || ' ';
            if (!arr) return '';
            var result = '';
            for (var i = 0; i < arr.length; i++) {
                if (i != 0) result += sep;
                result += arr[i][key];
            }
            return result;
        };
    })

    .filter('nl2br', function() {
        return function(data) {
            if (!data) return data;
            return data.replace(/\n\r?/g, '<br />');
        };
    })

    /*
        Returns whether an object has a property. This is sometimes more
        useful than the default if-behaviour, because if a property value is 0,
        the if would not resolve.
     */
    .filter('has', function () {
        return function (obj, prop) {
            return obj && obj.hasOwnProperty(prop);
        };
    })

    /*
        Parses a string value to a UTC Moment
     */
    .filter('parseUTC', function() {
        return function(value) {
            if (!value) return value;
            return moment.utc(value);
        };
    })

    /*
        Formats a Moment as UTC or as Local time.
        Currently not using moment-timezone, because of limitations of
        dygraphs. (only local or utc time are supported)
     */
    .filter('formatDate', /* @ngInject */ function(configService) {
        return function(value) {
            if (!value) return value;
            if (configService.get('utcOnly')) {
                return value.format();
            } else {
                return value.clone().local().format();
            }
        };
    })
})();

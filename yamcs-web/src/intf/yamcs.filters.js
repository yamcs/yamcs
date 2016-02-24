(function () {
    angular.module('yamcs.intf')

    /*
        Returns true if the value string is part of the same XTCE space/sub system
        base on qualified names.
     */
    .filter('sameSpaceSystem', function () {
        return function (value, otherValue) {
            if (!value || !otherValue) return false;
            var a = value.slice(0, value.lastIndexOf('/'));
            var b = otherValue.slice(0, otherValue.lastIndexOf('/'));
            return a === b;
        };
    })

    /*
        Returns the space system for the fully qualified XTCE name
     */
    .filter('spaceSystem', function () {
        return function (value) {
            if (!value) return '';
            return value.slice(0, value.lastIndexOf('/'));
        };
    })

    /*
        Returns the short name for the given fully qualified XTCE name
     */
    .filter('name', function () {
        return function (value) {
            if (!value) return '';
            return value.slice(value.lastIndexOf('/')+ 1);
        };
    })

    /*
        Outputs the string value of a pval
     */
    .filter('stringValue', function () {
        return function (param, usingRaw) {
            if (!param) return '';
            if(usingRaw) {
                var rv = param.rawValue;
                if (rv === undefined) {
                    return '';
                }
                var res = '';
                if (rv['type'] === 'STRING') res += '\'';
                for(var idx in rv) {
                    if(idx!='type') res += rv[idx];
                }
                if (rv['type'] === 'STRING') res += '\'';
                return res;
            } else {
                var ev=param.engValue;
                if(ev === undefined) {
                    console.log('got parameter without engValue: ', param);
                    return null;
                }
                switch(ev.type) {
                    case 'FLOAT':
                        return ev.floatValue;
                    case 'DOUBLE':
                        return ev.doubleValue;
                    case 'BINARY':
                        return window.atob(ev.binaryValue);
                }
                for(var idx in ev) {
                    if(idx!='type') return ev[idx];
                }
            }
        };
    })

    /*
        Outputs an up- or down-pointing arrow if the monitoring result of the monitoring result is LOW or HIGH
     */
    .filter('lohi', function() {
        return function (rangeCondition) {
            if (!rangeCondition) return '';
            switch (rangeCondition) {
                case 'LOW':
                    return ' (low)';
                    //return '<small><span class="glyphicon glyphicon-arrow-down"></span></small>';
                case 'HIGH':
                    return ' (high)';
                    //return '<small><span class="glyphicon glyphicon-arrow-up"></span></small>';
                default:
                    return '';
            }
        }
    })

    /*
        Replaces the input value with the replacemet if it is not set
     */
    .filter('nvl', function () {
        return function (value, replacement) {
            if (value === undefined || value === null) {
                return replacement;
            } else {
                return value;
            }
        };
    })

    /*
        Converts monitoringResult to a twitter bootstrap class
     */
    .filter('monitoringClass', function () {
        return function (monitoringResult) {
            if (!monitoringResult) return '';
            switch (monitoringResult) {
                case 'WATCH':
                case 'WARNING':
                case 'DISTRESS':
                case 'CRITICAL':
                case 'SEVERE':
                    return 'danger';
                case 'IN_LIMITS':
                    return 'success';
                default:
                    return 'default';
            }
        };
    })

    /*
        Converts monitoringResult to a twitter bootstrap class
     */
    .filter('monitoringValue', function () {
        return function (monitoringResult) {
            if (!monitoringResult) return '';
            switch (monitoringResult) {
                case 'WATCH': return 'Watch';
                case 'WARNING': return 'Warning';
                case 'DISTRESS': return 'Distress';
                case 'CRITICAL': return 'Critical';
                case 'SEVERE': return 'Severe';
                case 'IN_LIMITS': return 'In Limits';
                default: console.log('should handle value ' + monitoringResult); return '';
            }
        };
    })

    /*
        Converts monitoringResult to a numeric 0-5 severity level
     */
    .filter('monitoringLevel', function () {
        return function (monitoringResult) {
            if (!monitoringResult) return 0;
            switch (monitoringResult) {
                case 'WATCH':
                    return 1;
                case 'WARNING':
                    return 2;
                case 'DISTRESS':
                    return 3;
                case 'CRITICAL':
                    return 4;
                case 'SEVERE':
                    return 5;
                default:
                    return 0;
            }
        };
    })

    /*
        Converts the MDB operator type
     */
    .filter('asOperator', function () {
        return function (value) {
            if (!value) return '';
            switch (value) {
                case 'EQUAL_TO':
                    return '==';
                case 'NOT_EQUAL_TO':
                    return '!=';
                case 'GREATER_THAN':
                    return '>';
                case 'GREATER_THAN_OR_EQUAL_TO':
                    return '>=';
                case 'SMALLER_THAN':
                    return '<';
                case 'SMALLER_THAN_OR_EQUAL_TO':
                    return '<=';
                default:
                    return '??';
            }
        };
    });
})();

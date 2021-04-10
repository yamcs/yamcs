package org.yamcs.xtceproc;


import java.util.Map;

import org.yamcs.commanding.ArgumentValue;
import org.yamcs.parameter.LastValueCache;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.DataSource;

public interface MatchCriteriaEvaluator {

    public enum MatchResult {
        /**
         * condition matches
         */
        OK,
        /**
         * condition does not match
         */
        NOK,
        /**
         * matching cannot be determined because not all inputs are availalbe
         */
        UNDEF;
    }

    static class EvaluatorInput {

        /**
         * Last value for all parameters known in a processor (without command context parameters, see below).
         * <p>
         * It is never null.
         */
        final LastValueCache lastValueCache;

        /**
         * parameters which are just being extracted from a packet, may be incomplete. After the extraction, these are
         * moved to thelastValueCache.
         * <p>
         * Can be null.
         */
        ParameterValueList params;
        /**
         * when running in a context of a verifier or transmission constraint, the cmdArgs and cmdPArams will not be
         * null and will hold information about the command which is being sent. It will be used for checking argument
         * values and parameters of data source {@link DataSource#COMMAND}
         * <p>
         * when running outside of a command verifier or transmission constraint, they will be null. In this case it is
         * assumed that the database is validated and the match criteria will not hold references to arguments or
         * parameters with data source set to COMMAND or COMMAND_HISTORY.
         */
        Map<Argument, ArgumentValue> cmdArgs;
        ParameterValueList cmdParams;

        /**
         * when running in a context of a verifier, the cmdHistParams will not be null and will hold pseudo-parameters
         * related to the command history. It will be used for checking parameters of data source
         * {@link DataSource#COMMAND_HISTORY}
         * <p>
         * when running outside of a command verifier, it will be null
         * 
         */
        ParameterValueList cmdHistParams;

        public EvaluatorInput(LastValueCache lastValueCache) {
            this.lastValueCache = lastValueCache;
        }

        public EvaluatorInput(LastValueCache lastValueCache, Map<Argument, ArgumentValue> cmdArgs,
                ParameterValueList cmdParams) {
            this.lastValueCache = lastValueCache;
            this.cmdArgs = cmdArgs;
            this.cmdParams = cmdParams;
        }

        public EvaluatorInput(ParameterValueList params, LastValueCache lastValueCache) {
            this.params = params;
            this.lastValueCache = lastValueCache;
        }


        public void setCmdHistParams(ParameterValueList cmdHistParams) {
            this.cmdHistParams = cmdHistParams;
        }

        public void setParams(ParameterValueList params) {
            this.params = params;
        }

        public ParameterValueList getCmdHistParams() {
            return cmdHistParams;
        }

        @Override
        public String toString() {
            return "EvaluatorInput [params=" + params
                    + ", cmdArgs=" + ((cmdArgs == null) ? null : cmdArgs.values())
                    + ", cmdParams=" + cmdParams
                    + ", cmdHistParams=" + cmdHistParams + "]";
        }
    }

    MatchResult evaluate(EvaluatorInput input);
}
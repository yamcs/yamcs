package org.yamcs.http.api;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.Value;
import org.yamcs.parameterarchive.ConsumerAbortException;
import org.yamcs.utils.ValueUtility;

/**
 * Expected class type for use with {@link org.yamcs.http.api.ReplayFactory} Adds functionality for stopping a replay,
 * and has support for pagination
 */
public abstract class AbstractPaginatedParameterRetrievalConsumer {

    protected final boolean paginate;
    protected final long pos;
    protected final int limit;

    protected int rowNr = 0; // zero-based
    protected int emitted = 0;

    protected boolean noRepeat = false;
    protected Value lastValue;

    public AbstractPaginatedParameterRetrievalConsumer() {
        this(-1, -1);
    }

    public AbstractPaginatedParameterRetrievalConsumer(long pos, int limit) {
        if (pos == -1 && limit == -1) {
            paginate = false;
            this.pos = pos;
            this.limit = limit;
        } else {
            paginate = true;
            this.pos = Math.max(pos, 0);
            this.limit = Math.max(limit, 0);
        }
    }

    public void setNoRepeat(boolean noRepeat) {
        this.noRepeat = noRepeat;
    }

    static abstract class PaginatedSingleParameterRetrievalConsumer extends AbstractPaginatedParameterRetrievalConsumer
            implements Consumer<ParameterValueWithId> {
        public PaginatedSingleParameterRetrievalConsumer(long pos, int limit) {
            super(pos, limit);
        }

        public PaginatedSingleParameterRetrievalConsumer() {
            this(-1, -1);
        }

        @Override
        public void accept(ParameterValueWithId pvwid) {
            pvwid = prefilter(pvwid);
            if (pvwid == null) {
                return;
            }

            pvwid = filter(pvwid);
            if (pvwid == null) {
                return;
            }

            if (paginate) {
                if (rowNr >= pos) {
                    if (emitted < limit) {
                        emitted++;
                        onParameterData(pvwid);
                    } else {
                        throw new ConsumerAbortException();
                    }
                }
                rowNr++;
            } else {
                onParameterData(pvwid);
            }
        }


        public ParameterValueWithId filter(ParameterValueWithId pvwid) {
            return pvwid;
        }

        // Default filtering. Not overridable by implementations
        private ParameterValueWithId prefilter(ParameterValueWithId pvwid) {
            if (noRepeat) {
                ParameterValue pval = pvwid.getParameterValue();
                if (!ValueUtility.equals(lastValue, pval.getEngValue())) {
                    lastValue = pval.getEngValue();
                    return pvwid;
                } else {
                    return null;
                }
            } else {
                return pvwid;
            }
        }

        protected void onParameterData(ParameterValueWithId pvwid) {
        }

    }

    static abstract class PaginatedMultiParameterRetrievalConsumer extends AbstractPaginatedParameterRetrievalConsumer
            implements Consumer<List<ParameterValueWithId>> {
        public PaginatedMultiParameterRetrievalConsumer(long pos, int limit) {
            super(pos, limit);
        }

        public PaginatedMultiParameterRetrievalConsumer() {
            this(-1, -1);
        }
        @Override
        public void accept(List<ParameterValueWithId> params) {
            params = prefilter(params);
            if (params == null) {
                return;
            }

            params = filter(params);
            if (params == null) {
                return;
            }

            if (paginate) {
                if (rowNr >= pos) {
                    if (emitted < limit) {
                        emitted++;
                        onParameterData(params);
                    } else {
                        throw new ConsumerAbortException();
                    }
                }
                rowNr++;
            } else {
                onParameterData(params);
            }
        }

        // Default filtering. Not overridable by implementations
        private List<ParameterValueWithId> prefilter(List<ParameterValueWithId> params) {
            if (noRepeat) {
                List<ParameterValueWithId> plist = new ArrayList<>();

                for (ParameterValueWithId pvalid : params) {
                    ParameterValue pval = pvalid.getParameterValue();
                    if (!ValueUtility.equals(lastValue, pval.getEngValue())) {
                        plist.add(pvalid);
                    }
                    lastValue = pval.getEngValue();
                }
                return (plist.size() > 0) ? plist : null;
            } else {
                return params;
            }
        }

        /**
         * Override to filter out some replay data. Null means excluded. (which also means it will not be counted
         * towards the pagination).
         * 
         * @return filtered data
         */
        public List<ParameterValueWithId> filter(List<ParameterValueWithId> params) {
            return params;
        }

        protected void onParameterData(List<ParameterValueWithId> params) {
        }
    }
}

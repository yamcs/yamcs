package org.yamcs.yarch;

public interface TableVisitor {
    public enum ActionType {
        NONE, DELETE, UPDATE_VAL, UPDATE_ROW
    };

    class Action {
        final boolean stop;
        byte[] updatedValue, updatedKey;

        ActionType type;

        private Action(ActionType type, boolean stop) {
            this.type = type;
            this.stop = stop;
        }

        public static Action updateAction(byte[] updatdeValue, boolean stop) {
            Action a = new Action(ActionType.UPDATE_VAL, stop);
            a.updatedValue = updatdeValue;
            return a;
        }

        public static Action updateAction(byte[] updatedKey, byte[] updatedValue, boolean stop) {
            Action a = new Action(ActionType.UPDATE_ROW, stop);
            a.updatedKey = updatedKey;
            a.updatedValue = updatedValue;
            return a;
        }

        public boolean stop() {
            return stop;
        }

        public ActionType action() {
            return type;
        }

        public byte[] getUpdatedValue() {
            return updatedValue;
        }

        public byte[] getUpdatedKey() {
            return updatedKey;
        }
    }

    public static final Action ACTION_STOP = new Action(ActionType.NONE, true);
    public static final Action ACTION_CONTINUE = new Action(ActionType.NONE, false);
    public static final Action ACTION_DELETE = new Action(ActionType.DELETE, false);
    public static final Action ACTION_DELETE_STOP = new Action(ActionType.DELETE, true);
    public static final Action ACTION_UPDATE = new Action(ActionType.UPDATE_VAL, false);
    public static final Action ACTION_UPDATE_STOP = new Action(ActionType.UPDATE_VAL, false);

    Action visit(byte[] key, byte[] value);
}

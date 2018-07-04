package org.yamcs.xtce;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.xtce.util.AggregateMemberNames;

public class AggregateDataType extends NameDescription implements DataType {
    private static final long serialVersionUID = 1L;

    List<Member> memberList = new ArrayList<>();
    transient AggregateMemberNames memberNames;

    public AggregateDataType(String name) {
        super(name);
    }
    protected AggregateDataType(AggregateDataType t) {
        super(t);
        this.memberList = t.memberList;
        this.memberNames = t.memberNames;
    }

    public void addMember(Member memberType) {
        memberList.add(memberType);
    }

    public void addMembers(List<Member> memberList) {
        this.memberList.addAll(memberList);
    }

    @Override
    public String getTypeAsString() {
        StringBuilder sb = new StringBuilder();
        sb.append("aggregate {");
        boolean first = true;
        for (Member m : getMemberList()) {
            if (first) {
                first = false;
            } else {
                sb.append("; ");
            }
            sb.append(m.getType().getName());
            sb.append(" ");
            sb.append(m.getName());
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Returns a member on the given name. If no such member is present return null
     * 
     * @param name
     *            the name of the member to be returned
     * @return the member with the given name
     */
    public Member getMember(String name) {
        for (Member m : memberList) {
            if (name.equals(m.getName())) {
                return m;
            }
        }
        return null;
    }

    public List<Member> getMemberList() {
        return memberList;
    }

    @Override
    public Type getValueType() {
        return Value.Type.AGGREGATE;
    }

    /**
     * Returns a member in a hierarchical aggregate. It is equivalent with a chained call of {@link #getMember(String)}:
     * 
     * <pre>
     * getMember(path[0]).getMember(path[1])...getMember(path[n])
     * </pre>
     * 
     * assuming that all the elements on the path exist.
     * 
     * 
     * @param path
     *            - the path to be traversed. Its length has to be at least 1 - otherwise an
     *            {@link IllegalArgumentException}
     *            will be thrown.
     * @return the member obtained by traversing the path or null if not such member exist.
     */
    public Member getMember(String[] path) {
        if (path.length == 0) {
            throw new IllegalArgumentException("path cannot be empty");
        }
        Member m = getMember(path[0]);
        for (int i = 1; i < path.length; i++) {
            if (m == null) {
                return null;
            }
            DataType ptype = m.getType();
            if (ptype instanceof AggregateParameterType) {
                m = ((AggregateParameterType) ptype).getMember(path[i]);
            } else {
                return null;
            }
            m = getMember(path[i]);
        }
        return m;
    }

    @Override
    public void setInitialValue(String initialValue) {
        // TODO Auto-generated method stub

    }

    /**
     * 
     * @return the (unique) object encoding the member names 
     * 
     */
    public AggregateMemberNames getMemberNames() {
        if (memberNames == null) {
            String[] n = memberList.stream().map(m -> m.getName()).toArray(String[]::new);
            memberNames = AggregateMemberNames.get(n);
        }
        return memberNames;
    }
}

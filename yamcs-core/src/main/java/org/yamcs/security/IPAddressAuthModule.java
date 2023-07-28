package org.yamcs.security;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.ipfilter.IpFilterRule;
import io.netty.handler.ipfilter.IpFilterRuleType;
import io.netty.handler.ipfilter.IpSubnetFilterRule;

/**
 * An AuthModule that enforces a login of one fixed user account, where the remote IP address must match one of the
 * configured IP address rules.
 */
public class IPAddressAuthModule extends AbstractHttpRequestAuthModule {

    protected static final String OPTION_ADDRESS = "address";
    protected static final String OPTION_USERNAME = "username";
    protected static final String OPTION_NAME = "name";
    protected static final String OPTION_EMAIL = "email";
    protected static final String OPTION_SUPERUSER = "superuser";
    protected static final String OPTION_PRIVILEGES = "privileges";

    private List<IpFilterRule> rules = new ArrayList<>();

    private AuthenticationInfo authenticationInfo;
    private AuthorizationInfo authorizationInfo;

    @Override
    public Spec getSpec() {
        var spec = new Spec();
        spec.addOption(OPTION_ADDRESS, OptionType.LIST_OR_ELEMENT)
                .withElementType(OptionType.STRING)
                .withRequired(true);
        spec.addOption(OPTION_USERNAME, OptionType.STRING).withRequired(true);
        spec.addOption(OPTION_NAME, OptionType.STRING);
        spec.addOption(OPTION_EMAIL, OptionType.STRING);
        spec.addOption(OPTION_SUPERUSER, OptionType.BOOLEAN).withDefault(false);
        spec.addOption(OPTION_PRIVILEGES, OptionType.ANY);
        return spec;
    }

    @Override
    public void init(YConfiguration args) throws InitException {
        var username = args.getString(OPTION_USERNAME);
        authenticationInfo = new AuthenticationInfo(this, username);

        var name = args.getString(OPTION_NAME, null);
        authenticationInfo.setDisplayName(name);

        var email = args.getString(OPTION_EMAIL, null);
        authenticationInfo.setEmail(email);

        authorizationInfo = new AuthorizationInfo();
        if (args.getBoolean(OPTION_SUPERUSER)) {
            authorizationInfo.grantSuperuser();
        }
        if (args.containsKey(OPTION_PRIVILEGES)) {
            var privilegeConfigs = args.getConfig(OPTION_PRIVILEGES);
            for (var privilegeName : privilegeConfigs.getKeys()) {
                var objects = privilegeConfigs.<String> getList(privilegeName);
                if (privilegeName.equals("System")) {
                    for (var object : objects) {
                        authorizationInfo.addSystemPrivilege(new SystemPrivilege(object));
                    }
                } else {
                    var type = new ObjectPrivilegeType(privilegeName);
                    for (var object : objects) {
                        authorizationInfo.addObjectPrivilege(new ObjectPrivilege(type, object));
                    }
                }
            }
        }

        try {
            for (var address : args.<String> getList(OPTION_ADDRESS)) {
                if (address.indexOf('/') > 0) {
                    var parts = address.split("\\/");
                    var ipAddress = InetAddress.getByName(parts[0]);
                    var cidrPrefix = Integer.parseInt(parts[1]);
                    rules.add(new IpSubnetFilterRule(ipAddress, cidrPrefix, IpFilterRuleType.ACCEPT));
                } else {
                    var ipAddress = InetAddress.getByName(address);
                    if (ipAddress instanceof Inet4Address) {
                        rules.add(new IpSubnetFilterRule(ipAddress, 32, IpFilterRuleType.ACCEPT));
                    } else if (ipAddress instanceof Inet6Address) {
                        rules.add(new IpSubnetFilterRule(ipAddress, 128, IpFilterRuleType.ACCEPT));
                    } else {
                        throw new IllegalArgumentException("Only IPv4 and IPv6 addresses are supported");
                    }
                }
            }
        } catch (UnknownHostException e) {
            throw new InitException(e);
        }
    }

    @Override
    public boolean handles(ChannelHandlerContext ctx, HttpRequest request) {
        var remoteAddress = ctx.channel().remoteAddress();
        return accept((InetSocketAddress) remoteAddress);
    }

    @Override
    public AuthenticationInfo getAuthenticationInfo(
            ChannelHandlerContext ctx, HttpRequest request) throws AuthenticationException {
        var remoteAddress = ctx.channel().remoteAddress();
        if (accept((InetSocketAddress) remoteAddress)) {
            return authenticationInfo;
        } else {
            return null;
        }
    }

    @Override
    public AuthorizationInfo getAuthorizationInfo(AuthenticationInfo authenticationInfo) throws AuthorizationException {
        var incomingUsername = authenticationInfo.getUsername();
        if (incomingUsername.equals(this.authenticationInfo.getUsername())) {
            return authorizationInfo;
        } else {
            return new AuthorizationInfo();
        }
    }

    @Override
    public boolean verifyValidity(AuthenticationInfo authenticationInfo) {
        return authenticationInfo.equals(authenticationInfo);
    }

    private boolean accept(InetSocketAddress remoteAddress) {
        for (var rule : rules) {
            if (rule.matches(remoteAddress)) {
                return rule.ruleType() == IpFilterRuleType.ACCEPT;
            }
        }
        return false;
    }
}

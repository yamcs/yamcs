package org.yamcs.utils;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import com.google.common.net.InetAddresses;

import io.netty.handler.ipfilter.IpFilterRule;
import io.netty.handler.ipfilter.IpFilterRuleType;
import io.netty.handler.ipfilter.IpSubnetFilterRule;

/**
 * Parses and matches IP address / CIDR range strings.
 */
public class IpSubnetRuleUtils {

    /**
     * Parses a list of {@code address} or {@code address/cidrPrefix} strings into matchable rules.
     */
    public static List<IpFilterRule> parseRules(List<String> addresses) throws UnknownHostException {
        List<IpFilterRule> rules = new ArrayList<>();
        for (String address : addresses) {
            rules.add(parseRule(address));
        }
        return rules;
    }

    public static IpFilterRule parseRule(String address) throws UnknownHostException {
        String host = address;
        Integer cidrPrefix = null;
        int slashIdx = address.indexOf('/');
        if (slashIdx > 0) {
            host = address.substring(0, slashIdx);
            cidrPrefix = Integer.parseInt(address.substring(slashIdx + 1));
        }

        InetAddress ipAddress = parseNumeric(host);
        if (cidrPrefix == null) {
            if (ipAddress instanceof Inet4Address) {
                cidrPrefix = 32;
            } else if (ipAddress instanceof Inet6Address) {
                cidrPrefix = 128;
            } else {
                throw new IllegalArgumentException("Only IPv4 and IPv6 addresses are supported");
            }
        }
        return new IpSubnetFilterRule(ipAddress, cidrPrefix, IpFilterRuleType.ACCEPT);
    }

    private static InetAddress parseNumeric(String host) throws UnknownHostException {
        try {
            return InetAddresses.forString(host);
        } catch (IllegalArgumentException e) {
            throw new UnknownHostException(
                    "'" + host + "' is not a numeric IP address (hostnames are not supported here)");
        }
    }

    /**
     * @return {@code true} if {@code remoteAddress} matches one of the rules and is accepted by it.
     */
    public static boolean matches(List<? extends IpFilterRule> rules, InetSocketAddress remoteAddress) {
        for (var rule : rules) {
            if (rule.matches(remoteAddress)) {
                return rule.ruleType() == IpFilterRuleType.ACCEPT;
            }
        }
        return false;
    }

    /**
     * Tests whether the textual address {@code ip} matches one of the rules. Only numeric addresses are accepted -- no
     * DNS resolution is performed, since {@code ip} is expected to originate from an untrusted request header.
     */
    public static boolean matches(List<? extends IpFilterRule> rules, String ip) {
        try {
            return matches(rules, new InetSocketAddress(InetAddresses.forString(ip), 0));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Walks a (possibly multi-hop) {@code X-Forwarded-For} header from right to left, peeling off trailing entries that
     * themselves match one of {@code trustedProxyRules}, and returns the first entry (from the right) that doesn't --
     * i.e. the closest hop that can't be vouched for, which is either the original client or an untrusted intermediary.
     * This correctly supports chains of multiple trusted proxies (e.g. CDN -> load balancer -> Yamcs) without trusting
     * entries a client could have pre-pended itself.
     * <p>
     * Only call this once the immediate peer has already been established as a trusted proxy via
     * {@link #matches(List, InetSocketAddress)}.
     */
    public static String peelForwardedFor(List<? extends IpFilterRule> trustedProxyRules, String forwardedForHeader) {
        String[] parts = forwardedForHeader.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = parts[i].trim();
            if (!matches(trustedProxyRules, candidate)) {
                return candidate;
            }
        }
        return parts[0].trim();
    }

    /**
     * Same scan as {@link #peelForwardedFor}, but only to check whether it would stop before the leftmost entry. The
     * leftmost entry itself is excluded since it's expected to be the original client, not a proxy.
     */
    public static boolean hasUntrustedHop(List<? extends IpFilterRule> trustedProxyRules, String forwardedForHeader) {
        String[] parts = forwardedForHeader.split(",");
        for (int i = parts.length - 1; i >= 1; i--) {
            if (!matches(trustedProxyRules, parts[i].trim())) {
                return true;
            }
        }
        return false;
    }
}

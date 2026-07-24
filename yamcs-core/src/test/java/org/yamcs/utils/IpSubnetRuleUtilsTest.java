package org.yamcs.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;

import org.junit.jupiter.api.Test;

public class IpSubnetRuleUtilsTest {

    @Test
    public void testExactIPv4() throws UnknownHostException {
        var rules = IpSubnetRuleUtils.parseRules(List.of("127.0.0.1"));
        assertTrue(IpSubnetRuleUtils.matches(rules, addr("127.0.0.1")));
        assertFalse(IpSubnetRuleUtils.matches(rules, addr("127.0.0.2")));
    }

    @Test
    public void testExactIPv6() throws UnknownHostException {
        var rules = IpSubnetRuleUtils.parseRules(List.of("::1"));
        assertTrue(IpSubnetRuleUtils.matches(rules, addr("::1")));
        assertFalse(IpSubnetRuleUtils.matches(rules, addr("::2")));
    }

    @Test
    public void testCidrRange() throws UnknownHostException {
        var rules = IpSubnetRuleUtils.parseRules(List.of("10.0.0.0/8"));
        assertTrue(IpSubnetRuleUtils.matches(rules, addr("10.1.2.3")));
        assertFalse(IpSubnetRuleUtils.matches(rules, addr("11.0.0.1")));
    }

    @Test
    public void testHostnameRejected() {
        assertThrows(UnknownHostException.class,
                () -> IpSubnetRuleUtils.parseRule("proxy.internal.example"));
    }

    @Test
    public void testMatchesString() throws UnknownHostException {
        var rules = IpSubnetRuleUtils.parseRules(List.of("192.168.0.0/16"));
        assertTrue(IpSubnetRuleUtils.matches(rules, "192.168.1.1"));
        assertFalse(IpSubnetRuleUtils.matches(rules, "192.169.1.1"));
        // Not a numeric address -- must not attempt DNS resolution, just fail to match
        assertFalse(IpSubnetRuleUtils.matches(rules, "not-an-ip"));
    }

    @Test
    public void testPeelForwardedForSingleHop() throws UnknownHostException {
        // One trusted proxy directly connected; header carries only the real client
        var rules = IpSubnetRuleUtils.parseRules(List.of("10.0.0.1"));
        assertEquals("203.0.113.5", IpSubnetRuleUtils.peelForwardedFor(rules, "203.0.113.5"));
    }

    @Test
    public void testPeelForwardedForMultiHopChain() throws UnknownHostException {
        // client -> proxy1 (10.0.0.1) -> proxy2 (10.0.0.2, the immediate/trusted peer)
        // Header (as seen at Yamcs) lists only the hops before the immediate peer: client, proxy1
        var rules = IpSubnetRuleUtils.parseRules(List.of("10.0.0.1", "10.0.0.2"));
        assertEquals("203.0.113.5", IpSubnetRuleUtils.peelForwardedFor(rules, "203.0.113.5, 10.0.0.1"));
    }

    @Test
    public void testPeelForwardedForIgnoresClientPrependedEntries() throws UnknownHostException {
        // A malicious client pre-pends a fake entry before ever reaching the trusted proxy.
        // Only 10.0.0.1 (the trusted intermediary) should be peeled off; the fake entry must be
        // returned as-is rather than trusted further.
        var rules = IpSubnetRuleUtils.parseRules(List.of("10.0.0.1"));
        assertEquals("6.6.6.6", IpSubnetRuleUtils.peelForwardedFor(rules, "6.6.6.6, 10.0.0.1"));
    }

    @Test
    public void testPeelForwardedForAllTrustedFallsBackToLeftmost() throws UnknownHostException {
        var rules = IpSubnetRuleUtils.parseRules(List.of("10.0.0.1", "10.0.0.2"));
        assertEquals("10.0.0.1", IpSubnetRuleUtils.peelForwardedFor(rules, "10.0.0.1, 10.0.0.2"));
    }

    private static InetSocketAddress addr(String ip) {
        return new InetSocketAddress(com.google.common.net.InetAddresses.forString(ip), 0);
    }
}

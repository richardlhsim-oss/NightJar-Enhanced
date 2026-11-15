package com.limelight.preferences;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RemoteConnectionTest {
    @Test
    public void thisNetworkIsLocal() {
        for (String host : new String[] {"192.168.1.20", "10.0.0.5", "172.16.4.2", "127.0.0.1", "169.254.3.3",
                "fe80::1", "fd12:3456::1", "[fd00::2]", "gaming-pc", "gaming-pc.local", "pc.lan", null, ""}) {
            assertFalse(host, RemoteConnection.isRemote(host));
        }
    }

    @Test
    public void anythingElseIsRemote() {
        for (String host : new String[] {"203.0.113.9", "100.101.102.103", "2001:db8::1", "home.example.com"}) {
            assertTrue(host, RemoteConnection.isRemote(host));
        }
    }
}

package com.gunizi.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReleaseVersionTest {
    @Test public void olderGithubReleaseMustNotReplaceNewApp(){
        assertEquals(-1,ReleaseVersion.compare("v0.8.0","0.15.0"));
    }
    @Test public void newerReleaseAndSameRelease(){
        assertEquals(1,ReleaseVersion.compare("v0.16.0","0.15.0"));
        assertEquals(0,ReleaseVersion.compare("v0.15.0","0.15.0"));
        assertEquals(1,ReleaseVersion.compare("1.0.0","0.15.0"));
        assertEquals(-1,ReleaseVersion.compare("0.14.9","0.15.0"));
    }
    @Test public void untrustedNonVersionIsRejected(){
        assertEquals(Integer.MIN_VALUE,ReleaseVersion.compare("latest","0.15.0"));
        assertEquals(Integer.MIN_VALUE,ReleaseVersion.compare("v0.16.0","unknown"));
    }
}
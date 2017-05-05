package org.csanchez.jenkins.plugins.kubernetes.strategies;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Kirill Shepitko kirill.shepitko@ericsson.com
 *         Date: 07/03/2017
 */
public class AutoCreatedSlaveRetentionStrategyTest {

    @Test
    public void getPodName() throws Exception {
        AutoCreatedSlaveRetentionStrategy unit = new AutoCreatedSlaveRetentionStrategy("cloud", 1);
        assertEquals("1e87ea78-73e8-474c-b94d-7c62feb98d6c", unit.getPodName("1e87ea78-73e8-474c-b94d-7c62feb98d6c-7b61e5f4"));
    }

}
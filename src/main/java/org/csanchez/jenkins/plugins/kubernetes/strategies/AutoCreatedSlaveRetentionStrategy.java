package org.csanchez.jenkins.plugins.kubernetes.strategies;

import com.google.common.annotations.VisibleForTesting;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.CloudSlaveRetentionStrategy;
import hudson.slaves.OfflineCause;
import hudson.util.TimeUnit2;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ClientPodResource;
import jenkins.model.Jenkins;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.Messages;
import org.jvnet.localizer.Localizable;
import org.jvnet.localizer.ResourceBundleHolder;

import javax.annotation.concurrent.GuardedBy;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Kirill Shepitko kirill.shepitko@ericsson.com
 *         Date: 07/03/2017
 */
public class AutoCreatedSlaveRetentionStrategy extends CloudSlaveRetentionStrategy {

    private static final Logger LOGGER = Logger.getLogger(AutoCreatedSlaveRetentionStrategy.class.getName());

    /**
     * The resource bundle reference
     */
    private final static ResourceBundleHolder HOLDER = ResourceBundleHolder.get(Messages.class);

    private final String cloudName;
    private final int maxMinutesIdle;

    public AutoCreatedSlaveRetentionStrategy(String cloudName, int maxMinutesIdle) {
        this.cloudName = cloudName;
        this.maxMinutesIdle = maxMinutesIdle;
    }

    @GuardedBy("hudson.model.Queue.lock")
    @Override
    public long check(Computer c) {
        LOGGER.info("Comp " + c.getName() + " is checked");
        long recheckInMins = super.check(c);
        LOGGER.info("Comp " + c.getName() + " will be re-checked again in " + recheckInMins + " minutes");
        return recheckInMins;
    }

    @Override
    protected long getIdleMaxTime() {
        return TimeUnit2.MINUTES.toMillis(maxMinutesIdle);
    }

    @Override
    protected long checkCycle() {
        return 1;
    }

    @Override
    protected boolean isIdleForTooLong(Computer c) {
        LOGGER.info("Comp " + c.getName() + " isIdleForTooLong?");
        return super.isIdleForTooLong(c);
    }

    @Override
    protected void kill(Node n) throws IOException {
        LOGGER.info("Node " + n.getNodeDescription() + " will be killed now");
        terminate(n);
//        super.kill(n);
    }

    private void terminate(Node n) throws IOException {
        String name = n.getNodeName();
        LOGGER.log(Level.INFO, "Terminating Kubernetes instance for slave {0}", name);

        Computer computer = n.toComputer();
        if (computer == null) {
            LOGGER.log(Level.SEVERE, "Computer for slave is null: {0}", name);
            return;
        }

        if (cloudName == null) {
            LOGGER.log(Level.SEVERE, "Cloud name is not set for slave, can't terminate: {0}", name);
        }

        try {
            Cloud cloud = Jenkins.getInstance().getCloud(cloudName);
            if (!(cloud instanceof KubernetesCloud)) {
                LOGGER.log(Level.SEVERE, "Slave cloud is not a KubernetesCloud, something is very wrong: {0}", name);
            }
            KubernetesClient client = ((KubernetesCloud) cloud).connect();
            ClientPodResource<Pod, DoneablePod> pods = client.pods().withName(getPodName(name));
            pods.delete();
            LOGGER.log(Level.INFO, "Terminated Kubernetes instance for slave {0}", name);
            computer.disconnect(OfflineCause.create(new Localizable(HOLDER, "offline")));
            LOGGER.log(Level.INFO, "Disconnected computer {0}", name);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to terminate pod for slave " + name, e);
        }
    }

    @VisibleForTesting
    String getPodName(String nodeName) {
        int i = nodeName.lastIndexOf('-');
        return i == -1 ? nodeName : nodeName.substring(0, i);
    }
}

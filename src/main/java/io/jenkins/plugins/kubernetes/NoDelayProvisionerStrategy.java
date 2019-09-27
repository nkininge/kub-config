package io.jenkins.plugins.kubernetes;
import hudson.Extension;
import hudson.model.Label;
import hudson.model.LoadStatistics;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import jenkins.model.Jenkins;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Implementation of {@link NodeProvisioner.Strategy} which will provision a new node immediately as
 * a task enter the queue.
 * In kubernetes, we don't really need to wait before provisioning a new node,
 * because kubernetes agents can be started and destroyed quickly
 *
 * @author <a href="mailto:root@junwuhui.cn">runzexia</a>
 */
@Extension(ordinal = 100)
public class NoDelayProvisionerStrategy extends NodeProvisioner.Strategy {

    private static final Logger LOGGER = Logger.getLogger(NoDelayProvisionerStrategy.class.getName());
    private static final boolean DISABLE_NODELAY_PROVISING = Boolean.valueOf(
            System.getProperty("io.jenkins.plugins.kubernetes.disableNoDelayProvisioning"));

    @Override
    public NodeProvisioner.StrategyDecision apply(NodeProvisioner.StrategyState strategyState) {
        if (DISABLE_NODELAY_PROVISING) {
            LOGGER.log(Level.FINE, "Provisioning not complete, NoDelayProvisionerStrategy is disabled");
            return NodeProvisioner.StrategyDecision.CONSULT_REMAINING_STRATEGIES;
        }

        final Label label = strategyState.getLabel();

        LoadStatistics.LoadStatisticsSnapshot snapshot = strategyState.getSnapshot();
        int availableCapacity =
                snapshot.getAvailableExecutors()   // live executors
                        + snapshot.getConnectingExecutors()  // executors present but not yet connected
                        + strategyState.getPlannedCapacitySnapshot()     // capacity added by previous strategies from previous rounds
                        + strategyState.getAdditionalPlannedCapacity();  // capacity added by previous strategies _this round_
        int currentDemand = snapshot.getQueueLength();
        LOGGER.log(Level.FINE, "Available capacity={0}, currentDemand={1}",
                new Object[]{availableCapacity, currentDemand});
        if (availableCapacity < currentDemand) {
            List<Cloud> jenkinsClouds = new ArrayList<>(Jenkins.get().clouds);
            Collections.shuffle(jenkinsClouds);
            for (Cloud cloud : jenkinsClouds) {
                if (!(cloud instanceof KubernetesCloud)) continue;
                if (!cloud.canProvision(label)) continue;

                Collection<NodeProvisioner.PlannedNode> plannedNodes = cloud.provision(label, currentDemand - availableCapacity);
                LOGGER.log(Level.FINE, "Planned {0} new nodes", plannedNodes.size());
                strategyState.recordPendingLaunches(plannedNodes);
                availableCapacity += plannedNodes.size();
                LOGGER.log(Level.FINE, "After provisioning, available capacity={0}, currentDemand={1}", new Object[]{availableCapacity, currentDemand});
                break;
            }
        }
        if (availableCapacity >= currentDemand) {
            LOGGER.log(Level.FINE, "Provisioning completed");
            return NodeProvisioner.StrategyDecision.PROVISIONING_COMPLETED;
        } else {
            LOGGER.log(Level.FINE, "Provisioning not complete, consulting remaining strategies");
            return NodeProvisioner.StrategyDecision.CONSULT_REMAINING_STRATEGIES;
        }
    }

}
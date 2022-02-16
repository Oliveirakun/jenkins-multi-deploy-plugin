package io.jenkins.plugins.sample.dynamic;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;

public class ObjectsManager {
    KubernetesClient client;

    public ObjectsManager(KubernetesClient client) {
        this.client = client;
    }

    public void processAndDeploy(HasMetadata obj, String nodeLocation) {
        if (isNotRunning(obj)) {
            client.resource(obj).createOrReplace();
            return;
        } else if (isRunningInSameNode(obj, nodeLocation)) {
            // Do nothing
            return;
        } else if (doNotNeedToMigrateData(obj)) {
            client.resource(obj).delete();
            client.resource(obj).createOrReplace();
            return;
        }

        // Start process to migrate data (dynamic deployment)

    }

    private boolean isNotRunning(HasMetadata obj) {
        HasMetadata runningObject = client.resource(obj).fromServer().get();
        if (runningObject == null) {
            return true;
        }

        return false;
    }

    public boolean isRunningInSameNode(HasMetadata obj, String nodeLocation) {
        String name = obj.getMetadata().getName();

        if (obj.getKind().equals("Deployment")) {
            Deployment deployment = client.apps().deployments().withName(name).get();
            String location = deployment.getSpec().getTemplate().getSpec().getNodeSelector().get("location");
            return location.equals(nodeLocation);
        } else if (obj.getKind().equals("StatefulSet")) {
            StatefulSet stf = client.apps().statefulSets().withName(name).get();
            String location = stf.getSpec().getTemplate().getSpec().getNodeSelector().get("location");
            return location.equals(nodeLocation);
        }

        return true;
    }

    public boolean doNotNeedToMigrateData(HasMetadata obj) {
        if (obj.getKind().equals("StatefulSet")) {
            return false;
        }

       return true;
    }


}

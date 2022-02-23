package io.jenkins.plugins.sample.dynamic;

import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;

public class MigrationManager {
    private final KubernetesClient client;

    public MigrationManager(KubernetesClient client) {
        this.client = client;
    }

    public void migrate(StatefulSet stf, String nodeLocation) {
        System.out.println("Starting migration for " + stf.getMetadata().getName());

        String name = stf.getMetadata().getName();
        String path = getPath(name);
        String currentNode = getCurrentNode(name);
        deleteStatefulSet(stf);

        System.out.println("Downloading data from node...");
        PodHandler downloadHandler = new PodHandler(client, currentNode, path);
        downloadHandler.deployOnNode();
        downloadHandler.downloadDataFromNode();
        downloadHandler.deletePod();

        System.out.println("Uploading data to node...");
        PodHandler uploadHandler = new PodHandler(client, nodeLocation, path);
        uploadHandler.deployOnNode();
        uploadHandler.uploadDataToNode();
        uploadHandler.deletePod();

        client.resource(stf).createOrReplace();
        System.out.println("Finished migration for " + stf.getMetadata().getName());
    }

    private String getPath(String name) {
        return client.apps().statefulSets()
            .withName(name)
            .get()
            .getSpec()
            .getTemplate()
            .getSpec()
            .getVolumes()
            .stream().filter(v -> v.getHostPath() != null)
            .findFirst().orElse(null)
            .getHostPath()
            .getPath();
    }

    private String getCurrentNode(String name) {
        return client.apps().statefulSets()
            .withName(name)
            .get()
            .getSpec()
            .getTemplate()
            .getSpec()
            .getNodeSelector()
            .get("location");
    }

    private void deleteStatefulSet(StatefulSet stf) {
        String name = stf.getMetadata().getName();
        client.apps().statefulSets().withName(name).delete();
    }
}

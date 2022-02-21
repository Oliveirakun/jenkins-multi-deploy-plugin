package io.jenkins.plugins.sample.dynamic;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.Collections;

public class MigrationManager {
    private final KubernetesClient client;

    public MigrationManager(KubernetesClient client) {
        this.client = client;
    }

    public void migrate(StatefulSet stf, String nodeLocation) {
        System.out.println("Starting migration for " + stf.getMetadata().getName());
        System.out.println("Sending command to stop proxy...");
        sendStopToProxy();
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

    private void sendStopToProxy() {
        sendCommandToProxy("curl -X PUT http://localhost:8083/v1/toogle?stop=true");
    }

    private void sendResumeToProxy(String remoteBrokerAddress) {
        String command = String.format("curl -X PUT http://localhost:8083/v1/toogle?stop=false&broker-uri=tcp://%s",
            remoteBrokerAddress);
        sendCommandToProxy(command);
    }

    private void sendCommandToProxy(String command) {
        PodList podList = client.pods().withLabels(Collections.singletonMap("role", "mqtt-proxy")).list();
        Pod pod = podList.getItems().get(0);
        String defaultContainer = pod.getMetadata().getAnnotations().get("kubectl.kubernetes.io/default-container");

        client.pods()
            .withName(pod.getMetadata().getName())
            .inContainer(defaultContainer)
            .readingInput(System.in)
            .writingOutput(System.out)
            .writingError(System.err)
            .withTTY()
            .exec("sh","-c", command);
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

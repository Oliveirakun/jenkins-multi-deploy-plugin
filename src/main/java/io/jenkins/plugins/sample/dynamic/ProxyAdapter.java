package io.jenkins.plugins.sample.dynamic;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.Collections;

public class ProxyAdapter {
    private KubernetesClient client;

    public ProxyAdapter(KubernetesClient client) {
        this.client = client;
    }

    public void pauseSendingData() {
        String command = "curl -X PUT http://127.0.0.1:8083/v1/toogle?stop=true";
        execCommand(command);
    }

    public void resumeSendingData(String remoteBrokerAddress) {
        String brokerURI = String.format("tcp://%s", remoteBrokerAddress);
        String command = "curl -X PUT http://127.0.0.1:8083/v1/toogle?stop=false&broker-uri=" + brokerURI;
        execCommand(command);
    }

    private void execCommand(String command) {
        Pod pod = getProxyPod();
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

    private Pod getProxyPod() {
        PodList podList = client.pods().withLabels(Collections.singletonMap("role", "mqtt-proxy")).list();
        Pod pod = podList.getItems().get(0);

        return pod;
    }
}

package io.jenkins.plugins.sample.dynamic;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.jenkins.plugins.sample.KubernetesAdapter;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProxyManager {
    private final KubernetesClient client;
    private final Map<String, String> envVariables;
    private boolean proxyStopped;

    public ProxyManager(InputStream stream) {
        client = new KubernetesAdapter(stream).getKubernetesClient();
        envVariables = new HashMap<>();
    }

    public void sendStopCommand() {
        Pod pod = getProxyPod();
        if (pod == null) {
            return;
        }

        System.out.println("Sending stop command to proxy");
        pauseSendingData();
        proxyStopped = true;
    }

    public void sendResumeCommand(List<HttpHook> hooks) {
       if (!proxyStopped) {
           return;
       }

       System.out.println("Executing hooks");
       new HttpHookManager(hooks, client).execute();

       System.out.println("Sending resume command to proxy");
       String remoteBrokerAddress = envVariables.get("REMOTE_MQTT_BROKER");
       resumeSendingData(remoteBrokerAddress);
    }

    public void addEnvVariables(Map<String, String> variables) {
        envVariables.putAll(variables);
    }

    private void pauseSendingData() {
        String command = "curl -X PUT http://127.0.0.1:8083/v1/toogle?stop=true";
        execCommand(command);
    }

    private void resumeSendingData(String remoteBrokerAddress) {
        String command = "curl -X PUT http://localhost:8083/v1/toogle\\?stop\\=false\\&broker-uri\\=" + remoteBrokerAddress;
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
        if (podList.getItems().isEmpty()) {
            return null;
        }

        return podList.getItems().get(0);
    }
}

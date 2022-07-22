package io.jenkins.plugins.sample.dynamic;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class HttpHookManager {
    private final List<HttpHook> hooks;
    private final KubernetesClient client;

    public HttpHookManager(List<HttpHook> hooks, KubernetesClient client) {
        this.hooks = hooks;
        this.client = client;
    }

    public void execute() {
        hooks.forEach(hook -> {
            try {
                processHook(hook);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    private void processHook(HttpHook hook) throws InterruptedException {
        PodList podList = client.pods().withLabels(hook.getPodLabelMap()).list();
        if (podList.getItems().isEmpty()) {
            System.out.println("Pod not found for: " + hook.getPodLabel());
            return;
        }

        System.out.println("Waiting for pod to be ready: " + hook.getPodLabel());
        Pod pod = podList.getItems().get(0);

        int retries = 0;
        boolean finished = false;
        while (retries <= 3 && !finished) {
            try {
                client.resource(pod).waitUntilReady(60, TimeUnit.SECONDS);
                finished = true;
            } catch (Exception e) {
                System.out.println("Error waiting hook pod deployment, retrying..." + retries);
                retries++;
            }
        }

        if (!finished) {
            throw new KubernetesClientException("Failed to wait/deploy pod: " + pod.getMetadata().getName());
        }

        System.out.println("Executing command for pod: " + hook.getPodLabel());
        client.pods()
            .withName(pod.getMetadata().getName())
            .readingInput(System.in)
            .writingOutput(System.out)
            .writingError(System.err)
            .withTTY()
            .exec("sh","-c", hook.getCommand());
    }
}

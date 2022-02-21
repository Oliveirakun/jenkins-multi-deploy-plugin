package io.jenkins.plugins.sample.dynamic;

import com.google.crypto.tink.subtle.Random;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PodHandler {
    private final KubernetesClient client;
    private final String node;
    private final String hostVolumePath;
    private final String podName;

    public PodHandler(KubernetesClient client, String node, String hostVolumePath) {
        this.client = client;
        this.node = node;
        this.hostVolumePath = hostVolumePath;
        this.podName = "busybox-" + UUID.randomUUID();
    }

    public void deployOnNode() {
        try {
            Pod pod = buildPod();
            client.pods().createOrReplace(pod);
            client.pods().withName(podName).waitUntilReady(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void downloadDataFromNode() {
        Path downloadToPath = new File("/").toPath();

        client.pods()
            .withName(podName)
            .dir(hostVolumePath)
            .copy(downloadToPath);
    }

    public void uploadDataToNode() {
        File directoryToUpload = new File(hostVolumePath);

        client.pods()
            .withName(podName)
            .dir(hostVolumePath)
            .upload(directoryToUpload.toPath());
    }

    public void deletePod() {
        client.pods().withName(podName).delete();
    }

    private Pod buildPod() {
        return new PodBuilder()
            .withApiVersion("v1")
            .withNewMetadata()
            .withName(podName)
            .endMetadata()
            .withNewSpec()
            .withContainers()
            .addNewContainer()
            .withName(podName)
            .withImage("busybox")
            .withStdin(true)
            .withStdinOnce(true)
            .withTty(true)
            .withVolumeMounts()
            .addNewVolumeMount()
            .withName("db-volume")
            .withMountPath(hostVolumePath)
            .endVolumeMount()
            .endContainer()
            .withVolumes()
            .addNewVolume()
            .withName("db-volume")
            .withNewHostPath()
            .withType("DirectoryOrCreate")
            .withPath(hostVolumePath)
            .endHostPath()
            .endVolume()
            .addToNodeSelector("location", node)
            .endSpec()
            .build();
    }
}

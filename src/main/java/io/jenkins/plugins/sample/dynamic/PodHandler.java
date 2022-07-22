package io.jenkins.plugins.sample.dynamic;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PodHandler {
    private final KubernetesClient client;
    private final String node;
    private final String hostVolumePath;
    private final String podName;
    private static final String PREFIX_PATH = "/tmp/data-migration";

    public PodHandler(KubernetesClient client, String node, String hostVolumePath) {
        this.client = client;
        this.node = node;
        this.hostVolumePath = hostVolumePath;
        this.podName = "busybox-" + UUID.randomUUID();
    }

    public void deployOnNode() {
        Pod pod = client.pods().createOrReplace(buildPod());

        int retries = 0;
        boolean finished = false;
        while (retries <= 3 && !finished) {
            try {
                client.resource(pod).waitUntilReady(30, TimeUnit.SECONDS);
                finished = true;
            } catch (Exception e) {
                System.out.println("Error waiting busybox deployment, retrying..." + retries);
                retries++;
            }
        }

        if (!finished) {
            throw new KubernetesClientException("Failed to wait/deploy busybox: " + podName);
        }
    }

    @SuppressFBWarnings(value = "DMI_HARDCODED_ABSOLUTE_FILENAME")
    public void downloadDataFromNode() {
        Path downloadToPath = new File(PREFIX_PATH).toPath();

        int retries = 0;
        boolean finished = false;
        while (retries <= 3 && !finished) {
            try {
                client.pods()
                    .withName(podName)
                    .dir(hostVolumePath)
                    .copy(downloadToPath);

                finished = true;
            } catch (Exception e) {
                System.out.println("Error downloading data, retrying..." + retries);
                retries++;
            }
        }

        if (!finished) {
            throw new KubernetesClientException("Failed to download data from node/pod: " + podName);
        }
    }

   public void uploadDataToNode() {
        String pathName = String.format("%s%s",PREFIX_PATH, hostVolumePath);
        File directoryToUpload = new File(pathName);

        int retries = 0;
        boolean finished = false;
        while (retries <= 3 && !finished) {
            try {
                client.pods()
                    .withName(podName)
                    .dir(hostVolumePath)
                    .upload(directoryToUpload.toPath());

                finished = true;
            } catch (Exception e) {
                System.out.println("Error uploading data, retrying..." + retries);
                retries++;
            }
        }

        if (!finished) {
            throw new KubernetesClientException("Failed to upload data to node/pod: " + podName);
        }
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
            .withImage("busybox:1.35")
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

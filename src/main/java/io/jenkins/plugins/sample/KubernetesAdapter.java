package io.jenkins.plugins.sample;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ParameterNamespaceListVisitFromServerGetDeleteRecreateWaitApplicable;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class KubernetesAdapter {
    private Config kubeConfig;

    public KubernetesAdapter(InputStream stream) {
        convertStreamToString(stream);
    }

    public List<String> getNodes () {
        List<String> nodes = new ArrayList<String>();
        KubernetesClient client = new DefaultKubernetesClient(kubeConfig);

        NodeList nodeList = client.nodes().list();
        for (Node node : nodeList.getItems()) {
            Map<String,String> labels = node.getMetadata().getLabels();
            String location = labels.get("location");
            nodes.add(location);
        }

        return nodes;
    }

    public void deploy(String node, String image, String manifest) {
        try {
            KubernetesClient client = new DefaultKubernetesClient(kubeConfig);
            File manifestFile = File.createTempFile("manifest",".yml");
            Files.write(Paths.get(manifestFile.getPath()), manifest.getBytes(StandardCharsets.UTF_8));

            ParameterNamespaceListVisitFromServerGetDeleteRecreateWaitApplicable<HasMetadata> config =
                    client.load(new FileInputStream(manifestFile));
            config.delete();
            config.createOrReplace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void convertStreamToString(InputStream stream) {
        try {
            String content = IOUtils.toString(stream, StandardCharsets.UTF_8.name());
            kubeConfig = Config.fromKubeconfig(content);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                stream.close();
            } catch (IOException e) {
                System.out.println("Error closing kubeConfig stream: " + e);
            }
        }
    }
}

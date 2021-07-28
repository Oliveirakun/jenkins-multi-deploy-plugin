package io.jenkins.plugins.sample;

import io.fabric8.kubernetes.api.model.*;
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
    private KubernetesClient client;

    public KubernetesAdapter(InputStream stream) {
        Config kubeConfig = convertStreamToString(stream);
        client = new DefaultKubernetesClient(kubeConfig);
    }

    public List<String> getNodes () {
        List<String> nodes = new ArrayList<String>();

        NodeList nodeList = client.nodes().list();
        for (Node node : nodeList.getItems()) {
            Map<String,String> labels = node.getMetadata().getLabels();
            String location = labels.get("location");
            nodes.add(location);
        }

        return nodes;
    }

    public void deploy(String node, String image, String manifest) {
        FileInputStream inputStream = null;

        try {
            File manifestFile = File.createTempFile("manifest",".yml");
            Files.write(Paths.get(manifestFile.getPath()), manifest.getBytes(StandardCharsets.UTF_8));

            inputStream = new FileInputStream(manifestFile);
            ParameterNamespaceListVisitFromServerGetDeleteRecreateWaitApplicable<HasMetadata> config =
                    client.load(inputStream);
            config.delete();
            config.createOrReplace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeStream(inputStream);
        }
    }

    public void createConfigMap(String projectName, Map<String,String> variables) {
        ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                    .withName(String.format("%s-config", projectName))
                    .withNamespace("default")
                .endMetadata()
                .withApiVersion("v1")
                .withData(variables)
                .build();

        client.configMaps().createOrReplace(configMap);
    }

    private Config convertStreamToString(InputStream stream) {
        try {
            String content = IOUtils.toString(stream, StandardCharsets.UTF_8.name());
            return Config.fromKubeconfig(content);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            closeStream(stream);
        }
    }

    private void closeStream(InputStream stream) {
        try {
            if (stream != null)
                stream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

package io.jenkins.plugins.sample;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.apps.Deployment;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KubernetesAdapter {
    private KubernetesClient client;
    private List<EnvVar> varsList;

    public KubernetesAdapter(InputStream stream) {
        Config kubeConfig = convertStreamToString(stream);
        client = new DefaultKubernetesClient(kubeConfig);
        varsList = new ArrayList<>();
    }

    public Map<String,String> getNodes() {
        Map<String,String> nodes = new HashMap<>();

        NodeList nodeList = client.nodes().list();
        for (Node node : nodeList.getItems()) {
            Map<String,String> labels = node.getMetadata().getLabels();
            String location = labels.get("location");

            if (node.getMetadata().getLabels().containsKey("node-role.kubernetes.io/edge")){
                nodes.put(location, "edge");
            } else {
                nodes.put(location, "cloud");
            }
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

            config.get().forEach(obj -> {
                if (obj.getKind().equals("Deployment") && !varsList.isEmpty()) {
                    Deployment deployment = (Deployment) obj;
                    deployment.getSpec().getTemplate().getSpec().getContainers().forEach(container -> {
                        container.setEnv(varsList);
                    });
                    client.resource(deployment).delete();
                    client.resource(deployment).createOrReplace();
                } else {
                    client.resource(obj).delete();
                    client.resource(obj).createOrReplace();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeStream(inputStream);
        }
    }

    public void setEnvironmentVariables(String projectName, Map<String,String> variables) {
        variables.forEach((key,value) -> {
            EnvVar var = new EnvVar();
            var.setName(key);
            var.setValue(value);
            varsList.add(var);
        });
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

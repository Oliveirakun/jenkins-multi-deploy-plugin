package io.jenkins.plugins.sample;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ParameterNamespaceListVisitFromServerGetDeleteRecreateWaitApplicable;
import io.jenkins.plugins.sample.dynamic.ObjectsManager;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

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

    public void deploy(ProjectRepo project, String image, String manifest) {
        FileInputStream inputStream = null;

        try {
            File manifestFile = File.createTempFile("manifest",".yml");
            Files.write(Paths.get(manifestFile.getPath()), manifest.getBytes(StandardCharsets.UTF_8));

            inputStream = new FileInputStream(manifestFile);
            ParameterNamespaceListVisitFromServerGetDeleteRecreateWaitApplicable<HasMetadata> config =
                    client.load(inputStream);

            ObjectsManager manager = new ObjectsManager(client);
            config.get().forEach(obj -> {
                if (obj.getKind().equals("Deployment")) {
                    Deployment deployment = (Deployment) obj;
                    adjustDeployment(project, deployment);
                    manager.processAndDeploy(deployment, project.getNodeLocation());
                } else if (obj.getKind().equals("StatefulSet")) {
                    StatefulSet stf = (StatefulSet) obj;
                    adjustStatefulSet(project, stf);
                    manager.processAndDeploy(stf, project.getNodeLocation());
                } else {
                    manager.processAndDeploy(obj, project.getNodeLocation());
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

    private void adjustDeployment(ProjectRepo project, Deployment deployment) {
        if (!varsList.isEmpty()) {
            deployment.getSpec().getTemplate().getSpec().getContainers().forEach(container -> {
                container.setEnv(varsList);
            });
        }
        if (project.isOnEdge()) {
            deployment.getSpec().getTemplate().getSpec().setHostNetwork(true);
        }

        Map<String,String> selector = Collections.singletonMap("location", project.getNodeLocation());
        deployment.getSpec().getTemplate().getSpec().setNodeSelector(selector);
    }

    private void adjustStatefulSet(ProjectRepo project, StatefulSet stf) {
        if (!varsList.isEmpty()) {
            stf.getSpec().getTemplate().getSpec().getContainers().forEach(container -> {
                container.setEnv(varsList);
            });
        }
        if (project.isOnEdge()) {
            stf.getSpec().getTemplate().getSpec().setHostNetwork(true);
        }

        Map<String,String> selector = Collections.singletonMap("location", project.getNodeLocation());
        stf.getSpec().getTemplate().getSpec().setNodeSelector(selector);
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

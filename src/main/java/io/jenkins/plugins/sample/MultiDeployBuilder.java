package io.jenkins.plugins.sample;

import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.lang.text.StrSubstitutor;
import org.jenkinsci.plugins.plaincredentials.FileCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class MultiDeployBuilder extends Builder implements SimpleBuildStep {
    private List<ProjectRepo> projects;
    private String dockerRegistryUrl;
    private String dockerRegistryCredentialId;
    private boolean fastDeploy;

    @DataBoundConstructor
    public MultiDeployBuilder(List<ProjectRepo> projects) {
        this.projects = projects;
    }

    public List<ProjectRepo> getProjects() {
        return projects;
    }

    public String getDockerRegistryUrl() {
        return dockerRegistryUrl;
    }

    public String getDockerRegistryCredentialId() {
        return dockerRegistryCredentialId;
    }

    public boolean isFastDeploy() {
        return fastDeploy;
    }

    @DataBoundSetter
    public void setProjects(List<ProjectRepo> projects) {
        this.projects = projects;
    }

    @DataBoundSetter
    public void setDockerRegistryUrl(String dockerRegistryUrl) {
        this.dockerRegistryUrl = dockerRegistryUrl;
    }

    @DataBoundSetter
    public void setDockerRegistryCredentialId(String dockerRegistryCredentialId) {
        this.dockerRegistryCredentialId = dockerRegistryCredentialId;
    }

    @DataBoundSetter
    public void setFastDeploy(boolean fastDeploy) {
        this.fastDeploy = fastDeploy;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
       PrintStream logger = listener.getLogger();
       StandardUsernamePasswordCredentials credentials = findRegistryCredentials(getDockerRegistryCredentialId());

       ScriptBuilder scriptBuilder =  new ScriptBuilder();
       DockerAdapter dockerAdapter = new DockerAdapter(
            credentials.getUsername(),
            credentials.getPassword().getPlainText(),
            getDockerRegistryUrl()
        );
       scriptBuilder.appendCommand(dockerAdapter.setLoginCmd());

       List<String> images = new ArrayList<String>();
       for (ProjectRepo project : projects) {
           scriptBuilder.appendMessage("*****************************************************************************");
           scriptBuilder.appendMessage("***************  Building project: " + project.getName() + "  **************");
           scriptBuilder.appendMessage("*****************************************************************************");

           String projectRootPath = String.format("%s/%s", workspace.toURI().getPath(), project.getName());
           String versionFilePath = String.format("%s/VERSION", projectRootPath);
           String tagName = new String(Files.readAllBytes(Paths.get(versionFilePath)), StandardCharsets.UTF_8);

           dockerAdapter.setTagName(tagName.trim());
           dockerAdapter.setProjectName(project.getName());
           dockerAdapter.setProjectRootPath(projectRootPath);

           scriptBuilder.appendCommand(dockerAdapter.buildAndPushImageCmd());
           images.add(dockerAdapter.getImageTag());
       }

       if (!fastDeploy) {
           CommandRunner runner = new CommandRunner(logger, workspace.toURI().getPath());
           runner.execute(scriptBuilder.build().getPath());
       }

       for (int i = 0;i < images.size();i++) {
           ProjectRepo project = projects.get(i);

           logger.println("*****************************************************************************");
           logger.println("***************  Deploying project: " + project.getName() + "  **************");
           logger.println("*****************************************************************************");

           InputStream stream = new DescriptorImpl().getKubeConfig(project.getCredentialsId());
           KubernetesAdapter adapter = new KubernetesAdapter(stream);

           Map<String, String> envVariables = parseEnvVariables(project.getEnvVariables());
           if (!envVariables.isEmpty()) {
               adapter.createConfigMap(project.getName(), envVariables);
           }

           String projectRootPath = String.format("%s/%s", workspace.toURI().getPath(), project.getName());
           String manifestPath = String.format("%s/manifest/%s.yml", projectRootPath, project.getName());
           String templateManifest = new String(Files.readAllBytes(Paths.get(manifestPath)), StandardCharsets.UTF_8);

           Map<String,String> valuesMap = new HashMap<String,String>();
           valuesMap.put("image", images.get(i));
           valuesMap.put("location", project.getNode());
           String finalManifest = new StrSubstitutor(valuesMap).replace(templateManifest);

           adapter.deploy(project.getNode(), images.get(i), finalManifest);
       }
    }

    private StandardUsernamePasswordCredentials findRegistryCredentials(String credentialsId) {
        Item fakeProject = new FreeStyleProject(Jenkins.get(), "fake-" + UUID.randomUUID().toString());

        StandardUsernamePasswordCredentials credentials = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                        StandardUsernamePasswordCredentials.class,
                        fakeProject,
                        ACL.SYSTEM,
                        URIRequirementBuilder.fromUri("").build()
                ),
                CredentialsMatchers.withId(credentialsId)
        );

        return credentials;
    }

    private Map<String, String> parseEnvVariables(String envs) {
        Map<String, String> envVariables = new HashMap<>();

        Arrays.stream(envs.split("\n")).forEach(env -> {
            if (env.isEmpty())
                return;

            String key = env.split("=")[0];
            String value = env.split("=")[1];
            envVariables.put(key, value);
        });

        return envVariables;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        public ListBoxModel doFillDockerRegistryCredentialIdItems(@AncestorInPath Item item, @QueryParameter String credentialsId) {
            StandardListBoxModel result = new StandardListBoxModel();

            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return result.includeCurrentValue(credentialsId);
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ)
                        && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return result.includeCurrentValue(credentialsId);
                }
            }

            Item fakeProject = new FreeStyleProject(Jenkins.get(), "fake-" + UUID.randomUUID().toString());

            return result
                .includeEmptyValue()
                .includeMatchingAs(
                        ACL.SYSTEM,
                        fakeProject,
                        StandardCredentials.class,
                        URIRequirementBuilder.fromUri("").build(),
                        CredentialsMatchers.anyOf(new CredentialsMatcher[]{CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class)}))
                .includeCurrentValue(credentialsId);
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String credentialsId) {
            StandardListBoxModel result = new StandardListBoxModel();

            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return result.includeCurrentValue(credentialsId);
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ)
                        && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return result.includeCurrentValue(credentialsId);
                }
            }

            Item fakeProject = new FreeStyleProject(Jenkins.get(), "fake-" + UUID.randomUUID().toString());

            return result
                    .includeEmptyValue()
                    .includeMatchingAs(
                            ACL.SYSTEM,
                            fakeProject,
                            StandardCredentials.class,
                            URIRequirementBuilder.fromUri("").build(),
                            CredentialsMatchers.anyOf(new CredentialsMatcher[]{CredentialsMatchers.instanceOf(FileCredentials.class)}))
                    .includeCurrentValue(credentialsId);
        }

        public FormValidation doCheckCredentialsId(@AncestorInPath Item item, @QueryParameter String value) {
            if (value == null || value.isEmpty()) {
                return FormValidation.error("Cannot use empty credential");
            }

            return FormValidation.ok();
        }

        public ListBoxModel doFillNodeItems(@QueryParameter String credentialsId) throws IOException {
            ListBoxModel items = new ListBoxModel();

            if (credentialsId != null && !credentialsId.isEmpty()) {
                InputStream stream = getKubeConfig(credentialsId);
                List<String> nodes = new KubernetesAdapter(stream).getNodes();
                for (String node : nodes) {
                    items.add(node);
                }
            }

            return items;
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Multi Deploy";
        }

        private InputStream getKubeConfig(String credentialsId) throws IOException {
            Item fakeProject = new FreeStyleProject(Jenkins.get(), "fake-" + UUID.randomUUID().toString());

            FileCredentials credentials = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                    FileCredentials.class,
                    fakeProject,
                    ACL.SYSTEM,
                    URIRequirementBuilder.fromUri("").build()
                ),
                CredentialsMatchers.withId(credentialsId)
            );

            if (credentials == null)
                return null;

            return credentials.getContent();
        }
    }
}

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
import org.jenkinsci.plugins.plaincredentials.FileCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.UUID;

public class MultiDeployBuilder extends Builder implements SimpleBuildStep {
    private final String name;
    private List<ProjectRepo> projects;
    private String dockerRegistryUrl;
    private String dockerRegistryCredentialId;

    @DataBoundConstructor
    public MultiDeployBuilder(String name, List<ProjectRepo> projects) {
        this.name = name;
        this.projects = projects;
    }

    public String getName() {
        return name;
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

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        PrintStream logger = listener.getLogger();
        logger.println("Hello MultiDeployBuilder: " + name);
        for (ProjectRepo project : projects)
            logger.println("Project: " + project.getName() + " Deployed with success");

       StandardUsernamePasswordCredentials credentials = findRegistryCredentials(getDockerRegistryCredentialId());
       logger.println("Docker username: " + credentials.getUsername());
       logger.println("Docker password: " + credentials.getPassword());

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

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        public FormValidation doCheckName(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error(Messages.HelloWorldBuilder_DescriptorImpl_errors_missingName());
            if (value.length() < 4)
                return FormValidation.warning(Messages.HelloWorldBuilder_DescriptorImpl_warnings_tooShort());

            return FormValidation.ok();
        }

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

            return credentials.getContent();
        }

    }


}

package io.jenkins.plugins.sample;

import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
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
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.FileCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

public class MultiDeployBuilder extends Builder implements SimpleBuildStep {
    private final String name;
    private List<ProjectRepo> projects;

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

    @DataBoundSetter
    public void setProjects(List<ProjectRepo> projects) {
        this.projects = projects;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
       listener.getLogger().println("Hello MultiDeployBuilder: " + name);
       for (ProjectRepo project : projects)
            listener.getLogger().println("Project: " + project.getName() + " Deployed with success");
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
                        null,
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

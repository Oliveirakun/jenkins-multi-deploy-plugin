package io.jenkins.plugins.sample;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class ProjectRepo {
    private String name;
    private String node;
    private String credentialsId;

    @DataBoundConstructor
    public ProjectRepo(String name, String node, String credentialsId) {
        this.name = name;
        this.node = node;
        this.credentialsId = credentialsId;
    }

    public String getNode() {
        return node;
    }

    @DataBoundSetter
    public void setNode(String node) {
        this.node = node;
    }

    public String getName() {
        return name;
    }

    @DataBoundSetter
    public void setName(String name) {
        this.name = name;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialId(String credentialsId) {
        this.credentialsId = credentialsId;
    }
}

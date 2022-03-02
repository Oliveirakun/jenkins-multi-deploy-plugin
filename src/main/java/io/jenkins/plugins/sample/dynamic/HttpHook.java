package io.jenkins.plugins.sample.dynamic;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.Collections;
import java.util.Map;

public class HttpHook {
    private String podLabel;
    private String command;

    @DataBoundConstructor
    public HttpHook(String podLabel, String command) {
        this.podLabel = podLabel;
        this.command = command;
    }

    public String getPodLabel() {
        return podLabel;
    }

    @DataBoundSetter
    public void setPodLabel(String podLabel) {
        this.podLabel = podLabel;
    }

    public String getCommand() {
        return command;
    }

    @DataBoundSetter
    public void setCommand(String command) {
        this.command = command;
    }

    public Map<String,String> getPodLabelMap() {
        String[] param = podLabel.split(":", 2);
        String key = param[0];
        String value = param[1];

        return Collections.singletonMap(key, value);
    }
}

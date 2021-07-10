package io.jenkins.plugins.sample;

import hudson.model.Build;

import java.io.IOException;
import java.io.File;

public class NewStyleBuild extends Build<NewStyleProject,NewStyleBuild> {
    public NewStyleBuild(NewStyleProject project) throws IOException {
        super(project);
    }

    public NewStyleBuild(NewStyleProject project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    @Override
    public void run() {
        super.run();
    }
}

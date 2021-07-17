package io.jenkins.plugins.sample;

public class DockerAdapter {
    private String username;
    private String password;
    private String hostUrl;
    private String tagName;
    private String projectName;
    private String projectRootPath;

    public DockerAdapter(String username, String password, String hostUrl) {
        this.username = username;
        this.password = password;
        this.hostUrl = hostUrl;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public void setProjectRootPath(String projectRootPath) {
        this.projectRootPath = projectRootPath;
    }

    public String setLoginCmd() {
        String cmd = String.format("docker login -u %s -p %s %s", username, password, hostUrl);
        return cmd;
    }

    public String buildAndPushImageCmd() {
        String version = getImageTag();
        String dockerFilePath = String.format("%s/Dockerfile", projectRootPath);
        String cmd = String.format("docker buildx build -f \"%s\" --push --platform=linux/amd64,linux/arm64 --tag %s \"%s\"",
                dockerFilePath, version, projectRootPath);

        return cmd;
    }

    public String getImageTag() {
        String version = String.format("%s/%s:%s", username, projectName, tagName);
        return version;
    }
}

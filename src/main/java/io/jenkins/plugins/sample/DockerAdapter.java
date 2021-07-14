package io.jenkins.plugins.sample;

public class DockerAdapter {
    private String username;
    private String password;
    private String hostUrl;
    private CommandRunner commandRunner;
    private String tagName;
    private String projectName;

    public DockerAdapter(String username, String password, String hostUrl, CommandRunner cmd) {
        this.username = username;
        this.password = password;
        this.hostUrl = hostUrl;
        this.commandRunner = cmd;
        setLogin();
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public void setCommandRunner(CommandRunner runner) {
        this.commandRunner = runner;
    }

    public String buildAndPushImage() {
        String version = String.format("%s/%s:%s", username, projectName, tagName);
        String cmd = String.format("docker buildx build --push --platform=linux/amd64,linux/arm64 --tag %s .", version);
        commandRunner.execute(cmd);

        return version;
    }

    private void setLogin() {
        String cmd = String.format("docker login -u %s -p %s %s", username, password, hostUrl);
        commandRunner.execute(cmd);
    }
}

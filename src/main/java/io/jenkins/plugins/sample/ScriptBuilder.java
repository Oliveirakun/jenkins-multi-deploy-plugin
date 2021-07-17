package io.jenkins.plugins.sample;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ScriptBuilder {
    private StringBuilder commands;

    public ScriptBuilder() {
        commands = new StringBuilder();
        commands.append("#!/bin/bash");
        commands.append(System.lineSeparator());
    }

    public void appendMessage(String message) {
        String echoMessage = String.format("echo \"%s\"", message);
        appendCommand(echoMessage);
    }

    public void appendCommand(String command) {
        commands.append(command);
        commands.append(System.lineSeparator());
    }

    public File build() {
        try {
            File script = File.createTempFile("deploy",".sh");
            Files.write(Paths.get(script.getPath()), commands.toString().getBytes(StandardCharsets.UTF_8));
            script.setExecutable(true);

            return script;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}

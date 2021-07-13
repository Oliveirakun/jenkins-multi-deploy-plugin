package io.jenkins.plugins.sample;

import java.io.*;

public class CommandRunner {
    private PrintStream logger;
    private String workingDirectory;

    public CommandRunner(PrintStream logger, String workingDirectory) {
        this.logger = logger;
        this.workingDirectory = workingDirectory;
    }

    public void execute(String command) {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.directory(new File(workingDirectory));
        processBuilder.command("/bin/bash", "-c", command);
        logger.println("Executing command: " + command);

        try {
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                logger.println(line);
            }

            int exitCode = process.waitFor();
            logger.println("Exited with error code : " + exitCode);

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

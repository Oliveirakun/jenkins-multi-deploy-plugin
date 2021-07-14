package io.jenkins.plugins.sample;

import java.io.*;
import java.nio.charset.StandardCharsets;

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

        BufferedReader reader = null;
        Process process = null;
        try {
            process = processBuilder.start();
            reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

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
        } finally {
            closeReader(reader);
            destroyProcess(process);
        }
    }

    private void closeReader(BufferedReader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void destroyProcess(Process process) {
        if (process != null)
            process.destroy();
    }
}

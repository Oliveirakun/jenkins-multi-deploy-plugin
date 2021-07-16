package io.jenkins.plugins.sample;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

        Process process = null;
        ExecutorService inputExecutor = null;
        ExecutorService errorExecutor = null;
        try {
            process = processBuilder.start();
            inputExecutor = readStreamAsync(process.getInputStream());
            errorExecutor = readStreamAsync(process.getErrorStream());

            int exitCode = process.waitFor();
            logger.println("Exited with error code : " + exitCode);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            stopExecutor(inputExecutor);
            stopExecutor(errorExecutor);
            destroyProcess(process);
        }
    }

    private void stopExecutor(ExecutorService executor) {
        if (executor != null)
            executor.shutdownNow();
    }

    private void destroyProcess(Process process) {
        if (process != null)
            process.destroyForcibly();
    }

    private ExecutorService readStreamAsync(InputStream stream) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
           readStream(stream);
        });

        return executor;
    }

    private void readStream(InputStream stream) {
        BufferedReader reader = null;
        InputStreamReader streamReader = new InputStreamReader(stream, StandardCharsets.UTF_8);
        try {
            reader = new BufferedReader(streamReader);
            String line;
            while ((line = reader.readLine()) != null) {
                logger.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeStream(stream);
            closeReader(streamReader);
            closeReader(reader);
        }
    }

    private void closeStream(InputStream stream) {
        try {
            stream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void closeReader(Reader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

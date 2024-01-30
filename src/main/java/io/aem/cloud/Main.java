package io.aem.cloud;

import io.adobe.cloudmanager.IdentityManagementApiException;
import io.aem.cloud.api.ApiBuilder;
import io.aem.cloud.api.LogApi;
import io.aem.cloud.model.Credentials;
import io.aem.cloud.model.JwtToken;
import io.aem.cloud.processor.LogDownloader;
import io.aem.cloud.processor.LogTailor;
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Main {

    public static final String PROP_ADOBE_ORG_ID = "orgId";
    public static final String PROP_ADOBE_TECHNICAL_ACCOUNT_ID = "technicalAccountId";
    public static final String PROP_CLIENT_ID = "clientId";
    public static final String PROP_CLIENT_SECRET = "clientSecret";
    public static final String PROP_PRIVATE_KEY_PATH = "privateKeyPath";
    public static final String TEMP_LOG_FILE_NAME = "aem-log.log";

    public static void main(String[] args) throws IdentityManagementApiException, IOException, InterruptedException {
        CommandLine commandLine = parse(args);
        if (commandLine == null) {
            return;
        }
        final String path = commandLine.getOptionValue("file");
        final String envId = commandLine.getOptionValue("e");
        final String programId = commandLine.getOptionValue("p");
        final String serviceName = commandLine.getOptionValue("s");
        final String logName = commandLine.getOptionValue("log");
        if (path != null && !Files.exists(Paths.get(path))) {
            throw new FileNotFoundException("File at path \"" + path + "\"" + " not found.");
        }
        CredentialsLoader credentialsLoader = new CredentialsLoader(path);
        Credentials credentials = credentialsLoader.getCredentials();
        if (credentials == null) {
            return;
        }
        String token = JwtToken.token(credentials);
        File tempDirectory = createTempDirectory();
        LogApi logApi = ApiBuilder.create(credentials, token)
                .logApi(tempDirectory, serviceName, envId, programId, logName);
        final Thread downloader = createDownloader(new LogDownloader(logApi, tempDirectory));
        final Thread logTailor = createLogTailor(new LogTailor(tempDirectory));
        addHooks(tempDirectory, downloader, logTailor);
        downloader.start();
        logTailor.start();
        downloader.join();
        logTailor.join();
    }

    private static File createTempDirectory() throws IOException {
        File tempFile = Files.createTempDirectory("aem_logs").toFile();
        tempFile.setExecutable(true);
        tempFile.setWritable(true);
        tempFile.setExecutable(true);
        return tempFile;
    }

    private static CommandLine parse(String[] args) {
        Options options = new Options();
        Option propertiesOption = Option.builder("f")
                .longOpt("file")
                .hasArg()
                .required(true)
                .desc("Path to .properties file which contains Adobe Credentials. \n" +
                        "\tRequired headers in properties file are: \"orgId\", \"technicalAccountId\", " +
                        "\"clientId\", \"clientSecret\", \"privateKeyPath\"" +
                        "\n\tUsage: -f path/to/file or --filePath path/to/file")
                .build();
        Option envOption = Option.builder("e")
                .longOpt("environmentId")
                .hasArg()
                .required(true)
                .desc("Environment ID of AEMaaCS.\n" +
                        "\tUsage: -e <env_id> or --environmentId <env_id>.")
                .build();
        Option programOption = Option.builder("p")
                .longOpt("programId")
                .hasArg()
                .required(true)
                .desc("Program ID.\n" +
                        "\tUsage: -p <program_id> or --programId <program_id>.")
                .build();
        Option logNameOpt = Option.builder("log")
                .longOpt("log")
                .hasArg()
                .required(true)
                .desc("Log name to tail. For example \"aemerror\".\n" +
                        "\tUsage: -log <log-name>.")
                .build();
        Option serviceNameOpt = Option.builder("s")
                .longOpt("service")
                .hasArg()
                .required(true)
                .desc("Service name. For example \"author\" or \"publish\".\n" +
                        "\tUsage: -s <service_name> or --service <service_name>.")
                .build();
        options.addOption(propertiesOption);
        options.addOption(envOption);
        options.addOption(programOption);
        options.addOption(logNameOpt);
        options.addOption(serviceNameOpt);
        try {
            return new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            if (e instanceof MissingOptionException) {
                List<?> missingOptions = ((MissingOptionException) e).getMissingOptions();
                for (Object option : missingOptions) {
                    final String optionName = (String) option;
                    final Option op = options.getOption(optionName);
                    System.err.printf("Required option '%s' is missing. See the Description below:\n\t%s", op.getOpt(),
                            op.getDescription());
                    System.err.println("\n");
                }
                return null;
            }
            System.err.println(e.getMessage());
        }
        return null;
    }

    private static Thread createDownloader(Runnable task) {
        final Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.setName("log-downloader");
        return thread;
    }

    private static Thread createLogTailor(Runnable task) {
        final Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.setName("log-tailor");
        return thread;
    }

    private static void addHooks(File tempDir, Thread downloader, Thread logTailor) {
        Runtime runtime = Runtime.getRuntime();

        runtime.addShutdownHook(new Thread(downloader::interrupt));
        runtime.addShutdownHook(new Thread(logTailor::interrupt));
        runtime.addShutdownHook(new Thread(() -> {
            System.out.println("\n\nShutdown signal received.");
            System.out.println("Cleaning up...");
            FileUtils.deleteQuietly(tempDir);
            System.out.println("Clean up completed successfully.");
        }));
    }
}

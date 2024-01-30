package io.aem.cloud.processor;

import io.aem.cloud.Main;
import io.aem.cloud.api.LogApi;
import lombok.Cleanup;
import lombok.RequiredArgsConstructor;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

@RequiredArgsConstructor
public class LogDownloader implements Runnable {

    private final LogApi logApi;
    private final File tempDir;

    @Override
    public void run() {
        long lastArchiveSize = 0;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                File file = logApi.downloadLog();
                if (lastArchiveSize == 0 || lastArchiveSize < file.length()) {
                    decompress(file);
                }
                lastArchiveSize = file.length();
                TimeUnit.SECONDS.sleep(10);
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                } else {
                    System.err.println("\nERROR while getting the logs from AEMaaCS." +
                            "\n\tMessage: " + e);
                    System.exit(1);
                }
            }
        }

    }

    private File decompress(File archiveLog) throws IOException, InterruptedException {
        File actualLogFile = getOrCreateActualLogFile(archiveLog);
        long uncompressedSizeOfGZip = getUncompressedSizeOfGZip(archiveLog);
        if (uncompressedSizeOfGZip > actualLogFile.length()) {
            @Cleanup InputStream archiveData = new GZIPInputStream(new FileInputStream(archiveLog));
            archiveData.skip(actualLogFile.length());
            @Cleanup OutputStream outputStream = Files.newOutputStream(actualLogFile.toPath(),
                    StandardOpenOption.APPEND);
            archiveData.transferTo(outputStream);
            outputStream.flush();
            synchronized (tempDir) {
                tempDir.notify();
                tempDir.wait();
            }
        }
        return actualLogFile;
    }

    private File getOrCreateActualLogFile(File archiveLog) throws IOException {
        String actualLogFilePath = tempDir.getPath().concat("/".concat(Main.TEMP_LOG_FILE_NAME));
        File actualLogFile;
        Path actualLogAsPath = Paths.get(actualLogFilePath);
        if (Files.notExists(actualLogAsPath)) {
            actualLogFile = Files.createFile(actualLogAsPath).toFile();
            archiveLog.setReadable(true);
            archiveLog.setWritable(true);
            archiveLog.setExecutable(true);
        } else {
            actualLogFile = actualLogAsPath.toFile();
        }
        return actualLogFile;
    }

    private long getUncompressedSizeOfGZip(File archive) throws IOException {
        @Cleanup RandomAccessFile tempFile = new RandomAccessFile(archive, "r");
        tempFile.seek(tempFile.length() - 4);
        long b4 = tempFile.read();
        long b3 = tempFile.read();
        long b2 = tempFile.read();
        long b1 = tempFile.read();
        return (b1 << 24) | (b2 << 16) + (b3 << 8) + b4;
    }
}

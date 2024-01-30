package io.aem.cloud.processor;

import io.aem.cloud.Main;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

@RequiredArgsConstructor
public class LogTailor implements Runnable {

    private final File tempDir;

    @Override
    public void run() {
        long lastKnowPointer = 0;
        System.out.println();
        try {
            while (!Thread.currentThread().isInterrupted()) {
                synchronized (tempDir) {
                    tempDir.wait();
                }
                final File logFile = getLogFile(tempDir);
                if (logFile == null) {
                    synchronized (tempDir) {
                        tempDir.notify();
                    }
                    continue;
                }
                long fileLength = logFile.length();
                if (fileLength > lastKnowPointer) {
                    RandomAccessFile raf = new RandomAccessFile(logFile, "rw");
                    raf.seek(lastKnowPointer == 0 ? fileLength - FileUtils.ONE_KB * 5 : lastKnowPointer);
                    String line;
                    while ((line = raf.readLine()) != null) {
                        System.out.println(line);
                    }
                    lastKnowPointer = raf.getFilePointer();
                    IOUtils.closeQuietly(raf);
                }
                synchronized (tempDir) {
                    tempDir.notify();
                }
            }
        } catch (InterruptedException | IOException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            } else {
                System.err.println("\nERROR while tailing the log file." +
                        "\n\tMessage: " + e);
                System.exit(1);
            }
        }
    }

    private File getLogFile(File tempDir) {
        final File[] files = tempDir.listFiles((dir, name) -> name.equals(Main.TEMP_LOG_FILE_NAME));
        if (ArrayUtils.isEmpty(files)) {
            return null;
        }
        return files[0];
    }
}

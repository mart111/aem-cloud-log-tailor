package io.aem.cloud.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.Cleanup;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import javax.ws.rs.core.UriBuilder;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class LogApi {

    private static final String BASE_URL = "https://cloudmanager.adobe.io";
    private static final String DOWNLOAD_URL = "/api/program/{programId}/environment/{envId}/logs/download";

    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    private static final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().build();
    private final static Set<Long> cache = new CopyOnWriteArraySet<>();

    private final String clientId;
    private final String accessToken;
    private final String orgId;
    private final String service;
    private final String logName;
    private final String envId;
    private final String programId;
    private final File tempDir;

    static LogApi create(String clientId,
                         String accessToken,
                         String orgId,
                         String service, String logName,
                         String envId,
                         String programId,
                         File tempDir) {
        return new LogApi(clientId, accessToken, orgId, service, logName, envId, programId,
                tempDir);
    }

    public File downloadLog() throws IOException, InterruptedException {
        final HttpRequest redirectRequest = getRedirectRequest();

        HttpResponse<String> redirectResponse = httpClient.send(redirectRequest,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (redirectResponse.statusCode() != 200) {
            throw new RuntimeException("Failed to tail the logs." +
                    "\n\t Error response with code: " + redirectResponse.statusCode() + "\n\t" +
                    "Body: \t" + redirectResponse.body());
        }
        final String body = redirectResponse.body();
        final String redirectUrl = getRedirectUrlFromJson(body);
        if (redirectUrl.isEmpty()) {
            throw new RuntimeException("Failed to tail the logs." +
                    "\n\t Error response with code: " + redirectResponse.statusCode() + "\n\t" +
                    "Body: \t" + redirectResponse.body());
        }
        HttpResponse<InputStream> logResponse = httpClient.send(HttpRequest.newBuilder(URI.create(redirectUrl))
                .build(), HttpResponse.BodyHandlers.ofInputStream());
        if (logResponse.statusCode() != 200) {
            throw new RuntimeException("Failed to tail the logs." +
                    "\n\t Error message: Failed to obtain log data from remote server." + "\n\t" +
                    "Body: \t" + redirectResponse.body());
        }
        return getOrCreateLogArchiveFile(logResponse);
    }

    private File getOrCreateLogArchiveFile(HttpResponse<InputStream> logResponse) throws IOException {
        @Cleanup InputStream inputStream = logResponse.body();
        final String archivePath = tempDir.getPath().concat("/aemlog.log.gz");
        Path archiveAsPath = Paths.get(archivePath);
        File archiveFile;
        if (Files.notExists(archiveAsPath)) {
            archiveFile = Files.createFile(archiveAsPath).toFile();
            archiveFile.setReadable(true);
            archiveFile.setWritable(true);
            archiveFile.setExecutable(true);
        } else {
            archiveFile = archiveAsPath.toFile();
        }

        long contentLength = getContentLength(logResponse);
        if (!cache.contains(contentLength)) {
            FileUtils.copyToFile(inputStream, archiveFile);
        }
        cache.add(contentLength);
        return archiveFile;
    }

    private long getContentLength(HttpResponse<InputStream> logResponse) {
        return logResponse.headers().firstValue("content-length")
                .map(Long::parseLong)
                .orElse(0L);
    }

    private String getRedirectUrlFromJson(String json) throws JsonProcessingException {
        if (json == null || json.isEmpty()) {
            return StringUtils.EMPTY;
        }
        return mapper.readTree(json)
                .get("redirect")
                .asText(StringUtils.EMPTY);
    }

    private HttpRequest getRedirectRequest() {
        return HttpRequest.newBuilder(getURI())
                .GET()
                .header("x-gw-ims-org-id", orgId)
                .header("Authorization", accessToken)
                .header("x-api-key", clientId)
                .header("Accept", "application/json")
                .build();
    }

    private URI getURI() {
        return UriBuilder.fromUri(URI.create(BASE_URL))
                .path(DOWNLOAD_URL)
                .queryParam("service", service)
                .queryParam("name", logName)
                .queryParam("date", sdf.format(new Date()))
                .build(programId, envId);
    }
}

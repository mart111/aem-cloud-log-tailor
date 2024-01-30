package io.aem.cloud.api;

import io.adobe.cloudmanager.CloudManagerApi;
import io.aem.cloud.model.Credentials;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.io.File;


@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiBuilder {
    private final Credentials credentials;
    private final String token;

    public static ApiBuilder create(Credentials credentials, String token) {
        return new ApiBuilder(credentials, token);
    }

    public CloudManagerApi cloudManagerApi() {
        return CloudManagerApi.create(credentials.getOrgId(),
                credentials.getClientId(),
                token);
    }

    public LogApi logApi(File tempDir,
                         String service,
                         String envId,
                         String programId,
                         String logName) {
        return LogApi.create(credentials.getClientId(),
                token,
                credentials.getOrgId(),
                service,
                logName,
                envId,
                programId,
                tempDir);
    }
}

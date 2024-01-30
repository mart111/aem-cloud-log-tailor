package io.aem.cloud.model;

import io.adobe.cloudmanager.AdobeClientCredentials;
import io.adobe.cloudmanager.IdentityManagementApi;
import io.adobe.cloudmanager.IdentityManagementApiException;

public class JwtToken {

    public static String token(Credentials credentials) throws IdentityManagementApiException {
        final AdobeClientCredentials adobeClientCredentials = new AdobeClientCredentials(credentials.getOrgId(),
                credentials.getTechnicalAccountId(),
                credentials.getClientId(),
                credentials.getClientSecret(),
                credentials.getPrivateKey());
        return IdentityManagementApi.create()
                .authenticate(adobeClientCredentials);
    }
}



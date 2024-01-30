package io.aem.cloud;

import io.adobe.cloudmanager.AdobeClientCredentials;
import io.aem.cloud.model.Credentials;
import lombok.Cleanup;
import lombok.RequiredArgsConstructor;

import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Properties;

import static io.aem.cloud.Main.*;

@RequiredArgsConstructor
public class CredentialsLoader {
    private final String path;
    private final Credentials.CredentialsBuilder credentialsBuilder = Credentials.builder();

    public Credentials getCredentials() {
        try {
            @Cleanup InputStream credentials = null;
            @Cleanup BufferedReader reader = null;
            if (path == null || path.isEmpty()) {
                credentials = this.getClass().getClassLoader().getResourceAsStream("adobe-credentials.properties");
            } else {
                credentials = new FileInputStream(path);
            }
            if (credentials == null) {
                throw new FileNotFoundException("Adobe Credentials file couldn't be found under path: " + path);
            }
            reader = new BufferedReader(new InputStreamReader(credentials));
            final Properties properties = new Properties();
            properties.load(reader);
            final String privateKeyPath = properties.getProperty(PROP_PRIVATE_KEY_PATH);
            loadPrivateKey(privateKeyPath);

            credentialsBuilder.orgId(properties.getProperty(PROP_ADOBE_ORG_ID))
                    .clientId(properties.getProperty(PROP_CLIENT_ID))
                    .clientSecret(properties.getProperty(PROP_CLIENT_SECRET))
                    .technicalAccountId(properties.getProperty(PROP_ADOBE_TECHNICAL_ACCOUNT_ID));
            return credentialsBuilder.build();
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            System.err.println("Error while loading the credentials." +
                    "\n\tMessage: " + e.getMessage());
        }
        return null;
    }

    private void loadPrivateKey(String path) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        @Cleanup BufferedReader privateKeyStream = null;
        @Cleanup InputStream inputStream = null;
        if (path == null || path.isEmpty()) {
            inputStream = this.getClass().getClassLoader().getResourceAsStream("private.key");
            if (inputStream == null) {
                throw new FileNotFoundException("Private Key file couldn't be found in classpath.");
            }
            privateKeyStream = new BufferedReader(new InputStreamReader(inputStream));
        } else {
            privateKeyStream = new BufferedReader(new InputStreamReader(new FileInputStream(path)));
        }
        credentialsBuilder.privateKey(AdobeClientCredentials.getKeyFromPem(privateKeyStream));
    }
}

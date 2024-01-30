package io.aem.cloud.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.security.PrivateKey;

@Builder
@Getter
@ToString
public class Credentials {
    private String orgId;
    private String clientId;
    private String technicalAccountId;
    private String clientSecret;
    private PrivateKey privateKey;
}

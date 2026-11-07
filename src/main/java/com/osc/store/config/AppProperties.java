package com.osc.store.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    private Cors cors = new Cors();
    private Jwt jwt = new Jwt();
    private Minio minio = new Minio();
    private Mail mail = new Mail();
    private Company company = new Company();
    private Bank bank = new Bank();
    private SuperAdmin superAdmin = new SuperAdmin();

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private long expirationHours = 12;
    }

    @Getter
    @Setter
    public static class Minio {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket;
        private String publicUrl;
    }

    @Getter
    @Setter
    public static class Mail {
        private String from;
        private String fromName;
        private String notifyTo;
        private String logoUrl;
    }

    @Getter
    @Setter
    public static class Company {
        private String name;
        private String email;
        private String phone;
        private String website;
    }

    @Getter
    @Setter
    public static class Bank {
        private String bankName;
        private String accountName;
        private String accountNumber;
        private String accountType;
        private String branchCode;
        private String swift;
    }

    @Getter
    @Setter
    public static class SuperAdmin {
        private String name;
        private String email;
        private String password;
    }
}

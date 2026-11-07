package com.osc.store.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class StartupChecks implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupChecks.class);

    private final AppProperties props;
    private final Environment env;

    public StartupChecks(AppProperties props, Environment env) {
        this.props = props;
        this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppProperties.Minio minio = props.getMinio();
        String from = props.getMail().getFrom();
        if (from == null || !from.contains("@")) {
            log.warn("SPRING_MAIL_FROM must be a real email address (for example hello@yourdomain.com). Current value will fail SMTP.");
        }
        log.info("Wired: db={}, mail={}, from={}, minio={}/{}, cors={}",
                hostOf(env.getProperty("spring.datasource.url")),
                env.getProperty("spring.mail.host"),
                from,
                minio.getEndpoint(),
                minio.getBucket(),
                props.getCors().getAllowedOrigins());
    }

    private String hostOf(String url) {
        if (url == null) return "(missing)";
        int at = url.indexOf("://");
        if (at < 0) return url;
        String rest = url.substring(at + 3);
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }
}

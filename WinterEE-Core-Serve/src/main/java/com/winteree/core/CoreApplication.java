package com.winteree.core;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.security.oauth2.client.EnableOAuth2Sso;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.SpringCloudApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;

/**
 * CoreApplication
 *
 * @author RenFei
 */
@EnableAsync
@EnableOAuth2Sso
@EnableFeignClients
@SpringCloudApplication
@EnableConfigurationProperties
@EnableGlobalMethodSecurity(prePostEnabled = true)
@MapperScan(basePackages = "com.winteree.core.dao")
public class CoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(CoreApplication.class, args);
    }
}

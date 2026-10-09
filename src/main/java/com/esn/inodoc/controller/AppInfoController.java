package com.esn.inodoc.controller;

import com.esn.inodoc.config.AppProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the public application information (commercial name, version)
 * read from the configuration. Accessible without authentication.
 */
@RestController
@RequestMapping("/public")
public class AppInfoController {

    private final AppProperties appProperties;

    public AppInfoController(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @GetMapping("/app-info")
    public AppInfo appInfo() {
        AppProperties.App app = appProperties.getApp();
        return new AppInfo(app.getName(), app.getVersion());
    }

    public record AppInfo(String name, String version) {
    }
}

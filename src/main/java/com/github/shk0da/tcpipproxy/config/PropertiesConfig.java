package com.github.shk0da.tcpipproxy.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.configuration.CombinedConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;

import java.io.File;
import java.util.List;

@Slf4j
public class PropertiesConfig {

    private static final CombinedConfiguration configuration = new CombinedConfiguration();

    static {
        try {
            // Main
            if (PropertiesConfig.class.getClassLoader().getResource("application.properties") != null) {
                PropertiesConfiguration properties = new PropertiesConfiguration("application.properties");
                properties.setReloadingStrategy(new FileChangedReloadingStrategy());
                configuration.addConfiguration(properties);
            }

            // External ('-Dspring.config.location=file:./app.properties')
            if ((new File("app.properties")).exists()) {
                PropertiesConfiguration properties = new PropertiesConfiguration("app.properties");
                properties.setReloadingStrategy(new FileChangedReloadingStrategy());
                configuration.addConfiguration(properties);
            }

            configuration.setForceReloadCheck(true);
        } catch (ConfigurationException ex) {
            log.error(ex.getMessage());
        }
    }

    public static synchronized String getProperty(String key) {
        Object property = configuration.getProperty(key);
        if (property instanceof List) {
            return (String) ((List) property).get(((List) property).size() - 1);
        }

        return (String) configuration.getProperty(key);
    }
}

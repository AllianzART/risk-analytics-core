package org.pillarone.riskanalytics.core.util

import grails.util.Holders
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

public class Configuration {
    private static Log LOG = LogFactory.getLog(Configuration)

    public static boolean getBoolean(String key, boolean defaultValue) {
        ConfigObject config = Holders.config
        if (config != null) {
            if (config.containsKey(key)) {
                def value = config[key]
                if (value instanceof Boolean) {
                    return value
                }
            }
        }

        return defaultValue
    }

    // I forget standard precedence?  I guess: -D switch overrules config file entry, right ?
    //
    public static String coreGetAndLogStringConfig(String key, String defaultValue) {
        String s = System.getProperty(key)?.trim()
        if( s != null && s.size() > 0 ){
            LOG.info("System property recognised: -D${key}=${s}")
            return s
        }

        s = Holders.config.get(key)?.toString()?.trim()
        if( s != null && s.size() > 0 ){
            LOG.info("Config key (not -D switch) found: ${key}=${s}")
            return s
        }

        LOG.info("No useful config or -D switch found for '$key', defaulting to $defaultValue")
        return defaultValue
    }

    public static int coreGetAndLogIntConfig(String key, int defaultValue) {
        String s = System.getProperty(key)?.trim()
        if( s != null && s.size() > 0 ){
            try {
                int i = Integer.parseInt(s)
                LOG.info("System property recognised: -D${key}=${i}")
                return i
            } catch (NumberFormatException e) { // Typo maybe
                LOG.warn("NOT an int - supplied system property '-D${key}=${s}', defaulting to $defaultValue")
                return defaultValue
            }
        }

        s = Holders.config.get(key)?.toString()?.trim()
        if( s != null && s.size() > 0 ){
            LOG.info("Config key (not -D switch) found: ${key}=${s}")
            try {
                int i = Integer.parseInt(s)
                LOG.info("System property recognised: -D${key}=${i}")
                return i
            } catch (NumberFormatException e) { // Typo maybe
                LOG.warn("NOT an int - supplied system property '-D${key}=${s}', defaulting to $defaultValue")
                return defaultValue
            }
        }

        LOG.info("No useful config or -D switch found for '$key', defaulting to $defaultValue")
        return defaultValue
    }
}

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

    // Try work around initialisation order problems (found one with art-reports plugin)
    private static void report(Log log, String msg){
        if(log){
            log.info(msg)
        }else{
            System.out.println(msg)
        }
    }
    // I forget standard precedence?  I guess: -D switch overrules config file entry, right ?
    //
    public static String coreGetAndLogStringConfig(String key, String defaultValue, Log log = LOG) {
        String s = System.getProperty(key)?.trim()
        if( s != null && s.size() > 0 ){
            report(log, "System property recognised: -D${key}=${s}")
            return s
        }

        s = Holders.config.get(key)?.toString()?.trim()
        if( s != null && s.size() > 0 ){
            report(log, "Config key (not -D switch) found: ${key}=${s}")
            return s
        }

        report(log, "No useful config or -D switch found for '$key', defaulting to $defaultValue")
        return defaultValue
    }

    public static int coreGetAndLogIntConfig(String key, int defaultValue, Log log = LOG) {
        String s = System.getProperty(key)?.trim()
        if( s != null && s.size() > 0 ){
            try {
                int i = Integer.parseInt(s)
                report(log, "System property recognised: -D${key}=${i}")
                return i
            } catch (NumberFormatException e) { // Typo maybe
                report(log, "NOT an int - supplied system property '-D${key}=${s}', defaulting to $defaultValue")
                return defaultValue
            }
        }

        if(Holders.config != null){
            s = Holders.config.get(key)?.toString()?.trim()
            if( s != null && s.size() > 0 ){
                try {
                    int i = Integer.parseInt(s)
                    report(log, "Config key (not -D switch) found: ${key}=${s}")
                    return i
                } catch (NumberFormatException e) { // Typo maybe
                    report(log, "NOT an int - supplied config entry '${key}=${s}', defaulting to $defaultValue")
                    return defaultValue
                }
            }
        }else{
            report(log,"Holders.config is null! Cannot look in external config file..")
        }

        report(log, "No useful config or -D switch found for '$key', defaulting to $defaultValue")
        return defaultValue
    }
}

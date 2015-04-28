//Use a custom plugins dir, because different branches use different plugin versions
grails.project.plugins.dir = "../local-plugins/RiskAnalyticsCore-master"

grails.project.dependency.resolver = "maven"

grails.project.dependency.resolution = {
    inherits "global" // inherit Grails' default dependencies
    log "warn"

    repositories {
        grailsHome()
        mavenLocal()

        mavenRepo (name:"pillarone" , url:"http://zh-artisan-test.art-allianz.com:8085/nexus/content/groups/public/") {
            updatePolicy System.getProperty('snapshotUpdatePolicy') ?: 'daily'
        }

        //can be removed when newest ignite version 1.0.2 is moved to maven central
//        mavenRepo 'http://www.gridgainsystems.com/nexus/content/repositories/external'

        mavenCentral()
        grailsCentral()
        mavenRepo 'http://repo.spring.io/milestone'
    }

    plugins {
        runtime ":background-thread:1.3"
        runtime ":hibernate:3.6.10.3"
        runtime ":release:3.0.1", {
            exclude "groovy"
        }
        runtime ":spring-security-core:2.0-RC2"
        runtime ":tomcat:7.0.42"

        test ":code-coverage:1.2.7"
        runtime ":database-migration:1.3.6"
        test ":codenarc:0.20"
    }

    dependencies {
        compile 'org.apache.ignite:ignite-core:1.0.2'  , {
            excludes 'spring-aop', 'spring-beans','spring-core' ,'spring-context','spring-expression'
        }
        compile 'org.apache.ignite:ignite-spring:1.0.2' , {
            excludes 'spring-aop', 'spring-beans','spring-core' ,'spring-context','spring-expression'
        }
        compile('com.google.guava:guava:18.0')
        runtime 'net.sf.jasperreports:jasperreports:4.0.1', {
            excludes "xml-apis", "commons-collections", "jdtcore"
        }
        compile('org.apache.poi:poi:3.9')
        compile('org.apache.poi:poi-ooxml:3.9') {
            exclude "xmlbeans"
        }
        compile("org.apache.xmlbeans:xmlbeans:2.3.0")

        compile 'commons-cli:commons-cli:1.2'

        compile 'joda-time:joda-time:2.3'
        runtime("javax.mail:mail:1.4.4")
        runtime("net.sf.jtidy:jtidy:r938")
//        runtime("net.sf.cron4j:cron4j:2.2.5")
        runtime("com.sun.grizzly:grizzly-utils:1.9.43")
        runtime("jboss:jboss-serialization:4.2.2.GA")
        runtime("trove:trove:1.0.2")
        runtime("org.fusesource.jansi:jansi:1.2.1")
        //see http://jira.grails.org/browse/GRAILS-10671
        build "com.lowagie:itext:2.1.7"
        //build "org.apache.maven.wagon:wagon-http:jar:1.0-beta-2"
    }
}

grails.project.repos.default = "pillarone"

grails.project.dependency.distribution = {
    String password = ""
    String user = ""
    String scpUrl = "scpUrl"
    try {
        Properties properties = new Properties()
        String version = new GroovyClassLoader().loadClass('RiskAnalyticsCoreGrailsPlugin').newInstance().version
        println "reading deploy settings from ${userHome}/deployInfo.properties"

        properties.load(new File("${userHome}/deployInfo.properties").newInputStream())
        user = properties.get("user")
        password = properties.get("password")

        if (version?.endsWith('-SNAPSHOT')) {
            scpUrl = properties.get("urlSnapshot")
        } else {
            scpUrl = properties.get("url")
        }
    } catch (Throwable t) {
    }
    remoteRepository(id: "pillarone", url: scpUrl) {
        authentication username: user, password: password
    }
}

coverage {
    exclusions = [
            'models/**',
            '**/*Test*',
            '**/com/energizedwork/grails/plugins/jodatime/**',
            '**/grails/util/**',
            '**/org/codehaus/**',
            '**/org/grails/**',
            '**GrailsPlugin**',
            '**TagLib**'
    ]

}
codenarc.maxPriority1Violations = 0
codenarc.maxPriority2Violations = 0
codenarc.maxPriority3Violations = 0

codenarc.properties = {
    MisorderedStaticImports.enabled = false
    FactoryMethodName.enabled = false
    CatchException.enabled = false
    SerializableClassMustDefineSerialVersionUID.enabled = false
    ClassJavadoc.enabled = false

    //domain
    GrailsDomainHasEquals.enabled = false
    GrailsDomainHasToString.enabled = false

    //formatting
    SpaceAroundMapEntryColon.enabled = false
    SpaceBeforeOpeningBrace.enabled = false
    SpaceAfterIf.enabled = false
    UnnecessaryReturnKeyword.enabled = false
    //TODO discuss about rules together
//    GrailsPublicControllerMethod.enabled = false
//    SimpleDateFormatMissingLocale.enabled = false
//    ThrowRuntimeException.enabled = false
//    CatchThrowable.enabled = false
//    CatchRuntimeException.enabled = false
//    ThrowException.enabled = false
//    ReturnNullFromCatchBlock.enabled = false


    LineLength.length = 200

    def allTestClasses = testClasses().join(', ')

    MethodName.doNotApplyToClassNames = allTestClasses
    AbcComplexity.doNotApplyToClassNames = allTestClasses
    UnnecessaryGString.doNotApplyToClassNames = allTestClasses
}

private List testClasses() {
    List result = []
    new File('./test/').eachFileRecurse { File file ->
        String name = file.name
        if (name.endsWith('Tests.groovy')) {
            result << name.replaceAll('.groovy', '')
        }
    }
    result
}

codenarc.reports = {
    Jenkins('xml') {
        outputFile = 'target/code-analysis/CodeNarcReport.xml'
        title = 'Code Narc Code Report'
    }
    LocalReport('html') {
        outputFile = 'target/code-analysis/CodeNarcReport.html'
        title = 'Code Narc Code Report'
    }
}

codenarc.ruleSetFiles = [
        'rulesets/basic.xml',
        'rulesets/braces.xml',
        'rulesets/concurrency.xml',
        'rulesets/design.xml',
        'rulesets/exceptions.xml',
        'rulesets/formatting.xml',
        'rulesets/grails.xml',
        'rulesets/imports.xml',
        'rulesets/jdbc.xml',
        'rulesets/junit.xml',
        'rulesets/logging.xml',
        'rulesets/naming.xml',
        'rulesets/security.xml',
        'rulesets/serialization.xml',
        'rulesets/size.xml',
        'rulesets/unnecessary.xml',
        'rulesets/unused.xml'].join(',').toString()


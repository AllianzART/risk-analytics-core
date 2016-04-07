package org.pillarone.riskanalytics.core.queue

import grails.plugin.mail.MailService
import grails.util.Holders
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.pillarone.riskanalytics.core.simulation.engine.SimulationQueueTaskContext
import org.pillarone.riskanalytics.core.upload.UploadQueueTaskContext
import org.pillarone.riskanalytics.core.util.Configuration
import org.springframework.util.StringUtils

import javax.validation.constraints.NotNull

import static org.springframework.util.StringUtils.isEmpty

/**
 * Notifies users via e-mail on queue events
 */
class MailNotificationQueueListener<Q extends IQueueEntry> implements QueueListener<Q> {
    private static final Log LOG = LogFactory.getLog(MailNotificationQueueListener)

    private final String DEFAULT_EMAIL_USERNAME = Configuration.coreGetAndLogStringConfig( "email_defaultUser", "" )
    public  final String EMAIL_SUFFIX = Configuration.coreGetAndLogStringConfig( "email_orgAddress", "" )
    private final String SENDER = "srvartisan" + "@" + EMAIL_SUFFIX;
    private final boolean isMailServiceConfigured = !isEmpty(Holders.config.mail?.host)

    private Map<UUID, List<String>> notificationMap = new HashMap();

    @Override
    void starting(Q entry) {
    }

    @Override
    void finished(Q entry) {
        if(!isMailServiceConfigured){
            LOG.info("Queue item finished but no mail - service not configured ")
            return
        }
        if( isEmpty(EMAIL_SUFFIX) ){
            LOG.warn("MailService is configured but no email_orgAddress supplied! Cannot send mails ")
            return
        }

        if( isEmpty(DEFAULT_EMAIL_USERNAME) ){
            LOG.warn("MailService is configured but no email_defaultUser supplied.. Cannot cc: mails to admin")
        }

        final List<String> recipients = notificationMap.get(entry.getId())

        if(recipients){
            emailQueueEntryFinished(       // if @CompileStatic not used we get dynamic dispatch
                entry?.context,
                recipients.collect { it + "@" + EMAIL_SUFFIX },
                isEmpty(DEFAULT_EMAIL_USERNAME) ? null : DEFAULT_EMAIL_USERNAME+"@"+EMAIL_SUFFIX // cc:
            )
        }
    }

    void emailQueueEntryFinished( def context, List<String> recipients, String copyTo  ){
        LOG.warn("Unknown context class ${context.getClass().getSimpleName()} - no mail sent to ${recipients}")
    }

    void emailQueueEntryFinished( SimulationQueueTaskContext context, List<String> recipients, String copyTo  ){
        String state = context.simulationTask?.simulationState?.name() // nb brackets needed!
        String entryName = context.simulationTask?.simulation?.name
        sendMail(
                SENDER, recipients, copyTo,
                "Keeping you posted - sim ${state}",
                "Sim '$entryName' is now '$state'."
        )
    }

    void emailQueueEntryFinished( UploadQueueTaskContext context, List<String> recipients, String copyTo  ){
        String state = context.uploadState?.name() // nb brackets needed!
        String entryName = context.configuration?.simulation?.name
        sendMail(
                SENDER, recipients, copyTo,
                "Keeping you posted - upload ${state}",
                "Upload of '$entryName' is now '$state'."
        )
    }

    void sendMail(String sender, List<String> recipients, String copyTo, String subjectText, String bodyText){
        try {
            String recipientsCSV = recipients.join(",")
            LOG.info("Sending mail '${subjectText}' to $recipientsCSV (cc: $copyTo)")
            if(isEmpty(copyTo) || recipients.contains(copyTo) ){
                mailService.sendMail {
                    async true
                    to recipientsCSV
                    from sender
                    subject subjectText
                    body bodyText
                }
            }else{
                mailService.sendMail {
                    async true
                    to recipientsCSV
                    from sender
                    cc copyTo
                    subject subjectText
                    body bodyText
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to send mail '${subjectText}'", e)
        }
    }

    @Override
    void removed(UUID id) {
        notificationMap.remove(id)
    }

    @Override
    void offered(Q entry) {
        //Testing
//        String username = entry.getContext().getUsername()
//        String emailAddress = (username ?: DEFAULT_EMAIL_USERNAME) + "@" + EMAIL_SUFFIX
//        registerForNotification(entry.getId(), emailAddress)
    }

    public void registerForNotification(@NotNull UUID queueEntryId, @NotNull String emailAddress) {
        List<String> emailAddresses = notificationMap.get(queueEntryId)
        if (emailAddresses != null) {
            emailAddresses.add(emailAddress)
        } else {
            List<String> newEmailAddresses = new ArrayList<String>()
            newEmailAddresses.add(emailAddress)
            notificationMap.put(queueEntryId, newEmailAddresses)
        }
    }

    private MailService getMailService() {
        Holders.grailsApplication.mainContext.getBean('mailService', MailService)
    }


}

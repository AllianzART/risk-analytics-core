package org.pillarone.riskanalytics.core.queue

import grails.plugin.mail.MailService
import grails.util.Holders
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.pillarone.riskanalytics.core.batch.BatchRunService
import org.pillarone.riskanalytics.core.simulation.SimulationState
import org.pillarone.riskanalytics.core.simulation.engine.SimulationQueueTaskContext
import org.pillarone.riskanalytics.core.upload.UploadQueueTaskContext
import org.pillarone.riskanalytics.core.util.Configuration

import javax.validation.constraints.NotNull

/**
 * Notifies users via e-mail on queue events
 */
class MailNotificationQueueListener<Q extends IQueueEntry> implements QueueListener<Q> {
    private static final Log LOG = LogFactory.getLog(MailNotificationQueueListener)
    public static final String EMAIL_SUFFIX = Configuration.coreGetAndLogStringConfig( "email_orgAddress", "does_not_exist")
    private Map<UUID, List<String>> notificationMap = new HashMap();
    private static final String  DEFAULT_EMAIL_USERNAME = Configuration.coreGetAndLogStringConfig( "email_defaultUser", "does_not_exist")
    private static final boolean mailServiceConfigured = (Holders?.config?.mail?.host != "foo.bar.com")

    @Override
    void starting(Q entry) {
    }

    @Override
    void finished(Q entry) {
        if(!mailServiceConfigured){
            return
        }
        List<String> recipients = notificationMap.get(entry.getId())

        if( recipients  ){
            def context = entry?.context
            String state = "N/A"
            String entryName = "N/A"
            String queueType = "Upload"
            if (context instanceof SimulationQueueTaskContext) {
                state = (context as SimulationQueueTaskContext).simulationTask?.simulationState?.name() // nb brackets needed!
                entryName = (context as SimulationQueueTaskContext).simulationTask?.simulation?.name
                queueType = "Simulation"
            } else if (context instanceof UploadQueueTaskContext) {
                state = (context as UploadQueueTaskContext).uploadState?.name() // nb brackets needed!
                entryName = (context as UploadQueueTaskContext)?.configuration?.simulation?.name
            } else {
                LOG.warn("Unknown context implementation " + context.getClass().getSimpleName())
            }

            String subjectText = queueType + " Queue entry '${entryName}' is now ${state}"
            // Got recipients
            String bodyText = "You wanted to be notified when $queueType '$entryName' was done.\nIt is now in state '$state'."

            mailService.sendMail {
                async true
                to recipients.join(",")
                from "srvartisan" + "@" + EMAIL_SUFFIX
                cc DEFAULT_EMAIL_USERNAME + "@" + EMAIL_SUFFIX
                subject subjectText
                body bodyText
            }

            LOG.info("Email on finished event (state: $state) to ") + recipients.toString()
            // todo
        } else {
            LOG.info('nobody interested in this poor queue entry :-(')
        }


    }

    @Override
    void removed(UUID id) {
        String strings = notificationMap.get(id)?.toString() ?: "nobody"
        LOG.info("Email on remove event to " + strings)
        //todo
        notificationMap.remove(id)
    }

    @Override
    void offered(Q entry) {
        //TODO remove this
        String username = entry.getContext().getUsername()
        String emailAddress = (username ?: DEFAULT_EMAIL_USERNAME) + "@" + EMAIL_SUFFIX
        registerForNotification(entry.getId(), emailAddress)
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

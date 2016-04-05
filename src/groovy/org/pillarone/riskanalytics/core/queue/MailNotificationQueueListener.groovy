package org.pillarone.riskanalytics.core.queue

import javax.validation.constraints.NotNull

/**
 * Notifies users via e-mail on queue events
 */
class MailNotificationQueueListener<Q extends IQueueEntry> implements QueueListener<Q> {
    public static final String EMAIL_SUFFIX = "@art-allianz.com"
    private Map<UUID, List<String>> notificationMap = new HashMap();

    @Override
    void starting(Q entry) {
        println("[QueueListener] startet :" + entry.toString() + " by user " + entry.getContext().getUsername())

    }

    @Override
    void finished(UUID id) {
        println("[QueueListener] finished :" + id.toString())
        String strings = notificationMap.get(id) != null? notificationMap.get(id).toString() : "nobody"
        println("Email on finised event to " + strings)

    }

    @Override
    void removed(UUID id) {
        println("[QueueListener] removed :" + id.toString())
        String strings = notificationMap.get(id) != null? notificationMap.get(id).toString() : "nobody"
        println("Email on remove event to " + strings)

        notificationMap.remove(id)
    }

    @Override
    void offered(Q entry) {
        println("[QueueListener] offered :" + entry.toString()  + " by user " + entry.getContext().getUsername())

        //TODO remove this
        String username = entry.getContext().getUsername()
        String email = username != null? username + EMAIL_SUFFIX : "fazl.rahman" + EMAIL_SUFFIX
        addNotification(entry.getId(), email)

    }

    public void addNotification(@NotNull UUID queueEntryId, @NotNull String emailAddress) {
        List<String> emailAddresses = notificationMap.get(queueEntryId)
        if (emailAddresses != null) {
            emailAddresses.add(emailAddress)
        } else {
            List<String> newEmailAddresses = new ArrayList<String>()
            newEmailAddresses.add(emailAddress)
            notificationMap.put(queueEntryId, newEmailAddresses)
        }
    }

}

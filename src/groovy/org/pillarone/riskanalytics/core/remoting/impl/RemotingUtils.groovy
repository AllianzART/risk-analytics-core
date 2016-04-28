package org.pillarone.riskanalytics.core.remoting.impl

import grails.util.Holders
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.pillarone.riskanalytics.core.remoting.ITransactionService
import org.pillarone.riskanalytics.core.remoting.TransactionInfo

class RemotingUtils {

    private static Log LOG = LogFactory.getLog(RemotingUtils)

    public static ITransactionService getTransactionService() {
        long tt = System.currentTimeMillis()
        ITransactionService transactionService = (ITransactionService) Holders.grailsApplication.mainContext.getBean("transactionService")
        tt = System.currentTimeMillis() - tt
        LOG.info("Timed " + tt + " ms: getBean(transactionService)");
        try {
            tt = System.currentTimeMillis()
//            List<TransactionInfo> txnList = transactionService.getAllTransactions()
            transactionService.ping()
            tt = System.currentTimeMillis() - tt
            LOG.info("Timed " + tt + " ms: transactionService.ping()");
            return transactionService
        } catch (Throwable t) {
            tt = System.currentTimeMillis() - tt
            LOG.error "Timed $tt ms: Error obtaining remote service: ${t.message}"
            return [
                    getAllTransactions: {
                        return [new TransactionInfo(1, "Connection failed - contact support.", "")]
                    }
            ] as ITransactionService
        }
    }

    public static List<TransactionInfo> getAllTransactions() {
        return getTransactionService().allTransactions.sort { it.name?.toUpperCase() }
    }

}

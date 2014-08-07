package org.pillarone.riskanalytics.core.upload

import models.core.CoreModel
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

class LogOnlyUploadStrategy implements IUploadStrategy {
    private final static Log LOG = LogFactory.getLog(BasicUploadFuture)

    @Override
    IUploadFuture upload(UploadConfiguration configuration, int priority) {
        BasicUploadFuture future = new BasicUploadFuture()

        new Thread(new Runnable() {
            @Override
            void run() {
                LOG.debug("start upload simulation: ${configuration.simulation}")
                Thread.sleep(1000)
                if (future.canceled) {
                    LOG.debug("task was canceled -> exit")
                    return
                }
                LOG.debug("finished uploading simulation: ${configuration.simulation}")
                if (configuration.simulation.modelClass == CoreModel) {
                    future.failed(['upload not possible with core model'])
                } else {
                    future.done()
                }
            }
        }).start()
        future
    }

}
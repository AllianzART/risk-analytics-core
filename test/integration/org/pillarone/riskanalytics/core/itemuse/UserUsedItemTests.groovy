package org.pillarone.riskanalytics.core.itemuse

import org.joda.time.DateTime
import org.junit.Test
import org.pillarone.riskanalytics.core.ParameterizationDAO
import org.pillarone.riskanalytics.core.example.model.EmptyModel
import org.pillarone.riskanalytics.core.simulation.item.Parameterization
import org.pillarone.riskanalytics.core.simulation.item.VersionNumber
import org.pillarone.riskanalytics.core.user.itemuse.item.UserUsedParameterization

import static org.junit.Assert.assertNotNull

/**
 * Created by IntelliJ IDEA.
 * User: bzetterstrom
 * Date: 28/06/11
 * Time: 17:38
 * To change this template use File | Settings | File Templates.
 */
class UserUsedItemTests {
    @Test
    void testUserUsedParameterization() {
        UserUsedParameterization userUsedParameterization = new UserUsedParameterization()
        userUsedParameterization.time = new DateTime();
        Parameterization parameterization = new Parameterization('testUserUsedParameterization')
        parameterization.versionNumber = new VersionNumber('1')
        parameterization.modelClass = EmptyModel
        parameterization.save()
        userUsedParameterization.parameterization = parameterization.dao
        userUsedParameterization.save();

        assertNotNull(UserUsedParameterization.findByParameterization(ParameterizationDAO.findByName("testUserUsedParameterization")))
    }
}

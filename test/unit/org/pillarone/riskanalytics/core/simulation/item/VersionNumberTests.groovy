package org.pillarone.riskanalytics.core.simulation.item

import grails.test.GrailsMock
import grails.test.mixin.TestMixin
import grails.test.mixin.support.GrailsUnitTestMixin
import org.pillarone.riskanalytics.core.ParameterizationDAO
import org.pillarone.riskanalytics.core.example.model.EmptyModel

@TestMixin(GrailsUnitTestMixin)
class VersionNumberTests {

    void testParse() {
        assertEquals '1', new VersionNumber('1').toString()
        assertEquals '1.1', new VersionNumber('1.1').toString()
        assertEquals 'R1', new VersionNumber('R1').toString()
        assertEquals 'R1.1', new VersionNumber('R1.1').toString()
        assertEquals '1.1.1', new VersionNumber('1.1.1').toString()
        assertEquals '2.10.111', new VersionNumber('2.10.111').toString()
    }

    void testCompareTo() {
        assertEquals 1, new VersionNumber('2').compareTo(new VersionNumber('1'))
        assertEquals 1, new VersionNumber('1.1').compareTo(new VersionNumber('1'))
        assertEquals new Integer(-1), new VersionNumber('1.1').compareTo(new VersionNumber('2'))
        assertEquals 1, new VersionNumber('1.10').compareTo(new VersionNumber('1.9'))
        assertEquals 0, new VersionNumber('1.10').compareTo(new VersionNumber('1.10'))

        assertEquals 0, new VersionNumber('R1').compareTo(new VersionNumber('R1'))
        assertEquals 1, new VersionNumber('R2').compareTo(new VersionNumber('R1'))
        assertEquals(-1, new VersionNumber('R1').compareTo(new VersionNumber('R2')))
        assertEquals(1, new VersionNumber('R1').compareTo(new VersionNumber('1')))
        assertEquals(1, new VersionNumber('R1').compareTo(new VersionNumber('2')))

    }

    void testChild() {
        assertTrue new VersionNumber('2.1').isDirectChildVersionOf(new VersionNumber('2'))
        assertTrue new VersionNumber('2.1.1').isDirectChildVersionOf(new VersionNumber('2.1'))
        assertFalse new VersionNumber('2.1.1').isDirectChildVersionOf(new VersionNumber('2'))
        assertFalse new VersionNumber('3.1').isDirectChildVersionOf(new VersionNumber('2'))

    }

    void testIncrementVersion() {
        ParameterizationDAO dao = new ParameterizationDAO(itemVersion: '1')
        GrailsMock daoMock = mockFor(ParameterizationDAO)
        daoMock.demand.static.findAllByNameAndModelClassName { name, className ->
            [dao]
        }

        Parameterization parameterization = new Parameterization('')
        parameterization.versionNumber = new VersionNumber('1')
        parameterization.modelClass = EmptyModel
        assertEquals '2', VersionNumber.incrementVersion(parameterization).toString()

        dao = new ParameterizationDAO(itemVersion: 'R1')
        daoMock = mockFor(ParameterizationDAO)
        daoMock.demand.static.findAllByNameAndModelClassName { name, className ->
            [dao]
        }

        parameterization = new Parameterization('')
        parameterization.versionNumber = new VersionNumber('R1')
        parameterization.modelClass = EmptyModel
        assertEquals 'R2', VersionNumber.incrementVersion(parameterization).toString()

        dao = new ParameterizationDAO(itemVersion: '1')
        ParameterizationDAO dao2 = new ParameterizationDAO(itemVersion: '2')
        daoMock = mockFor(ParameterizationDAO)
        daoMock.demand.static.findAllByNameAndModelClassName { name, className ->
            [dao, dao2]
        }

        parameterization = new Parameterization('')
        parameterization.versionNumber = new VersionNumber('1')
        parameterization.modelClass = EmptyModel
        assertEquals '1.1', VersionNumber.incrementVersion(parameterization).toString()

        dao = new ParameterizationDAO(itemVersion: '1')
        dao2 = new ParameterizationDAO(itemVersion: '2')
        ParameterizationDAO dao3 = new ParameterizationDAO(itemVersion: '1.1')
        daoMock = mockFor(ParameterizationDAO)
        daoMock.demand.static.findAllByNameAndModelClassName { name, className ->
            [dao, dao2, dao3]
        }

        parameterization = new Parameterization('')
        parameterization.versionNumber = new VersionNumber('1.1')
        parameterization.modelClass = EmptyModel
        assertEquals '1.2', VersionNumber.incrementVersion(parameterization).toString()

        dao = new ParameterizationDAO(itemVersion: '1')
        dao2 = new ParameterizationDAO(itemVersion: '2')
        dao3 = new ParameterizationDAO(itemVersion: '1.1')
        daoMock = mockFor(ParameterizationDAO)
        daoMock.demand.static.findAllByNameAndModelClassName { name, className ->
            [dao, dao2, dao3]
        }

        parameterization = new Parameterization('')
        parameterization.versionNumber = new VersionNumber('1')
        parameterization.modelClass = EmptyModel
        assertEquals '1.2', VersionNumber.incrementVersion(parameterization).toString()
    }

}
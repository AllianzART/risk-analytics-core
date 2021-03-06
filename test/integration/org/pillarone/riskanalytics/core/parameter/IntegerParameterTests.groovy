package org.pillarone.riskanalytics.core.parameter

import org.junit.Test

import static org.junit.Assert.*

class IntegerParameterTests {

    @Test
    void testObjectCreation() {
        IntegerParameter parameter = new IntegerParameter(path: "path", integerValue: 1)
        assertEquals 1, parameter.integerValue
        parameter = new IntegerParameter(path: "path", integerValue: new Integer(2))
        int instance = parameter.integerValue
        assertEquals new Integer(2), instance
        parameter = new IntegerParameter(path: "path", integerValue: 3)
        assertEquals 3, parameter.integerValue
    }

    @Test
    void testInsert() {
        IntegerParameter parameter = new IntegerParameter(path: "path", integerValue: 2)

        IntegerParameter savedParam = parameter.save()
        assertNotNull(savedParam)
        assertNotNull(savedParam.id)

    }

}

package org.pillarone.riskanalytics.core.components

/**
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 */
class ComponentUtilsTests extends GroovyTestCase {

    void testGetNormalizedPath() {
        assertEquals "universe >  milky way >  solar system >  earth >  europe >  switzerland",
            ComponentUtils.getNormalizedPath("Universe:subMilkyWay:subSolarSystem:subEarth:subEurope:parmSwitzerland", " > ")
        assertEquals "heinecken ->  brewery ->  ale", ComponentUtils.getNormalizedPath("Heinecken:subBrewery:outAle", " -> ")
        assertEquals " > heinecken >  brewery >  ale", ComponentUtils.getNormalizedPath(":heinecken:subBrewery:outAle", " > ")
    }

    void testGetNormalizedName() {
        assertEquals 'claims generator', ComponentUtils.getNormalizedName('subClaimsGenerator')
    }

    void testRemoveNamingConventions() {
        assertEquals 'ClaimsGenerator', ComponentUtils.removeNamingConventions('subClaimsGenerator')
        assertEquals 'Portion', ComponentUtils.removeNamingConventions('parmPortion')
        assertEquals 'Ultimate', ComponentUtils.removeNamingConventions('outUltimate')
        assertEquals 'Ultimate', ComponentUtils.removeNamingConventions('globalUltimate')
        assertEquals 'Ultimate', ComponentUtils.removeNamingConventions('runtimeUltimate')
        assertEquals 'Ultimate', ComponentUtils.removeNamingConventions('Ultimate')
        assertNull ComponentUtils.removeNamingConventions(null)
    }

    void testGetComponentNormalizedName() {
        assertEquals "subEurope", ComponentUtils.getComponentNormalizedName("Universe:subMilkyWay:subSolarSystem:subEarth:subEurope")
        assertEquals "subSolarSystem", ComponentUtils.getComponentNormalizedName("Universe:subMilkyWay:subSolarSystem")
    }

    void testInsertBlanks() {
        assertEquals "milky way", ComponentUtils.insertBlanks("milkyWay")
        assertEquals "milky way", ComponentUtils.insertBlanks("MilkyWay")
        assertEquals "m i l k y  w a y", ComponentUtils.insertBlanks("MILKY WAY")
    }
}

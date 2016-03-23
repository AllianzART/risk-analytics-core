package org.pillarone.riskanalytics.core.search
import com.google.common.collect.ImmutableList
import org.apache.commons.lang.StringUtils
import org.pillarone.riskanalytics.core.modellingitem.ParameterizationCacheItem
import org.pillarone.riskanalytics.core.modellingitem.ResourceCacheItem
import org.pillarone.riskanalytics.core.modellingitem.ResultConfigurationCacheItem
import org.pillarone.riskanalytics.core.modellingitem.SimulationCacheItem
import org.pillarone.riskanalytics.core.parameter.comment.Tag
import org.pillarone.riskanalytics.core.simulation.item.VersionNumber
import org.pillarone.riskanalytics.core.user.Person
import org.pillarone.riskanalytics.core.workflow.Status

class AllFieldsFilterTests extends GroovyTestCase {

    AllFieldsFilter filter = new AllFieldsFilter();

    // Neat approach to more succinct test code from http://stackoverflow.com/a/18002336
    // Just call with map of specific fields you need to override from defaults
    //
    def createP14nCacheItem(Map map = [:]){
        def m = [
                id:1,
                versionNumber:null,
                name:'PARAM_NAME',
                modelClass:null,
                creationDate:null,
                modificationDate:null,
                creator:null,
                lastUpdater:null,
                tags:null,
                valid:false,
                status:null,
                dealId:1
        ]
        m << map // supplied values overwrite defaults
        new ParameterizationCacheItem(
                m.id,
                m.versionNumber,
                m.name,
                m.modelClass,
                m.creationDate,
                m.modificationDate,
                m.creator,
                m.lastUpdater,
                m.tags,
                m.valid,
                m.status,
                m.dealId
        )
    }

    def createSimCacheItem(Map map = [:]){
        def m = [
                id:1,
                name:'SIM_NAME',
                parameterization:null,
                resultConfiguration:null,
                tags:null,
                modelClass:null,
                versionNumber:null,
                end:null,
                start:null,
                creationDate:null,
                modificationDate:null,
                creator:null,
                lastUpdater:null,
                numberOfIterations:1,
                batch:null,
                randomSeed:12345
        ]
        m << map // supplied values overwrite defaults
        new SimulationCacheItem(
                m.id,
                m.name,
                m.parameterization,
                m.resultConfiguration,
                m.tags,
                m.modelClass,
                m.versionNumber,
                m.end,
                m.start,
                m.creationDate,
                m.modificationDate,
                m.creator,
                m.numberOfIterations,
                m.batch,
                m.randomSeed
        )
    }

    def createResourceCacheItem(Map map = [:]){
        def m = [
                id:1,
                name:'RES_NAME',
                modelClass:null,
                versionNumber:null,
                creationDate:null,
                modificationDate:null,
                creator:null,
                lastUpdater:null,
                tags:null,
                valid:false,
                status:null
        ]
        m << map // supplied values overwrite defaults
        new ResourceCacheItem(
                m.id,
                m.name,
                m.modelClass,
                m.versionNumber,
                m.creationDate,
                m.modificationDate,
                m.creator,
                m.lastUpdater,
                m.tags,
                m.valid,
                m.status
        )
    }

    def createResConfigCacheItem(Map map = [:]){
        def m = [
                id:1,
                name:'RES_NAME',
                modelClass:null,
                versionNumber:null,
                creationDate:null,
                modificationDate:null,
                creator:null,
                lastUpdater:null,
        ]
        m << map // supplied values overwrite defaults
        new ResultConfigurationCacheItem(
                m.id,
                m.name,
                m.modelClass,
                m.versionNumber,
                m.creationDate,
                m.modificationDate,
                m.creator,
                m.lastUpdater,
        )
    }

    // Minor helper avoids creating zillions of filters in tests
    // Dumb Java uses void return on setters preventing simple chaining of function calls
    //
    AllFieldsFilter query(AllFieldsFilter f, String q){
        f.setQuery(q)
        f        
    }

    // Every item type has an id, so this should apply to wide range
    //
    void testSearchByDbId() {
        ParameterizationCacheItem    p14n = createP14nCacheItem([ id:123, name:'itemName', ])
        SimulationCacheItem          sim  = createSimCacheItem ([ id:123, name:'itemName',  ])
        ResourceCacheItem            resource = createResourceCacheItem([ id:123, name:'itemName',  ])
        ResultConfigurationCacheItem config = createResConfigCacheItem ([ id:123, name:'itemName',  ])

        [p14n,sim,resource,config].each{
            assert  query(filter, 'dbid:12').accept(it)                   // match partial
            assert !query(filter, 'dbid=12').accept(it)                   // wrong exact id
            assert  query(filter, 'dbid:13,12,44').accept(it)             // match partial, csv list
            assert  query(filter, 'dbid=123,122,144').accept(it)          // match partial, csv list
            assert  query(filter, 'dbid=122,144,123,').accept(it)         // match partial, csv list
            assert !query(filter, 'dbid=3123,122,144').accept(it)         // missing exact match, csv list
        }
    }


    void testSearchByOwner() {
        ParameterizationCacheItem    p14n = createP14nCacheItem([ name:'itemName', creator:new Person(username: 'creatorName') ])
        SimulationCacheItem          sim  = createSimCacheItem ([ name:'itemName', creator:new Person(username: 'creatorName') ])
        ResourceCacheItem            resource = createResourceCacheItem([ name:'itemName', creator:new Person(username: 'creatorName') ])
        ResultConfigurationCacheItem config = createResConfigCacheItem ([ name:'itemName', creator:new Person(username: 'creatorName') ])

        [p14n,sim,resource,config].each{
            assert query(filter, 'cre').accept(it)                       // match partial owner
            assert query(filter, 'owner:cre').accept(it)                 // match partial owner
            assert query(filter, 'creatorName').accept(it)               // match owner
            assert query(filter, 'o:creatorName').accept(it)             // match owner

            // Must not find 'user' in name of item, nor 'testName' in username
            assert !query(filter, 'name:creatorName').accept(it)         // wrong item name
            assert !query(filter, 'owner:itemName').accept(it)           // wrong owner name
        }
    }

    // nb No tags on Result configs. Also see http://jira/i#browse/AR-184
    //
    void testSearchByTag() {
        ParameterizationCacheItem    p14n = createP14nCacheItem([
                name:'itemName', versionNumber:new VersionNumber('2'),
                tags:ImmutableList.copyOf([new Tag(name: 'firstTag'), new Tag(name: 'secondTag')])
        ])
        SimulationCacheItem          sim  = createSimCacheItem ([
                name:'itemName', versionNumber:new VersionNumber('2'),
                tags:ImmutableList.copyOf([new Tag(name: 'firstTag'), new Tag(name: 'secondTag')])
        ])
        ResourceCacheItem            resource = createResourceCacheItem([
                name:'itemName', versionNumber:new VersionNumber('2'),
                tags:ImmutableList.copyOf([new Tag(name: 'firstTag'), new Tag(name: 'secondTag')])
        ])

        [p14n,sim,resource].each{
            assert  query(filter, 'first').accept(it)                   //match first tag
            assert  query(filter, 'tag:first').accept(it)               //ditto
            assert  query(filter, 'tag:nemo OR name:emna').accept(it)   //match partial itemname
            assert !query(filter, 'tag:item AND name:seco').accept(it)  //fail match on tag / item
            assert  query(filter, 'item AND seco').accept(it)           //match partial item name

            //SOMEHOW BROKEN
//            assert  query(filterUnderTest, 'itemName v2').accept(it)            //match full nameAndVersion
        }
    }

    // Added exact match tests for name/tag
    //
    void testSearchParameterizations() {
        ParameterizationCacheItem p14n = createP14nCacheItem([name:'testName',versionNumber:new VersionNumber('3')])

        verifyOnlyNameSensitiveFiltersMatch('test', p14n)
        assert ! query(filter, 'not found').accept(p14n)
        assert   query(filter, 'n=testName').accept(p14n)          //correct full name
        assert ! query(filter, 'n=testName v3').accept(p14n)       //incorrect full name
        assert   query(filter, 'n:testName v3').accept(p14n)       //nameAndVersion contains..
        assert   query(filter, 'n:testName v').accept(p14n)        //nameAndVersion contains..

        ParameterizationCacheItem statusP14n = createP14nCacheItem([
                name:'reviewing',status: Status.IN_REVIEW
        ])
        assert  query(filter, 'review').accept(statusP14n)              //match either status or name
        assert !query(filter, 's:viewing').accept(statusP14n)           //wrong status
        assert  query(filter, 's:view').accept(statusP14n)              //match partial status
        assert  query(filter, 'viewing').accept(statusP14n)             //match partial name
        assert !query(filter, 'n=viewing').accept(statusP14n)           //incorrect full name
    }

    void testSearchSimulations() {
        SimulationCacheItem simulation = createSimCacheItem([
                name:'testName',  parameterization: createP14nCacheItem([:]), randomSeed:12345
        ])
        verifyOnlyNameSensitiveFiltersMatch('test', simulation)
        assert !query(filter, 'not found').accept(simulation)
        assert !query(filter, 'name:not found').accept(simulation)

        // Random seed tests
        //
        assert  query(filter, 'seed:345').accept(simulation)        //partial match seed
        assert !query(filter, '!seed:345').accept(simulation)       //fail to reject good snippet of seed
        assert !query(filter, 'seed:543').accept(simulation)        //partial match fails on bad seed snippet
        assert  query(filter, '!seed:543').accept(simulation)       //successfully reject foreign substring on seed
        assert !query(filter, 'seed=345').accept(simulation)        //exact match fails on wrong seed
        assert  query(filter, '!seed=345').accept(simulation)       //successfully reject wrong seed
        assert !query(filter, '!seed=12345').accept(simulation)     //fail to reject good seed
        assert  query(filter, 'seed=12345').accept(simulation)      //successfully match correct seed

        // Sims should match on their P14n name but not on their p14n's tags
        //
        ParameterizationCacheItem parameterization = createP14nCacheItem([
                name:'PARAM_NAME',
                tags:ImmutableList.copyOf([new Tag(name: 'paramTagName')]),
        ])
        simulation = createSimCacheItem([
                parameterization:parameterization,
                tags:ImmutableList.copyOf([new Tag(name: 'simTagName'), new Tag(name: 'simTagTwo')]),
        ])
        assert  query(filter, 'PARAM_NAME').accept(simulation)     //should match on pn
        assert  query(filter, 'name:param_').accept(simulation)    //case insensitive too
        assert  query(filter, 't:simtag').accept(simulation)       //match on sim's tags
        assert !query(filter, 't:paramTagName').accept(simulation) //no match on pn's tags

        // Sims should match on their result config's name
        // (If configs had tags, sims shouldn't match on them - test may be useful one day)
        //
        ResultConfigurationCacheItem resultConfiguration = createResConfigCacheItem([
                name:'TEMPLATE_NAME',
                tags:ImmutableList.copyOf([new Tag(name: 'templateTagName')]), // Not used yet!!
        ])
        simulation = createSimCacheItem([
                parameterization:parameterization, resultConfiguration:resultConfiguration,
                tags:ImmutableList.copyOf([new Tag(name: 'testName'), new Tag(name: 'secondTag')]),
        ])
        assert  query(filter, 'TEMPLATE_NAME').accept(simulation)      //should match tmpl name
        assert  query(filter, 'name:template').accept(simulation)      //should match on tmpl
        assert !query(filter, 't:templateTagName').accept(simulation)  //no match on "tpl's tags"

        // Filter on Deal id
        //
        parameterization = createP14nCacheItem( [name: 'PARAM_NAME', dealId: 12345] )
        simulation = createSimCacheItem([
                name:'some other name',
                parameterization:parameterization,
        ])
        assert  query(filter, 'd=12345').accept(simulation)        //match only pn's exact deal id
        assert  query(filter, '12345').accept(simulation)          //match including pn's deal id
        assert  query(filter, 'dealid:2345').accept(simulation)    //match partial only deal id
        assert  query(filter, '2345').accept(simulation)           //match including partial deal id
        assert !query(filter, 'd=99999').accept(simulation)        //wrong pn's exact deal id
        assert !query(filter, '99999').accept(simulation)          //wrong including pn's deal id
        assert !query(filter, 'dealid:999 ').accept(simulation)    //wrong partial only deal id
        assert !query(filter, ' 999').accept(simulation)           //wrong including partial deal id

        // Filter on iterations
        //
        parameterization = createP14nCacheItem([name:'PARAM_NAME'])
        simulation = createSimCacheItem([
                name:'some other name', numberOfIterations:5000, randomSeed:54321,
                parameterization:parameterization, resultConfiguration:resultConfiguration,
                tags:ImmutableList.copyOf([new Tag(name: 'paramTestName')]),
        ])
        assert  query(filter, 'its=5000').accept(simulation)       //should match exact iterations
        assert  query(filter, 'iterations=5000').accept(simulation)//should match exact iterations
        assert !query(filter, 'its=5').accept(simulation)          //but not match wrong iterations
        assert  query(filter, 'its:5').accept(simulation)          //should match partially
        assert  query(filter, 'iterations:5').accept(simulation)   //ditto
        assert  query(filter, '!its=4321').accept(simulation)      //should reject wrong value
        assert !query(filter, '!its:50').accept(simulation)        //should fail to reject contained substring
        assert !query(filter, 'its:1234').accept(simulation)       //should fail to match foreign substring
    }

    void testSearchResources() {
        ResourceCacheItem resource = createResourceCacheItem([name:'testName'])
        verifyOnlyNameSensitiveFiltersMatch('TEST', resource)
        assert !query(filter, 'not found').accept(resource)
        assert !query(filter, 'tag:not found').accept(resource)
        assert !query(filter, 'state:not found').accept(resource)
        assert !query(filter, 'dealid:not found').accept(resource)
        assert !query(filter, 'owner:not found').accept(resource)

        resource = createResourceCacheItem([
                name:'some other name',
                tags:ImmutableList.copyOf([new Tag(name: 'testName'), new Tag(name: 'secondTag')])
        ])
        assert query(filter, 'testName').accept(resource)
        assert query(filter, 'tag:testName').accept(resource)
        assert !query(filter, 'name:testName').accept(resource)
        assert query(filter, 'name:testName OR TAG:TESTNAME').accept(resource)
        assert query(filter, 'name:other AND tag:second').accept(resource)
    }

    void testSearchWithMultipleValues() {
        ResourceCacheItem resource1 = createResourceCacheItem([name:'firstName', versionNumber:new VersionNumber('1')])
        ResourceCacheItem resource2 = createResourceCacheItem([name:'secondName', versionNumber:new VersionNumber('2')])
        ResourceCacheItem resource3 = createResourceCacheItem([name:'firstName', versionNumber:new VersionNumber('3')])
        assert query(filter, 'firstName v1 OR secondName OR thirdName').accept(resource1)
        assert filter.accept(resource2)
        assert !filter.accept(resource3)

        //
        // Note: you can search for the version as part of a 'name contains' search
        // 2016-03-22 Turns out the CacheItem is a hierarchy roughly parallel to Modelling items and only
        // the ResourceCacheItem class had a VersionNumber in it. Am changing so also ResultConfig- and
        // P14n- respective items also ahve it.
        //
        assert query(filter, 'name:firstName v1').accept(resource1)
        assert query(filter, 'name = firstName ').accept(resource1)
        assert query(filter, 'name != unknownName ').accept(resource1)
        assert query(filter, 'name:first AND name:v1').accept(resource1)
        assert !query(filter, 'name:first AND name:v2').accept(resource1)
        assert query(filter, 'name:first OR name:v2').accept(resource1)

    }


    // Supply sim, pn or resource with given name-fragment (and no other fields matching the fragment)
    // Checks that only a generic filter or a name-specific one will match fragment via the name
    //
    private void verifyOnlyNameSensitiveFiltersMatch(String nameFragment, def modellingItem) {

        //Checks are pointless unless item name contains supplied name fragment
        //
        assert StringUtils.containsIgnoreCase(modellingItem.name, nameFragment)

        //Generic filter should match by name
        //
        assert  (query(filter, nameFragment)).accept(modellingItem)

        //Name-specific filter should match by name
        //
        assert  (query(filter, 'name:' + nameFragment)).accept(modellingItem)
        assert  (query(filter, 'n:' + nameFragment)).accept(modellingItem)
        assert !(query(filter, '!n:' + nameFragment)).accept(modellingItem)

        //Other-column-specific filters should not match by name
        //
        assert !(query(filter, 'dealid:' + nameFragment)).accept(modellingItem)
        assert !(query(filter, 'd:' + nameFragment)).accept(modellingItem)

        assert !(query(filter, 'owner:' + nameFragment)).accept(modellingItem)
        assert !(query(filter, 'o:' + nameFragment)).accept(modellingItem)
        assert  (query(filter, '!o:' + nameFragment)).accept(modellingItem)   // all items have owner field

        assert !(query(filter, 'state:' + nameFragment)).accept(modellingItem)
        assert !(query(filter, 's:' + nameFragment)).accept(modellingItem)

        assert !(query(filter, 'tag:' + nameFragment)).accept(modellingItem)
        assert !(query(filter, 't:' + nameFragment)).accept(modellingItem)
        assert  (query(filter, '!t:' + nameFragment)).accept(modellingItem)

        assert !(query(filter, 'seed:' + nameFragment)).accept(modellingItem)
        assert !(query(filter, 'seed=' + nameFragment)).accept(modellingItem)

        assert !(query(filter, 'iterations:' + nameFragment)).accept(modellingItem)
        assert !(query(filter, 'iterations=' + nameFragment)).accept(modellingItem)
        assert !(query(filter, 'its:' + nameFragment)).accept(modellingItem)
        assert !(query(filter, 'its=' + nameFragment)).accept(modellingItem)

        assert !(query(filter, 'dbid:' + nameFragment)).accept(modellingItem)
        assert !(query(filter, 'dbid=' + nameFragment)).accept(modellingItem)

    }
}

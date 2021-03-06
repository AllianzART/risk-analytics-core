package org.pillarone.riskanalytics.core.parameterization

import models.core.CoreModel
import org.junit.Test
import org.pillarone.riskanalytics.core.example.parameter.ExampleParameterObjectClassifier
import org.pillarone.riskanalytics.core.parameter.Parameter
import org.pillarone.riskanalytics.core.simulation.item.Parameterization
import org.pillarone.riskanalytics.core.simulation.item.parameter.IntegerParameterHolder
import org.pillarone.riskanalytics.core.simulation.item.parameter.ParameterHolderFactory
import org.pillarone.riskanalytics.core.simulation.item.parameter.ParameterObjectParameterHolder
import org.pillarone.riskanalytics.core.simulation.item.parameter.StringParameterHolder
import models.resource.ResourceModel
import org.pillarone.riskanalytics.core.simulation.item.parameter.ResourceParameterHolder
import org.pillarone.riskanalytics.core.example.component.ExampleResource
import org.pillarone.riskanalytics.core.simulation.item.Resource
import org.pillarone.riskanalytics.core.simulation.item.parameter.ParameterHolder
import org.pillarone.riskanalytics.core.components.ResourceHolder
import org.pillarone.riskanalytics.core.simulation.item.VersionNumber
import org.pillarone.riskanalytics.core.example.parameter.ExampleParameterObject
import org.pillarone.riskanalytics.core.example.parameter.ExampleResourceConstraints

import static org.junit.Assert.*

class ParameterizationHelperTests {

    @Test
    void testDefaultName() {
        CoreModel model = new CoreModel()
        Parameterization parameterization = ParameterizationHelper.createDefaultParameterization(model)
        assertEquals "Core-Default", parameterization.name
    }

    @Test
    void testModelClassName() {
        CoreModel model = new CoreModel()
        Parameterization parameterization = ParameterizationHelper.createDefaultParameterization(model)
        assertSame CoreModel, parameterization.modelClass
    }

    @Test
    void testGetAllParameter() {
        CoreModel model = new CoreModel()
        model.init()
        Map parameter = ParameterizationHelper.getAllParameter(model)

        assertEquals 4, parameter.size()

        assertSame model.exampleInputOutputComponent.parmParameterObject, parameter["exampleInputOutputComponent:parmParameterObject"]
    }

    @Test
    void testCreateDefaultParameterization() {
        int initialParameterCount = Parameter.count()
        CoreModel model = new CoreModel()
        Parameterization parameterization = ParameterizationHelper.createDefaultParameterization(model)
        assertEquals 4, parameterization.parameters.size()

        parameterization.save()
        assertEquals initialParameterCount + 10, Parameter.count()
    }

    @Test
    void testCreateDefaultResourceParameterization() {
        int initialParameterCount = Parameter.count()
        ResourceModel model = new ResourceModel()
        Parameterization parameterization = ParameterizationHelper.createDefaultParameterization(model)
        assertEquals 5, parameterization.parameters.size()
        ResourceParameterHolder resource = parameterization.parameterHolders.find { it instanceof ResourceParameterHolder} as ResourceParameterHolder
        assertNotNull(resource)
        assertEquals(ExampleResource, resource.resourceClass)
        parameterization.save()
        assertEquals initialParameterCount + 7, Parameter.count()

        def stringWriter = new StringWriter()
        BufferedWriter writer = new BufferedWriter(stringWriter)
        new ParameterWriter().write(parameterization.toConfigObject(), writer)
        assertTrue(stringWriter.toString().contains("parmResource[0]=new org.pillarone.riskanalytics.core.components.ResourceHolder(org.pillarone.riskanalytics.core.example.component.ExampleResource,'exampleResource', new org.pillarone.riskanalytics.core.simulation.item.VersionNumber(1))"))
    }

    @Test
    void testCreateDefaultResource() {
        Resource resource = ParameterizationHelper.createDefaultResource("test", new ExampleResource())
        assertEquals(3, resource.parameterHolders.size())
        assertEquals("test", resource.name)
        assertEquals(ExampleResource, resource.modelClass)
    }

    @Test
    void testCreateDefaultParameterizationForMultiplePeriods() {
        int initialParameterCount = Parameter.count()
        CoreModel model = new CoreModel()
        Parameterization parameterization = ParameterizationHelper.createDefaultParameterization(model, 3)
        assertEquals 12, parameterization.parameters.size()

        parameterization.save()
        assertEquals initialParameterCount + 30, Parameter.count()
    }

    @Test
    void testCreateParameterizationFromConfigObject() {
        ConfigObject configObject = new ConfigObject()
        configObject.model = CoreModel
        configObject.periodCount = 2
        configObject.displayName = 'Name'
        configObject.tags = ['tag1', 'tag2']
        configObject.comments = ["[path:'path',period:0, lastChange:new org.joda.time.DateTime(${new Date().getTime()}), user:null, comment:'test']"]
        configObject.components.exampleInputOutputComponent.parmParameterObject[0] = ExampleParameterObjectClassifier.TYPE0.getParameterObject(["a": 0, "b": 1])
        configObject.components.exampleInputOutputComponent.parmParameterObject[1] = ExampleParameterObjectClassifier.TYPE1.getParameterObject(["p1": 0, "p2": 1])

        Parameterization param = ParameterizationHelper.createParameterizationFromConfigObject(configObject, 'anotherName')
        param.save()
        assertEquals 'Name', param.name

        def parameterObject = param.getParameters('exampleInputOutputComponent:parmParameterObject')
        assertEquals 2, parameterObject.size()

        def comments = param.comments
        assertEquals 1, param.comments.size()
        assertEquals 'test', comments.get(0).getText()
        assertEquals 'path', comments.get(0).getPath()

        def tags = param.tags
        assertEquals 2, tags.size()

        def pop = parameterObject[0] as ParameterObjectParameterHolder
        assertEquals 'TYPE0', pop.classifier.toString()

        pop = parameterObject[1] as ParameterObjectParameterHolder
        assertEquals 'TYPE1', pop.classifier.toString()
    }

    @Test
    void testCopyParameters() {
        List params = []
        IntegerParameterHolder p1 = ParameterHolderFactory.getHolder('intPath', 2, 5)
        params << p1
        StringParameterHolder p2 = ParameterHolderFactory.getHolder('strPath', 1, "test")
        params << p2

        def newParams = ParameterizationHelper.copyParameters(params)

        assertEquals params.size(), newParams.size()
        def newP1 = newParams.find { it.path == 'intPath' }
        assertNotNull newP1
        assertEquals p1.periodIndex, newP1.periodIndex
        assertEquals p1.businessObject, newP1.businessObject

        def newP2 = newParams.find { it.path == 'strPath' }
        assertNotNull newP2
        assertEquals p2.periodIndex, newP2.periodIndex
        assertEquals p2.businessObject, newP2.businessObject
    }

    @Test
    void testCollectResources() {
        List<ParameterHolder> parameters = [ParameterHolderFactory.getHolder("res", 0, new ResourceHolder<ExampleResource>(ExampleResource, "example", new VersionNumber("1"))), ParameterHolderFactory.getHolder("double", 0, 1d)]
        List<Resource> resources = ParameterizationHelper.collectUsedResources(parameters)

        assertEquals(1, resources.size())
        Resource resource = resources[0]
        assertEquals("example", resource.name)
        assertEquals("1", resource.versionNumber.toString())
        assertEquals(ExampleResource, resource.modelClass)
    }

    @Test
    void testCollectResourcesPO() {
        ExampleParameterObject parameterObject = ExampleParameterObjectClassifier.getStrategy(ExampleParameterObjectClassifier.RESOURCE, [resource:
                new ConstrainedMultiDimensionalParameter([[new ResourceHolder<ExampleResource>(ExampleResource, "example", new VersionNumber("1"))]], ["title"], new ExampleResourceConstraints())])

        List<ParameterHolder> parameters = [ParameterHolderFactory.getHolder("a", 0, parameterObject)]
        List<Resource> resources = ParameterizationHelper.collectUsedResources(parameters)

        assertEquals(1, resources.size())
        Resource resource = resources[0]
        assertEquals("example", resource.name)
        assertEquals("1", resource.versionNumber.toString())
        assertEquals(ExampleResource, resource.modelClass)
    }

    @Test
    void testCollectResourcesMDP() {


        List<ParameterHolder> parameters = [ParameterHolderFactory.getHolder(
                "a", 0, new ConstrainedMultiDimensionalParameter(
                        [
                                [new ResourceHolder<ExampleResource>(ExampleResource, "example", new VersionNumber("1"))],
                                [new ResourceHolder<ExampleResource>(ExampleResource, "example2", new VersionNumber("2"))]
                        ], ["title", "title2"], new ExampleResourceConstraints())
        )]
        List<Resource> resources = ParameterizationHelper.collectUsedResources(parameters).sort { it.name }

        assertEquals(2, resources.size())
        Resource resource = resources[0]
        assertEquals("example", resource.name)
        assertEquals("1", resource.versionNumber.toString())
        assertEquals(ExampleResource, resource.modelClass)
        resource = resources[1]
        assertEquals("example2", resource.name)
        assertEquals("2", resource.versionNumber.toString())
        assertEquals(ExampleResource, resource.modelClass)
    }

}
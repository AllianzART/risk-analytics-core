package models.resource

import org.junit.Before
import org.pillarone.riskanalytics.core.components.ResourceRegistry
import org.pillarone.riskanalytics.core.simulation.engine.ModelTest
import org.pillarone.riskanalytics.core.simulation.item.Resource
import org.pillarone.riskanalytics.core.example.component.ExampleResource
import org.pillarone.riskanalytics.core.simulation.item.parameter.ParameterHolderFactory
import org.pillarone.riskanalytics.core.components.ResourceHolder

import static org.junit.Assert.*


class ResourceModelTests extends ModelTest {

    @Before
    void setUp() {
        ResourceRegistry.clear()
        Resource resource = new Resource("myResource", ExampleResource)
        resource.addParameter(ParameterHolderFactory.getHolder("parmInteger", 0, 99))
        resource.addParameter(ParameterHolderFactory.getHolder("parmString", 0, "String"))
        resource.save()
        super.setUp()
    }

    @Override
    Class getModelClass() {
        ResourceModel
    }

    @Override
    void postSimulationEvaluation() {
        ResourceModel model = runner.currentScope.model
        ResourceHolder<ExampleResource> resource = model.resourceComponent.parmResource
        assertEquals(99, resource.resource.parmInteger)
        assertEquals("String", resource.resource.parmString)
        assertEquals("myResource", resource.name)
        assertNotNull(resource.resource.simulationScope)

    }


}

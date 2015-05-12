package org.pillarone.riskanalytics.core.simulation.engine.grid


class SimulationTaskTest extends GroovyTestCase {
    void testGenerateBlocks_it1000_nodes1() {
        SimulationTask task = new SimulationTask()

        Collection<SimulationBlock> blocks = task.generateBlocks(1000, 1)

        assertEquals("blocks size" , 1, blocks.size())
        assertEquals("iterationOffset_0" , 0, blocks.get(0).iterationOffset)
        assertEquals("blocksize_0" , 1000, blocks.get(0).blockSize)
        assertEquals("streamOffset_0" , 0, blocks.get(0).streamOffset)
    }

    void testGenerateBlocks_it2000_nodes1() {
        SimulationTask task = new SimulationTask()

        Collection<SimulationBlock> blocks = task.generateBlocks(2000, 1)

        assertEquals("blocks size" , 1, blocks.size())
        assertEquals("iterationOffset_0" , 0, blocks.get(0).iterationOffset)
        assertEquals("blocksize_0" , 2000, blocks.get(0).blockSize)
        assertEquals("streamOffset_0" , 0, blocks.get(0).streamOffset)
    }

    void testGenerateBlocks_it2001_nodes2() {
        SimulationTask task = new SimulationTask()

        Collection<SimulationBlock> blocks = task.generateBlocks(2001, 2)

        assertEquals("blocks size" , 2, blocks.size())
        assertEquals("iterationOffset_0" , 0, blocks.get(0).iterationOffset)
        assertEquals("blocksize_0" , 1001, blocks.get(0).blockSize)
        assertEquals("streamOffset_0" , 0, blocks.get(0).streamOffset)

        assertEquals("iterationOffset_1" , 1001, blocks.get(1).iterationOffset)
        assertEquals("blocksize_1" , 1000, blocks.get(1).blockSize)
        assertEquals("streamOffset_1" , 1, blocks.get(1).streamOffset)
    }

    void testNextStreamOffset() {
        SimulationTask task = new SimulationTask()

        assertEquals("202" , 210, task.nextStreamOffset(202))
    }
}

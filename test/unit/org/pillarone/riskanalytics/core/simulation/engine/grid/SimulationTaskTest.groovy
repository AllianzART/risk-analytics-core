package org.pillarone.riskanalytics.core.simulation.engine.grid


class SimulationTaskTest extends GroovyTestCase {
    void testGenerateBlocks_it1000_BlockSize1000() {
        //Note this test relies on SimulationTask.SIMULATION_BLOCK_SIZE = 1000
        assertEquals("test precondition changed", 1000, SimulationTask.SIMULATION_BLOCK_SIZE)
        SimulationTask task = new SimulationTask()

        Collection<SimulationBlock> blocks = task.generateBlocks(1000)

        assertEquals("blocks size" , 1, blocks.size())
        assertEquals("iterationOffset_0" , 0, blocks.get(0).iterationOffset)
        assertEquals("blocksize_0" , 1000, blocks.get(0).blockSize)
        assertEquals("streamOffset_0" , 0, blocks.get(0).streamOffset)
    }

    void testGenerateBlocks_it2000_BlockSize1000() {
        //Note this test relies on SimulationTask.SIMULATION_BLOCK_SIZE = 1000
        assertEquals("test precondition changed", 1000, SimulationTask.SIMULATION_BLOCK_SIZE)
        SimulationTask task = new SimulationTask()

        Collection<SimulationBlock> blocks = task.generateBlocks(2000)

        assertEquals("blocks size" , 2, blocks.size())
        assertEquals("iterationOffset_0" , 0, blocks.get(0).iterationOffset)
        assertEquals("blocksize_0" , 1000, blocks.get(0).blockSize)
        assertEquals("streamOffset_0" , 0, blocks.get(0).streamOffset)

        assertEquals("iterationOffset_1" , 1000, blocks.get(1).iterationOffset)
        assertEquals("blocksize_1" , 1000, blocks.get(1).blockSize)
        assertEquals("streamOffset_1" , 1, blocks.get(1).streamOffset)
    }

    void testGenerateBlocks_it2001_BlockSize1000() {
        //Note this test relies on SimulationTask.SIMULATION_BLOCK_SIZE = 1000
        assertEquals("test precondition changed", 1000, SimulationTask.SIMULATION_BLOCK_SIZE)
        SimulationTask task = new SimulationTask()

        Collection<SimulationBlock> blocks = task.generateBlocks(2001)

        assertEquals("blocks size" , 3, blocks.size())
        assertEquals("iterationOffset_0" , 0, blocks.get(0).iterationOffset)
        assertEquals("blocksize_0" , 1000, blocks.get(0).blockSize)
        assertEquals("streamOffset_0" , 0, blocks.get(0).streamOffset)

        assertEquals("iterationOffset_1" , 1000, blocks.get(1).iterationOffset)
        assertEquals("blocksize_1" , 1000, blocks.get(1).blockSize)
        assertEquals("streamOffset_1" , 1, blocks.get(1).streamOffset)

        assertEquals("iterationOffset_1" , 2000, blocks.get(2).iterationOffset)
        assertEquals("blocksize_1" , 1, blocks.get(2).blockSize)
        assertEquals("streamOffset_1" , 2, blocks.get(2).streamOffset)
    }

    void testGenerateBlocks_it5_BlockSize1000() {
        //Note this test relies on SimulationTask.SIMULATION_BLOCK_SIZE = 1000
        assertEquals("test precondition changed", 1000, SimulationTask.SIMULATION_BLOCK_SIZE)
        SimulationTask task = new SimulationTask()

        Collection<SimulationBlock> blocks = task.generateBlocks(5)

        assertEquals("blocks size" , 1, blocks.size())
        assertEquals("iterationOffset_0" , 0, blocks.get(0).iterationOffset)
        assertEquals("blocksize_0" , 5, blocks.get(0).blockSize)
        assertEquals("streamOffset_0" , 0, blocks.get(0).streamOffset)
    }

    void testNextStreamOffset() {
        SimulationTask task = new SimulationTask()

        assertEquals("202" , 210, task.nextStreamOffset(202))
    }
}

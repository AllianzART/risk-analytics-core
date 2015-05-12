package org.pillarone.riskanalytics.core.simulation.engine.grid.output
import groovy.transform.CompileStatic
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.apache.ignite.Ignite
import org.pillarone.riskanalytics.core.output.ICollectorOutputStrategy
import org.pillarone.riskanalytics.core.output.SingleValueResultPOJO
import org.pillarone.riskanalytics.core.simulation.engine.SimulationRunner
import org.pillarone.riskanalytics.core.simulation.engine.grid.GridHelper
import org.pillarone.riskanalytics.core.util.GroovyUtils

@CompileStatic
class GridOutputStrategy implements ICollectorOutputStrategy, Serializable {

    private static final int PACKET_LIMIT = 100000
    private static Log LOG = LogFactory.getLog(GridOutputStrategy)

    private HashMap<ResultDescriptor, ByteArrayOutputStream> streamCache = new HashMap<ResultDescriptor, ByteArrayOutputStream>();

    private Ignite grid
    private UUID masterNodeId
    private SimulationRunner runner
    private UUID jobIdentifier

    private int resultCount = 0

    int totalMessages = 0

    public GridOutputStrategy(UUID masterNodeId, SimulationRunner runner, UUID jobIdentifier) {
        this.masterNodeId = masterNodeId
        this.runner = runner
        this.jobIdentifier = jobIdentifier
    }

    private Ignite getGrid() {
        if (grid == null) {
            grid = GridHelper.getGrid()
        }
        return grid
    }

    void finish() {
        sendResults()
    }

    ICollectorOutputStrategy leftShift(List<SingleValueResultPOJO> results) {
        LOG.debug("Received ${results.size()} results...")
        HashMap<ResultDescriptor, List<IterationValue>> singleResults = new HashMap<ResultDescriptor, List<IterationValue>>();
        int iteration;
        for (SingleValueResultPOJO result in results) {
            iteration = result.iteration;
            ResultDescriptor descriptor = new ResultDescriptor(GroovyUtils.getId(result.field), result.path.pathName, GroovyUtils.getId(result.collector), result.period)

            List<IterationValue> values = singleResults.get(descriptor);
            if (values == null) {
                values = new ArrayList<IterationValue>();
                singleResults.put(descriptor, values);
            }
            values.add(new IterationValue(result.value, result.date != null ? result.date.millis : 0l));
            resultCount++;
        }

        for (ResultDescriptor descriptor : singleResults.keySet()) {
            List<IterationValue> values = singleResults.get(descriptor);
            ByteArrayOutputStream buffer = streamCache.get(descriptor);
            if (buffer == null) {
                buffer = new ByteArrayOutputStream();
                streamCache.put(descriptor, buffer);
            }
            DataOutputStream dos = new DataOutputStream(buffer);
            dos.writeInt(iteration);
            dos.writeInt(values.size());
            for (IterationValue i : values) {
                dos.writeDouble(i.value);
                dos.writeLong(i.tstamp);
            }

        }

        if (resultCount > PACKET_LIMIT) {
            sendResults()
        }
        return this
    }

    protected synchronized void sendResults() {
        for (Map.Entry<ResultDescriptor, ByteArrayOutputStream> entry : streamCache.entrySet()) {
            ResultDescriptor resultDescriptor = entry.key
            ByteArrayOutputStream stream = entry.value

            Ignite ignite = getGrid()
            ignite.message(ignite.cluster().forNodeId(masterNodeId)).send("dataSendTopic", new ResultTransferObject(resultDescriptor, jobIdentifier, stream.toByteArray(),
                    runner.getProgress()))
            totalMessages++
            stream.reset()
        }
        LOG.debug("Sent results back for ${streamCache.size()} streams. Total count: ${totalMessages}")
        streamCache.clear()
        resultCount = 0
    }
}

class IterationValue {
    public double value;
    public long tstamp;

    public IterationValue(double value, long tstamp) {
        this.value = value;
        this.tstamp = tstamp;
    }
}

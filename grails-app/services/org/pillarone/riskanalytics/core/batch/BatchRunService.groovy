package org.pillarone.riskanalytics.core.batch

import grails.plugin.springsecurity.SpringSecurityService
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.pillarone.riskanalytics.core.output.SimulationRun
import org.pillarone.riskanalytics.core.simulation.engine.SimulationConfiguration
import org.pillarone.riskanalytics.core.simulation.engine.SimulationQueueService
import org.pillarone.riskanalytics.core.simulation.item.*
import org.pillarone.riskanalytics.core.simulation.item.parameter.ParameterHolder
import org.pillarone.riskanalytics.core.simulationprofile.SimulationProfileService
import org.pillarone.riskanalytics.core.user.UserManagement
import org.springframework.util.StringUtils

import java.text.SimpleDateFormat

class BatchRunService {

    private static final Log log = LogFactory.getLog(BatchRunService)

    static private final String defaultBatchSimPrefix = 'batch'
    // Each simulation name will begin with this prefix.. by default just 'batch'
    // But sometimes its useful to use something else eg for Version Migration batches where
    // sim names will appear in reports so it would help to distinguish comparison vs reference sims
    //
    private static String getBatchPrefix(){
        return defaultBatchSimPrefix;
    }

    SimulationQueueService simulationQueueService
    SimulationProfileService simulationProfileService
    SpringSecurityService springSecurityService

    private static
    final String BATCH_SIMNAME_STAMP_FORMAT = System.getProperty("BatchRunService.BATCH_SIMNAME_STAMP_FORMAT", "yyyyMMdd HH:mm:ss z")

    void runBatch(Batch batch, String batchPrefixParam) {
        batch.load()
        if (!batch.executed) {
            log.info("Run batch: $batch")
            offer(createSimulations(batch, batchPrefixParam)) // bottleneck - loads each p14n first
            batch.executed = true
            batch.save()
        }
    }

    private List<Simulation> createSimulations(Batch batch, String batchPrefixParam) {
        Map<Class, SimulationProfile> byModelClass = simulationProfileService.getSimulationProfilesGroupedByModelClass(batch.simulationProfileName)
        batch.parameterizations.collect {
            createSimulation(it, byModelClass[it.modelClass], batch, batchPrefixParam)
        }
    }

    void runBatchRunSimulation(Simulation simulationRun) {
        offer(simulationRun)
    }

    private static boolean shouldRun(Simulation run) {
        run.end == null && run.start == null
    }

    private void offer(List<Simulation> simulationRuns) {
        List<SimulationConfiguration> configurations = simulationRuns.findAll { Simulation simulationRun -> shouldRun(simulationRun) }.collect {
            new SimulationConfiguration(it, currentUsername)
        }
        configurations.each { start(it) }
    }

    private String getCurrentUsername() {
        UserManagement.currentUser?.username
    }

    private void offer(Simulation simulation) {
        if (shouldRun(simulation)) {
            start(new SimulationConfiguration(simulation, currentUsername))
        }
    }

    private void start(SimulationConfiguration simulationConfiguration) {
        log.info("Queuing sim: ${simulationConfiguration.simulation.name} (batch=${simulationConfiguration.simulation.batch.name})")
        simulationQueueService.offer(simulationConfiguration, 5)
    }

    boolean deleteBatch(Batch batch) {
        SimulationRun.withTransaction {
            SimulationRun.withBatchRunId(batch.id).list().each {
                it.batchRun = null
                it.save()
            }
            batch.delete()
        }
    }

    Batch createBatch(List<Parameterization> parameterizations) {
        Batch batch = new Batch(new SimpleDateFormat(BATCH_SIMNAME_STAMP_FORMAT).format(new Date()))
        batch.parameterizations = parameterizations
        batch.executed = false
        batch
    }

    private
    static Simulation createSimulation(Parameterization parameterization, SimulationProfile simulationProfile, Batch batch = null, Object batchPrefixParam=null) {
        parameterization.load()
        def prefix = StringUtils.isEmpty(batchPrefixParam)? getBatchPrefix() : batchPrefixParam
        String name = prefix + " " +  parameterization.nameAndVersion + " " + new SimpleDateFormat(BATCH_SIMNAME_STAMP_FORMAT).format(new Date())
        Simulation simulation = new Simulation(name)
        simulation.modelClass = parameterization.modelClass
        simulation.parameterization = parameterization
        simulation.structure = ModelStructure.getStructureForModel(parameterization.modelClass)
        simulation.batch = batch
        simulation.template = simulationProfile.template
        //TODO decide if we need it and should add it to simulation profiles
        //simulation.beginOfFirstPeriod = beginOfFirstPeriod

        simulation.numberOfIterations = simulationProfile.numberOfIterations ?: 0
        simulation.periodCount = parameterization.periodCount
        if (simulationProfile.randomSeed != null) {
            simulation.randomSeed = simulationProfile.randomSeed
        } else {
            long millis = System.currentTimeMillis()
            long millisE5 = millis / 1E5
            simulation.randomSeed = millis - millisE5 * 1E5
        }

        for (ParameterHolder holder in simulationProfile.runtimeParameters) {
            simulation.addParameter(holder)
        }
        simulation.save()
        return simulation
    }

    Simulation findSimulation(Batch batch, Parameterization parameterization) {
        SimulationRun run = SimulationRun.withBatchRunId(batch?.id).withParamId(parameterization?.id).get()
        if (run) {
            Simulation simulation = new Simulation(run.name)
            simulation.load()
            return simulation
        }
        return null
    }
}

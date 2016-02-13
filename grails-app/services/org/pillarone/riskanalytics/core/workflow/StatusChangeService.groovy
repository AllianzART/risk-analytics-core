package org.pillarone.riskanalytics.core.workflow

import grails.orm.HibernateCriteriaBuilder
import grails.util.Holders
import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import org.apache.commons.lang.StringUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.joda.time.DateTime
import org.pillarone.riskanalytics.core.ParameterizationDAO
import org.pillarone.riskanalytics.core.parameter.comment.workflow.IssueStatus
import org.pillarone.riskanalytics.core.parameterization.ParameterizationHelper
import org.pillarone.riskanalytics.core.remoting.TransactionInfo
import org.pillarone.riskanalytics.core.remoting.impl.RemotingUtils
import org.pillarone.riskanalytics.core.simulation.item.Parameterization
import org.pillarone.riskanalytics.core.simulation.item.VersionNumber
import org.pillarone.riskanalytics.core.simulation.item.parameter.ParameterHolder
import org.pillarone.riskanalytics.core.simulation.item.parameter.comment.Comment
import org.pillarone.riskanalytics.core.simulation.item.parameter.comment.workflow.WorkflowComment
import org.pillarone.riskanalytics.core.user.UserManagement
import org.pillarone.riskanalytics.core.util.Configuration

import java.util.regex.Matcher
import java.util.regex.Pattern

import static org.pillarone.riskanalytics.core.workflow.Status.*

class StatusChangeService {

    private final Log LOG = LogFactory.getLog(StatusChangeService)

    private final String atomDealNameRegex = Configuration.coreGetAndLogStringConfig(
            "atomDealNameRegex",
            "^(.+)  \\(([^)]+)\\)"
    );
    // Notes on regex
    // --------------
    // ^(  = start group capturing deal name
    //   .+ = captured group of text up to (not including) two spaces
    //   )  = end capture group followed by two spaces
    // \\(  = literal open bracket
    //   (  = start group capturing deal state
    //   [^)]+  = captured group of text inside the brackets (sequence of non-close-bracket chars)
    //   )  = end capture group
    // \\) = literal close bracket
    //

    private final Pattern atomDealNamePattern = Pattern.compile(atomDealNameRegex)
    private final String defaultAtomDealStatusList = //'Inquiry,Triage,Active,In Force,In Force - Final,Declined by ART,Declined by Client,Expired Run-Off,Expired Commuted,Old version amended'
        [
                'Inquiry',
                'Triage',
                'Active',
                'In Force',
                'In Force - Final',
                'Declined by ART',
                'Declined by Client',
                'Expired Run-Off',
                'Expired Commuted',
                'Old version amended'
        ].join(',');

    private  final String atomDealStatusList = Configuration.coreGetAndLogStringConfig(
            "atomDealStatusList", defaultAtomDealStatusList
    );
    private  final Set<String> atomDealStatusSet = new HashSet<String>(Arrays.asList(StringUtils.split(atomDealStatusList,',')));

    @CompileStatic
    public static StatusChangeService getService() {
        return (StatusChangeService) Holders.grailsApplication.mainContext.getBean(StatusChangeService.class)
    }

    private Map<Status, Closure> actions = [
            (NONE): { Parameterization parameterization ->
                throw new IllegalStateException("Cannot change status to ${NONE.getDisplayName()}")
            },
            (DATA_ENTRY): { Parameterization parameterization ->
                if (parameterization.status == IN_REVIEW) {
                    parameterization.status = REJECTED
                    parameterization.save()
                } else if (parameterization.status == NONE) {
                    // NONE -> DATA_ENTRY means promoting sandbox p14n to a workflow for chosen deal
                    // Need to check : Are there already any workflows for chosen deal (in same model tree)?
                    //
                    HibernateCriteriaBuilder criteria = ParameterizationDAO.createCriteria()
                    List<ParameterizationDAO> p14nsInWorkflow = criteria.list {
                        and {
                            eq("dealId",            parameterization.dealId)            // looking for p14ns with chosen deal set
                            ne("id",                parameterization.id)                // excluding selected p14n
                            ne("status",            Status.NONE)                        // and only workflows, not sandbox models
                            eq("modelClassName",    parameterization.modelClass.name)   // and restricted to current model tree
                        }
                    }

                    if (!p14nsInWorkflow.isEmpty()) {
                        ParameterizationDAO firstExistingWorkflowP14n = p14nsInWorkflow.first();
                        String nameAndVersion = firstExistingWorkflowP14n?.name + " v" + firstExistingWorkflowP14n?.itemVersion;
                        String w = "Sorry, ${parameterization.modelClass?.simpleName} already has a workflow for deal '${getTransactionName(parameterization.dealId)}'," +
                                "\nEg '$nameAndVersion'" +
                                "\nHint: Choose different deal / use existing workflow / try different model tree.."
                        throw new WorkflowException( "P14n '" + parameterization.name + "'", DATA_ENTRY,  w )
                    }
                }
                Parameterization newParameterization = incrementVersion(parameterization, parameterization.status == NONE)
                newParameterization.status = DATA_ENTRY
                newParameterization.save()
                if (parameterization.status == Status.REJECTED) {
                    audit(IN_REVIEW, DATA_ENTRY, parameterization, newParameterization)
                } else {
                    audit(NONE, DATA_ENTRY, null, newParameterization)
                }
                return newParameterization
            },
            (IN_REVIEW): { Parameterization parameterization ->
                validate(parameterization)
                audit(parameterization.status, IN_REVIEW, parameterization, parameterization)
                parameterization.status = IN_REVIEW
                parameterization.save()
                return parameterization
            },
            (IN_PRODUCTION): { Parameterization parameterization ->
                audit(parameterization.status, IN_PRODUCTION, parameterization, parameterization)
                parameterization.status = IN_PRODUCTION
                parameterization.save()
                return parameterization
            }
    ]

    void validate(Parameterization parameterization) {
        //are there any validation errors ?
        parameterization.validate()
        if (parameterization.realValidationErrors) {
            throw new WorkflowException("P14n '" + parameterization.name + "'", IN_REVIEW, "Pls fix validation errors in model.")
        }

    }

    // TODO http://jira/i#browse/AR-190
    // Called from ra-app/AbstractWorkflowAction to create a new workflow version.
    // In principle we could have a sibling method that also accepted a sandbox p14n to clone instead.
    // To allow users to make a new workflow version out of an existing sandbox model they already have setup.
    //
    @CompileStatic
    Parameterization changeStatus(Parameterization parameterization, Status to) {
        Parameterization newParameterization = null
        reviewComments(parameterization, to)
        AuditLog.withTransaction { status ->
            newParameterization = (Parameterization) actions.get(to).call(parameterization)
        }
        return newParameterization
    }

    void clearAudit(Parameterization parameterization) {
        AuditLog.withTransaction {
            parameterization.load(false)
            List<AuditLog> auditLogs = AuditLog.findAllByFromParameterizationOrToParameterization(parameterization.dao, parameterization.dao)
            auditLogs*.delete()
        }
    }

    //TODO: re-use MIF

    @CompileStatic
    private Parameterization incrementVersion(Parameterization item, boolean newR) {
        String parameterizationName = newR ? getTransactionName(item.dealId) : item.name
        Parameterization newItem = new Parameterization(parameterizationName)

        List<ParameterHolder> newParameters = ParameterizationHelper.copyParameters(item.parameters)
        newParameters.each { ParameterHolder it ->
            newItem.addParameter(it)
        }
        newItem.periodCount = item.periodCount
        newItem.periodLabels = item.periodLabels
        newItem.modelClass = item.modelClass
        newItem.versionNumber = newR ? new VersionNumber("R1") : VersionNumber.incrementVersion(item)
        newItem.dealId = item.dealId
        newItem.valuationDate = item.valuationDate

        for (Comment comment in item.comments) {
            if (comment instanceof WorkflowComment) {
                if ((comment as WorkflowComment).status != IssueStatus.CLOSED) {
                    newItem.addComment(comment.clone())
                }
            } else {
                newItem.addComment(comment.clone())
            }
        }

        def newId = newItem.save()
//        newItem.load() not needed after a save
        return newItem
    }

    @TypeChecked
    private void audit(Status from, Status to, Parameterization fromParameterization, Parameterization toParameterization) {
        AuditLog auditLog = new AuditLog(fromStatus: from, toStatus: to, fromParameterization: (ParameterizationDAO) fromParameterization?.dao, toParameterization: (ParameterizationDAO) toParameterization.dao)
        auditLog.date = new DateTime()
        auditLog.person = UserManagement.getCurrentUser()
        if (!auditLog.save(flush: true)) {
            LOG.error "Error saving audit log: ${auditLog.errors}"
        }
    }

    private void reviewComments(Parameterization from, Status to) {
        switch (to) {
            case DATA_ENTRY:
                for (Comment comment in from.comments) {
                    if (comment instanceof WorkflowComment) {
                        if (comment.status == IssueStatus.RESOLVED) {
                            throw new WorkflowException(from.name, to, "Resolved comments found - must be closed or reopened.")
                        }
                    }
                }
                break;
            case IN_REVIEW:
                for (Comment comment in from.comments) {
                    if (comment instanceof WorkflowComment) {
                        if (comment.status == IssueStatus.OPEN) {
                            throw new WorkflowException(from.name, to, "Open comments found - must be resolved first.")
                        }
                    }
                }
                break;
            case IN_PRODUCTION:
                for (Comment comment in from.comments) {
                    if (comment instanceof WorkflowComment) {
                        if (comment.status != IssueStatus.CLOSED) {
                            throw new WorkflowException(from.name, to, "Unclosed comments found - must be closed first.")
                        }
                    }
                }
                break;
        }
    }

    @CompileStatic
    private String getTransactionName(long dealId) {
        return dropStatusSuffix(RemotingUtils.allTransactions.find { TransactionInfo it -> it.dealId == dealId }.name)
    }

    private String dropStatusSuffix( String name ){
        if(atomDealNameRegex.empty || atomDealNameRegex.equalsIgnoreCase("disabled")){
            LOG.info(String.format("atomDealNameRegex '%s', keeping suffixes, returning '%s'", atomDealNameRegex, name ));
            return name
        }
        Matcher matcher = atomDealNamePattern.matcher(name);
        if( !matcher.find() ){
            LOG.info(String.format("regex '%s' doesn't match '%s'", atomDealNameRegex, name ));
            return name;
        }
        // Strip, if suffix is known status (in expected format)..
        //
        String bareName = matcher.group(1);
        String status = matcher.group(2);
        LOG.info(String.format("matched name is: '%s' ", bareName ));
        LOG.info(String.format("matched status is: '%s' ", status ));

        if( atomDealStatusSet.contains(status) ){

            LOG.info(String.format("'%s'is a known ATOM status, returning bare name '%s'", status, bareName ));
            return bareName;
        }

        LOG.warn(String.format("'%s'is unknown in ATOM must be a spurious coincidence of naming in the deal, returning name '%s'", status, name ));

        return name;
    }

}

@CompileStatic
class WorkflowException extends RuntimeException {

    public WorkflowException(String itemName, Status to, String cause) {
        super("Cannot change status of $itemName to ${to.displayName}.\nCause: $cause".toString())
    }
}

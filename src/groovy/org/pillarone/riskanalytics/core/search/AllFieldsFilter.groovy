package org.pillarone.riskanalytics.core.search

import groovy.transform.CompileStatic
import org.apache.commons.lang.StringUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.pillarone.riskanalytics.core.ParameterizationDAO
import org.pillarone.riskanalytics.core.modellingitem.BatchCacheItem
import org.pillarone.riskanalytics.core.modellingitem.CacheItem
import org.pillarone.riskanalytics.core.modellingitem.ParameterizationCacheItem
import org.pillarone.riskanalytics.core.modellingitem.ResourceCacheItem
import org.pillarone.riskanalytics.core.modellingitem.SimulationCacheItem
import org.pillarone.riskanalytics.core.remoting.TransactionInfo
import org.pillarone.riskanalytics.core.remoting.impl.RemotingUtils
import org.pillarone.riskanalytics.core.util.Configuration

/* frahman 2014-01-02 Extended filtering syntax introduced.

  Originally :-

  AllFieldsFilter supported one or more _alternative_ search terms separated by OR.
  Matching occurred in these ways:
   - term found in item name
   - term exactly matched item's creator
   - term found in any of item's tags (for taggable items - Simulations, Parameterizations and Resources)
   - Parameterizations: additionally, term found in status, or exact match to its numeric deal id
   - Simulation: additionally, term found in name of its Pn or Result Template

   and yielded all items matching on any of the above tests on any of the OR terms.

   New :-

   + Filter terms can restrict their effect to the Name, State, Tags, Deal Id, and Owner fields.
   + Filtering can successively narrow a search via AND clauses
   + Negative filtering allows to exclude specific values via ! prefix
   + Match allowed on partial username (it can be hard to remember exact usernames)
   + Allow finding simulation results, in addition to finding P4ns, for given (exact) deal id

   This has been extended as described below.

   Note: The implementation is not very 'object oriented' and might fit nicely into some kind of visitor
   pattern implementation if some brightspark can see how to do that without doubling the size of the
   code (or doubling the filtering time :-P ) (or both :P :P)
   -----------------------------------------

   + FILTER ON SPECIFIC FIELDS

        Terms can focused on a specific field by prefixing with one of (case not sensitive):
            DEALID:, NAME:, OWNER:, STATE:, or TAG:
            *New* DEALNAME: (dn) DEALTAG: (dt), SEED:, ITERATIONS: (its) and DBID:
            *New: Id-matchers can take a CSV list value

        (Shorter forms D:, N:, O:, S:, and T: are allowed too.)

        This refinement decays gracefully to the old behaviour if keywords are not used.

        If term begins with "<keyword>:" and <keyword> is relevant to the item type being matched, use it as now.
        (Ditto if term doesn't begin with "<keyword>:")
        Else force it to fail matching the item.

        E.g. originally the search text:  "review"
            yielded items with the word 'review' in the name or with status 'in review'.

        Now, the search text: "status:review"
            will only yield items with status 'in review';
            items with 'review' in the *name* but in status 'data entry' will not match.


   + ALLOW AND-ing MULTIPLE FILTERS

        After  i) is implemented, this is easy to layer on top of existing design.

        Formerly the filter 'review OR production' made sense (matching on names or status values),
        whereas filter 'review AND production' could not make sense for status values (an item has only one status).
        Now, the filter 'name:review AND name:production' makes sense and helps refine the search.

        The implementation is somewhat simplistic but useful as long as the query doesn't get too clever:
        The query is initially split into successive restriction filters at AND boundaries.
        Each restriction is then split into alternative search terms at OR boundaries.
        Each filter's terms are applied to the results of the prior filter, successively shrinking the matching tree.

   + ALLOW NEGATIVE FILTERS TO EXCLUDE ITEMS

        Eg The filter 'tag:Q4 2013 AND !tag:Allianz Re' would be useful for listing all the non AZRE models in the Q4 quarter run.

   + ALLOW EXACT MATCH IN ADDITION TO CONTAINMENT MATCH

        Using the = operator instead of the : allows specifying an *exact* match.

        So whereas the filter : 't:50K'
        would match tags :  '50K' and '14Q1v11_50K',
        the filter : 't=50K'
        would only match the tag '50K'.
*/

@CompileStatic
class AllFieldsFilter implements ISearchFilter {

    protected static Log LOG = LogFactory.getLog(AllFieldsFilter)
    private static
    final boolean matchSimulationsOnDeal = Configuration.coreGetAndLogStringConfig("matchSimulationResultsOnDealId", "true").equalsIgnoreCase("true");
    private static final defaultSearchFilterText = Configuration.coreGetAndLogStringConfig("defaultSearchFilterText", "")

    // It can be counterproductive to filter batches.  You search for p14ns to put into a batch but the batch name
    // is not likely to match the same filter exactly, so you can't see the batch once it's created. So by default
    // we don't filter batches.  Matthias may come up with a friendly toggle in the GUI; meanwhile I need to test..
    private static final boolean filterBatchesInGUI = System.getProperty("filterBatchesInGUI", "false").equalsIgnoreCase("true");


    static final String AND_SEPARATOR = " AND "
    static final String OR_SEPARATOR = " OR "
    static Map<Long,String> dealIdToNameMap = null;
    String query = defaultSearchFilterText

    static Map<Long,String> getTransactions(){
        if(!dealIdToNameMap){
            dealIdToNameMap = new HashMap<Long,String>()
        }
        return dealIdToNameMap
    }

    // Convert the list into a map once then every item match can be performed quicker
    //
    static void setTransactions( final List<TransactionInfo> txnList ){

        dealIdToNameMap = (Map<Long,String>) txnList.collectEntries{ TransactionInfo it ->
            [ (it.dealId) : (it.name) ]
        }
    }

    List<MatchTerm[]> matchTermArrays;

    // Add setter to avoid splitting query string for each item ( > 3K items )
    //
    void setQuery(String q) {
        LOG.debug("*** Splitting up filter : " + q)
        query = q

        final String[] restrictions = query.split(AND_SEPARATOR)
        LOG.debug("Restrictions (AND clauses): " + restrictions)
        if (matchTermArrays == null) {
            matchTermArrays = new ArrayList<MatchTerm[]>();
        }
        matchTermArrays.clear()
        restrictions.each { final String r ->
            matchTermArrays.add(
                r.split(OR_SEPARATOR).collect{ String s -> new MatchTerm(s.trim())}.toArray() as MatchTerm[]
            )
        }

        // Only load up list of transaction names if filter EXPLICITLY references them..
        //
        if( matchTermArrays.any { final MatchTerm[] matchTerms ->
                matchTerms.any { final MatchTerm it ->
                    it.isDealNameMatcher
                }
            }
        ){
            final List<TransactionInfo> txnList = RemotingUtils.allTransactions

            if(!txnList || txnList.isEmpty() || txnList.first().name.contains('Connection failed')){
                // NB avoid crashing gui - catch it in app plugin and place error in search filter
                //
                throw new IllegalStateException("Cannot filter on deal names - Transaction Service down")
            }
            setTransactions( txnList )
        }
    }

    @Override
    boolean accept(final CacheItem item) {
        if( !item ){
            false
        }
        if( (!matchTermArrays)  || matchTermArrays.empty ){
            true
        }
        return matchTermArrays.every { final MatchTerm[] it -> passesRestriction(item, it) }
    }

    static boolean passesRestriction(final CacheItem item, final MatchTerm[] matchTerms) {

        // Name, owner and id fields found on every item type
        //
        return  FilterHelp.matchName(item, matchTerms)  ||
                FilterHelp.matchOwner(item, matchTerms) ||
                FilterHelp.matchDbId(item, matchTerms)  ||
                internalAccept(item, matchTerms)
    }

    // With CompileStatic we have to dispatch ourselves - but this gives us control over search order
    // These items must already have failed to match on name / owner if we get here.
    //
    private static boolean internalAccept(final CacheItem item, final MatchTerm[] matchTerms) {
        // Put the most frequently hit ones first for speed
        // Avoids asking thousands of sims if they are a Batch etc
        if( item instanceof SimulationCacheItem ){
            return internalAccept( item as SimulationCacheItem , matchTerms)
        } else
        if( item instanceof ParameterizationCacheItem ){
            return internalAccept( item as ParameterizationCacheItem, matchTerms)
        } else
        if( item instanceof BatchCacheItem ){
            return !filterBatchesInGUI;  // Can configure so only appear if match on name, owner etc
        } else
        if( item instanceof ResourceCacheItem ){
            return internalAccept( item as ResourceCacheItem, matchTerms)
        } else{
            return false
        }
    }

    //  Didn't match simulation on name via general name match attempt, so
    //  try match sim on p14n or result config names, or sim tags
    //  --NEW--
    //  or on deal
    //  or random seed (helpful for version migration comparison reports in new releases).
    //  or iterations
    //
    private static boolean internalAccept(final SimulationCacheItem sim, final MatchTerm[] matchTerms) {
        return  matchTerms.any { final MatchTerm term ->
            term.isNameAcceptor &&
            (
                StringUtils.containsIgnoreCase(sim.parameterization?.nameAndVersion, term.text) ||
                StringUtils.containsIgnoreCase(sim.resultConfiguration?.nameAndVersion, term.text)
            )
        } ||
        FilterHelp.matchTags(sim.tags*.name, matchTerms) ||
        (
            //Can disable matchSimulationResultsOnDealId to check performance impact..
            //
            matchSimulationsOnDeal &&
            FilterHelp.matchDeal(sim.parameterization, matchTerms, getTransactions())
        ) ||
        matchTerms.any { final MatchTerm term ->
            (term.isSeedEqualsOp    &&  StringUtils.equals(""+sim.randomSeed, term.text))   ||
            (term.isSeedNotEqualsOp && !StringUtils.equals(""+sim.randomSeed, term.text))   ||
            (term.isSeedAcceptor    &&  StringUtils.contains(""+sim.randomSeed, term.text)) ||
            (term.isSeedRejector    && !StringUtils.contains(""+sim.randomSeed, term.text))
        } ||
        matchTerms.any { final MatchTerm term ->
            (term.isIterationsEqualsOp    &&  StringUtils.equals(""+sim.numberOfIterations,   term.text)) ||
            (term.isIterationsNotEqualsOp && !StringUtils.equals(""+sim.numberOfIterations,   term.text)) ||
            (term.isIterationsAcceptor    &&  StringUtils.contains(""+sim.numberOfIterations, term.text)) ||
            (term.isIterationsRejector    && !StringUtils.contains(""+sim.numberOfIterations, term.text))
        } ||
        (
            //Can disable this to check performance impact..
            //
            matchSimulationsOnDeal &&
            FilterHelp.matchDealTags(sim.parameterization, matchTerms)
        )
    }

    private static boolean internalAccept(final ParameterizationCacheItem p14n, final MatchTerm[] matchTerms) {
        return FilterHelp.matchTags(p14n.tags*.name, matchTerms) ||
               FilterHelp.matchState(p14n, matchTerms) ||
               FilterHelp.matchDeal(p14n, matchTerms, getTransactions() ) ||
               FilterHelp.matchDealTags(p14n, matchTerms )
        ;
    }

    private static boolean internalAccept(ResourceCacheItem res, MatchTerm[] matchTerms) {
        return FilterHelp.matchTags(res.tags*.name, matchTerms);
    }

}


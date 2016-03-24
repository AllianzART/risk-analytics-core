package org.pillarone.riskanalytics.core.search

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

import static org.pillarone.riskanalytics.core.search.MatchTerm.getNonePrefix

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

class AllFieldsFilter implements ISearchFilter {

    protected static Log LOG = LogFactory.getLog(AllFieldsFilter)
    private static
    final boolean matchSimulationResultsOnDealId = Configuration.coreGetAndLogStringConfig("matchSimulationResultsOnDealId", "true").equalsIgnoreCase("true");
    private static final defaultSearchFilterText = Configuration.coreGetAndLogStringConfig("defaultSearchFilterText", "")

    // It can be counterproductive to filter batches.  You search for p14ns to put into a batch but the batch name
    // is not likely to match the same filter exactly, so you can't see the batch once it's created. So by default
    // we don't filter batches.  Matthias may come up with a friendly toggle in the GUI; meanwhile I need to test..
    private static final boolean filterBatchesInGUI = System.getProperty("filterBatchesInGUI", "false").equalsIgnoreCase("true");


    static final String AND_SEPARATOR = " AND "
    static final String OR_SEPARATOR = " OR "
    static Map<Long,String> dealIdToNameMap = null;
    String query = defaultSearchFilterText

    // Convert the list into a map once then every item match can be performed quicker
    //
    static void setTransactions( List<TransactionInfo> txnList ){

        if(!dealIdToNameMap){
            dealIdToNameMap = new HashMap<Long,String>(1024)
        }
        dealIdToNameMap.clear()
        for(TransactionInfo transactionInfo : txnList){
            if(0 < transactionInfo?.dealId){
                dealIdToNameMap[transactionInfo.dealId] = transactionInfo.name
            }
        }
    }
    static Map<Long,String> getTransactions(){
        if(!dealIdToNameMap){
            dealIdToNameMap = new HashMap<Long,String>(100)
        }
        return dealIdToNameMap
    }


    List<MatchTerm[]> matchTermArrays;

    // Add setter to avoid splitting query string for each item ( > 3K items )
    //
    void setQuery(String q) {
        LOG.debug("*** Splitting up filter : " + q)
        query = q

        String[] restrictions = query.split(AND_SEPARATOR)
        LOG.debug("Restrictions (AND clauses): " + restrictions)
        if (matchTermArrays == null) {
            matchTermArrays = new ArrayList<MatchTerm[]>();
        }
        matchTermArrays.clear()
        restrictions.each {
            String[] orArray = it.split(OR_SEPARATOR)
            MatchTerm[] matcherArray = (orArray.collect { new MatchTerm(it.trim())}).toArray() as MatchTerm[]
            LOG.debug("Terms (OR clauses): " + orArray)
            matchTermArrays.add(matcherArray)
        }

        // Do we need to load up list of transaction names ? Only if filter EXPLICITLY references them..
        //
        if( matchTermArrays.any { MatchTerm[] matchTerms ->
                matchTerms.any {
                    it.getPrefix().startsWith(MatchTerm.dealNameShort)    ||
                    it.getPrefix().startsWith(MatchTerm.dealName)
                }
            }
        ){
            List<TransactionInfo> txnList = RemotingUtils.allTransactions

            if(!txnList || txnList.isEmpty() || txnList.first().name.contains('Connection failed')){
                // Throwing here brought the gui down - we catch it in app and leave an error in search filter
                //
                throw new IllegalStateException("Cannot filter on deal names - Transaction Service down")
            }
            setTransactions( txnList )
        }
    }

    @Override
    boolean accept(CacheItem item) {
        if( !item ){
            false
        }
        if( (!matchTermArrays)  || matchTermArrays.empty ){
            true
        }
        return matchTermArrays.every { passesRestriction(item, it) }
    }

    static boolean passesRestriction(CacheItem item, MatchTerm[] matchTerms) {

        // Name, owner and id fields found on every item type
        //
        return  FilterHelp.matchName(item, matchTerms)  ||
                FilterHelp.matchOwner(item, matchTerms) ||
                FilterHelp.matchDbId(item, matchTerms)  ||
                internalAccept(item, matchTerms)
    }

    // These items must already have failed to match on name / owner if we get here.
    //
    private static boolean internalAccept(CacheItem item, MatchTerm[] searchStrings) {
        if(LOG.debugEnabled){
            LOG.debug("CacheItem IGNORED by AllFieldsFilter: ${item.nameAndVersion} ")
        }
        return false
    }

    //  Didn't match simulation on name via general name match attempt, so
    //  try match sim's tags or p14n or result config
    //  --NEW--
    //  or random seed (helpful for version migration comparison reports in new releases).
    //
    private static boolean internalAccept(SimulationCacheItem sim, MatchTerm[] matchTerms) {
        return FilterHelp.matchTags(sim, matchTerms) ||
                matchTerms.any {
                    it.isNameAcceptor() &&
                    (
                        StringUtils.containsIgnoreCase(sim.parameterization?.nameAndVersion, it.text) ||
                        StringUtils.containsIgnoreCase(sim.resultConfiguration?.nameAndVersion, it.text)
                    )
                } ||
                (
                    //Can disable this to check performance impact..
                    //
                    matchSimulationResultsOnDealId && FilterHelp.matchDeal(sim.parameterization, matchTerms, getTransactions())
                ) ||
                matchTerms.any {
                    (it.isSeedEqualsOp()    &&  StringUtils.equals(""+sim.randomSeed, it.text))   ||
                    (it.isSeedNotEqualsOp() && !StringUtils.equals(""+sim.randomSeed, it.text))   ||
                    (it.isSeedAcceptor()    &&  StringUtils.contains(""+sim.randomSeed, it.text)) ||
                    (it.isSeedRejector()    && !StringUtils.contains(""+sim.randomSeed, it.text))
                } ||
                matchTerms.any {
                    (it.isIterationsEqualsOp()    &&  StringUtils.equals(""+sim.numberOfIterations,   it.text)) ||
                    (it.isIterationsNotEqualsOp() && !StringUtils.equals(""+sim.numberOfIterations,   it.text)) ||
                    (it.isIterationsAcceptor()    &&  StringUtils.contains(""+sim.numberOfIterations, it.text)) ||
                    (it.isIterationsRejector()    && !StringUtils.contains(""+sim.numberOfIterations, it.text))
                } ||
                (
                    //Can disable this to check performance impact..
                    //
                    matchSimulationResultsOnDealId && FilterHelp.matchDealTags(sim.parameterization, matchTerms)
                )
    }

    // This override renders Batches immune to filtering (so they always appear).
    // Set -DfilterBatchesInGUI=true to remove this immunity.
    //
    private static boolean internalAccept(BatchCacheItem b, MatchTerm[] matchTerms) {
        return !filterBatchesInGUI;
    }

    private static boolean internalAccept(ParameterizationCacheItem p14n, MatchTerm[] matchTerms) {
        return FilterHelp.matchTags(p14n, matchTerms) ||
                FilterHelp.matchState(p14n, matchTerms) ||
                FilterHelp.matchDeal(p14n, matchTerms, transactions ) ||
                FilterHelp.matchDealTags(p14n, matchTerms )
        ;
    }

    private static boolean internalAccept(ResourceCacheItem res, MatchTerm[] matchTerms) {
        return FilterHelp.matchTags(res, matchTerms);
    }

    //------------------------------------------------------------------------------------------------------------------
    // Santa's not-so-little helper..
    //
    static class FilterHelp {

        private static Log LOG = LogFactory.getLog(FilterHelp)



        // Gets list of distinct tags on all p14ns in given deal
        // (http://docs.jboss.org/hibernate/core/3.6/reference/en-US/html/queryhql.html)
        //
        static final String dealTagQuery = "select distinct pt.tag.name as tagName " +
                "from ParameterizationDAO as pd " +
                "left outer join pd.tags as pt " +
                "where pd.dealId = :dealId " +
                "and pt.tag.name <> 'LOCKED' "


        static boolean matchName(CacheItem item, final MatchTerm[] matchTerms) {
            final String itemName = item.name
            final String itemNameAndVersion = item.nameAndVersion
            return matchTerms.any {
                  it.isNameAcceptor()    ?  StringUtils.containsIgnoreCase(itemNameAndVersion, it.text)
                : it.isNameRejector()    ? !StringUtils.containsIgnoreCase(itemNameAndVersion, it.text)
                : it.isNameEqualsOp()    ?  StringUtils.equalsIgnoreCase(itemName, it.text)
                : it.isNameNotEqualsOp() ? !StringUtils.equalsIgnoreCase(itemName, it.text)
                : false
            };
        }

        static boolean matchOwner(CacheItem item, final MatchTerm[] matchTerms) {
            final String username = item.creator?.username
            return matchTerms.any {
                  it.isOwnerAcceptor()      ?  StringUtils.containsIgnoreCase(username, it.text)
                : it.isOwnerRejector()      ? !StringUtils.containsIgnoreCase(username, it.text)
                : it.isOwnerEqualsOp()      ?  StringUtils.equalsIgnoreCase(username, it.text)
                : it.isOwnerNotEqualsOp()   ? !StringUtils.equalsIgnoreCase(username, it.text)
                : false
            };
        }

        static boolean matchDbId(CacheItem item, final MatchTerm[] matchTerms) {
            final String itemId = ""+item?.id
            matchTerms.any {
                  it.isDbIdAcceptor()    ?  containsAnyCsvElement(itemId, it.text )  // allow listing many
                : it.isDbIdRejector()    ? !StringUtils.containsIgnoreCase(itemId, it.text)
                : it.isDbIdEqualsOp()    ?  equalsAnyCsvElement(itemId, it.text )    // allow listing many
                : it.isDbIdNotEqualsOp() ? !StringUtils.equalsIgnoreCase(itemId, it.text)
                : false
            }
        }

        // Case insensitive
        //
        static boolean containsAnyCsvElement( final String text, final String csvTerms ){
//          final String[] terms = csvTerms.split(",")
            csvTerms.split(",").any { StringUtils.containsIgnoreCase(text, it.trim()) }
        }
        static boolean equalsAnyCsvElement( final String text, final String csvTerms ){
//          String[] terms = csvTerms.split(",")
            csvTerms.split(",").any { StringUtils.equalsIgnoreCase(text, it.trim()) }
        }

        static boolean matchState(final ParameterizationCacheItem p14n, final MatchTerm[] matchTerms) {
            final String p14nStatus =  p14n.status?.toString()
            return matchTerms.any {
                    it.isStateAcceptor()    ?  StringUtils.containsIgnoreCase(p14nStatus,  it.text)
                  : it.isStateRejector()    ? !StringUtils.containsIgnoreCase(p14nStatus, it.text)
                  : it.isStateEqualsOp()    ?  StringUtils.equalsIgnoreCase(p14nStatus, it.text)
                  : it.isStateNotEqualsOp() ? !StringUtils.equalsIgnoreCase(p14nStatus, it.text)
                : false
            };
        }

        static boolean matchDeal(final ParameterizationCacheItem p14n, final MatchTerm[] matchTerms, final Map<Long,String> txInfos ) {
            final String dealId = p14n?.dealId?.toString()
            // nb don't identify dealname unless we need it
            return matchTerms.any {
                  it.isDealIdAcceptor()      ?  containsAnyCsvElement(dealId, it.text ) //can use list
                : it.isDealIdEqualsOp()      ?  equalsAnyCsvElement(dealId, it.text )   //can use list
                : it.isDealIdRejector()      ? !StringUtils.containsIgnoreCase(dealId, it.text)
                : it.isDealIdNotEqualsOp()   ? !StringUtils.equalsIgnoreCase(dealId, it.text)
                : it.isDealNameAcceptor()    ?  StringUtils.containsIgnoreCase(getDealName(p14n,txInfos), it.text)
                : it.isDealNameEqualsOp()    ?  StringUtils.equalsIgnoreCase(getDealName(p14n,txInfos), it.text)
                : it.isDealNameRejector()    ? !StringUtils.containsIgnoreCase(getDealName(p14n,txInfos), it.text)
                : it.isDealNameNotEqualsOp() ? !StringUtils.equalsIgnoreCase(getDealName(p14n,txInfos), it.text)
                : false
            };
        }

        // In ROFO scenarios, want to exclude items connected to a deal that has *any* item with new qtr tag..
        //
        // To avoid crippling performance (should really test this) I avoid db checks on items that have
        // no real deal set (ie ignoring sandbox and AZRe items)..
        //
        static boolean matchDealTags(final ParameterizationCacheItem item, final MatchTerm[] matchTerms) {

            return (0 < item?.dealId )  &&  matchTerms.any {
                  it.isDealTagAcceptor()    ?  dealContainsTagLike( item, it.text)
                : it.isDealTagEqualsOp()    ?  dealContainsTagExact(item, it.text)
                : it.isDealTagRejector()    ? !dealContainsTagLike( item, it.text)
                : it.isDealTagNotEqualsOp() ? !dealContainsTagExact(item, it.text)
                : false
            };
        }

        //e.g. 'dt:Q4 2013' to match a pn if any pn with same deal has tag with 'Q4 2013' in the name
        //
        private static boolean dealContainsTagLike(final ParameterizationCacheItem item, final String term, boolean equalsOp = false){

            // Try avoid DB query if item itself satisfies dealtag check
            //
            if(equalsOp){
                if( item.tags*.name.any { StringUtils.equalsIgnoreCase(it, term)} ){
                    return true
                }
            }else{
                if( item.tags*.name.any { StringUtils.containsIgnoreCase(it, term)} ){
                    return true
                }
            }

            // Slower: do *any* models on same deal satisfy query ? Get ArrayList of tags on any p14ns in deal
            //
            List results = ParameterizationDAO.executeQuery(dealTagQuery, ["dealId": item.dealId], [readOnly: true])

            //LOG.debug("${results.size()} distinct tags on deal ${item.dealId}-related models eg (${item.name})")
            return equalsOp ? results.any { String tag -> StringUtils.equalsIgnoreCase(  tag, term)} :
                              results.any { String tag -> StringUtils.containsIgnoreCase(tag, term) };
        }
        private static boolean dealContainsTagExact(final ParameterizationCacheItem item, final String it){
            return dealContainsTagLike( item, it, true);
        }

        private static String getDealName(final ParameterizationCacheItem p14n, final Map<Long,String> transactions){
            if(!p14n?.dealId || p14n.dealId < 0){
//                LOG.debug("${p14n.nameAndVersion} - not connected to a txn")
                return ""
            }

            if(!transactions|| transactions.isEmpty()){
                String w ="No transaction info - looking up deal name on '${p14n.nameAndVersion}'"
                LOG.warn(w)
                throw new IllegalStateException(w)
            }

            if(!transactions.containsKey(p14n.dealId)){
                LOG.warn("${p14n.nameAndVersion} - has unknown deal id '${p14n.dealId}'")
            }
            return transactions.get(p14n.dealId, "");
        }
        // Only call this for things that have tags (Simulation, Parameterization or Resource)
        private static boolean matchTags(final def item, final MatchTerm[] matchTerms) {
            final def tagNames = item.tags*.name
            return matchTerms.any { def it ->
                if (it.isTagAcceptor()) {
                    //e.g. term 'tag:Q4 2013' will match any sim or pn tagged 'Q4 2013' (but also eg 'Q4 2013 ReRun')
                    tagNames.any { String tag -> StringUtils.containsIgnoreCase(tag, it.text) }
                }
                else if (it.isTagRejector()){
                    //e.g. term '!tag:Q4 2013' will match any sim or pn tagged 'H2 2013' (but not 'Q4 2013 ReRun')
                    !tagNames.any { String tag -> StringUtils.containsIgnoreCase(tag, it.text) }
                }
                else if (it.isTagEqualsOp()) {
                    //e.g. term 'tag=Q4 2013' will match any sim or pn tagged 'Q4 2013' (but not eg 'Q4 2013 ReRun')
                    tagNames.any { String tag -> StringUtils.equalsIgnoreCase(tag, it.text) }
                }
                else if (it.isTagNotEqualsOp()) {
                    //e.g. term '!tag = Q4 2013' will match any sim or pn tagged 'Q1 2015' (but also eg 'Q4 2013 ReRun')
                    !tagNames.any { String tag -> StringUtils.equalsIgnoreCase(tag, it.text) }
                }
                else{
                    //e.g. term 'status:Q4 2013' would fail to match a sim tagged Q4 2013 (and status 'in review' etc)
                    false
                }
            }
        }

        // Drop any column prefix to return unadorned text user wanted to filter with
        // (?i)     = case insensitive
        // ^        = start of string
        // [!]? *   = optional bang followed by optional spaces
        // [a-z]+ * = alphabetic word followed by optional spaces
        // [:=]     = colon or equals
        //
//        @Deprecated
//        private static String getText(String specificSearchTerm) {
//            String raw = specificSearchTerm.replaceFirst("(?i)^[!]? *[a-z]+ *[:=]", ""); //case insensitive regex-replace
//            LOG.debug("getText(${specificSearchTerm}-->${raw})")
//
//            return raw.trim()
//        }
    }

}


package org.pillarone.riskanalytics.core.search

import groovy.transform.CompileStatic
import org.apache.commons.lang.StringUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.pillarone.riskanalytics.core.ParameterizationDAO
import org.pillarone.riskanalytics.core.modellingitem.CacheItem
import org.pillarone.riskanalytics.core.modellingitem.ParameterizationCacheItem

//------------------------------------------------------------------------------------------------------------------
// AllFieldFilter's not-so-little helper.. just a namespace holding helper methods
//
@CompileStatic
public class FilterHelp {

    private static Log LOG = LogFactory.getLog(FilterHelp)
    private static final String EmptyString = ""

    // Gets list of distinct tags on all p14ns in given deal
    // (http://docs.jboss.org/hibernate/core/3.6/reference/en-US/html/queryhql.html)
    //
    static final String dealTagQuery = "select distinct pt.tag.name as tagName " +
            "from ParameterizationDAO as pd " +
            "left outer join pd.tags as pt " +
            "where pd.dealId = :dealId " +
            "and pt.tag.name <> 'LOCKED' "

    static boolean matchName(final CacheItem item, final MatchTerm[] matchTerms) {
        final String itemNameAndVersion = item.nameAndVersion
        return matchTerms.any { final MatchTerm it ->
            if(!it.isNameMatcher){
                false
            }else if(it.isNameAcceptor){
                  StringUtils.containsIgnoreCase(itemNameAndVersion, it.text)
            }else if(it.isNameRejector){
                 !StringUtils.containsIgnoreCase(itemNameAndVersion, it.text)
            }else if(it.isNameEqualsOp){
                  StringUtils.equalsIgnoreCase(item.name, it.text)
            }else if(it.isNameNotEqualsOp){
                 !StringUtils.equalsIgnoreCase(item.name, it.text)
            }else{
                 false
            }
//              it.isNameAcceptor    ?  StringUtils.containsIgnoreCase(itemNameAndVersion, it.text)
//            : it.isNameRejector    ? !StringUtils.containsIgnoreCase(itemNameAndVersion, it.text)
//            : it.isNameEqualsOp    ?  StringUtils.equalsIgnoreCase(item.name, it.text)
//            : it.isNameNotEqualsOp ? !StringUtils.equalsIgnoreCase(item.name, it.text)
//            : false
        };
    }

    static boolean matchOwner(final CacheItem item, final MatchTerm[] matchTerms) {
        final String itemOwner = item.creator?.username
        return matchTerms.any { final MatchTerm it ->
            if(it.isOwnerMatcher){
                  it.isOwnerAcceptor    ?  StringUtils.containsIgnoreCase(itemOwner, it.text)
                : it.isOwnerRejector    ? !StringUtils.containsIgnoreCase(itemOwner, it.text)
                : it.isOwnerEqualsOp    ?  StringUtils.equalsIgnoreCase(itemOwner,   it.text)
                : it.isOwnerNotEqualsOp ? !StringUtils.equalsIgnoreCase(itemOwner,   it.text)
                : false
            }else{
                false
            }
        };
    }

    static boolean matchDbId(final CacheItem item, final MatchTerm[] matchTerms) {
        final String itemId = ""+item?.id
        matchTerms.any { final MatchTerm it ->
            if(it.isDbIdMatcher){
                  it.isDbIdAcceptor    ?  containsAnyCsvElement(itemId, it.text )  // allow listing many
                : it.isDbIdRejector    ? !StringUtils.containsIgnoreCase(itemId, it.text)
                : it.isDbIdEqualsOp    ?  equalsAnyCsvElement(itemId, it.text )    // allow listing many
                : it.isDbIdNotEqualsOp ? !StringUtils.equalsIgnoreCase(itemId, it.text)
                : false
            }else{
                false
            }
        }
    }

    // Case insensitive
    //
    static boolean containsAnyCsvElement( final String text, final String csvTerms ){
        csvTerms.split(",").any { String term -> StringUtils.containsIgnoreCase(text, term.trim()) }
    }
    static boolean equalsAnyCsvElement( final String text, final String csvTerms ){
        csvTerms.split(",").any { String term -> StringUtils.equalsIgnoreCase(text, term.trim()) }
    }

    static boolean matchState(final ParameterizationCacheItem p14n, final MatchTerm[] matchTerms) {
        final String p14nStatus =  p14n.status?.toString()
        return matchTerms.any { final MatchTerm it ->
            if(it.isStateMatcher){
                  it.isStateAcceptor    ?  StringUtils.containsIgnoreCase(p14nStatus,  it.text)
                : it.isStateRejector    ? !StringUtils.containsIgnoreCase(p14nStatus, it.text)
                : it.isStateEqualsOp    ?  StringUtils.equalsIgnoreCase(p14nStatus, it.text)
                : it.isStateNotEqualsOp ? !StringUtils.equalsIgnoreCase(p14nStatus, it.text)
                : false
            }else{
                false
            }
        };
    }

    static boolean matchDeal(final ParameterizationCacheItem p14n, final MatchTerm[] matchTerms, final Map<Long,String> txInfos ) {
        final String dealId = p14n?.dealId?.toString()
        // nb don't pre-query dealname
        return matchTerms.any { final MatchTerm it ->
            if(it.isDealNameMatcher){
                  it.isDealNameAcceptor    ?  StringUtils.containsIgnoreCase(getDealName(p14n,txInfos), it.text)
                : it.isDealNameEqualsOp    ?  StringUtils.equalsIgnoreCase(getDealName(  p14n,txInfos), it.text)
                : it.isDealNameRejector    ? !StringUtils.containsIgnoreCase(getDealName(p14n,txInfos), it.text)
                : it.isDealNameNotEqualsOp ? !StringUtils.equalsIgnoreCase(getDealName(  p14n,txInfos), it.text)
                : false
            }else if(it.isDealIdMatcher){
                  it.isDealIdAcceptor      ?  containsAnyCsvElement(dealId, it.text ) //can use list
                : it.isDealIdEqualsOp      ?  equalsAnyCsvElement(dealId, it.text )   //can use list
                : it.isDealIdRejector      ? !StringUtils.containsIgnoreCase(dealId, it.text)
                : it.isDealIdNotEqualsOp   ? !StringUtils.equalsIgnoreCase(dealId, it.text)
                : false
            }else{
                false
            }
        };
    }

    // For building ROFO batches, helps to exclude items connected to a deal that has *any* item with new qtr tag..
    //
    // To avoid crippling performance (should really test this) I avoid db checks on items that have
    // no real deal set (ie ignoring sandbox and AZRe items)..
    //
    static boolean matchDealTags(final ParameterizationCacheItem item, final MatchTerm[] matchTerms) {

        return (0 < item?.dealId )  &&  matchTerms.any { final MatchTerm it ->
            if(it.isDealTagMatcher){
                  it.isDealTagAcceptor    ?  dealContainsTagLike( item, it.text)
                : it.isDealTagEqualsOp    ?  dealContainsTagExact(item, it.text)
                : it.isDealTagRejector    ? !dealContainsTagLike( item, it.text)
                : it.isDealTagNotEqualsOp ? !dealContainsTagExact(item, it.text)
                : false
            }else{
                false
            }
        };
    }

    //e.g. 'dt:Q4 2013' to match a pn if any pn with same deal has tag with 'Q4 2013' in the name
    //
    private static boolean dealContainsTagExact(final ParameterizationCacheItem item, final String it){
        return dealContainsTagLike( item, it, true);
    }

    private static boolean dealContainsTagLike(final ParameterizationCacheItem item, final String term, boolean equalsOp = false){

        // Avoid DB query if item itself satisfies dealtag check
        //
        if(equalsOp){
            if( item.tags*.name.any { final String it -> StringUtils.equalsIgnoreCase(it, term)} ){
                return true
            }
        }else{
            if( item.tags*.name.any { final String it -> StringUtils.containsIgnoreCase(it, term)} ){
                return true
            }
        }

        // Slower: do *any* models on same deal satisfy query ? Get ArrayList of tags on any p14ns in deal
        //
        final List results = ParameterizationDAO.executeQuery(dealTagQuery, ["dealId": item.dealId], [readOnly: true])

        //LOG.debug("${results.size()} distinct tags on deal ${item.dealId}-related models eg (${item.name})")
        return equalsOp ? results.any { final String tag -> StringUtils.equalsIgnoreCase(  tag, term)} :
                          results.any { final String tag -> StringUtils.containsIgnoreCase(tag, term) };
    }

    private static String getDealName(final ParameterizationCacheItem p14n, final Map<Long,String> txInfos){
        if(!p14n?.dealId || p14n.dealId < 0){
//                LOG.debug("${p14n.nameAndVersion} - not connected to a txn")
            return EmptyString
        }

        if(!txInfos|| txInfos.isEmpty()){
            String w ="No transaction info - looking up deal name on '${p14n.nameAndVersion}'"
            LOG.warn(w)
            throw new IllegalStateException(w)
        }

        if(!txInfos.containsKey(p14n.dealId)){
            LOG.info("${p14n.nameAndVersion} - has unknown deal id '${p14n.dealId}'")
        }
        return txInfos.get(p14n.dealId, EmptyString);
    }

    static boolean matchTags(final List<String> tagNames, final MatchTerm[] matchTerms) {
        return matchTerms.any { MatchTerm it ->
            if (it.isTagAcceptor) {
                //e.g. term 'tag:Q4 2013' will match any sim or pn tagged 'Q4 2013' (but also eg 'Q4 2013 ReRun')
                tagNames.any { String tag -> StringUtils.containsIgnoreCase(tag, it.text) }
            }
            else if (it.isTagRejector){
                //e.g. term '!tag:Q4 2013' will match any sim or pn tagged 'H2 2013' (but not 'Q4 2013 ReRun')
                !tagNames.any { String tag -> StringUtils.containsIgnoreCase(tag, it.text) }
            }
            else if (it.isTagEqualsOp) {
                //e.g. term 'tag=Q4 2013' will match any sim or pn tagged 'Q4 2013' (but not eg 'Q4 2013 ReRun')
                tagNames.any { String tag -> StringUtils.equalsIgnoreCase(tag, it.text) }
            }
            else if (it.isTagNotEqualsOp) {
                //e.g. term '!tag = Q4 2013' will match any sim or pn tagged 'Q1 2015' (but also eg 'Q4 2013 ReRun')
                !tagNames.any { String tag -> StringUtils.equalsIgnoreCase(tag, it.text) }
            }
            else{
                //e.g. term 'status:Q4 2013' would fail to match a sim tagged Q4 2013 (and status 'in review' etc)
                false
            }
        }
    }
}


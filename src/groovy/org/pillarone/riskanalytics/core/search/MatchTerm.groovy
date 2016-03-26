package org.pillarone.riskanalytics.core.search

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.pillarone.riskanalytics.core.modellingitem.ParameterizationCacheItem

import static java.lang.Boolean.FALSE
import static java.lang.Boolean.TRUE

/**
 * Created with IntelliJ IDEA.
 * User: frahman
 * Date: 20/03/16
 * Time: 20:26
 * To change this template use File | Settings | File Templates.
 */
class MatchTerm  {

    private static Log LOG = LogFactory.getLog(MatchTerm)

    static final String nonePrefix = ""

    static String getDealName(ParameterizationCacheItem paramterizationCacheItem, HashMap map){
        String e = "Landed in MatchTerm.getDealName()"
        LOG.error(e)
        throw new IllegalStateException(e)
    }

    //Search term can begin with a column prefix to restrict its use against a specific 'column'
    static final String dealId         = "DEALID"
    static final String dealName       = "DEALNAME"
    static final String dealTag        = "DEALTAG"
    static final String name           = "NAME"
    static final String owner          = "OWNER"
    static final String state          = "STATE"
    static final String tag            = "TAG"

    //Shorter versions of the above for more concise filter expressions
    //
    static final String dealIdShort     = "D"
    static final String dealNameShort   = "DN"
    static final String dealTagShort    = "DT"
    static final String nameShort       = "N"
    static final String ownerShort      = "O"
    static final String stateShort      = "S"
    static final String tagShort        = "T"

    // Mostly admin use
    //
    static final String dbId            = "DBID"        //any item
    static final String iterations      = "ITERATIONS"  //sims
    static final String iterationsShort = "ITS"
    static final String seed            = "SEED"        //sims

    // TODO Should enforce excluding these special characters from item fields (name, tags, status etc)
    //
    static final String FILTER_NEGATION = "!"
    static final String COLON = ":"
    static final String EQUALS = "="

    //Use a lookup set (for speed):
    //
    static final Map<String,String> columnFilterPrefixes = [
        name,
        nameShort,
        owner,
        ownerShort,
        state,
        stateShort,
        tag,
        tagShort,

        dealId,
        dealIdShort,
        dealName,
        dealNameShort,
        dealTag,
        dealTagShort,
        iterations,
        iterationsShort,
        dbId,
        seed,
    ].collectEntries{ [ (it) : (it) ] }

    static final int MISSING = -1;

    final String original;
    final String prefix;
    final String text;
    int bangIndex;
    int colonIndex;
    int equalsIndex;

    boolean isDealIdAcceptor         =false
    boolean isDealNameAcceptor       =false
    boolean isDealTagAcceptor        =false
    boolean isNameAcceptor           =false
    boolean isOwnerAcceptor          =false
    boolean isStateAcceptor          =false
    boolean isTagAcceptor            =false
    boolean isSeedAcceptor           =false
    boolean isDbIdAcceptor           =false
    boolean isIterationsAcceptor     =false
    boolean isDealIdRejector         =false
    boolean isDealNameRejector       =false
    boolean isDealTagRejector        =false
    boolean isNameRejector           =false
    boolean isOwnerRejector          =false
    boolean isStateRejector          =false
    boolean isTagRejector            =false
    boolean isSeedRejector           =false
    boolean isDbIdRejector           =false
    boolean isIterationsRejector     =false
    boolean isDealIdEqualsOp         =false
    boolean isDealNameEqualsOp       =false
    boolean isDealTagEqualsOp        =false
    boolean isNameEqualsOp           =false
    boolean isOwnerEqualsOp          =false
    boolean isStateEqualsOp          =false
    boolean isTagEqualsOp            =false
    boolean isSeedEqualsOp           =false
    boolean isDbIdEqualsOp           =false
    boolean isIterationsEqualsOp     =false
    boolean isDealIdNotEqualsOp      =false
    boolean isDealNameNotEqualsOp    =false
    boolean isDealTagNotEqualsOp     =false
    boolean isNameNotEqualsOp        =false
    boolean isOwnerNotEqualsOp       =false
    boolean isStateNotEqualsOp       =false
    boolean isTagNotEqualsOp         =false
    boolean isSeedNotEqualsOp        =false
    boolean isDbIdNotEqualsOp        =false
    boolean isIterationsNotEqualsOp  =false


    private  Boolean isAcceptorObj = null
    private  Boolean isRejectorObj = null
    private  Boolean isEqualsObj   = null
    private  Boolean isNotEqualsObj= null

    private void initBasePredicates(){

        if(isAcceptorObj == null){
            isAcceptorObj = (
                bangIndex   != MISSING ||
                equalsIndex != MISSING
            ) ? FALSE
              : TRUE
        }

        if(isRejectorObj == null){
            isRejectorObj = (
                bangIndex   == MISSING ||
                equalsIndex != MISSING
            ) ? FALSE
              : TRUE
        }

        if(isEqualsObj == null){
            isEqualsObj = (
                bangIndex   != MISSING ||
                equalsIndex == MISSING
            ) ? FALSE
              : TRUE
        }

        if(isNotEqualsObj == null){
            isNotEqualsObj = (
                bangIndex   == MISSING ||
                equalsIndex == MISSING
            ) ? FALSE
              : TRUE
        }
    }

    private void setBangMissing(){
        bangIndex = MISSING
        isRejectorObj = FALSE
        isNotEqualsObj = FALSE
    }
    private void knowBangPresent(){
        isEqualsObj = FALSE
        isAcceptorObj = FALSE
    }
    private void setEqualsMissing(){
        equalsIndex = MISSING
        isEqualsObj = FALSE
        isNotEqualsObj = FALSE
    }
    private void knowEqualsPresent(){
        isAcceptorObj = FALSE
        isRejectorObj = FALSE
    }

    private void initAcceptorPredicates(){

        if(isAcceptorObj == null){
            throw new IllegalStateException("initAcceptorPredicates called before isAcceptorObj set..")
        } else if(isAcceptorObj == TRUE){
            isDbIdAcceptor       = prefix == dbId
            isDealIdAcceptor     = [nonePrefix, dealIdShort, dealId].any { prefix == it }
            isDealNameAcceptor   = [dealNameShort, dealName].any { prefix == it }
            isDealTagAcceptor    = [dealTagShort, dealTag].any { prefix == it }
            isIterationsAcceptor = [iterationsShort, iterations].any { prefix == it }
            isNameAcceptor       = [nonePrefix, nameShort, name].any { prefix == it }
            isOwnerAcceptor      = [nonePrefix, ownerShort, owner].any { prefix == it }
            isSeedAcceptor       = prefix == seed
            isStateAcceptor      = [nonePrefix, stateShort, state].any { prefix == it }
            isTagAcceptor        = [nonePrefix, tagShort, tag].any { prefix == it }
        }
    }
    private void initRejectorPredicates(){

        if(isRejectorObj == null){
            throw new IllegalStateException("initRejectorPredicates called before isRejectorObj set..")
        } else if(isRejectorObj == TRUE){
            isDbIdRejector      = [dbId].any { prefix == it }
            isDealIdRejector    = [dealIdShort, dealId].any { prefix == it }
            isDealNameRejector  = [dealNameShort, dealName].any { prefix == it }
            isDealTagRejector   = [dealTagShort, dealTag].any { prefix == it }
            isIterationsRejector =[iterationsShort, iterations].any { prefix == it }
            isNameRejector      = [nameShort, name].any { prefix == it }
            isOwnerRejector     = [ownerShort, owner].any { prefix == it }
            isSeedRejector      = [seed].any { prefix == it }
            isStateRejector     = [stateShort, state].any { prefix == it }
            isTagRejector       = [tagShort, tag].any { prefix == it }
        }
    }
    private void initEqualsPredicates(){

        if(isEqualsObj == null){
            throw new IllegalStateException("initEqualsPredicates called before isEqualsObj set..")
        } else if(isEqualsObj == TRUE){
            isDbIdEqualsOp      = [dbId].any { prefix == it }
            isDealIdEqualsOp    = [dealIdShort, dealId].any { prefix == it }
            isDealNameEqualsOp  = [dealNameShort, dealName].any { prefix == it }
            isDealTagEqualsOp   = [dealTagShort, dealTag].any { prefix == it }
            isIterationsEqualsOp =[iterationsShort, iterations].any { prefix == it }
            isNameEqualsOp      = [nameShort, name].any { prefix == it }
            isOwnerEqualsOp     = [ownerShort, owner].any { prefix == it }
            isSeedEqualsOp      = [seed].any { prefix == it }
            isStateEqualsOp     = [stateShort, state].any { prefix == it }
            isTagEqualsOp       = [tagShort, tag].any { prefix == it }
        }
    }
    private void initNotEqualsPredicates(){

        if(isNotEqualsObj == null){
            throw new IllegalStateException("initNotEqualsPredicates called before isNotEqualsObj set..")
        } else if(isNotEqualsObj == TRUE){
            isDealIdNotEqualsOp     = [dealIdShort, dealId].any { prefix == it }
            isDealNameNotEqualsOp   = [dealNameShort, dealName].any { prefix == it }
            isDealTagNotEqualsOp    = [dealTagShort, dealTag].any { prefix == it }
            isNameNotEqualsOp       = [nameShort, name].any { prefix == it }
            isOwnerNotEqualsOp      = [ownerShort, owner].any { prefix == it }
            isStateNotEqualsOp      = [stateShort, state].any { prefix == it }
            isTagNotEqualsOp        = [tagShort, tag].any { prefix == it }
            isSeedNotEqualsOp       = [seed].any { prefix == it }
            isDbIdNotEqualsOp       = [dbId].any { prefix == it }
            isIterationsNotEqualsOp = [iterationsShort, iterations].any { prefix == it }
        }
    }

    boolean isNameMatcher       = false
    boolean isStateMatcher      = false
    boolean isOwnerMatcher      = false
    boolean isTagMatcher        = false
    boolean isDbIdMatcher       = false
    boolean isDealNameMatcher   = false
    boolean isDealIdMatcher     = false
    boolean isDealTagMatcher    = false

    private void setClassifiers(){
        isNameMatcher  =  isNameAcceptor || isNameRejector || isNameEqualsOp || isNameNotEqualsOp 
        isStateMatcher = isStateAcceptor || isStateRejector || isStateEqualsOp || isStateNotEqualsOp 
        isOwnerMatcher = isOwnerAcceptor || isOwnerRejector || isOwnerEqualsOp || isOwnerNotEqualsOp 
        isTagMatcher   = isTagAcceptor || isTagRejector || isTagEqualsOp || isTagNotEqualsOp 
        isDbIdMatcher  = isDbIdAcceptor || isDbIdRejector || isDbIdEqualsOp || isDbIdNotEqualsOp
        isDealIdMatcher= isDealIdAcceptor || isDealIdRejector || isDealIdEqualsOp || isDealIdNotEqualsOp
        isDealNameMatcher = isDealNameAcceptor || isDealNameRejector || isDealNameEqualsOp || isDealNameNotEqualsOp
        isDealTagMatcher = isDealTagAcceptor || isDealTagRejector || isDealTagEqualsOp || isDealTagNotEqualsOp
    }

    // In the ctor we :
    // Note presence of any [!:=] directives within term
    // Note any recognised prefix
    // Note the text value to be matched against modeling items
    //
    // Additionally to squeeze the last drops of performance out we set various predicates
    // as soon as we are in a position to decide, here in the ctor, to save repeated checks
    // when items are matched
    //
    MatchTerm(String term){
        long t;
        if (CacheItemSearchService.PROFILE_CACHE_FILTERING) {
            t = System.currentTimeMillis()
        }

        original    = term
        colonIndex  = term.indexOf(COLON);
        equalsIndex = term.indexOf(EQUALS);
        bangIndex   = term.indexOf(FILTER_NEGATION);

        // if both := present, EARLIER wins, LATER becomes part of the 'text'
        //
        if( (colonIndex != MISSING && equalsIndex != MISSING)  ) {
            LOG.info("Analysing: '${term}' has both colon AND equals")
            if(colonIndex < equalsIndex){
                setEqualsMissing()
            }else if(equalsIndex < colonIndex){
                colonIndex = MISSING
                knowEqualsPresent()
            }else{
                String e = "Insanity! colonIndex = equalsIndex (=$colonIndex)"
                LOG.error(e)
                throw new IllegalStateException(e)
            }
        }

        if( (colonIndex == MISSING && equalsIndex == MISSING)  ) { // not a column-specific term
            setBangMissing()                        // no prefix - ensure any bang in term not treated as a negation
            setEqualsMissing()                      // set any sure predicates
            prefix = nonePrefix;
            text = term;
        }else{
            //
            //
            //
            if(bangIndex != MISSING){
                if(  (colonIndex != MISSING  &&  colonIndex  < bangIndex) ||    // : precedes !
                     (equalsIndex != MISSING &&  equalsIndex < bangIndex)    ){ // = precedes !
                    setBangMissing()                // no prefix - ensure any bang in term not treated as a negation
                }
            }

            //Column prefix sits (with any bang) before the colon/equals (only one of which is present)
            //
            String found = (colonIndex != MISSING)  ? term.substring(0, colonIndex).trim()  //+ COLON
                                                    : term.substring(0, equalsIndex).trim() //+ EQUALS
                                                    ;
            String squished = found.replaceAll("\\s*",'');
            if(bangIndex != MISSING){
                knowBangPresent()                // set any sure predicates

                //Remove the ! before trying to lookup a recognised prefix
                //
                squished = squished.replace(FILTER_NEGATION,'');
            }

            String lookup = columnFilterPrefixes.get(squished.toUpperCase())

            if( !lookup ){
                LOG.info("Weird match term: '${term}' - UNKNOWN PREFIX '$found' - treat as lacking prefix")
                setBangMissing()                        // no prefix - ensure any bang in term not treated as a negation
                setEqualsMissing()                      // set any sure predicates
                prefix = nonePrefix;
                text = term
            }else{
                prefix = lookup
                text = (colonIndex != MISSING)  ? term.substring(colonIndex+1).trim()
                                                : term.substring(equalsIndex+1).trim()
                                                ;
            }
        }
        initBasePredicates()                       // we're done here
        initAcceptorPredicates()
        initRejectorPredicates()
        initEqualsPredicates()
        initNotEqualsPredicates()
        setClassifiers()

        if (CacheItemSearchService.PROFILE_CACHE_FILTERING) {
            t = System.currentTimeMillis() - t
            LOG.info("Timed " + t + " ms: Parsing '$original'");
        }

    }

}


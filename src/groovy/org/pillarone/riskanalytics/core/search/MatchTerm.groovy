package org.pillarone.riskanalytics.core.search

import org.apache.commons.lang.StringUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.pillarone.riskanalytics.core.modellingitem.ParameterizationCacheItem

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
        LOG.error("Landed in MatchTerm.getDealName()")
        return ""
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
        name+EQUALS,            
        name+COLON,
        nameShort+EQUALS,       
        nameShort+COLON,
        owner+EQUALS,           
        owner+COLON,
        ownerShort+EQUALS,      
        ownerShort+COLON,
        state+EQUALS,           
        state+COLON,
        stateShort+EQUALS,      
        stateShort+COLON,
        tag+EQUALS,             
        tag+COLON,
        tagShort+EQUALS,        
        tagShort+COLON,

        dealId+EQUALS,          
        dealId+COLON,
        dealIdShort+EQUALS,     
        dealIdShort+COLON,
        dealName+EQUALS,        
        dealName+COLON,
        dealNameShort+EQUALS,   
        dealNameShort+COLON,
        dealTag+EQUALS,         
        dealTag+COLON,
        dealTagShort+EQUALS,    
        dealTagShort+COLON,
        iterations+EQUALS,      
        iterations+COLON,
        iterationsShort+EQUALS, 
        iterationsShort+COLON,

        dbId+EQUALS,            
        dbId+COLON,
        seed+EQUALS,            
        seed+COLON,
    ].collectEntries{ [ (it) : (it) ] }

    static final int MISSING = -1;

    final String prefix;
    final String text;
    final int bangIndex;
    final int colonIndex;
    final int equalsIndex;

    // In the ctor we :
    // Note presence of any [!:=] directives within term
    // Note any recognised prefix
    // Note the text value to be matched against modeling items
    //
    MatchTerm(String term){

        colonIndex  = term.indexOf(COLON);
        equalsIndex = term.indexOf(EQUALS);
        bangIndex   = term.indexOf(FILTER_NEGATION);

        // if both := present, EARLIER wins, LATER becomes part of the 'text'
        //
        if( (colonIndex != MISSING && equalsIndex != MISSING)  ) {
            LOG.info("Analysing: '${term}' has both colon AND equals")
            if(colonIndex < equalsIndex){
                equalsIndex = MISSING
            }else if(equalsIndex < colonIndex){
                colonIndex = MISSING
            }else{
                String e = "Insanity! colonIndex = equalsIndex (=$colonIndex)"
                LOG.error(e)
                throw new IllegalStateException(e)
            }
        }

        if( (colonIndex == MISSING && equalsIndex == MISSING)  ) { // not a column-specific term
            bangIndex = MISSING                 // no prefix - ensure any bang in term not treated as a negation
            prefix = nonePrefix;
            text = term;
            int debugMe = 0;
        }else{
            //
            //
            //
            if(bangIndex != MISSING){
                if(  (colonIndex != MISSING  &&  colonIndex  < bangIndex) ||    // : precedes !
                     (equalsIndex != MISSING &&  equalsIndex < bangIndex)    ){ // = precedes !
                    bangIndex = MISSING          // not part of prefix - ensure bang not treated as a negation
                }
            }

            //Column prefix sits (with any bang) before the colon/equals (only one of which is present)
            //
            String found = (colonIndex != MISSING)  ? term.substring(0, colonIndex).trim()  + COLON
                                                    : term.substring(0, equalsIndex).trim() + EQUALS
                                                    ;
            String squished = found.replace(FILTER_NEGATION,'').replaceAll("\\s*",'');

            String lookup = columnFilterPrefixes.get(squished.toUpperCase())

            if( !lookup ){
                LOG.info("Weird match term: '${term}' - UNKNOWN PREFIX '$found' - treat as lacking prefix")
                prefix = nonePrefix;
                text = term
            }else{
                prefix = lookup
                text = (colonIndex != MISSING)  ? term.substring(colonIndex+1).trim()
                                                : term.substring(equalsIndex+1).trim()
                                                ;
                boolean debugMe = false;
            }
        }

    }

    //Acceptor terms either have no prefix or prefix matches the column in question and ends in ':'
    //
    private boolean isAcceptor(final ArrayList<String> values) {
        if (bangIndex != MISSING) {
            return false
        }
        if (equalsIndex != MISSING){ // checking for = here is not a mistake
            return false
        }
        return values.any { prefix == (it.length() ? it+COLON : it)} // isAcceptor special (could be fed nonePrefix)
    }

    // Rejector terms begin with "!", match the column in question, and end in ':'
    //
    private boolean isRejector(final ArrayList<String> values) {
        if (bangIndex == MISSING) {
            return false
        }
        if (equalsIndex != MISSING){ // checking for equalsIndex here is not a mistake
            return false
        }
        return values.any { prefix == it+COLON }
    }

    //EqualsOp terms have prefix matches a column in question and ends in '='
    //
    private boolean isEqualsOp(final ArrayList<String> values) {
        if (bangIndex != MISSING) {
            return false
        }
        if (equalsIndex == MISSING){
            return false
        }
        return values.any { prefix == it+EQUALS}
    }

    // Not-equals terms begin with "!", match the column in question, and end in '='
    //
    private boolean isNotEqualsOp(final ArrayList<String> values) {
        if (bangIndex == MISSING) {
            return false
        }
        if (equalsIndex == MISSING){
            return false
        }
        return values.any { prefix == it+EQUALS}
    }

    boolean isDealIdAcceptor() {
        isAcceptor( [nonePrefix, dealIdShort, dealId] )
    }

    boolean isDealNameAcceptor() {
        isAcceptor( [dealNameShort, dealName] )
    }

    boolean isDealTagAcceptor() {
        // don't include nonePrefix here
        // else serach terms not prefixed with 'dealtag' / 'dt' will trigger deal tag search
        //
        isAcceptor( [dealTagShort, dealTag] )
    }

    boolean isNameAcceptor() {
        isAcceptor( [nonePrefix, nameShort, name] )
    }

    boolean isOwnerAcceptor() {
        isAcceptor( [nonePrefix, ownerShort, owner] )
    }

    boolean isStateAcceptor() {
        isAcceptor( [nonePrefix, stateShort, state] )
    }

    boolean isTagAcceptor() {
        isAcceptor( [nonePrefix, tagShort, tag] )
    }

    boolean isSeedAcceptor() {
        isAcceptor( [seed] )
    }

    boolean isDbIdAcceptor() {
        isAcceptor( [dbId] )
    }

    boolean isIterationsAcceptor() {
        isAcceptor( [iterationsShort, iterations] )
    }

//================= REJECTORS ==========================

    boolean isDealIdRejector() {
        isRejector( [dealIdShort, dealId] )
    }

    boolean isDealNameRejector() {
        isRejector( [dealNameShort, dealName] )
    }

    boolean isDealTagRejector() {
        isRejector( [dealTagShort, dealTag] )
    }

    boolean isNameRejector() {
        isRejector( [nameShort, name] )
    }

    boolean isOwnerRejector() {
        isRejector( [ownerShort, owner] )
    }

    boolean isStateRejector() {
        isRejector( [stateShort, state] )
    }

    boolean isTagRejector() {
        isRejector( [tagShort, tag] )
    }

    boolean isSeedRejector() {
        isRejector( [seed] )
    }

    boolean isDbIdRejector() {
        isRejector( [dbId] )
    }

    boolean isIterationsRejector() {
        isRejector( [iterationsShort, iterations] )
    }

//  ============================================================

    boolean isDealIdEqualsOp() {
        isEqualsOp( [dealIdShort, dealId] )
    }

    boolean isDealNameEqualsOp() {
        isEqualsOp( [dealNameShort, dealName] )
    }

    boolean isDealTagEqualsOp() {
        isEqualsOp( [dealTagShort, dealTag] )
    }

    boolean isNameEqualsOp() {
        isEqualsOp( [nameShort, name] )
    }

    boolean isOwnerEqualsOp() {
        isEqualsOp( [ownerShort, owner] )
    }

    boolean isStateEqualsOp() {
        isEqualsOp( [stateShort, state] )
    }

    boolean isTagEqualsOp() {
        isEqualsOp( [tagShort, tag] )
    }

    boolean isSeedEqualsOp() {
        isEqualsOp( [seed] )
    }

    boolean isDbIdEqualsOp() {
        isEqualsOp( [dbId] )
    }

    boolean isIterationsEqualsOp() {
        isEqualsOp( [iterationsShort, iterations] )
    }
    
//  ===========================================================

     boolean isDealIdNotEqualsOp() {
        isNotEqualsOp( [dealIdShort, dealId] )
    }

     boolean isDealNameNotEqualsOp() {
        isNotEqualsOp( [dealNameShort, dealName] )
    }

     boolean isDealTagNotEqualsOp() {
        isNotEqualsOp( [dealTagShort, dealTag] )
    }

     boolean isNameNotEqualsOp() {
        isNotEqualsOp( [nameShort, name] )
    }

     boolean isOwnerNotEqualsOp() {
        isNotEqualsOp( [ownerShort, owner] )
    }

     boolean isStateNotEqualsOp() {
        isNotEqualsOp( [stateShort, state] )
    }

     boolean isTagNotEqualsOp() {
        isNotEqualsOp( [tagShort, tag] )
    }

     boolean isSeedNotEqualsOp() {
        isNotEqualsOp( [seed] )
    }

     boolean isDbIdNotEqualsOp() {
        isNotEqualsOp( [dbId] )
    }

     boolean isIterationsNotEqualsOp() {
        isNotEqualsOp( [iterationsShort, iterations] )
    }
    
}


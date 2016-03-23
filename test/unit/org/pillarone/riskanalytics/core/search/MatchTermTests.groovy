package org.pillarone.riskanalytics.core.search

class MatchTermTests extends GroovyTestCase {

    AllFieldsFilter filterUnderTest = new AllFieldsFilter();


    void testNonColumnSpecificTerm(){

        String text = "simple text match"
        MatchTerm t = new MatchTerm(text)

        assert t.prefix == ""
        assert t.text == text

        assert t.equalsIndex == MatchTerm.MISSING
        assert t.bangIndex == MatchTerm.MISSING
        assert t.colonIndex == MatchTerm.MISSING

        assert t.isNameAcceptor()
        assert t.isTagAcceptor()
        assert t.isStateAcceptor()
        assert t.isOwnerAcceptor()
        assert t.isDealIdAcceptor()

        assert ! t.isDbIdAcceptor()
        assert ! t.isDbIdEqualsOp()
        assert ! t.isDbIdNotEqualsOp()
        assert ! t.isDbIdRejector()
        assert ! t.isDealIdEqualsOp()
        assert ! t.isDealIdNotEqualsOp()
        assert ! t.isDealIdRejector()
        assert ! t.isDealNameAcceptor()
        assert ! t.isDealNameEqualsOp()
        assert ! t.isDealNameNotEqualsOp()
        assert ! t.isDealNameRejector()
        assert ! t.isDealTagAcceptor()
        assert ! t.isDealTagEqualsOp()
        assert ! t.isDealTagNotEqualsOp()
        assert ! t.isDealTagRejector()
        assert ! t.isIterationsAcceptor()
        assert ! t.isIterationsEqualsOp()
        assert ! t.isIterationsNotEqualsOp()
        assert ! t.isIterationsRejector()
        assert ! t.isNameEqualsOp()
        assert ! t.isNameNotEqualsOp()
        assert ! t.isNameRejector()
        assert ! t.isOwnerEqualsOp()
        assert ! t.isOwnerNotEqualsOp()
        assert ! t.isOwnerRejector()
        assert ! t.isSeedAcceptor()
        assert ! t.isSeedEqualsOp()
        assert ! t.isSeedNotEqualsOp()
        assert ! t.isSeedRejector()
        assert ! t.isStateEqualsOp()
        assert ! t.isStateNotEqualsOp()
        assert ! t.isStateRejector()
        assert ! t.isTagEqualsOp()
        assert ! t.isTagNotEqualsOp()
        assert ! t.isTagRejector()
    }

    void testNameLikeTerm(){

        String prefix = "name:"
        String text = "What's my name?"
        MatchTerm t = new MatchTerm(prefix+text)

        assert t.prefix == MatchTerm.name + MatchTerm.COLON
        assert t.text == text

        assert t.equalsIndex == MatchTerm.MISSING
        assert t.bangIndex == MatchTerm.MISSING
        assert t.colonIndex != MatchTerm.MISSING

        assert t.isNameAcceptor()
        assert !t.isTagAcceptor()
        assert !t.isStateAcceptor()
        assert !t.isOwnerAcceptor()

        assert ! t.isDbIdAcceptor()
        assert ! t.isDbIdEqualsOp()
        assert ! t.isDbIdNotEqualsOp()
        assert ! t.isDbIdRejector()
        assert ! t.isDealIdAcceptor()
        assert ! t.isDealIdEqualsOp()
        assert ! t.isDealIdNotEqualsOp()
        assert ! t.isDealIdRejector()
        assert ! t.isDealNameAcceptor()
        assert ! t.isDealNameEqualsOp()
        assert ! t.isDealNameNotEqualsOp()
        assert ! t.isDealNameRejector()
        assert ! t.isDealTagAcceptor()
        assert ! t.isDealTagEqualsOp()
        assert ! t.isDealTagNotEqualsOp()
        assert ! t.isDealTagRejector()
        assert ! t.isIterationsAcceptor()
        assert ! t.isIterationsEqualsOp()
        assert ! t.isIterationsNotEqualsOp()
        assert ! t.isIterationsRejector()
        assert ! t.isNameEqualsOp()
        assert ! t.isNameNotEqualsOp()
        assert ! t.isNameRejector()
        assert ! t.isOwnerEqualsOp()
        assert ! t.isOwnerNotEqualsOp()
        assert ! t.isOwnerRejector()
        assert ! t.isSeedAcceptor()
        assert ! t.isSeedEqualsOp()
        assert ! t.isSeedNotEqualsOp()
        assert ! t.isSeedRejector()
        assert ! t.isStateEqualsOp()
        assert ! t.isStateNotEqualsOp()
        assert ! t.isStateRejector()
        assert ! t.isTagEqualsOp()
        assert ! t.isTagNotEqualsOp()
        assert ! t.isTagRejector()

    }

    void testNameNotLikeTerm(){

        String text = "What's my name?"
        MatchTerm t = new MatchTerm("!name:" + text)
        assert t.prefix == MatchTerm.name + MatchTerm.COLON
        assert t.text == text
        checkNameNotLikeTerm(t)

        t = new MatchTerm("! name :" + text)
        assert t.prefix == MatchTerm.name + MatchTerm.COLON
        assert t.text == text
        checkNameNotLikeTerm(t)

        t = new MatchTerm("!  name: " + text)
        assert t.prefix == MatchTerm.name + MatchTerm.COLON
        assert t.text == text
        checkNameNotLikeTerm(t)

        t = new MatchTerm("name  !  :  " + text)
        assert t.prefix == MatchTerm.name + MatchTerm.COLON
        assert t.text == text
        checkNameNotLikeTerm(t)

        t = new MatchTerm("name!  :  " + text)
        assert t.prefix == MatchTerm.name + MatchTerm.COLON
        assert t.text == text
        checkNameNotLikeTerm(t)

        t = new MatchTerm("name !:  " + text)
        assert t.prefix == MatchTerm.name + MatchTerm.COLON
        assert t.text == text
        checkNameNotLikeTerm(t)
    }
    static void checkNameNotLikeTerm(MatchTerm t){

        assert t.equalsIndex == MatchTerm.MISSING
        assert t.bangIndex != MatchTerm.MISSING
        assert t.colonIndex != MatchTerm.MISSING

        assert  t.isNameRejector()

        assert !t.isNameAcceptor()
        assert !t.isTagAcceptor()
        assert !t.isStateAcceptor()
        assert !t.isOwnerAcceptor()

        assert ! t.isDbIdAcceptor()
        assert ! t.isDbIdEqualsOp()
        assert ! t.isDbIdNotEqualsOp()
        assert ! t.isDbIdRejector()
        assert ! t.isDealIdAcceptor()
        assert ! t.isDealIdEqualsOp()
        assert ! t.isDealIdNotEqualsOp()
        assert ! t.isDealIdRejector()
        assert ! t.isDealNameAcceptor()
        assert ! t.isDealNameEqualsOp()
        assert ! t.isDealNameNotEqualsOp()
        assert ! t.isDealNameRejector()
        assert ! t.isDealTagAcceptor()
        assert ! t.isDealTagEqualsOp()
        assert ! t.isDealTagNotEqualsOp()
        assert ! t.isDealTagRejector()
        assert ! t.isIterationsAcceptor()
        assert ! t.isIterationsEqualsOp()
        assert ! t.isIterationsNotEqualsOp()
        assert ! t.isIterationsRejector()
        assert ! t.isNameEqualsOp()
        assert ! t.isNameNotEqualsOp()
        assert ! t.isOwnerEqualsOp()
        assert ! t.isOwnerNotEqualsOp()
        assert ! t.isOwnerRejector()
        assert ! t.isSeedAcceptor()
        assert ! t.isSeedEqualsOp()
        assert ! t.isSeedNotEqualsOp()
        assert ! t.isSeedRejector()
        assert ! t.isStateEqualsOp()
        assert ! t.isStateNotEqualsOp()
        assert ! t.isStateRejector()
        assert ! t.isTagEqualsOp()
        assert ! t.isTagNotEqualsOp()
        assert ! t.isTagRejector()
    }

    void testNameEqualsTerm(){

        String prefix = "n="
        String text = "What's my name?"
        MatchTerm t = new MatchTerm(prefix+text)

        assert t.prefix == MatchTerm.nameShort + MatchTerm.EQUALS
        assert t.text == text

        checkNameEqualsTerm(t)

    }
    static void checkNameEqualsTerm(MatchTerm t){

        assert t.equalsIndex != MatchTerm.MISSING
        assert t.bangIndex == MatchTerm.MISSING
        assert t.colonIndex == MatchTerm.MISSING

        assert !t.isTagAcceptor()
        assert !t.isStateAcceptor()
        assert !t.isOwnerAcceptor()

        assert ! t.isDbIdAcceptor()
        assert ! t.isDbIdEqualsOp()
        assert ! t.isDbIdNotEqualsOp()
        assert ! t.isDbIdRejector()
        assert ! t.isDealIdAcceptor()
        assert ! t.isDealIdEqualsOp()
        assert ! t.isDealIdNotEqualsOp()
        assert ! t.isDealIdRejector()
        assert ! t.isDealNameAcceptor()
        assert ! t.isDealNameEqualsOp()
        assert ! t.isDealNameNotEqualsOp()
        assert ! t.isDealNameRejector()
        assert ! t.isDealTagAcceptor()
        assert ! t.isDealTagEqualsOp()
        assert ! t.isDealTagNotEqualsOp()
        assert ! t.isDealTagRejector()
        assert ! t.isIterationsAcceptor()
        assert ! t.isIterationsEqualsOp()
        assert ! t.isIterationsNotEqualsOp()
        assert ! t.isIterationsRejector()
        assert ! t.isNameAcceptor()
        assert ! t.isNameRejector()
        assert t.isNameEqualsOp()
        assert ! t.isNameNotEqualsOp()
        assert ! t.isOwnerEqualsOp()
        assert ! t.isOwnerNotEqualsOp()
        assert ! t.isOwnerRejector()
        assert ! t.isSeedAcceptor()
        assert ! t.isSeedEqualsOp()
        assert ! t.isSeedNotEqualsOp()
        assert ! t.isSeedRejector()
        assert ! t.isStateEqualsOp()
        assert ! t.isStateNotEqualsOp()
        assert ! t.isStateRejector()
        assert ! t.isTagEqualsOp()
        assert ! t.isTagNotEqualsOp()
        assert ! t.isTagRejector()
    }

    void testNameNotEqualTerm(){

        String scooby = "Scooby Doo, Where Are You?"
        MatchTerm t = new MatchTerm("!name=" + scooby)
        assert t.prefix == MatchTerm.name + MatchTerm.EQUALS
        assert t.text == scooby
        checkNameNotEqualTerm(t)

        t = new MatchTerm("! name =" + scooby)
        assert t.prefix == MatchTerm.name + MatchTerm.EQUALS
        assert t.text == scooby
        checkNameNotEqualTerm(t)

        t = new MatchTerm("!  name= " + scooby)
        assert t.prefix == MatchTerm.name + MatchTerm.EQUALS
        assert t.text == scooby
        checkNameNotEqualTerm(t)

        t = new MatchTerm("name!=" + scooby)
        assert t.prefix == MatchTerm.name + MatchTerm.EQUALS
        assert t.text == scooby
        checkNameNotEqualTerm(t)

        t = new MatchTerm("name  !  =  " + scooby)
        assert t.prefix == MatchTerm.name + MatchTerm.EQUALS
        assert t.text == scooby
        checkNameNotEqualTerm(t)

        t = new MatchTerm("name!  =  " + scooby)
        assert t.prefix == MatchTerm.name + MatchTerm.EQUALS
        assert t.text == scooby
        checkNameNotEqualTerm(t)

        t = new MatchTerm("na!me =  " + scooby)
        assert t.prefix == MatchTerm.name + MatchTerm.EQUALS
        assert t.text == scooby
        checkNameNotEqualTerm(t)
    }
    static void checkNameNotEqualTerm(MatchTerm t){

        assert t.equalsIndex != MatchTerm.MISSING
        assert t.bangIndex != MatchTerm.MISSING
        assert t.colonIndex == MatchTerm.MISSING

        assert t.isNameNotEqualsOp()

        assert !t.isTagAcceptor()
        assert !t.isStateAcceptor()
        assert !t.isOwnerAcceptor()

        assert ! t.isDbIdAcceptor()
        assert ! t.isDbIdEqualsOp()
        assert ! t.isDbIdNotEqualsOp()
        assert ! t.isDbIdRejector()
        assert ! t.isDealIdAcceptor()
        assert ! t.isDealIdEqualsOp()
        assert ! t.isDealIdNotEqualsOp()
        assert ! t.isDealIdRejector()
        assert ! t.isDealNameAcceptor()
        assert ! t.isDealNameEqualsOp()
        assert ! t.isDealNameNotEqualsOp()
        assert ! t.isDealNameRejector()
        assert ! t.isDealTagAcceptor()
        assert ! t.isDealTagEqualsOp()
        assert ! t.isDealTagNotEqualsOp()
        assert ! t.isDealTagRejector()
        assert ! t.isIterationsAcceptor()
        assert ! t.isIterationsEqualsOp()
        assert ! t.isIterationsNotEqualsOp()
        assert ! t.isIterationsRejector()
        assert ! t.isNameAcceptor()
        assert ! t.isNameRejector()
        assert ! t.isNameEqualsOp()
        assert ! t.isOwnerEqualsOp()
        assert ! t.isOwnerNotEqualsOp()
        assert ! t.isOwnerRejector()
        assert ! t.isSeedAcceptor()
        assert ! t.isSeedEqualsOp()
        assert ! t.isSeedNotEqualsOp()
        assert ! t.isSeedRejector()
        assert ! t.isStateEqualsOp()
        assert ! t.isStateNotEqualsOp()
        assert ! t.isStateRejector()
        assert ! t.isTagEqualsOp()
        assert ! t.isTagNotEqualsOp()
        assert ! t.isTagRejector()
    }

    void testBangInDifferentPlaces(){

        String scooby = "Scooby Doo, Where Are You?"
        MatchTerm t = new MatchTerm("!name=" + scooby)
        assert t.prefix == MatchTerm.name + MatchTerm.EQUALS
        assert t.text == scooby
        checkNameNotEqualTerm(t)

        t = new MatchTerm("! name =" + scooby)
        assert t.prefix == MatchTerm.name + MatchTerm.EQUALS
        assert t.text == scooby
        checkNameNotEqualTerm(t)

        t = new MatchTerm("!  name= " + scooby)
        assert t.prefix == MatchTerm.name + MatchTerm.EQUALS
        assert t.text == scooby
        checkNameNotEqualTerm(t)

        t = new MatchTerm("name!=" + scooby)
        assert t.prefix == MatchTerm.name + MatchTerm.EQUALS
        assert t.text == scooby
        checkNameNotEqualTerm(t)

        t = new MatchTerm("name  !  =  " + scooby)
        assert t.prefix == MatchTerm.name + MatchTerm.EQUALS
        assert t.text == scooby
        checkNameNotEqualTerm(t)

        t = new MatchTerm("name!  =  " + scooby)
        assert t.prefix == MatchTerm.name + MatchTerm.EQUALS
        assert t.text == scooby
        checkNameNotEqualTerm(t)

        t = new MatchTerm("na!me =  " + scooby)
        assert t.prefix == MatchTerm.name + MatchTerm.EQUALS
        assert t.text == scooby
        checkNameNotEqualTerm(t)
    }

}

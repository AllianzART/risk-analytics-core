package org.pillarone.riskanalytics.core.parameter.comment

import org.pillarone.riskanalytics.core.simulation.item.parameter.comment.EnumTagType

class Tag {

    static final String LOCKED_TAG = 'LOCKED'
    public static final String azReTagMatcherRegex = System.getProperty("azReTagMatcherRegex", "^Allianz Re\$" )
    public static final String qtrTagMatcherRegex = System.getProperty("qtrTagMatcherRegex", "^1[1-9]Q[1234]\$" )

    String name
    EnumTagType tagType = EnumTagType.COMMENT

    String toString() {
        name
    }

    boolean equals(Object obj) {
        if (obj instanceof Tag) {
            return name.equals(obj.name)
        }
        return false
    }

    static constraints = {
        name(unique: true)
    }

    int hashCode() {
        return name.hashCode()
    }

    boolean isAZReTag(){
        return name.matches(azReTagMatcherRegex)
    }

    boolean isQuarterTag(){
        return name.matches(qtrTagMatcherRegex)
    }

    String getQuarter(){
        if(isQuarterTag()){
            return name.substring(2)
        }
        throw new IllegalStateException("Cannot get Qtr digits from non-Quarter tag '$name'")
    }

    String getYYDigits(){
        if(isQuarterTag()){
            return name.substring(0,2)
        }
        throw new IllegalStateException("Cannot get YY digits from non-Quarter tag '$name'")
    }
}

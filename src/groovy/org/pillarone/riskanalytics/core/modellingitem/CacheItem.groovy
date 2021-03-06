package org.pillarone.riskanalytics.core.modellingitem

import org.joda.time.DateTime
import org.pillarone.riskanalytics.core.simulation.item.VersionNumber
import org.pillarone.riskanalytics.core.user.Person

abstract class CacheItem {
    final Long id
    final DateTime creationDate
    final DateTime modificationDate
    final Person creator
    final Person lastUpdater
    final String name
    final Class modelClass

    CacheItem(Long id, DateTime creationDate, DateTime modificationDate, Person creator, Person lastUpdater, String name, Class modelClass) {
        if (!id) {
            throw new IllegalStateException('id must not be null')
        }
        this.id = id
        this.creationDate = creationDate
        this.modificationDate = modificationDate
        this.creator = creator
        this.lastUpdater = lastUpdater
        this.name = name
        this.modelClass = modelClass
    }

    // Template method. Selected subclasses have a VersionNumber.
    // But all subclasses have a getNameAndVersion() method.
    // :-)
    // Fixes AR-272 Allow searching items on full nameAndVersion
    //
    String getNameAndVersion() {
        if(getVersionNumber() != null){
            "$name v${versionNumber?.toString()}"
        }else{
            return name
        }
    }

    VersionNumber getVersionNumber(){
        return null
    }

    abstract Class getItemClass()

    final boolean equals(o) {
        if (this.is(o)) return true
        if (!(o instanceof CacheItem)) return false

        CacheItem that = (CacheItem) o

        if (id != that.id) return false
        if (itemClass != that.itemClass) return false

        return true
    }

    final int hashCode() {
        int result
        result = id.hashCode()
        result = 31 * result + itemClass.hashCode()
        return result
    }
}

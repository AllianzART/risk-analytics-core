package org.pillarone.riskanalytics.core.packets;

import java.util.Collection;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: palbini
 * Date: 23/06/15
 * Time: 11:36
 * To change this template use File | Settings | File Templates.
 */
public interface IAggregatableSummable { //Replace some methods in pc-cashflow/ClaimUtils.java. Minimal usage in this commit
                                         // - not a proper refactoring yet


    //both methods below would be better suited as static methods, but static methods are not suitable to interfaces
    //they'll have to be instance methods

    public IAggregatableSummable sum(Collection<IAggregatableSummable> homogeneousPacketCollection);

    public List<IAggregatableSummable> aggregateByBaseClaim(Collection<IAggregatableSummable> homogeneousPacketCollection);
    //note: actually the concept of BaseClaim vs KeyClaim as packet origins are distinct for CCP only (and not for CDP)
    //We'll handle this problem later...


}

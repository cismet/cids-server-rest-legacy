/***************************************************
*
* cismet GmbH, Saarbruecken, Germany
*
*              ... and it just works.
*
****************************************************/
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.cismet.cidsx.server.cores.legacy.custom;

import java.util.List;

import de.cismet.cidsx.server.cores.legacy.utils.json.SubscriptionResponse;

/**
 * DOCUMENT ME!
 *
 * @author   therter
 * @version  $Revision$, $Date$
 */
public interface CustomOfflineActionGrouper {

    //~ Methods ----------------------------------------------------------------

    /**
     * DOCUMENT ME!
     *
     * @param   actionList  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    List<List<SubscriptionResponse.Payload.Data.Action>> groupActions(
            List<SubscriptionResponse.Payload.Data.Action> actionList);
    /**
     * DOCUMENT ME!
     *
     * @param   action  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    boolean canHandleAction(SubscriptionResponse.Payload.Data.Action action);
}

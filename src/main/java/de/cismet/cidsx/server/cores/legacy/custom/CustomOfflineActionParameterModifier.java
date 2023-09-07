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

import de.cismet.cidsx.server.cores.legacy.utils.json.SubscriptionResponse;

/**
 * DOCUMENT ME!
 *
 * @author   therter
 * @version  $Revision$, $Date$
 */
public interface CustomOfflineActionParameterModifier {

    //~ Methods ----------------------------------------------------------------

    /**
     * DOCUMENT ME!
     *
     * @param   action  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    String modifyParameter(SubscriptionResponse.Payload.Data.Action action);

    /**
     * DOCUMENT ME!
     *
     * @param   action  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    String modifyBody(SubscriptionResponse.Payload.Data.Action action);

    /**
     * DOCUMENT ME!
     *
     * @param   action  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    boolean canHandleAction(SubscriptionResponse.Payload.Data.Action action);
}

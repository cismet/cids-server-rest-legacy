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

import lombok.extern.slf4j.Slf4j;

import de.cismet.cidsx.server.cores.legacy.utils.json.SubscriptionResponse;

/**
 * DOCUMENT ME!
 *
 * @author   therter
 * @version  $Revision$, $Date$
 */
//@ServiceProvider(service = CustomOfflineActionParameterModifier.class)
@Slf4j
public class VzkActionParameterModifier implements CustomOfflineActionParameterModifier {

    //~ Static fields/initializers ---------------------------------------------

    private static final String[] TASKNAMES = { "saveObject" };

    //~ Methods ----------------------------------------------------------------

    @Override
    public String modifyParameter(final SubscriptionResponse.Payload.Data.Action a) {
        return a.getParameter();
    }

    @Override
    public String modifyBody(final SubscriptionResponse.Payload.Data.Action a) {
        return a.getBody();
    }

    @Override
    public boolean canHandleAction(final SubscriptionResponse.Payload.Data.Action action) {
        for (final String taskname : TASKNAMES) {
            if (taskname.equals(action.getAction())) {
                return true;
            }
        }

        return false;
    }
}

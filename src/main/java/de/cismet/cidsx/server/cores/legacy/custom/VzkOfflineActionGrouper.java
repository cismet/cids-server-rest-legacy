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

import java.text.ParseException;
import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import de.cismet.cidsx.server.cores.legacy.utils.json.SubscriptionResponse;

/**
 * DOCUMENT ME!
 *
 * @author   therter
 * @version  $Revision$, $Date$
 */
//@ServiceProvider(service = CustomOfflineActionGrouper.class)
@Slf4j
public class VzkOfflineActionGrouper implements CustomOfflineActionGrouper {

    //~ Static fields/initializers ---------------------------------------------

    private static String[] TASKNAMES = { "saveObject" };

    //~ Methods ----------------------------------------------------------------

    @Override
    public List<List<SubscriptionResponse.Payload.Data.Action>> groupActions(
            final List<SubscriptionResponse.Payload.Data.Action> actionList) {
        final List<List<SubscriptionResponse.Payload.Data.Action>> groupedActions = new ArrayList<>();

        groupedActions.add(actionList);

        Collections.sort(actionList, new Comparator<SubscriptionResponse.Payload.Data.Action>() {

                @Override
                public int compare(final SubscriptionResponse.Payload.Data.Action o1,
                        final SubscriptionResponse.Payload.Data.Action o2) {
                    return toDate(o1.getCreatedAt()).compareTo(toDate(o2.getCreatedAt()));
                }

                private Date toDate(final String dateString) {
                    final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'H:m:s.SSSX");

                    try {
                        return formatter.parse(dateString);
                    } catch (ParseException e) {
                        log.warn("Cannot parse date: " + dateString);

                        return new Date();
                    }
                }
            });

        return groupedActions;
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

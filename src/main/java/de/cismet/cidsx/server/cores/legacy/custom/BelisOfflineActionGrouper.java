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

import org.openide.util.lookup.ServiceProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.cismet.cids.server.actions.ServerActionParameter;

import de.cismet.cidsx.server.cores.legacy.utils.OfflineActionExecutioner;
import de.cismet.cidsx.server.cores.legacy.utils.json.SubscriptionResponse;

/**
 * DOCUMENT ME!
 *
 * @author   therter
 * @version  $Revision$, $Date$
 */
@ServiceProvider(service = CustomOfflineActionGrouper.class)
public class BelisOfflineActionGrouper implements CustomOfflineActionGrouper {

    //~ Static fields/initializers ---------------------------------------------

    private static String[] TASKNAMES = {
            "addDocument",
            "BelisWebDavTunnelAction",
            "LockEntities",
            "addIncident",
            "uploadDocument",
            "protokollFortfuehrungsantrag",
            "protokollLeuchteLeuchtenerneuerung",
            "protokollLeuchteLeuchtmittelwechselElekpruefung",
            "protokollLeuchteLeuchtmittelwechsel",
            "protokollLeuchteRundsteuerempfaengerwechsel",
            "protokollLeuchteSonderturnus",
            "protokollLeuchteVorschaltgeraetwechsel",
            "protokollMauerlaschePruefung",
            "protokollSchaltstelleRevision",
            "protokollStandortAnstricharbeiten",
            "protokollStandortElektrischePruefung",
            "protokollStandortMasterneuerung",
            "protokollStandortRevision",
            "protokollStandortStandsicherheitspruefung",
            "protokollStatusAenderung"
        };

    //~ Methods ----------------------------------------------------------------

    @Override
    public List<List<SubscriptionResponse.Payload.Data.Action>> groupActions(
            final List<SubscriptionResponse.Payload.Data.Action> actionList) {
        final List<List<SubscriptionResponse.Payload.Data.Action>> groupedActions = new ArrayList<>();
        final Map<String, List<SubscriptionResponse.Payload.Data.Action>> actionsByProtocol = new HashMap<>();

        for (final SubscriptionResponse.Payload.Data.Action a : actionList) {
            final String parameter = a.getParameter();
            final List<ServerActionParameter> parameterList = OfflineActionExecutioner.convertParameters(parameter);
            String protocollId = null;

            for (final ServerActionParameter para : parameterList) {
                if (para.getKey().equalsIgnoreCase("PROTOKOLL_ID")) {
                    protocollId = String.valueOf(para.getValue());
                    break;
                }
            }

            List<SubscriptionResponse.Payload.Data.Action> list = actionsByProtocol.get(protocollId);

            if (list == null) {
                list = new ArrayList<>();
                actionsByProtocol.put(protocollId, list);
            }

            list.add(a);
        }

        // convert map to list
        for (final String key : actionsByProtocol.keySet()) {
            if (key == null) {
                for (final SubscriptionResponse.Payload.Data.Action tmp : actionsByProtocol.get(key)) {
                    final List<SubscriptionResponse.Payload.Data.Action> tmpList = new ArrayList<>();
                    tmpList.add(tmp);
                    groupedActions.add(tmpList);
                }
            } else {
                groupedActions.add(actionsByProtocol.get(key));
            }
        }

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

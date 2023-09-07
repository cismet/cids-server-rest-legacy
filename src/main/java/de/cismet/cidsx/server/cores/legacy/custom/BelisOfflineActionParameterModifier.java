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

import org.openide.util.lookup.ServiceProvider;

import de.cismet.cidsx.server.cores.legacy.utils.json.SubscriptionResponse;

/**
 * DOCUMENT ME!
 *
 * @author   therter
 * @version  $Revision$, $Date$
 */
@ServiceProvider(service = CustomOfflineActionParameterModifier.class)
@Slf4j
public class BelisOfflineActionParameterModifier implements CustomOfflineActionParameterModifier {

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
    public String modifyParameter(final SubscriptionResponse.Payload.Data.Action a) {
        String parameter = a.getParameter();

        if (parameter.indexOf("\"ImageData\":") > 0) {
            String tmp = parameter.substring(0, parameter.indexOf("\"ImageData\":") + "\"ImageData\":".length());
            tmp += "\"stripped\"";
            tmp += parameter.substring(parameter.indexOf(
                        "\"",
                        parameter.indexOf("\"", parameter.indexOf("\"ImageData\":") + "\"ImageData\":".length())
                                + 1) + 1);
            parameter = tmp;
        }

        return parameter;
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

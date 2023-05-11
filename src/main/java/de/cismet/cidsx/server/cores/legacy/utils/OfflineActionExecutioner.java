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
package de.cismet.cidsx.server.cores.legacy.utils;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

import org.openide.util.Lookup;

import java.rmi.RemoteException;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import de.cismet.cids.server.actions.ServerActionParameter;

import de.cismet.cidsx.server.api.tools.Tools;
import de.cismet.cidsx.server.api.types.User;
import de.cismet.cidsx.server.backend.legacy.LegacyCoreBackend;
import de.cismet.cidsx.server.cores.legacy.custom.CustomOfflineActionParameterModifier;
import de.cismet.cidsx.server.cores.legacy.utils.json.SubscriptionResponse;

/**
 * DOCUMENT ME!
 *
 * @author   therter
 * @version  $Revision$, $Date$
 */
@Slf4j
public class OfflineActionExecutioner implements Runnable {

    //~ Instance fields --------------------------------------------------------

    private final List<SubscriptionResponse.Payload.Data.Action> action;
    private final List<SubscriptionResponse.Payload.Data.Action> waitingAction = new ArrayList<>();
    private final String hasuraUrlString;
    private final String hasuraSecret;

    //~ Constructors -----------------------------------------------------------

    /**
     * Creates a new ActionExecutioner object.
     *
     * @param  action           DOCUMENT ME!
     * @param  hasuraUrlString  DOCUMENT ME!
     * @param  hasuraSecret     DOCUMENT ME!
     */
    public OfflineActionExecutioner(final List<SubscriptionResponse.Payload.Data.Action> action,
            final String hasuraUrlString,
            final String hasuraSecret) {
        this.action = action;
        this.hasuraUrlString = hasuraUrlString;
        this.hasuraSecret = hasuraSecret;
    }

    //~ Methods ----------------------------------------------------------------

    @Override
    public void run() {
        final HasuraHelper helper = new HasuraHelper(hasuraUrlString, hasuraSecret);
        waitingAction.addAll(action);
        int attempt = 0;
        final Collection<? extends CustomOfflineActionParameterModifier> modifier = Lookup.getDefault()
                    .lookupAll(CustomOfflineActionParameterModifier.class);

        while (!waitingAction.isEmpty() && (attempt <= 10)) {
            Collections.sort(waitingAction, new Comparator<SubscriptionResponse.Payload.Data.Action>() {

                    @Override
                    public int compare(final SubscriptionResponse.Payload.Data.Action o1,
                            final SubscriptionResponse.Payload.Data.Action o2) {
                        final Integer index1 = action.indexOf(o1);
                        final Integer index2 = action.indexOf(o2);

                        return index1.compareTo(index2);
                    }
                });

            ++attempt;

            final List<SubscriptionResponse.Payload.Data.Action> tmpList = new ArrayList<>();
            tmpList.addAll(waitingAction);
            waitingAction.clear();

            for (final SubscriptionResponse.Payload.Data.Action a : tmpList) {
                try {
                    final User user = Tools.validationHelper("Bearer " + a.getJwt());

                    if (Tools.canHazUserProblems(user)) {
                        // jwt invalid. The result in the db should be jwt invalid
                        helper.sendStatusUpdate(a, 401);
                        break;
                    }

                    helper.sendStatusUpdate(a, 202);

                    final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, null);
                    final String bodyString = a.getBody();
                    final String parameters = a.getParameter();
                    final boolean bodyUsedAsParameter = helper.isBodyUsedAsParameter(parameters);
                    final List<ServerActionParameter> parameterList = convertParameters(parameters);
                    byte[] body = null;

                    if (!bodyUsedAsParameter) {
                        if (bodyString != null) {
                            body = Base64.getDecoder().decode(bodyString);
                        }
                    }

                    if (log.isDebugEnabled()) {
                        log.debug("execute action " + a.getAction());
                    }

                    Object actionResult = null;
                    final ServerActionParameter[] saps = parameterList.toArray(new ServerActionParameter[0]);

                    try {
                        actionResult = LegacyCoreBackend.getInstance().getService()
                                    .executeTask(
                                            cidsUser,
                                            a.getAction(),
                                            cidsUser.getDomain(),
                                            body,
                                            LegacyCoreBackend.getInstance().getConnectionContext(),
                                            saps);
                    } catch (RemoteException e) {
                        helper.sendStatusResultUpdate(a, "{\"Exception\": \"" + e.getMessage() + "\"}", 500);

                        break;
                    }

                    // missing id as param
                    if ((actionResult instanceof Exception) && (((Exception)actionResult).getMessage() != null)
                                && ((Exception)actionResult).getMessage().equals(
                                    "A lock for the desired object is already existing")) {
                        waitingAction.add(a);
                        helper.sendStatusResultUpdate(
                            a,
                            "{\"Exception\": \""
                                    + ((Exception)actionResult).getMessage()
                                    + "\"}",
                            210
                                    + attempt);
                    } else if (actionResult instanceof Exception) {
                        log.warn("Exception returned from action " + a.getAction(), (Exception)actionResult);
                        if ((((Exception)actionResult).getMessage() != null)
                                    && ((Exception)actionResult).getMessage().equals("missing id as param")) {
                            log.error("missing id as param");
                        }
                        helper.sendStatusResultUpdate(
                            a,
                            "{\"Exception\": \""
                                    + ((Exception)actionResult).getMessage()
                                    + "\"}",
                            500);
                    } else if (actionResult != null) {
                        final CustomOfflineActionParameterModifier m = getModifier(modifier, a);

                        if (m != null) {
                            helper.sendUpdate(a, actionResult.toString(), m.modifyParameter(a), 200);
                        } else {
                            helper.sendStatusResultUpdate(a, actionResult.toString(), 200);
                        }
                    } else {
                        // The result in the db should be exception null invalid
                        final CustomOfflineActionParameterModifier m = getModifier(modifier, a);

                        if (m != null) {
                            helper.sendUpdate(a, null, m.modifyParameter(a), 200);
                        } else {
                            helper.sendStatusUpdate(a, 200);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error while executing action", e);
                }
            }

            if (!waitingAction.isEmpty()) {
                try {
                    Thread.sleep((long)Math.pow(2, (double)attempt));
                } catch (InterruptedException ex) {
                    // nothing to do
                }
            }
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param   modifier  DOCUMENT ME!
     * @param   a         DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    private CustomOfflineActionParameterModifier getModifier(
            final Collection<? extends CustomOfflineActionParameterModifier> modifier,
            final SubscriptionResponse.Payload.Data.Action a) {
        if (modifier != null) {
            for (final CustomOfflineActionParameterModifier m : modifier) {
                if (m.canHandleAction(a)) {
                    return m;
                }
            }
        }

        return null;
    }

    /**
     * DOCUMENT ME!
     *
     * @param   json  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public static List<ServerActionParameter> convertParameters(final String json) {
        final List<ServerActionParameter> cidsSAPs = new ArrayList<>();
        final ObjectMapper mapper = new ObjectMapper(new JsonFactory());

        try {
            final JsonNode node = mapper.readTree(json);
            final Iterator<Map.Entry<String, JsonNode>> it = node.fields();

            while (it.hasNext()) {
                final Map.Entry<String, JsonNode> n = it.next();

                if (n.getValue() instanceof ArrayNode) {
                    final ArrayNode array = (ArrayNode)n.getValue();
                    final ArrayList list = new ArrayList();

                    for (int i = 0; i < array.size(); ++i) {
                        if (array.get(i) instanceof ObjectNode) {
                            final HashMap<String, Object> map = new HashMap<>();

                            final ObjectNode oNode = (ObjectNode)array.get(i);

                            final Iterator<String> fields = oNode.fieldNames();
                            while (fields.hasNext()) {
                                final String field = fields.next();

                                final JsonNode subNode = oNode.get(field);
                                map.put(field, subNode.asText());
                            }

                            list.add(map);
                        }
                    }
                    final ServerActionParameter cidsSAP = new ServerActionParameter(n.getKey(),
                            list);
                    cidsSAPs.add(cidsSAP);
                } else {
                    final ServerActionParameter cidsSAP = new ServerActionParameter(n.getKey(),
                            n.getValue().asText());
                    cidsSAPs.add(cidsSAP);
                }
            }
        } catch (Exception e) {
            log.error("Error while parsing parameter: " + json, e);
        }

        return cidsSAPs;
    }
}

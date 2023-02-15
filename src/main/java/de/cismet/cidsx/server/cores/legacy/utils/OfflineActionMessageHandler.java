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
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import org.openide.util.Lookup;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import de.cismet.cids.utils.MetaClassCacheService;

import de.cismet.cidsx.server.cores.legacy.custom.CustomOfflineActionGrouper;
import de.cismet.cidsx.server.cores.legacy.utils.json.GraphQlQuery;
import de.cismet.cidsx.server.cores.legacy.utils.json.SubscriptionResponse;

/**
 * DOCUMENT ME!
 *
 * @author   therter
 * @version  $Revision$, $Date$
 */
@Slf4j
public class OfflineActionMessageHandler implements WebsocketClientEndpoint.MessageHandler {

    //~ Static fields/initializers ---------------------------------------------

    private static final int MILLIS_PER_SECOND = 1000;
    private static final String INIT_WEBSOCKET_QUERY =
        "{\"type\":\"connection_init\",\"payload\":{\"headers\":{\"X-Hasura-Admin-Secret\":\"%1s\"}}}";
    private static final String INIT_SUBSCRIPTION_QUERY =
        "{\"id\":\"1\",\"type\":\"start\",\"payload\":{\"query\":\"subscription onActionChanged "
                + "{action(where: {_and: {isCompleted: {_eq: false}, result: {_is_null: true}, status: {_is_null: true}}}) "
                + "{id jwt isCompleted applicationId createdAt updatedAt status action, result} }\"}}";

    //~ Instance fields --------------------------------------------------------

    private WebsocketClientEndpoint websocketClient = null;
    private ExecutorService executor;
    private final String hasuraUrlString;
    private final String hasuraSecret;
    private final Map<String, SubscriptionResponse.Payload.Data.Action> lastActions =
        new HashMap<String, SubscriptionResponse.Payload.Data.Action>();
    private final List<ConnectionStatusListener> statusListener = new ArrayList<>();

    //~ Constructors -----------------------------------------------------------

    /**
     * Creates a new CustomMessageHandler object.
     *
     * @param  executor         DOCUMENT ME!
     * @param  hasuraUrlString  DOCUMENT ME!
     * @param  hasuraSecret     DOCUMENT ME!
     */
    public OfflineActionMessageHandler(final ExecutorService executor,
            final String hasuraUrlString,
            final String hasuraSecret) {
        this.executor = executor;
        this.hasuraUrlString = hasuraUrlString;
        this.hasuraSecret = hasuraSecret;
    }

    //~ Methods ----------------------------------------------------------------

    /**
     * DOCUMENT ME!
     *
     * @param  listener  DOCUMENT ME!
     */
    public void addConnectionStatusListener(final ConnectionStatusListener listener) {
        statusListener.add(listener);
    }

    /**
     * DOCUMENT ME!
     *
     * @param  websocketClient  DOCUMENT ME!
     */
    public void addWebsocketClientEndpoint(final WebsocketClientEndpoint websocketClient) {
        this.websocketClient = websocketClient;
    }

    @Override
    public void handleMessage(final String message) {
        if (log.isDebugEnabled() && !message.equalsIgnoreCase("{\"type\":\"ka\"}")) {
            // do not log the keep alive messsages
            log.debug("retrieve message: " + message);
        }

        synchronized (lastActions) {
            try {
                final ObjectMapper mapper = new ObjectMapper(new JsonFactory());
                final HasuraHelper helper = new HasuraHelper(hasuraUrlString, hasuraSecret);
                final SubscriptionResponse response = mapper.readValue(message, SubscriptionResponse.class);

                if (response.getType().equals("data")) {
                    final Map<CustomOfflineActionGrouper, List<SubscriptionResponse.Payload.Data.Action>> groupedActions =
                        new HashMap<>();

                    for (final SubscriptionResponse.Payload.Data.Action action
                                : response.getPayload().getData().getAction()) {
                        final SubscriptionResponse.Payload.Data.Action lastState = lastActions.get(action.getId());

                        if ((lastState == null) || !lastState.equals(action)) {
                            lastActions.put(action.getId(), action);
                            if (((action.getStatus() == null)
                                            || ((action.getStatus() != 200) && (action.getStatus() != 202)
                                                && !((action.getStatus() >= 210) && (action.getStatus() <= 231))))) {
                                final Collection<? extends CustomOfflineActionGrouper> actionGrouper = Lookup
                                            .getDefault().lookupAll(CustomOfflineActionGrouper.class);
                                boolean grouperFound = false;

                                final String bodyString = helper.getBody(action.getId());
                                final String parameters = helper.getParameters(action.getId());

                                action.setParameter(helper.buildParametersString(parameters, bodyString));
                                action.setBody(bodyString);

                                for (final CustomOfflineActionGrouper grouper : actionGrouper) {
                                    if (grouper.canHandleAction(action)) {
                                        List<SubscriptionResponse.Payload.Data.Action> actionList = groupedActions.get(
                                                grouper);

                                        if (actionList == null) {
                                            actionList = new ArrayList<>();
                                            groupedActions.put(grouper, actionList);
                                        }

                                        actionList.add(action);
                                        grouperFound = true;
                                        break;
                                    }
                                }

                                if (!grouperFound) {
                                    List<SubscriptionResponse.Payload.Data.Action> actionList = groupedActions.get(
                                            null);

                                    if (actionList == null) {
                                        actionList = new ArrayList<>();
                                        groupedActions.put(null, actionList);
                                    }

                                    actionList.add(action);
                                }
                            }
                        }
                    }

                    List<List<SubscriptionResponse.Payload.Data.Action>> actionList = new ArrayList<>();

                    for (final CustomOfflineActionGrouper grouper : groupedActions.keySet()) {
                        if (grouper == null) {
                            for (final SubscriptionResponse.Payload.Data.Action action : groupedActions.get(null)) {
                                actionList.add(Arrays.asList(action));
                            }
                        } else {
                            final List<List<SubscriptionResponse.Payload.Data.Action>> grouperResult =
                                grouper.groupActions(groupedActions.get(grouper));

                            for (final List<SubscriptionResponse.Payload.Data.Action> list : grouperResult) {
                                actionList.add(list);
                            }
                        }
                    }

                    Collections.sort(actionList, new Comparator<List<SubscriptionResponse.Payload.Data.Action>>() {

                            @Override
                            public int compare(final List<SubscriptionResponse.Payload.Data.Action> o1,
                                    final List<SubscriptionResponse.Payload.Data.Action> o2) {
                                final SubscriptionResponse.Payload.Data.Action a1 = o1.get(0);
                                final SubscriptionResponse.Payload.Data.Action a2 = o2.get(0);

                                return toDate(a1.getCreatedAt()).compareTo(toDate(a2.getCreatedAt()));
                            }

                            private Date toDate(final String dateString) {
                                // todo pruefen, ob der Datestring wirklich geparst werden kann
                                final SimpleDateFormat formatter = new SimpleDateFormat();

                                try {
                                    return formatter.parse(dateString);
                                } catch (ParseException e) {
                                    log.warn("Cannot parse date: " + dateString);

                                    return new Date();
                                }
                            }
                        });

                    for (final List<SubscriptionResponse.Payload.Data.Action> actions : actionList) {
                        final OfflineActionExecutioner ae = new OfflineActionExecutioner(
                                actions,
                                hasuraUrlString,
                                hasuraSecret);

                        executor.submit(ae);
                    }
                } else if (response.getType().equals("error")) {
                    log.error("An error message was send:\n" + message);
                }
            } catch (Exception e) {
                log.error("Cannot handle hasura message", e);
            }
        }
    }

    @Override
    public void connectionOpened() {
        try {
            Thread.sleep(MILLIS_PER_SECOND);
        } catch (Exception e) {
            // nothing to do
        }
        if (log.isDebugEnabled()) {
            log.debug("connection is open");
        }

        websocketClient.sendMessage(String.format(INIT_WEBSOCKET_QUERY, hasuraSecret));
        websocketClient.sendMessage(INIT_SUBSCRIPTION_QUERY);
    }

    @Override
    public void connectionClosed() {
        for (final ConnectionStatusListener listener : statusListener) {
            listener.connectionClosed();
        }
    }

    /**
     * DOCUMENT ME!
     */
    public void dispose() {
        statusListener.clear();
        ;
    }

    //~ Inner Interfaces -------------------------------------------------------

    /**
     * DOCUMENT ME!
     *
     * @version  $Revision$, $Date$
     */
    public static interface ConnectionStatusListener {

        //~ Methods ------------------------------------------------------------

        /**
         * DOCUMENT ME!
         */
        void connectionOpened();
        /**
         * DOCUMENT ME!
         */
        void connectionClosed();
    }
}

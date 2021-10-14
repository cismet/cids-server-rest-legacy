/***************************************************
*
* cismet GmbH, Saarbruecken, Germany
*
*              ... and it just works.
*
****************************************************/
package de.cismet.cidsx.server.cores.legacy;

import Sirius.server.middleware.impls.domainserver.DomainServerImpl;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import org.openide.util.lookup.ServiceProvider;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import java.rmi.RemoteException;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import de.cismet.cids.server.actions.ServerActionParameter;

import de.cismet.cids.utils.serverresources.GeneralServerResources;
import de.cismet.cids.utils.serverresources.ServerResourcesLoader;

import de.cismet.cidsx.server.api.tools.Tools;
import de.cismet.cidsx.server.api.types.User;
import de.cismet.cidsx.server.backend.legacy.LegacyCoreBackend;
import de.cismet.cidsx.server.cores.CidsServerCore;
import de.cismet.cidsx.server.cores.InitialisableCore;
import de.cismet.cidsx.server.cores.legacy.utils.WebsocketClientEndpoint;
import de.cismet.cidsx.server.cores.legacy.utils.json.ActionExecutionServiceConfiguration;
import de.cismet.cidsx.server.cores.legacy.utils.json.GraphQlQuery;
import de.cismet.cidsx.server.cores.legacy.utils.json.SubscriptionResponse;
import de.cismet.cidsx.server.cores.legacy.utils.json.UpdateResult;

import de.cismet.commons.concurrency.CismetConcurrency;
import de.cismet.commons.concurrency.CismetExecutors;

import de.cismet.connectioncontext.AbstractConnectionContext;
import de.cismet.connectioncontext.ConnectionContext;

/**
 * DOCUMENT ME!
 *
 * @author   thorsten
 * @version  1.0
 */
@Slf4j
@ServiceProvider(service = CidsServerCore.class)
public class LegacyOfflineActionCore implements de.cismet.cidsx.server.cores.OfflineActionCore,
    InitialisableCore,
    Runnable {

    //~ Static fields/initializers ---------------------------------------------

    private static final ConnectionContext CC = ConnectionContext.create(
            AbstractConnectionContext.Category.OTHER,
            "ActionExecutionService");
    private static final int MILLIS_PER_SECOND = 1000;
    private static final int MIN_DURATION_BETWEEN_LOGGING = 5 * 60 * MILLIS_PER_SECOND;
    private static final String UPDATE_QUERY =
        "mutation UpdateActionResult {update_action(where: {id: {_eq: \"%1s\"}}, _set: {result: \"%2s\", updatedAt: \"now()\"}){affected_rows}}";
    private static final String STATUS_UPDATE_QUERY =
        "mutation UpdateActionStatus {update_action(where: {id: {_eq: \"%1s\"}}, _set: {status: %2s, updatedAt: \"now()\"}){affected_rows}}";
    private static final String STATUS_RESULT_UPDATE_QUERY =
        "mutation UpdateActionStatus {update_action(where: {id: {_eq: \"%1s\"}}, _set: {result: \"%2s\", status: %3s, updatedAt: \"now()\"}){affected_rows}}";

    //~ Instance fields --------------------------------------------------------

    private ExecutorService executor;
    private int maxParallelThreads = 10;
    private final Map<String, SubscriptionResponse.Payload.Data.Action> lastActions =
        new HashMap<String, SubscriptionResponse.Payload.Data.Action>();
    private boolean connectionOpen = false;
    private String pathServerResources;
    private long lastServerErrorLogging = 0;

    //~ Constructors -----------------------------------------------------------

    /**
     * Creates a new LegacyOfflineActionCore object.
     */
    public LegacyOfflineActionCore() {
    }

    //~ Methods ----------------------------------------------------------------

    @Override
    public void run() {
        Thread.currentThread().setName("ActionExecutionService");

        final ActionExecutionServiceConfiguration config = readConfig();

        if (config == null) {
            // The configuration file does not exist, so this service is not required
            return;
        }

        if (executor == null) {
            // if the executor was created once, changes in the config file will not affect it.

            if (config.getMaxParallelThreads() != null) {
                maxParallelThreads = config.getMaxParallelThreads();
            }

            executor = CismetExecutors.newFixedThreadPool(
                    maxParallelThreads,
                    CismetConcurrency.getInstance("CacheRefreshService").createThreadFactory(
                        "CacheRefreshService"));
        }

        do {
            try {
                final CustomMessageHandler handler = new CustomMessageHandler(
                        config.getServiceUrl(),
                        config.getHasuraSecret());
                final WebsocketClientEndpoint clientEndPoint = new WebsocketClientEndpoint(handler);

                handler.addWebsocketClientEndpoint(clientEndPoint);
                clientEndPoint.openConnection(new URI(config.getWebSocketUrl()));
                connectionOpen = true;
                lastServerErrorLogging = 0;

                while (connectionOpen) {
                    try {
                        Thread.sleep(MILLIS_PER_SECOND);
                    } catch (InterruptedException e) {
                        // nothing to do
                    }
                }
            } catch (Exception e) {
                // do not bloat the logging file, when the server is not available
                if ((System.currentTimeMillis() - lastServerErrorLogging) > MIN_DURATION_BETWEEN_LOGGING) {
                    log.error("WebSocketException. Retry to connect", e);
                    lastServerErrorLogging = System.currentTimeMillis();
                }

                try {
                    Thread.sleep(MILLIS_PER_SECOND);
                } catch (InterruptedException ex) {
                    // nothing to do
                }
            }
        } while (true);
    }

    /**
     * Reads the configuration file.
     *
     * @return  The configuration object or null, if the configuration file does not exist
     */
    private ActionExecutionServiceConfiguration readConfig() {
        ActionExecutionServiceConfiguration config = new ActionExecutionServiceConfiguration();
        try {
//            ServerResourcesLoader.getInstance()
//                    .setResourcesBasePath(
//                        "/home/therter/ApplicationData/WuppDist/server/linux/SB__wunda_live/server_resources");
            ServerResourcesLoader.getInstance().setResourcesBasePath(
                pathServerResources);
            config = ServerResourcesLoader.getInstance()
                        .loadJson(GeneralServerResources.OFFLINE_ACTION_JSON.getValue(),
                                ActionExecutionServiceConfiguration.class);
        } catch (Exception e) {
            log.info("Cannot load the configuration for the LegacyOflineActionCore. So this core is deactivated", e);

            return null;
        }

        return config;
    }

    @Override
    public String getCoreKey() {
        return "core.legacy.offlineAction"; // NOI18N
    }

    @Override
    public void init(final String pathServerResources) {
        this.pathServerResources = pathServerResources;
        new Thread(this).start();
    }

    //~ Inner Classes ----------------------------------------------------------

    /**
     * DOCUMENT ME!
     *
     * @version  $Revision$, $Date$
     */
    private class ActionExecutioner implements Runnable {

        //~ Instance fields ----------------------------------------------------

        private final SubscriptionResponse.Payload.Data.Action action;
        private final String hasuraUrlString;
        private final String hasuraSecret;

        //~ Constructors -------------------------------------------------------

        /**
         * Creates a new ActionExecutioner object.
         *
         * @param  action           DOCUMENT ME!
         * @param  hasuraUrlString  DOCUMENT ME!
         * @param  hasuraSecret     DOCUMENT ME!
         */
        public ActionExecutioner(final SubscriptionResponse.Payload.Data.Action action,
                final String hasuraUrlString,
                final String hasuraSecret) {
            this.action = action;
            this.hasuraUrlString = hasuraUrlString;
            this.hasuraSecret = hasuraSecret;
        }

        //~ Methods ------------------------------------------------------------

        @Override
        public void run() {
            try {
                final User user = Tools.validationHelper("Bearer " + action.getJwt());

                if (Tools.canHazUserProblems(user)) {
                    // jwt invalid. The result in the db should be jwt invalid
                    sendStatusUpdate(401);
                    return;
                }

                sendStatusUpdate(202);

                final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, null);
                final List<ServerActionParameter> parameterList = convertParameters(action.getParameter());
                byte[] body = null;

                if (action.getBody() != null) {
                    body = Base64.getDecoder().decode(action.getBody());
                }

                if (log.isDebugEnabled()) {
                    log.debug("execute action " + action.getAction());
                }

                Object actionResult = null;

                try {
                    actionResult = LegacyCoreBackend.getInstance().getService()
                                .executeTask(
                                        cidsUser,
                                        action.getAction(),
                                        cidsUser.getDomain(),
                                        body,
                                        LegacyCoreBackend.getInstance().getConnectionContext(),
                                        parameterList.toArray(new ServerActionParameter[0]));
                } catch (RemoteException e) {
                    sendStatusResultUpdate("{\"Exception\": \"" + e.getMessage() + "\"}", 500);

                    return;
                }

                if (actionResult != null) {
                    sendStatusResultUpdate(actionResult.toString(), 200);
                } else {
                    // The result in the db should be exception null invalid
                    sendStatusUpdate(200);
                    return;
                }
            } catch (Exception e) {
                log.error("Error while executing action", e);
            }
        }

        /**
         * DOCUMENT ME!
         *
         * @param   result  DOCUMENT ME!
         *
         * @throws  Exception  DOCUMENT ME!
         */
        private void sendResultUpdate(final String result) throws Exception {
            action.setResult(result);
            final String query = String.format(
                    UPDATE_QUERY,
                    action.getId(),
                    action.getResult().replace("\"", "\\\""));
            final GraphQlQuery queryObject = new GraphQlQuery();
            queryObject.setOperationName("UpdateActionResult");
            queryObject.setQuery(query);

            final ObjectMapper mapper = new ObjectMapper(new JsonFactory());
            final String updateResult = sendHasuraRequest(queryObject, hasuraUrlString);
            final UpdateResult uResult = mapper.readValue(updateResult, UpdateResult.class);

            if ((uResult.getData().getUpdate_action().getAffected_rows() == null)
                        || !uResult.getData().getUpdate_action().getAffected_rows().equals(1)) {
                // some error occured
                log.error("Unexpected response when updating action result:\n"
                            + updateResult);
            }
        }

        /**
         * DOCUMENT ME!
         *
         * @param   status  result DOCUMENT ME!
         *
         * @throws  Exception  DOCUMENT ME!
         */
        private void sendStatusUpdate(final Integer status) throws Exception {
            action.setStatus(status);
            final String query = String.format(
                    STATUS_UPDATE_QUERY,
                    action.getId(),
                    action.getStatus());
            final GraphQlQuery queryObject = new GraphQlQuery();
            queryObject.setOperationName("UpdateActionStatus");
            queryObject.setQuery(query);

            final ObjectMapper mapper = new ObjectMapper(new JsonFactory());
            final String updateResult = sendHasuraRequest(queryObject, hasuraUrlString);
            final UpdateResult uResult = mapper.readValue(updateResult, UpdateResult.class);

            if ((uResult.getData().getUpdate_action().getAffected_rows() == null)
                        || !uResult.getData().getUpdate_action().getAffected_rows().equals(1)) {
                // some error occured
                log.error("Unexpected response when updating action result:\n"
                            + updateResult);
            }
        }

        /**
         * DOCUMENT ME!
         *
         * @param   result  DOCUMENT ME!
         * @param   status  DOCUMENT ME!
         *
         * @throws  Exception  DOCUMENT ME!
         */
        private void sendStatusResultUpdate(final String result, final Integer status) throws Exception {
            action.setResult(result);
            action.setStatus(status);
            final String query = String.format(
                    STATUS_RESULT_UPDATE_QUERY,
                    action.getId(),
                    action.getResult().replace("\"", "\\\""),
                    action.getStatus());
            final GraphQlQuery queryObject = new GraphQlQuery();
            queryObject.setOperationName("UpdateActionStatus");
            queryObject.setQuery(query);

            final ObjectMapper mapper = new ObjectMapper(new JsonFactory());
            final String updateResult = sendHasuraRequest(queryObject, hasuraUrlString);
            final UpdateResult uResult = mapper.readValue(updateResult, UpdateResult.class);

            if ((uResult.getData().getUpdate_action().getAffected_rows() == null)
                        || !uResult.getData().getUpdate_action().getAffected_rows().equals(1)) {
                // some error occured
                log.error("Unexpected response when updating action result:\n"
                            + updateResult);
            }
        }

        /**
         * DOCUMENT ME!
         *
         * @param   queryObject      query DOCUMENT ME!
         * @param   hasuraUrlString  DOCUMENT ME!
         *
         * @return  DOCUMENT ME!
         *
         * @throws  Exception  DOCUMENT ME!
         */
        private String sendHasuraRequest(final GraphQlQuery queryObject, final String hasuraUrlString)
                throws Exception {
            final URL hasuraUrl = new URL(hasuraUrlString);
            final HttpURLConnection con = (HttpURLConnection)hasuraUrl.openConnection();

            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            con.setRequestProperty("x-hasura-admin-secret", hasuraSecret);
            con.setDoOutput(true);
            con.setDoInput(true);
            con.connect();

            final BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                        con.getOutputStream()));
            w.write(new ObjectMapper().writeValueAsString(queryObject));
            w.close();

            if (log.isDebugEnabled()) {
                log.debug("send request: " + new ObjectMapper().writeValueAsString(queryObject));
            }

            final BufferedReader r = new BufferedReader(new InputStreamReader(
                        con.getInputStream()));
            final StringBuffer requestResult = new StringBuffer();
            String tmp;

            while ((tmp = r.readLine()) != null) {
                requestResult.append(tmp);
            }

            r.close();

            return requestResult.toString();
        }

        /**
         * DOCUMENT ME!
         *
         * @param   json  DOCUMENT ME!
         *
         * @return  DOCUMENT ME!
         */
        private List<ServerActionParameter> convertParameters(final String json) {
            final List<ServerActionParameter> cidsSAPs = new ArrayList<>();
            final ObjectMapper mapper = new ObjectMapper(new JsonFactory());

            try {
                final JsonNode node = mapper.readTree(json);
                final Iterator<Map.Entry<String, JsonNode>> it = node.fields();

                while (it.hasNext()) {
                    final Map.Entry<String, JsonNode> n = it.next();

                    final ServerActionParameter cidsSAP = new ServerActionParameter(n.getKey(), n.getValue().asText());
                    cidsSAPs.add(cidsSAP);
                }
            } catch (Exception e) {
                log.error("Error while parsing parameter: " + json, e);
            }

            return cidsSAPs;
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @version  $Revision$, $Date$
     */
    private class CustomMessageHandler implements WebsocketClientEndpoint.MessageHandler {

        //~ Instance fields ----------------------------------------------------

        private WebsocketClientEndpoint websocketClient = null;
        private String hasuraUrlString;
        private String hasuraSecret;

        //~ Constructors -------------------------------------------------------

        /**
         * Creates a new CustomMessageHandler object.
         *
         * @param  hasuraUrlString  DOCUMENT ME!
         * @param  hasuraSecret     DOCUMENT ME!
         */
        public CustomMessageHandler(final String hasuraUrlString,
                final String hasuraSecret) {
            this.hasuraUrlString = hasuraUrlString;
            this.hasuraSecret = hasuraSecret;
        }

        //~ Methods ------------------------------------------------------------

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
            if (log.isDebugEnabled()) {
                log.debug("retrieve message: " + message);
            }

            try {
                final ObjectMapper mapper = new ObjectMapper(new JsonFactory());

                final SubscriptionResponse response = mapper.readValue(message, SubscriptionResponse.class);

                if (response.getType().equals("data")) {
                    for (final SubscriptionResponse.Payload.Data.Action action
                                : response.getPayload().getData().getAction()) {
                        final SubscriptionResponse.Payload.Data.Action lastState = lastActions.get(action.getId());

                        if ((lastState == null) || !lastState.equals(action)) {
                            lastActions.put(action.getId(), action);
                            if (((action.getStatus() == null)
                                            || ((action.getStatus() != 200) && (action.getStatus() != 202)))) {
                                final ActionExecutioner ae = new ActionExecutioner(
                                        action,
                                        hasuraUrlString,
                                        hasuraSecret);

                                executor.submit(ae);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                log.error("Cannot handle hasura message", e);
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

            websocketClient.sendMessage(
                "{\"type\":\"connection_init\",\"payload\":{\"headers\":{\"X-Hasura-Admin-Secret\":\"mysecretaccesskey\"}}}");
            websocketClient.sendMessage(
                "{\"id\":\"1\",\"type\":\"start\",\"payload\":{\"query\":\"subscription onActionChanged {\\n            action {\\n                id\\n                jwt\\n                isCompleted\\n                applicationId\\n                createdAt\\n                updatedAt\\n                status\\n                action,\\n                parameter,\\n                result\\n            }       \\n        }\"}}");
        }

        @Override
        public void connectionClosed() {
            connectionOpen = false;
        }
    }
}

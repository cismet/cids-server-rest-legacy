/***************************************************
*
* cismet GmbH, Saarbruecken, Germany
*
*              ... and it just works.
*
****************************************************/
package de.cismet.cidsx.server.cores.legacy;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

import org.openide.util.Lookup;
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

import de.cismet.cids.utils.MetaClassCacheService;
import de.cismet.cids.utils.serverresources.GeneralServerResources;
import de.cismet.cids.utils.serverresources.ServerResourcesLoader;

import de.cismet.cidsx.server.api.tools.Tools;
import de.cismet.cidsx.server.api.types.User;
import de.cismet.cidsx.server.backend.legacy.LegacyCoreBackend;
import de.cismet.cidsx.server.cores.CidsServerCore;
import de.cismet.cidsx.server.cores.InitialisableCore;
import de.cismet.cidsx.server.cores.legacy.utils.OfflineActionExecutioner;
import de.cismet.cidsx.server.cores.legacy.utils.OfflineActionMessageHandler;
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
    Runnable,
    OfflineActionMessageHandler.ConnectionStatusListener {

    //~ Static fields/initializers ---------------------------------------------

    private static final ConnectionContext CC = ConnectionContext.create(
            AbstractConnectionContext.Category.OTHER,
            "ActionExecutionService");
    private static final int MILLIS_PER_SECOND = 1000;
    private static final int MIN_DURATION_BETWEEN_LOGGING = 5 * 60 * MILLIS_PER_SECOND;

    //~ Instance fields --------------------------------------------------------

    private ExecutorService executor;
    private int maxParallelThreads = 10;
    private volatile boolean connectionOpen = false;
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
//        startTest(null);
        do {
            try {
                final OfflineActionMessageHandler handler = new OfflineActionMessageHandler(
                        executor,
                        config.getServiceUrl(),
                        config.getHasuraSecret());
                handler.addConnectionStatusListener(LegacyOfflineActionCore.this);
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

                handler.dispose();
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

    @Override
    public void connectionClosed() {
        connectionOpen = false;
    }

    @Override
    public void connectionOpened() {
    }

    /**
     * DOCUMENT ME!
     *
     * @param  args  DOCUMENT ME!
     */
    public static void startTest(final String[] args) {
        final String jwt =
            "eyJhbGciOiJSUzI1NiJ9.eyJqdGkiOiIxIiwic3ViIjoiYWRtaW4iLCJkb21haW4iOiJCRUxJUzItVEVTVCIsImh0dHBzOi8vaGFzdXJhLmlvL2p3dC9jbGFpbXMiOnsieC1oYXN1cmEtZGVmYXVsdC1yb2xlIjoidXNlciIsIngtaGFzdXJhLWFsbG93ZWQtcm9sZXMiOlsiZWRpdG9yIiwidXNlciIsIm1vZCJdfX0.fYY0qv3Pnf-sMj4iwk_pL7XwSDgU0dvEL_AGIQIhszv02uesyAtw7nO0dXLrM6boSvAcKvXB1NjO9FVazIjnvGtk_QijL2L5wP6vey4-w2B3YsZyrIuc5O1P0KlK0QUNFQXk0N09jT5LaxFYzrkn0MkpPOdJXTfq56bBxF1RFIIb0j-5DKROMH1ecnrdlNJlJCYHZKRcM57UEC6gC3nrj8owcPwQQFRL-v99J75EGqKAaP5yFi9VXGXfyK2F2hAV9AlPOwLulz7knvMT4uDudevBKp77bRr3xc35j1VIfmO81q2S_zF2NZ3KIBtqg7KTgKnfA8thmKm9PsvOtDOoQA";
        final User user = Tools.validationHelper("Bearer " + jwt);
        final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, null);
        final String bodyString = null;
        final String parameters =
            "{\"actionname\":\"protokollStatusAenderung\",\"protokoll_id\":92184,\"status\":2,\"material\":null,\"bemerkung\":null,\"monteur\":null,\"objekt_typ\":\"arbeitsprotokoll\",\"object_name\":\"# 2 - Mast - 14 (A00014218)\",\"ccnonce\":3326849253}";
        final boolean bodyUsedAsParameter = false;
        final List<ServerActionParameter> parameterList = OfflineActionExecutioner.convertParameters(parameters);
        byte[] body = null;
        final String action = "protokollStatusAenderung";

        if (!bodyUsedAsParameter) {
            if (bodyString != null) {
                body = Base64.getDecoder().decode(bodyString);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("execute action " + action);
        }

        Object actionResult = null;

        try {
            actionResult = LegacyCoreBackend.getInstance().getService()
                        .executeTask(
                                cidsUser,
                                action,
                                cidsUser.getDomain(),
                                body,
                                LegacyCoreBackend.getInstance().getConnectionContext(),
                                parameterList.toArray(new ServerActionParameter[0]));
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        System.out.println("res: " + String.valueOf(actionResult));
    }
}

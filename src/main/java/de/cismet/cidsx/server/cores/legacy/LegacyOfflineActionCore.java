/***************************************************
*
* cismet GmbH, Saarbruecken, Germany
*
*              ... and it just works.
*
****************************************************/
package de.cismet.cidsx.server.cores.legacy;

import lombok.extern.slf4j.Slf4j;

import org.openide.util.lookup.ServiceProvider;

import java.net.URI;

import java.rmi.RemoteException;

import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;

import de.cismet.cids.server.actions.ServerActionParameter;

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
        final String jwt = "";
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

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

import java.net.URI;

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

/**
 * DOCUMENT ME!
 *
 * @author   therter
 * @version  $Revision$, $Date$
 */
@ClientEndpoint
public class WebsocketClientEndpoint {

    //~ Instance fields --------------------------------------------------------

    private Session userSession = null;
    private MessageHandler messageHandler;

    //~ Constructors -----------------------------------------------------------

    /**
     * Creates a new WebsocketClientEndpoint object.
     *
     * @param  msgHandler  endpointURI DOCUMENT ME!
     */
    public WebsocketClientEndpoint(final MessageHandler msgHandler) {
        this.messageHandler = msgHandler;
    }

    //~ Methods ----------------------------------------------------------------

    /**
     * DOCUMENT ME!
     *
     * @param   endpointURI  DOCUMENT ME!
     *
     * @throws  Exception  DOCUMENT ME!
     */
    public void openConnection(final URI endpointURI) throws Exception {
        final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.connectToServer(this, endpointURI);
    }

    /**
     * Callback hook for Connection open events.
     *
     * @param  userSession  the userSession which is opened.
     */
    @OnOpen
    public void onOpen(final Session userSession) {
        this.userSession = userSession;

        if (messageHandler != null) {
            messageHandler.connectionOpened();
        }
    }

    /**
     * Callback hook for Connection close events.
     *
     * @param  userSession  the userSession which is getting closed.
     * @param  reason       the reason for connection close
     */
    @OnClose
    public void onClose(final Session userSession, final CloseReason reason) {
        this.userSession = null;

        if (messageHandler != null) {
            messageHandler.connectionClosed();
        }
    }

    /**
     * Callback hook for Message Events. This method will be invoked when a client send a message.
     *
     * @param  message  The text message
     */
    @OnMessage
    public void onMessage(final String message) {
        if (this.messageHandler != null) {
            this.messageHandler.handleMessage(message);
        }
    }

    /**
     * register message handler.
     *
     * @param  msgHandler  DOCUMENT ME!
     */
    public void addMessageHandler(final MessageHandler msgHandler) {
        this.messageHandler = msgHandler;
    }

    /**
     * Send a message.
     *
     * @param  message  DOCUMENT ME!
     */
    public void sendMessage(final String message) {
        this.userSession.getAsyncRemote().sendText(message);
    }

    //~ Inner Interfaces -------------------------------------------------------

    /**
     * Message handler.
     *
     * @author   Jiji_Sasidharan
     * @version  $Revision$, $Date$
     */
    public static interface MessageHandler {

        //~ Methods ------------------------------------------------------------

        /**
         * DOCUMENT ME!
         *
         * @param  message  DOCUMENT ME!
         */
        void handleMessage(String message);

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

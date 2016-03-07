package de.cismet.cidsx.client.connector.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jackson.JsonLoader;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.Base64;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import de.cismet.cids.client.tools.DevelopmentTools;
import de.cismet.cids.dynamics.CidsBean;
import de.cismet.cids.dynamics.CidsBeanInfo;
import de.cismet.cidsx.client.connector.RESTfulInterfaceConnector;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Properties;
import java.util.PropertyResourceBundle;
import javax.ws.rs.core.MultivaluedMap;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;

/**
 *
 * @author Pascal Dihé <pascal.dihe@cismet.de>
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class RESTfulInterfaceTest extends RESTfulInterfaceConnector {

    private static String HOST;
    private static String BASIC_AUTH_STRING;
    private final CidsBean defaultCidsBean;
    private static RESTfulInterfaceTest INSTANCE;

    private final static Logger LOGGER = Logger.getLogger(RESTfulInterfaceTest.class);

    public RESTfulInterfaceTest() throws IOException, Exception {
        super(HOST);

        final JsonNode node = JsonLoader.fromURL(RESTfulInterfaceTest.class.getResource("metadata.json"));
        this.defaultCidsBean = insertDefaultCidsBean(node);
        
        final CidsBean originalBidsBean = CidsBean.createNewCidsBeanFromJSON(false, node.toString());
        originalBidsBean.setProperty(originalBidsBean.getPrimaryKeyFieldname(), defaultCidsBean.getPrimaryKeyValue());
        originalBidsBean.setProperty("id", defaultCidsBean.getPrimaryKeyValue());
        
        assertEquals(originalBidsBean.toJSONString(true), defaultCidsBean.toJSONString(true));
        LOGGER.info("RESTfulInterfaceTest successfully initialized");

        if (INSTANCE == null) {
            INSTANCE = this;
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @throws Exception DOCUMENT ME!
     */
    @BeforeClass
    public static void setUpClass() throws Exception {
        final Properties p = new Properties();
        p.put("log4j.appender.Remote", "org.apache.log4j.net.SocketAppender");
        p.put("log4j.appender.Remote.remoteHost", "localhost");
        p.put("log4j.appender.Remote.port", "4445");
        p.put("log4j.appender.Remote.locationInfo", "true");
        p.put("log4j.rootLogger", "ALL,Remote");
        org.apache.log4j.PropertyConfigurator.configure(p);

        PropertyResourceBundle bundle;

        try {
            bundle = new PropertyResourceBundle(RESTfulInterfaceTest.class.getResourceAsStream("service.properties"));
        } catch (Exception ex) {
            LOGGER.error("could not find local properties file 'service.properties': " + ex.getMessage(), ex);
            throw ex;
        }

        assertNotNull(bundle.getString("host"));
        assertNotNull(bundle.getString("username"));
        assertNotNull(bundle.getString("usergroup"));
        assertNotNull(bundle.getString("domain"));
        assertNotNull(bundle.getString("password"));

        HOST = bundle.getString("host");
        BASIC_AUTH_STRING = "Basic "
                + new String(Base64.encode(bundle.getString("username")
                        + "@" + bundle.getString("domain")
                        + ":" + bundle.getString("password")));
        
        DevelopmentTools.initSessionManagerFromRestfulConnectionOnLocalhost(
                bundle.getString("domain"), 
                bundle.getString("usergroup"), 
                bundle.getString("username"), 
                bundle.getString("password"));
    }

    private WebResource.Builder createAuthorisationHeader(final WebResource webResource)
            throws RemoteException {
        final WebResource.Builder builder = webResource.header("Authorization", BASIC_AUTH_STRING);
        return builder;
    }

    private void deleteDefaultCidsBean() throws RemoteException {

        final int objectId = defaultCidsBean.getPrimaryKeyValue();
        final String className = defaultCidsBean.getCidsBeanInfo().getClassKey();
        final String domain = defaultCidsBean.getCidsBeanInfo().getDomainKey();

        final MultivaluedMap queryParameters = new MultivaluedMapImpl();
        final WebResource webResource = this.createWebResource(ENTITIES_API)
                .path(domain + "." + className + "/" + objectId)
                .queryParams(queryParameters);
        WebResource.Builder builder = this.createAuthorisationHeader(webResource);
        builder = this.createMediaTypeHeaders(builder);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("deleteMetaObject '" + objectId + "@" + className + "@" + domain
                    + "' :" + webResource.toString());
        }

        try {
            builder.delete(ObjectNode.class);
        } catch (UniformInterfaceException ue) {
            final ClientResponse.Status status = ue.getResponse().getClientResponseStatus();
            final String message = "could not delete meta object '"
                    + objectId
                    + "@"
                    + className
                    + "@"
                    + domain
                    + "': "
                    + status.getReasonPhrase();

            LOGGER.error(message, ue);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(ue.getResponse().getEntity(String.class));
            }
            throw new RemoteException(message, ue);
        }
    }

    private CidsBean insertDefaultCidsBean(JsonNode defaultCidsBeanNode)
            throws RemoteException {

        final CidsBeanInfo beanInfo
                = new CidsBeanInfo(defaultCidsBeanNode.get(CidsBeanInfo.JSON_CIDS_OBJECT_KEY_IDENTIFIER).textValue());

        final MultivaluedMap queryParameters = new MultivaluedMapImpl();
        queryParameters.add("requestResultingInstance", "true");
        final WebResource webResource = this.createWebResource(ENTITIES_API)
                .path(beanInfo.getDomainKey() + "." + beanInfo.getClassKey())
                .queryParams(queryParameters);
        WebResource.Builder builder = this.createAuthorisationHeader(webResource);
        builder = this.createMediaTypeHeaders(builder);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("insertMetaObject for class '" + beanInfo.getDomainKey() + "."
                    + beanInfo.getClassKey() + "):" + webResource.toString());
        }

        try {
            final JsonNode objectNode = builder.post(ObjectNode.class, defaultCidsBeanNode);
            if ((objectNode == null) || (objectNode.size() == 0)) {
                LOGGER.error("could not insert meta object for class '" + beanInfo.getDomainKey() + "."
                        + beanInfo.getClassKey() + "': newly inserted meta object could not be found");
                return null;
            }

            final CidsBean cidsBean;
            try {
                cidsBean = CidsBean.createNewCidsBeanFromJSON(false, objectNode.toString());
            } catch (Exception ex) {
                final String message = "could not deserialize cids bean from object node for class '"
                        + beanInfo.getClassKey()
                        + "': "
                        + ex.getMessage();
                LOGGER.error(message, ex);
                throw new RemoteException(message, ex);
            }

            if (cidsBean != null) {
                LOGGER.info("default cids bean with id " + cidsBean.getPrimaryKeyValue() + " created");
                return cidsBean;
            } else {
                LOGGER.error("could not insert meta object for class '" + beanInfo.getClassKey() + "@" + beanInfo.getDomainKey()
                        + "': newly inserted meta object could not be found");
                return null;
            }
        } catch (UniformInterfaceException ue) {
            final ClientResponse.Status status = ue.getResponse().getClientResponseStatus();
            final String message = "could not insert meta object for class  '"
                    + beanInfo.getClassKey()
                    + "@"
                    + beanInfo.getDomainKey()
                    + "': "
                    + status.getReasonPhrase();

            LOGGER.error(message, ue);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(ue.getResponse().getEntity(String.class));
            }
            throw new RemoteException(message, ue);
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @throws Exception DOCUMENT ME!
     */
    @AfterClass
    public static void tearDownClass() throws Exception {
        if (INSTANCE.defaultCidsBean != null) {
            LOGGER.info("removing cids bean with id " + INSTANCE.defaultCidsBean.getPrimaryKeyValue() + " created");
            INSTANCE.deleteDefaultCidsBean();
        } else {
            LOGGER.warn("defaultCidsBean not found!");
        }
    }

    /**
     * DOCUMENT ME!
     */
    @Before
    public void setUp() {
    }

    /**
     * DOCUMENT ME!
     */
    @After
    public void tearDown() {
        

    }

    @Test
    public void a_doNothing() {

    }
}

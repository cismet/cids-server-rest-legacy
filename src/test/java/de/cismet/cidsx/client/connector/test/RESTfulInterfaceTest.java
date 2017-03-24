package de.cismet.cidsx.client.connector.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.github.fge.jackson.JsonLoader;
import com.google.common.collect.Lists;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.client.apache.ApacheHttpClient;
import com.sun.jersey.client.apache.config.DefaultApacheHttpClientConfig;
import com.sun.jersey.client.urlconnection.URLConnectionClientHandler;
import com.sun.jersey.core.util.Base64;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import de.cismet.cids.client.tools.DevelopmentTools;
import de.cismet.cids.dynamics.CidsBean;
import de.cismet.cids.dynamics.CidsBeanInfo;
import de.cismet.cids.jsonpatch.CidsBeanPatch;
import de.cismet.cids.jsonpatch.CidsBeanPatchUtils;
import de.cismet.cidsx.client.connector.RESTfulInterfaceConnector;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Iterator;
import java.util.List;
import java.util.PropertyResourceBundle;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriBuilder;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

/**
 *
 * @author Pascal Dihé <pascal.dihe@cismet.de>
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(DataProviderRunner.class)
@Ignore // requires a running service!
public class RESTfulInterfaceTest extends RESTfulInterfaceConnector {

    private static String HOST;
    private static String BASIC_AUTH_STRING;
    private static CidsBean DEFAULT_CIDS_BEAN;
    private static RESTfulInterfaceTest INSTANCE;
    private static final ObjectMapper OBJECT_MAPPER = CidsBeanPatchUtils.getInstance().getCidsBeanMapper();
    private static final ObjectReader OBJECT_READER
            = CidsBeanPatchUtils.getInstance().getCidsBeanMapper().reader().withType(CidsBeanPatch.class);

    private final static Logger LOGGER = Logger.getLogger(RESTfulInterfaceTest.class);

    public RESTfulInterfaceTest() throws IOException, Exception {
        super(HOST);
    }

    /**
     * DOCUMENT ME!
     *
     * @throws Exception DOCUMENT ME!
     */
    @BeforeClass
    public static void setUpClass() throws Exception {
        LOGGER.debug("setUpClass()");
        
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

        if (INSTANCE == null) {
            INSTANCE = new RESTfulInterfaceTest();
        }

        final JsonNode node = JsonLoader.fromURL(RESTfulInterfaceTest.class.getResource("metadata.json"));
        DEFAULT_CIDS_BEAN = insertDefaultCidsBean(node);

        final CidsBean originalBidsBean = CidsBean.createNewCidsBeanFromJSON(false, node.toString());
        originalBidsBean.setProperty("id", DEFAULT_CIDS_BEAN.getPrimaryKeyValue());

        try {
            assertEquals(originalBidsBean.toJSONString(true), DEFAULT_CIDS_BEAN.toJSONString(true));
        } catch(AssertionError ae) {
            LOGGER.error(ae.getMessage(), ae);
            throw ae;
        }

        LOGGER.info("RESTfulInterfaceTest successfully initialized");
    }

    private WebResource.Builder createAuthorisationHeader(final WebResource webResource)
            throws RemoteException {
        final WebResource.Builder builder = webResource.header("Authorization", BASIC_AUTH_STRING);
        return builder;
    }

    private void deleteDefaultCidsBean() throws RemoteException {

        final int objectId = DEFAULT_CIDS_BEAN.getPrimaryKeyValue();
        final String className = DEFAULT_CIDS_BEAN.getCidsBeanInfo().getClassKey();
        final String domain = DEFAULT_CIDS_BEAN.getCidsBeanInfo().getDomainKey();

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

    private CidsBean patchCidsBean(final CidsBean cidsBean, final CidsBeanPatch patch) throws RemoteException {
        //final DefaultApacheHttpClientConfig config = new DefaultApacheHttpClientConfig();
        //config.getProperties().put(URLConnectionClientHandler.PROPERTY_HTTP_URL_CONNECTION_SET_METHOD_WORKAROUND, true);
        //config.getClasses().add(JacksonJsonProvider.class);
        //Client client = ApacheHttpClient.create(config);
        //client.property(HttpUrlConnectorProvider.SET_METHOD_WORKAROUND, true);
        //final UriBuilder uriBuilder = UriBuilder.fromPath(this.getRootResource());
        //final WebResource webResource = client.resource(uriBuilder.build());

        final CidsBeanInfo beanInfo = cidsBean.getCidsBeanInfo();

        final MultivaluedMap queryParameters = new MultivaluedMapImpl();
        queryParameters.add("requestResultingInstance", "true");
        final WebResource webResource = INSTANCE.createWebResource(ENTITIES_API)
                .path(beanInfo.getDomainKey() + "." + beanInfo.getClassKey()+"/"+cidsBean.getPrimaryKeyValue())
                .queryParams(queryParameters);
        WebResource.Builder builder = INSTANCE.createAuthorisationHeader(webResource);
        builder = INSTANCE.createMediaTypeHeaders(builder);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("patch cids bean for class '" + beanInfo.getDomainKey() + "."
                    + beanInfo.getClassKey() + "': " + webResource.toString());
        }

        try {
            final JsonNode objectNode = builder.method("PATCH", ObjectNode.class, patch);
            if ((objectNode == null) || (objectNode.size() == 0)) {
                LOGGER.error("could not patch cids bean for class '" + beanInfo.getDomainKey() + "."
                        + beanInfo.getClassKey() + "': patched cids bean could not be found");
                return null;
            }

            final CidsBean patchedCidsBean;
            try {
                patchedCidsBean = CidsBean.createNewCidsBeanFromJSON(false, objectNode.toString());
            } catch (Exception ex) {
                final String message = "could not deserialize cids bean from object node for class '"
                        + beanInfo.getClassKey()
                        + "': "
                        + ex.getMessage();
                LOGGER.error(message, ex);
                throw new RemoteException(message, ex);
            }

            if (patchedCidsBean != null) {
                LOGGER.info("cids bean with id " + cidsBean.getPrimaryKeyValue() + " patched");
                return patchedCidsBean;
            } else {
                LOGGER.error("could not patch cids bean for class '" + beanInfo.getClassKey() + "@" + beanInfo.getDomainKey()
                        + "': patched cids bean could not be found");
                return null;
            }
        } catch (UniformInterfaceException ue) {
            final ClientResponse.Status status = ue.getResponse().getClientResponseStatus();
            final String message = "could not patch meta object for class  '"
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

    private static CidsBean insertDefaultCidsBean(JsonNode defaultCidsBeanNode)
            throws RemoteException {

        final CidsBeanInfo beanInfo
                = new CidsBeanInfo(defaultCidsBeanNode.get(CidsBeanInfo.JSON_CIDS_OBJECT_KEY_IDENTIFIER).textValue());

        final MultivaluedMap queryParameters = new MultivaluedMapImpl();
        queryParameters.add("requestResultingInstance", "true");
        final WebResource webResource = INSTANCE.createWebResource(ENTITIES_API)
                .path(beanInfo.getDomainKey() + "." + beanInfo.getClassKey())
                .queryParams(queryParameters);
        WebResource.Builder builder = INSTANCE.createAuthorisationHeader(webResource);
        builder = INSTANCE.createMediaTypeHeaders(builder);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("insertMetaObject for class '" + beanInfo.getDomainKey() + "."
                    + beanInfo.getClassKey() + "': " + webResource.toString());
        }

        try {
            final JsonNode objectNode = builder.method("POST", ObjectNode.class, defaultCidsBeanNode);
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
        LOGGER.debug("tearDownClass()");
        if (INSTANCE.DEFAULT_CIDS_BEAN != null) {
            LOGGER.info("removing cids bean with id " + INSTANCE.DEFAULT_CIDS_BEAN.getPrimaryKeyValue() + " created");
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
        LOGGER.debug("setUp()");
    }

    /**
     * DOCUMENT ME!
     */
    @After
    public void tearDown() {
        LOGGER.debug("tearDown()");
    }

    @Test
    @UseDataProvider("getPatches")
    public void testPatches(final String comment, final CidsBeanPatch patch, final CidsBean expected) throws Exception {

        LOGGER.info("testing patch '" + comment + "'");

        try {
            expected.setProperty("id", DEFAULT_CIDS_BEAN.getPrimaryKeyValue());

            DEFAULT_CIDS_BEAN = this.patchCidsBean(DEFAULT_CIDS_BEAN, patch);

            // compare updated instance with resulting instance
            String actualString = DEFAULT_CIDS_BEAN.toJSONString(false);
            String expectedString = expected.toJSONString(false);
            assertEquals(expectedString, actualString);

            LOGGER.debug("patch '" + comment + "' test passed!");

        } catch (Exception ex) {
            LOGGER.error(ex.getMessage(), ex);
            throw ex;
        } catch (AssertionError ae) {
            LOGGER.error("patch '" + comment + " test assertion failed: " + ae.getMessage());
            throw ae;
        }
    }

    @DataProvider
    public final static Object[][] getPatches() throws Exception {
        JsonNode operations = JsonLoader.fromURL(RESTfulInterfaceTest.class.getResource("patches.json"));

        LOGGER.debug("loading " + operations.size() + " patch operations");
        final List<Object[]> list = Lists.newArrayList();
        final Iterator<JsonNode> nodeIterator = operations.iterator();
        while (nodeIterator.hasNext()) {
            try {
                final JsonNode node = nodeIterator.next();
                list.add(new Object[]{
                    node.get("comment").asText(),
                    OBJECT_READER.readValue(node.get("patch")),
                    OBJECT_MAPPER.treeToValue(node.get("expected"), CidsBean.class)
                });
            } catch (Exception ex) {
                LOGGER.error("cannot deserialize beans for patch operations:"
                        + ex.getMessage(), ex);
                throw ex;
            }
        }

        LOGGER.info(list.size() + " patch operation loaded");
        return list.toArray(new Object[list.size()][3]);
    }

    @Override
    protected WebResource createWebResource(final String path) {
        // remove leading '/' if present
        final String resource;
        if ((path == null) || path.isEmpty()) {
            resource = getRootResource();
        } else if ('/' == path.charAt(0)) {
            resource = getRootResource() + path.substring(1, path.length() - 1);
        } else {
            resource = getRootResource() + path;
        }

        final DefaultApacheHttpClientConfig clientConfig = new DefaultApacheHttpClientConfig();

        clientConfig.getClasses().add(JacksonJsonProvider.class);
        clientConfig.getProperties().put(URLConnectionClientHandler.PROPERTY_HTTP_URL_CONNECTION_SET_METHOD_WORKAROUND, true);

        final Client client = ApacheHttpClient.create(clientConfig);
        //client.property(HttpUrlConnectorProvider.SET_METHOD_WORKAROUND, true);
        final UriBuilder uriBuilder = UriBuilder.fromPath(resource);

        final WebResource webResource = client.resource(uriBuilder.build());
        return webResource;
    }
}

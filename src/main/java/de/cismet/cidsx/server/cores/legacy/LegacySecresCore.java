/***************************************************
*
* cismet GmbH, Saarbruecken, Germany
*
*              ... and it just works.
*
****************************************************/
package de.cismet.cidsx.server.cores.legacy;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.Setter;

import lombok.extern.slf4j.Slf4j;

import org.openide.util.lookup.ServiceProvider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import java.net.URL;
import java.net.URLEncoder;

import java.nio.file.Files;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.servlet.http.HttpServletResponse;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import de.cismet.cids.server.actions.HttpTunnelAction;
import de.cismet.cids.server.actions.ServerActionParameter;

import de.cismet.cidsx.server.api.types.User;
import de.cismet.cidsx.server.backend.legacy.LegacyCoreBackend;
import de.cismet.cidsx.server.cores.CidsServerCore;
import de.cismet.cidsx.server.cores.SecresCore;
import de.cismet.cidsx.server.exceptions.CidsServerException;

import de.cismet.commons.security.AccessHandler;
import de.cismet.commons.security.WebDavClient;

import de.cismet.connectioncontext.AbstractConnectionContext;
import de.cismet.connectioncontext.ConnectionContext;
import java.util.Base64;

/**
 * DOCUMENT ME!
 *
 * @author   thorsten
 * @version  1.0
 */
@Slf4j
@ServiceProvider(service = CidsServerCore.class)
public class LegacySecresCore implements SecresCore {

    //~ Static fields/initializers ---------------------------------------------

    private static final ConnectionContext CC = ConnectionContext.create(
            AbstractConnectionContext.Category.ACTION,
            "LegacyWebdavCore");
    private static final String CONF_ATTR_PREFIX = "secres://";
    private static final Set<String> missingConfigurations = new TreeSet<String>();

    //~ Enums ------------------------------------------------------------------

    /**
     * DOCUMENT ME!
     *
     * @version  $Revision$, $Date$
     */
    private enum Mode {

        //~ Enum constants -----------------------------------------------------

        DEFAULT, INTERN, LOCAL
    }

    //~ Methods ----------------------------------------------------------------

    @Override
    public String getCoreKey() {
        return "core.legacy.secres"; // NOI18N
    }

    @Override
    public ResponseBuilder executeQuery(final User user,
            final String type,
            final String url,
            final MultivaluedMap<String, String> queryParams,
            final String authString) {
        final Sirius.server.newuser.User cidsUser = LegacyCoreBackend.getInstance().getCidsUser(user, null, true);
        String contentType = "";
        InputStream contentStream = null;
        Response.Status responseStatus = Response.Status.INTERNAL_SERVER_ERROR;
        final Map<String, String> respHeaderList = new HashMap<>();

        try {
            final String configAttr = LegacyCoreBackend.getInstance()
                        .getService()
                        .getConfigAttr(cidsUser, CONF_ATTR_PREFIX + type, CC);
            final ObjectMapper mapper = new ObjectMapper(new JsonFactory());
            final Class<ConfigurationJson> clazz = ConfigurationJson.class;
            ConfigurationJson config = null;

            try {
                config = mapper.readValue(configAttr, clazz);
            } catch (Exception e) {
                if (!missingConfigurations.contains(type)) {
                    log.error("cannot read configuration for " + type, e);
                    missingConfigurations.add(type);
                }
                throw new CidsServerException(
                    "Cannot read configuration",
                    "Cannot read configuration",
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    e);
            }
            final Map<String, List<String>> removedParameters = removeReservedParamsFromUrl(queryParams, config);

            final Mode mode = parseMode(config.getMode());

            switch (mode) {
                case INTERN: {
                    final Collection<ServerActionParameter> params = new ArrayList<>();

                    if ((config.getUser() != null) && (config.getPassword() != null)) {
                        final HashMap<String, String> credentials = new HashMap<String, String>();
                        credentials.put(HttpTunnelAction.CREDENTIALS_USERNAME_KEY, config.getUser());
                        credentials.put(HttpTunnelAction.CREDENTIALS_PASSWORD_KEY, config.getPassword());

                        params.add(new ServerActionParameter<>(
                                HttpTunnelAction.PARAMETER_TYPE.CREDENTIALS.toString(),
                                credentials));
                    }

                    params.add(new ServerActionParameter<>(
                            HttpTunnelAction.PARAMETER_TYPE.METHOD.toString(),
                            AccessHandler.ACCESS_METHODS.GET_REQUEST));
                    params.add(new ServerActionParameter<>(
                            HttpTunnelAction.PARAMETER_TYPE.URL.toString(),
                            new URL(config.getBaseUrl()
                                        + url)));
                    if (getParamString(queryParams) != null) {
                        params.add(new ServerActionParameter<>(
                                HttpTunnelAction.PARAMETER_TYPE.REQUEST.toString(),
                                getParamString(queryParams)));
                    }
                    params.add(new ServerActionParameter<>(
                            HttpTunnelAction.PARAMETER_TYPE.WITH_CONTENT_TYPE.toString(),
                            Boolean.TRUE));

                    final Object httpResponse = LegacyCoreBackend.getInstance()
                                .getService()
                                .executeTask(
                                    cidsUser,
                                    "httpTunnelAction",
                                    cidsUser.getDomain(),
                                    null,
                                    LegacyCoreBackend.getInstance().getConnectionContext(),
                                    params.toArray(new ServerActionParameter[0]));

                    if (httpResponse instanceof byte[]) {
                        final byte[] response = (byte[])httpResponse;
                        int index = 0;
                        final byte separator = "\n".getBytes("utf-8")[0];

                        while (response[index] != separator) {
                            ++index;
                        }

                        responseStatus = Response.Status.OK;

                        if (index > 0) {
                            contentType = new String(Arrays.copyOf(response, index));
                        }
                        ++index;
                        contentStream = new ByteArrayInputStream(response, index, response.length);
                    }
                    break;
                }
                case LOCAL: {
                    final String baseUrl = (config.getBaseUrl() == null) ? "" : config.getBaseUrl();
                    final File file = new File(baseUrl + url);

                    if (file.exists()) {
                        final InputStream is = new FileInputStream(file);

                        contentStream = is;
                        contentType = Files.probeContentType(file.toPath());

                        responseStatus = Response.Status.OK;
                    } else {
                        responseStatus = Response.Status.NOT_FOUND;
                    }
                    break;
                }
                default: {
                    // The webdav client is a normal http client that uses Basic Authentifizierung, if a user and
                    // password is set
                    final WebDavClient webDav;
                    
                    if (authString != null) {
                        String decodedAuthString = new String(Base64.getDecoder().decode(authString));
                        
                        if (decodedAuthString.contains(":")) {
                            String userFromAuthString = decodedAuthString.substring(0, decodedAuthString.indexOf(":"));
                            String passwdFromAuthString = decodedAuthString.substring(decodedAuthString.indexOf(":") + 1);

                            webDav = new WebDavClient(null, userFromAuthString, passwdFromAuthString);
                        } else {
                            webDav = new WebDavClient(null, config.getUser(), config.getPassword());
                        }
                    } else {
                        webDav = new WebDavClient(null, config.getUser(), config.getPassword());
                    }
                    final String baseUrl = (config.getBaseUrl() == null) ? "" : config.getBaseUrl();
                    final Map<String, String> headerList = new HashMap<>();
                    final Map<String, Object> statusList = new HashMap<>();
                    final String paramString = getParamString(queryParams);
                    String targetUrl = baseUrl + url + ((paramString != null) ? ("?" + paramString) : "");
                    InputStream is = null;

                    try {
                        is = webDav.getInputStream(targetUrl, headerList, statusList);
                    } catch (IllegalArgumentException e) {
                        targetUrl = baseUrl + url
                                    + ((paramString != null) ? ("?" + URLEncoder.encode(paramString, "UTF-8")) : "");
                        is = webDav.getInputStream(targetUrl, headerList, statusList);
                    }

                    contentStream = is;

                    for (final String headerName : headerList.keySet()) {
                        if (headerName.equalsIgnoreCase("Content-Type")) {
                            contentType = headerList.get(headerName);
                        } else {
                            respHeaderList.put(headerName, headerList.get(headerName));
                        }
                    }

                    if (Response.Status.fromStatusCode((Integer)statusList.get("code")) != null) {
                        responseStatus = Response.Status.fromStatusCode((Integer)statusList.get("code"));
                    }
                }
            }

            ResponseBuilder rb = Response.status(responseStatus).header("Content-Type", contentType);

            if ((config.getForwardResponseHeaders() != null)
                        && config.getForwardResponseHeaders().equalsIgnoreCase("true")) {
                for (final String headerName : respHeaderList.keySet()) {
                    rb.header(headerName, respHeaderList.get(headerName));
                }
            }

            rb = addConfiguredHeader(rb, config, removedParameters);

            return rb.entity(contentStream);
        } catch (CidsServerException ex) {
            throw ex;
        } catch (final Exception ex) {
            final String message = "error while executing secres task with url '"
                        + url + "': " + ex.getMessage();
            log.error(message, ex);
            throw new CidsServerException(message, message,
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex);
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param   queryParams  url DOCUMENT ME!
     * @param   config       DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    private Map<String, List<String>> removeReservedParamsFromUrl(final MultivaluedMap<String, String> queryParams,
            final ConfigurationJson config) {
        final Map<String, List<String>> removedParameters = new HashMap<>();

        if (config.getParams() != null) {
            for (final ConfigurationJson.Params param : config.getParams()) {
                if (queryParams.containsKey(param.getKey())) {
                    removedParameters.put(param.getKey(), queryParams.get(param.getKey()));
                }
            }
        }

        for (final String key : removedParameters.keySet()) {
            queryParams.remove(key);
        }

        return removedParameters;
    }

    /**
     * DOCUMENT ME!
     *
     * @param   builder            DOCUMENT ME!
     * @param   config             DOCUMENT ME!
     * @param   removedParameters  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    private ResponseBuilder addConfiguredHeader(final ResponseBuilder builder,
            final ConfigurationJson config,
            final Map<String, List<String>> removedParameters) {
        ResponseBuilder builderWithHeader = builder;

        if (config.getResponseHeader() != null) {
            for (final ConfigurationJson.ResponseHeader header : config.getResponseHeader()) {
                builderWithHeader = builder.header(header.key, header.value);
            }
        }

        if (config.getOverwritableHeader() != null) {
            for (final ConfigurationJson.OverwritableHeader header : config.getOverwritableHeader()) {
                builderWithHeader = builder.header(
                        header.key,
                        getFirstValueOrDefault(
                            removedParameters.get(header.value),
                            getDefaultValue(header.value, config)));
            }
        }

        return builderWithHeader;
    }

    /**
     * DOCUMENT ME!
     *
     * @param   listValue     key DOCUMENT ME!
     * @param   defaultValue  config DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    private String getFirstValueOrDefault(final List<String> listValue, final String defaultValue) {
        if (listValue != null) {
            if (listValue.size() > 0) {
                return listValue.get(0);
            }
        }

        return defaultValue;
    }

    /**
     * DOCUMENT ME!
     *
     * @param   key     DOCUMENT ME!
     * @param   config  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    private String getDefaultValue(final String key, final ConfigurationJson config) {
        if (config.getParams() != null) {
            for (final ConfigurationJson.Params param : config.getParams()) {
                if (param.getKey().equals(key)) {
                    return param.getDefaultValue();
                }
            }
        }

        return null;
    }

    /**
     * Creates a parameter string from the given parameter map.
     *
     * @param   queryParams  the map to convert to a string
     *
     * @return  the created string
     */
    private String getParamString(final MultivaluedMap<String, String> queryParams) {
        String paramString = null;

        if ((queryParams != null) && (queryParams.size() > 0)) {
            final StringBuilder sb = new StringBuilder();
            boolean firstValue = true;

            for (final String key : queryParams.keySet()) {
                if (firstValue) {
                    firstValue = false;
                } else {
                    sb.append("&");
                }
                sb.append(key).append("=").append(getParamValuesAsString(queryParams.get(key)));
            }

            paramString = sb.toString();
        }

        return paramString;
    }

    /**
     * Creates a value string from the given list.
     *
     * @param   values  the list to convert to a string
     *
     * @return  the created string
     */
    private String getParamValuesAsString(final List<String> values) {
        String valueString = "";

        if ((values != null) && (values.size() > 0)) {
            final StringBuilder sb = new StringBuilder();
            boolean firstValue = true;

            for (final String value : values) {
                if (firstValue) {
                    firstValue = false;
                } else {
                    sb.append(",");
                }
                sb.append(value);
            }

            valueString = sb.toString();
        }

        return valueString;
    }

    /**
     * Parse the given mode string to the corresponding mode enum.
     *
     * @param   mode  the mode to parse
     *
     * @return  the mode or default, iff the mode cannot be parsed
     */
    private Mode parseMode(final String mode) {
        if (Mode.INTERN.toString().equalsIgnoreCase(mode)) {
            return Mode.INTERN;
        } else if (Mode.LOCAL.toString().equalsIgnoreCase(mode)) {
            return Mode.LOCAL;
        } else if (Mode.DEFAULT.toString().equalsIgnoreCase(mode)) {
            return Mode.DEFAULT;
        } else {
            log.warn("Unknown secres mode " + mode + " found. Default mode will be used");
            return Mode.DEFAULT;
        }
    }

    //~ Inner Classes ----------------------------------------------------------

    /**
     * DOCUMENT ME!
     *
     * @version  $Revision$, $Date$
     */
    @Getter
    @Setter
    public static class ConfigurationJson {

        //~ Instance fields ----------------------------------------------------

        private String user;
        private String password;
        private String baseUrl;
        private String mode;
        private String forwardResponseHeaders;
        private ResponseHeader[] responseHeader;
        private Params[] params;
        private OverwritableHeader[] overwritableHeader;

        //~ Inner Classes ------------------------------------------------------

        /**
         * DOCUMENT ME!
         *
         * @version  $Revision$, $Date$
         */
        @Getter
        @Setter
        public static class ResponseHeader {

            //~ Instance fields ------------------------------------------------

            private String key;
            private String value;
        }

        /**
         * DOCUMENT ME!
         *
         * @version  $Revision$, $Date$
         */
        @Getter
        @Setter
        public static class Params {

            //~ Instance fields ------------------------------------------------

            private String key;
            private String defaultValue;
        }

        /**
         * DOCUMENT ME!
         *
         * @version  $Revision$, $Date$
         */
        @Getter
        @Setter
        public static class OverwritableHeader {

            //~ Instance fields ------------------------------------------------

            private String key;
            private String value;
        }
    }
}

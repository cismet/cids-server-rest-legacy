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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import java.net.HttpURLConnection;
import java.net.URL;

import de.cismet.cidsx.server.cores.legacy.utils.json.GraphQlQuery;
import de.cismet.cidsx.server.cores.legacy.utils.json.SubscriptionResponse;
import de.cismet.cidsx.server.cores.legacy.utils.json.UpdateResult;

/**
 * DOCUMENT ME!
 *
 * @author   therter
 * @version  $Revision$, $Date$
 */
@Slf4j
public class HasuraHelper {

    //~ Static fields/initializers ---------------------------------------------

    private static final String UPDATE_QUERY =
        "mutation UpdateActionResult {update_action(where: {id: {_eq: \"%1s\"}}, _set: {result: \"%2s\", updatedAt: \"now()\"}){affected_rows}}";
    private static final String STATUS_UPDATE_QUERY =
        "mutation UpdateActionStatus {update_action(where: {id: {_eq: \"%1s\"}}, _set: {status: %2s, updatedAt: \"now()\"}){affected_rows}}";
    private static final String STATUS_RESULT_UPDATE_QUERY =
        "mutation UpdateActionStatus {update_action(where: {id: {_eq: \"%1s\"}}, _set: {result: \"%2s\", status: %3s, updatedAt: \"now()\"}){affected_rows}}";
    private static final String GET_PARAMETER_QUERY =
        "query GetParameter {action(where: {_and: {id: {_eq: \"%1s\"}}}) {id parameter}}";
    private static final String GET_BODY_QUERY =
        "query GetBody {action(where: {_and: {id: {_eq: \"%1s\"}}}) {id body}}";
    private static final String BODY_MARKER = "$$_body_$$";

    //~ Instance fields --------------------------------------------------------

    private final String hasuraUrlString;
    private final String hasuraSecret;

    //~ Constructors -----------------------------------------------------------

    /**
     * Creates a new HasuraHelper object.
     *
     * @param  hasuraUrlString  DOCUMENT ME!
     * @param  hasuraSecret     DOCUMENT ME!
     */
    public HasuraHelper(final String hasuraUrlString, final String hasuraSecret) {
        this.hasuraUrlString = hasuraUrlString;
        this.hasuraSecret = hasuraSecret;
    }

    //~ Methods ----------------------------------------------------------------

    /**
     * DOCUMENT ME!
     *
     * @param   queryObject  query DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     *
     * @throws  Exception  DOCUMENT ME!
     */
    public String sendHasuraRequest(final GraphQlQuery queryObject) throws Exception {
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
     * @param   body  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public String buildParametersString(final String json, final String body) {
        final String extendedJson = (((body != null) && isBodyUsedAsParameter(json)) ? json.replace(BODY_MARKER, body)
                                                                                     : json);

        if ((body == null) && isBodyUsedAsParameter(json)) {
            log.warn(
                "The body placeholder is set in parameter field, but the body field is null. Do not replace body placeholder");
        }

        return extendedJson;
    }

    /**
     * DOCUMENT ME!
     *
     * @param   parameters  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public boolean isBodyUsedAsParameter(final String parameters) {
        return (parameters != null)
                    && parameters.contains(BODY_MARKER);
    }

    /**
     * DOCUMENT ME!
     *
     * @param   id  result DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     *
     * @throws  Exception  DOCUMENT ME!
     */
    public String getBody(final String id) throws Exception {
        final String query = String.format(
                GET_BODY_QUERY,
                id);
        final GraphQlQuery queryObject = new GraphQlQuery();
        queryObject.setOperationName("GetBody");
        queryObject.setQuery(query);

        final ObjectMapper mapper = new ObjectMapper(new JsonFactory());
        final String res = sendHasuraRequest(queryObject);
        final SubscriptionResponse.Payload result = mapper.readValue(
                res,
                SubscriptionResponse.Payload.class);

        if (!result.getData().getAction()[0].getId().equals(id)) {
            // some error occured
            log.error("Unexpected response when retrieving parameters:\n" + res);
        }

        return result.getData().getAction()[0].getBody();
    }

    /**
     * DOCUMENT ME!
     *
     * @param   id  result DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     *
     * @throws  Exception  DOCUMENT ME!
     */
    public String getParameters(final String id) throws Exception {
        final String query = String.format(
                GET_PARAMETER_QUERY,
                id);
        final GraphQlQuery queryObject = new GraphQlQuery();
        queryObject.setOperationName("GetParameter");
        queryObject.setQuery(query);

        final ObjectMapper mapper = new ObjectMapper(new JsonFactory());
        final String res = sendHasuraRequest(queryObject);
        final SubscriptionResponse.Payload result = mapper.readValue(
                res,
                SubscriptionResponse.Payload.class);

        if (!result.getData().getAction()[0].getId().equals(id)) {
            // some error occured
            log.error("Unexpected response when retrieving parameters:\n" + res);
        }

        return result.getData().getAction()[0].getParameter();
    }

    /**
     * DOCUMENT ME!
     *
     * @param   a       DOCUMENT ME!
     * @param   result  DOCUMENT ME!
     *
     * @throws  Exception  DOCUMENT ME!
     */
    public void sendResultUpdate(final SubscriptionResponse.Payload.Data.Action a, final String result)
            throws Exception {
        a.setResult(result);
        final String query = String.format(
                UPDATE_QUERY,
                a.getId(),
                a.getResult().replace("\"", "\\\""));
        final GraphQlQuery queryObject = new GraphQlQuery();
        queryObject.setOperationName("UpdateActionResult");
        queryObject.setQuery(query);

        final ObjectMapper mapper = new ObjectMapper(new JsonFactory());
        final String updateResult = sendHasuraRequest(queryObject);
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
     * @param   a       DOCUMENT ME!
     * @param   status  result DOCUMENT ME!
     *
     * @throws  Exception  DOCUMENT ME!
     */
    public void sendStatusUpdate(final SubscriptionResponse.Payload.Data.Action a, final Integer status)
            throws Exception {
        a.setStatus(status);
        final String query = String.format(
                STATUS_UPDATE_QUERY,
                a.getId(),
                a.getStatus());
        final GraphQlQuery queryObject = new GraphQlQuery();
        queryObject.setOperationName("UpdateActionStatus");
        queryObject.setQuery(query);

        final ObjectMapper mapper = new ObjectMapper(new JsonFactory());
        final String updateResult = sendHasuraRequest(queryObject);
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
     * @param   a       DOCUMENT ME!
     * @param   result  DOCUMENT ME!
     * @param   status  DOCUMENT ME!
     *
     * @throws  Exception  DOCUMENT ME!
     */
    public void sendStatusResultUpdate(final SubscriptionResponse.Payload.Data.Action a,
            final String result,
            final Integer status) throws Exception {
        a.setResult(result);
        a.setStatus(status);
        final String query = String.format(
                STATUS_RESULT_UPDATE_QUERY,
                a.getId(),
                a.getResult().replace("\"", "\\\""),
                a.getStatus());
        final GraphQlQuery queryObject = new GraphQlQuery();
        queryObject.setOperationName("UpdateActionStatus");
        queryObject.setQuery(query);

        final ObjectMapper mapper = new ObjectMapper(new JsonFactory());
        final String updateResult = sendHasuraRequest(queryObject);
        final UpdateResult uResult = mapper.readValue(updateResult, UpdateResult.class);

        if ((uResult.getData().getUpdate_action().getAffected_rows() == null)
                    || !uResult.getData().getUpdate_action().getAffected_rows().equals(1)) {
            // some error occured
            log.error("Unexpected response when updating action result:\n"
                        + updateResult);
        }
    }
}

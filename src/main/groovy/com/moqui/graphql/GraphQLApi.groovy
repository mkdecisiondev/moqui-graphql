/*
 * This software is in the public domain under CC0 1.0 Universal plus a
 * Grant of Patent License.
 *
 * To the extent possible under law, the author(s) have dedicated all
 * copyright and related and neighboring rights to this software to the
 * public domain worldwide. This software is distributed without any
 * warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication
 * along with this software (see the LICENSE.md file). If not, see
 * <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.moqui.graphql

import com.moqui.impl.service.GraphQLSchemaDefinition
import graphql.ExceptionWhileDataFetching
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.GraphQLError
import graphql.language.Document
import graphql.parser.Parser
import groovy.transform.CompileStatic
import org.antlr.v4.runtime.misc.ParseCancellationException
import org.moqui.context.ExecutionContextFactory
import org.moqui.resource.ResourceReference
import org.moqui.util.MNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CompileStatic
class GraphQLApi {
    protected final static Logger logger = LoggerFactory.getLogger(GraphQLApi.class)

    @SuppressWarnings("GrFinalVariableAccess")
    protected final ExecutionContextFactory ecf

    static long RUN_TIME_WARN_THRESHOLD
    private GraphQL graphQL

    private final long graphQLTTI
    private long lastLoadTime = 0

    private Map<String, MNode> schemaNodeMap = new LinkedHashMap<>()

    GraphQLApi(ExecutionContextFactory ecf) {
        this.ecf = ecf

        String graphQLTtiProperty = System.getProperty("service.graphql.graphql.tti")
        graphQLTTI = graphQLTtiProperty ? graphQLTtiProperty.toLong() * 1000 : Long.MAX_VALUE

        String runTimeWarnThresholdProperty = System.getProperty("service.graphql.graphql.runTimeWarnThreshold")
        RUN_TIME_WARN_THRESHOLD = runTimeWarnThresholdProperty ? runTimeWarnThresholdProperty.toLong() : 1000

        loadSchemaNode()
    }

    GraphQLResult execute(String requestString) {
        return execute(requestString, null, (Object) null, Collections.<String, Object> emptyMap())
    }

    GraphQLResult execute(String requestString, Map<String, Object> arguments) {
        return execute(requestString, null, (Object) null, arguments)
    }

    GraphQLResult execute(String requestString, String operationName, Object context, Map<String, Object> arguments) {
        if ((System.currentTimeMillis() - lastLoadTime) > graphQLTTI) loadSchemaNode()

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query("${requestString}")
                .operationName(operationName)
                .context(context)
                .variables(arguments)
                .build()
        ExecutionResult executionResult = graphQL.execute(executionInput)
        return new GraphQLResult(executionResult)
    }

    Document parse (String requestString) {
        Parser parser = new Parser()
        Document document
        try {
            document = parser.parseDocument(requestString)
        } catch (ParseCancellationException e) {
            logger.error("Parse graphql error: ${e}")
        }
        return document
    }

    synchronized void loadSchemaNode() {
        long startTime = System.currentTimeMillis()
        boolean needToReload = false

        // find *.graphql.xml files in component/service directories
        for (String location in this.ecf.getComponentBaseLocations().values()) {
            ResourceReference serviceDirRr = this.ecf.getResource().getLocationReference(location + "/service")
            if (serviceDirRr.supportsAll()) {
                // if for some weird reason this isn't a directory, skip it
                if (!serviceDirRr.exists || !serviceDirRr.isDirectory()) continue
                for (ResourceReference rr in serviceDirRr.directoryEntries) {
                    if (!rr.fileName.endsWith(".graphql.xml")) continue

                    long lastModified = rr.supportsLastModified() ? rr.lastModified : 0
                    if (lastLoadTime >= lastModified) continue

                    needToReload = true

                    logger.info("Adding ${rr.fileName} to be loaded")
                    // Parse .graphql.xml, add schema to cache
                    MNode schemaNode = MNode.parse(rr)
                    schemaNodeMap.put(rr.fileName, schemaNode)
                }
            } else {
                logger.warn("Can't load GraphQL APIs from component at [${serviceDirRr.location}] because it doesn't support exists/directory/etc")
            }
        }
        if (needToReload) {
            GraphQLSchemaDefinition.clearAllCachedGraphQLTypes()

            GraphQLSchemaDefinition schemaDef = new GraphQLSchemaDefinition(ecf, schemaNodeMap)
            graphQL = GraphQL.newGraphQL(schemaDef.getSchema()).build()

            lastLoadTime = System.currentTimeMillis()
            logger.info("Loaded GraphQL schema, in ${System.currentTimeMillis() - startTime}ms")
        }
    }

    static class GraphQLResult {
        Map responseObj = new HashMap<String, Object>()

        GraphQLResult(ExecutionResult executionResult) {
            if (executionResult.getErrors().size() > 0) {
                List<Map<String, Object>> errors = new ArrayList<>()
                Map<String, Object> errorMap

                for (GraphQLError error in executionResult.getErrors()) {
                    errorMap = new LinkedHashMap<>()
                    errorMap.put('message', error.getMessage())
                    errorMap.put('errorType', error.getErrorType().toString())
                    if (error instanceof ExceptionWhileDataFetching) {
                        Throwable t = ((ExceptionWhileDataFetching) error).getException()
                        if (t instanceof DataFetchingException)
                            errorMap.put('errorCode', ((DataFetchingException) t).errorCode)
                    }
                    errors.add(errorMap)
                }
                responseObj.put('errors', errors)
            }
            responseObj.put("data", executionResult.getData())
        }

    }

}

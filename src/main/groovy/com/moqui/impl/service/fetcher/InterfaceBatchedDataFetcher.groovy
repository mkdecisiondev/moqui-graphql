package com.moqui.impl.service.fetcher

import com.moqui.graphql.GraphQLApi
import com.moqui.impl.util.GraphQLSchemaUtil
import graphql.schema.DataFetchingEnvironment
import org.moqui.context.ExecutionContext
import org.moqui.context.ExecutionContextFactory
import org.moqui.entity.EntityFind
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.moqui.impl.context.UserFacadeImpl
import org.moqui.impl.entity.EntityDefinition
import org.moqui.util.MNode

import org.slf4j.Logger
import org.slf4j.LoggerFactory


import static com.moqui.impl.service.GraphQLSchemaDefinition.FieldDefinition

class InterfaceBatchedDataFetcher extends BaseDataFetcher {
    protected final static Logger logger = LoggerFactory.getLogger(InterfaceBatchedDataFetcher.class)

    String primaryField
    String resolverField
    String requireAuthentication
    String operation
    String fieldRawType
    InternalDataFetcher defaultFetcher
    Map<String, String> relKeyMap = new HashMap<>(1)
    Map<String, InternalDataFetcher> resolverFetcherMap = new HashMap<>(1)
    boolean useCache

    InterfaceBatchedDataFetcher(MNode node, MNode refNode, FieldDefinition fieldDef, ExecutionContextFactory ecf) {
        super(fieldDef, ecf)

        primaryField = node.attribute("primary-field") ?: (refNode != null ? refNode.attribute("primary-field") : "")
        resolverField = node.attribute("resolver-field") ?: (refNode != null ? refNode.attribute("resolver-field") : "")
        useCache = "true" == (node.attribute("cache") ?: (refNode != null ? refNode.attribute("cache") : "false"))

        Map<String, String> pkRelMap = new HashMap<>(1)
        pkRelMap.put(primaryField, primaryField)

        ArrayList<MNode> keyMapChildren = node.children("key-map") ?: refNode?.children("key-map")
        for (MNode keyMapNode in keyMapChildren) {
            relKeyMap.put(keyMapNode.attribute("field-name"), keyMapNode.attribute("related") ?: keyMapNode.attribute("field-name"))
        }

        ArrayList<MNode> defaultFetcherChildren = node.children("default-fetcher") ?: refNode?.children("default-fetcher")

        if (defaultFetcherChildren.size() != 1) throw new IllegalArgumentException("interface-fetcher.default-fetcher not found")
        MNode defaultFetcherNode = defaultFetcherChildren[0]
        defaultFetcher = buildDataFetcher(defaultFetcherNode.children[0], fieldDef, ecf, relKeyMap)

        ArrayList<MNode> resolverFetcherChildren = node.children("resolver-fetcher") ?: refNode?.children("resolver-fetcher")
        for (MNode resolverFetcherNode in resolverFetcherChildren) {
            String resolverValue = resolverFetcherNode.attribute("resolver-value")
            InternalDataFetcher dataFetcher = buildDataFetcher(resolverFetcherNode.children[0], fieldDef, ecf, pkRelMap)
            resolverFetcherMap.put(resolverValue, dataFetcher)
        }

        initializeFields()
    }

    private void initializeFields() {
        this.requireAuthentication = fieldDef.requireAuthentication ?: "true"
        this.fieldRawType = fieldDef.type
        if ("true".equals(fieldDef.isList)) this.operation = "list"
        else this.operation = "one"
    }

    private static InternalDataFetcher buildDataFetcher(MNode node, FieldDefinition fieldDef, ExecutionContextFactory ecf, Map<String, String> relKeyMap) {
        switch (node.name) {
            case "entity-fetcher":
                return new InternalEntityDataFetcher(node, fieldDef, ecf, relKeyMap)
                break
            case "service-fetcher":
                return new InternalServiceDataFetcher(node, fieldDef, ecf, relKeyMap)
                break
        }
    }

    private List<Map<String, Object>> mergeWithConcreteValue(List<Map<String, Object>> interfaceValueList) {
        Set<String> resolverValues = new HashSet<>()

        interfaceValueList.each { Map<String, Object> it ->
            resolverValues.add(it.get(resolverField) as String)
        }

        for (String resolverValue in resolverValues) {
            InternalDataFetcher resolverFetcher = resolverFetcherMap.get(resolverValue)
            if (resolverFetcher == null) continue

            List<Map<String, Object>> filterValueList = interfaceValueList.findAll { Map<String, Object> it ->
                it.get(resolverField) == resolverValue
            }

            List<Map<String, Object>> concreteValueList = resolverFetcher.searchFormMap(filterValueList, [:], null)

            concreteValueList.each { Map<String, Object> concreteValue ->
                Map<String, Object> interValue = interfaceValueList.find { Map<String, Object> it ->
                    it.get(primaryField) == concreteValue.get(primaryField)
                }
                if (interValue != null) interValue.putAll(concreteValue)
            }
        }
        return interfaceValueList
    }

    private Map<String, Object> mergeWithConcreteValue(Map<String, Object> interfaceValue) {
        if (interfaceValue == null) return interfaceValue
        String resolverValue = interfaceValue.get(resolverField)
        InternalDataFetcher resolverFetcher = resolverFetcherMap.get(resolverValue)
        if (resolverFetcher == null) return interfaceValue

        Map<String, Object> concreteValue = resolverFetcher.searchFormMap(interfaceValue, [:], null)
        if (concreteValue != null) interfaceValue.putAll(concreteValue)
        return interfaceValue
    }

    private static boolean matchParentByRelKeyMap(Map<String, Object> sourceItem, Map<String, Object> self, Map<String, String> relKeyMap) {
        int found = -1
        for (Map.Entry<String, String> entry in relKeyMap.entrySet()) {
            found = (found == -1) ? (sourceItem.get(entry.key) == self.get(entry.value) ? 1 : 0)
                    : (found == 1 && sourceItem.get(entry.key) == self.get(entry.value) ? 1 : 0)
        }
        return found == 1
    }

    @Override
    Object fetch(DataFetchingEnvironment environment) {
//        logger.info("---- running interface data fetcher on primary field [${primaryField}] operation [${operation}] ...")
//        logger.info("source     - ${environment.source}")
//        logger.info("arguments  - ${environment.arguments}")
//        logger.info("context    - ${environment.context}")
//        logger.info("fields     - ${environment.fields}")
//        logger.info("fieldType  - ${environment.fieldType}")
//        logger.info("parentType - ${environment.parentType}")
//        logger.info("schema     - ${environment.graphQLSchema}")
//        logger.info("relKeyMap  - ${relKeyMap}")

        long startTime = System.currentTimeMillis()
        ExecutionContext ec = environment.context as ExecutionContext

        // ASA: BatchedExecutionStrategy used to populate the environment source field as a List, AsyncExecutionStrategy
        // does not guarantee that anymore so we create a singleton list if it's a map
        List sourceList = environment.source instanceof List ? (List) environment.source : Collections.singletonList(environment.source)
        int sourceItemCount = sourceList.size()
        int relKeyCount = relKeyMap.size()

        if (sourceItemCount == 0)
            throw new IllegalArgumentException("Source should be wrapped in List with at least 1 item")

        if (sourceItemCount > 1 && relKeyCount == 0 && operation == "one")
            throw new IllegalArgumentException("Source contains more than 1 item, but no relationship key map defined")

        boolean loggedInAnonymous = false
        if ("anonymous-all".equals(requireAuthentication)) {
            ec.artifactExecution.setAnonymousAuthorizedAll()
            loggedInAnonymous = ec.getUser().loginAnonymousIfNoUser()
        } else if ("anonymous-view".equals(requireAuthentication)) {
            ec.artifactExecution.setAnonymousAuthorizedView()
            loggedInAnonymous = ec.getUser().loginAnonymousIfNoUser()
        }

        try {
            Map<String, Object> inputFieldsMap = new HashMap<>()
            GraphQLSchemaUtil.transformArguments(environment.arguments, inputFieldsMap)

            List<Map<String, Object>> resultList = new ArrayList<>((sourceItemCount != 0 ? sourceItemCount : 1) as int)
            for (int i = 0; i < sourceItemCount; i++) resultList.add(null)

            Map<String, Object> jointOneMap
            String cursor

            if (operation == "one") {
//                logger.warn("running one operation")
                List<Map<String, Object>> interfaceValueList
                if (!useCache) {
                    interfaceValueList = defaultFetcher.searchFormMap(sourceList, inputFieldsMap, environment)
                    mergeWithConcreteValue(interfaceValueList)
                } else {
                    interfaceValueList = new ArrayList<>(sourceList.size())
                    sourceList.eachWithIndex { Object object, int index ->
                        Map sourceItem = (Map) object
                        Map<String, Object> interfaceValue = defaultFetcher.searchFormMap(sourceItem, inputFieldsMap, environment)

                        if (interfaceValue != null) {
                            mergeWithConcreteValue(interfaceValue)
                            interfaceValueList.add(interfaceValue)
                        }
                    }
                }

                sourceList.eachWithIndex { Object object, int index ->
                    Map sourceItem = (Map) object

                    jointOneMap = relKeyCount == 0 ? (interfaceValueList.size() > 0 ? interfaceValueList[0] : null) :
                            interfaceValueList.find { Map<String, Object> it -> matchParentByRelKeyMap(sourceItem, it, relKeyMap) }

                    if (jointOneMap == null) return
                    cursor = GraphQLSchemaUtil.encodeRelayCursor(jointOneMap, [primaryField])
                    jointOneMap.put("id", cursor)
                    resultList.set(index, jointOneMap)
                }
            } else { // Operation == "list"
                Map<String, Object> resultMap
                Map<String, Object> edgesData
                List<Map<String, Object>> edgesDataList

                if (!GraphQLSchemaUtil.requirePagination(environment)) {
//                    logger.warn("running list with batch")
                    inputFieldsMap.put("pageNoLimit", "true")
                    List<Map<String, Object>> interfaceValueList = defaultFetcher.searchFormMap(sourceList, inputFieldsMap, environment)

                    if (!useCache) {
                        mergeWithConcreteValue(interfaceValueList)
                    } else {
                        interfaceValueList = interfaceValueList.collect { Map<String, Object> interfaceValue -> mergeWithConcreteValue(interfaceValue) }
                    }

                    sourceList.eachWithIndex { Object object, int index ->
                        Map sourceItem = (Map) object
                        List<Map<String, Object>> jointOneList = relKeyCount == 0 ? interfaceValueList :
                            interfaceValueList.findAll { Map<String, Object> it -> matchParentByRelKeyMap(sourceItem, it, relKeyMap) }


                        edgesDataList = jointOneList.collect { Map<String, Object> it ->
                            edgesData = new HashMap<>(2)
                            cursor = GraphQLSchemaUtil.encodeRelayCursor(it, [primaryField])
                            it.put("id", cursor)
                            edgesData.put("cursor", cursor)
                            edgesData.put("node", it)
                            return edgesData
                        }
                        resultMap = new HashMap<>(1)
                        resultMap.put("edges", edgesDataList)
                        resultList.set(index, resultMap)
                    }
                } else { // Used pagination or field selection set includes pageInfo
//                    logger.warn("running list with no batch!!!!")
                    sourceList.eachWithIndex { Object object, int index ->
                        Map sourceItem = (Map) object

                        Map<String, Object> interfaceValueMap = defaultFetcher.searchFormMapWithPagination([sourceItem], inputFieldsMap, environment)
                        List<Map<String, Object>> interfaceValueList = (List<Map<String, Object>>) interfaceValueMap.data

                        if (!useCache) {
                            interfaceValueList = mergeWithConcreteValue(interfaceValueList)
                        } else {
                            interfaceValueList.collect { Map<String, Object> interfaceValue -> mergeWithConcreteValue(interfaceValue) }
                        }

                        int count = interfaceValueMap.count as int
                        int pageIndex = interfaceValueMap.pageIndex as int
                        int pageSize = interfaceValueMap.pageSize as int
                        int pageMaxIndex = ((count - 1) as BigDecimal).divide(pageSize as BigDecimal, 0, BigDecimal.ROUND_DOWN).intValue()
                        int pageRangeLow = pageIndex * pageSize + 1
                        int pageRangeHigh = (pageIndex * pageSize) + pageSize
                        if (pageRangeHigh > count) pageRangeHigh = count
                        boolean hasPreviousPage = pageIndex > 0
                        boolean hasNextPage = pageMaxIndex > pageIndex

                        resultMap = new HashMap<>(2)
                        Map<String, Object> pageInfo = ['pageIndex'      : pageIndex, 'pageSize': pageSize, 'totalCount': count,
                                                        'pageMaxIndex'   : pageMaxIndex, 'pageRangeLow': pageRangeLow, 'pageRangeHigh': pageRangeHigh,
                                                        'hasPreviousPage': hasPreviousPage, 'hasNextPage': hasNextPage] as Map<String, Object>

                        edgesDataList = new ArrayList(interfaceValueList.size())

                        if (interfaceValueList != null && interfaceValueList.size() > 0) {
                            pageInfo.put("startCursor", GraphQLSchemaUtil.encodeRelayCursor(interfaceValueList.get(0), [primaryField]))
                            pageInfo.put("endCursor", GraphQLSchemaUtil.encodeRelayCursor(interfaceValueList.get(interfaceValueList.size() - 1), [primaryField]))
                            edgesDataList = interfaceValueList.collect { Map<String, Object> it ->
                                edgesData = new HashMap<>(2)
                                cursor = GraphQLSchemaUtil.encodeRelayCursor(it, [primaryField])
                                it.put("id", cursor)
                                edgesData.put("cursor", cursor)
                                edgesData.put("node", it)
                                return edgesData
                            }
                        }
                        resultMap.put("edges", edgesDataList)
                        resultMap.put("pageInfo", pageInfo)
                        resultList.set(index, resultMap)
                    }
                }
            }
            long runTime = System.currentTimeMillis() - startTime
            if (runTime > GraphQLApi.RUN_TIME_WARN_THRESHOLD) {
                logger.warn("ran interface batched data fetcher with operation [${operation}] use cache ${useCache} in ${runTime}ms")
            } else {
                logger.info("ran interface batched data fetcher with operation [${operation}] use cache ${useCache} in ${runTime}ms")
            }
            // ASA: when the operation is one, the result should be the actual object and not a list with that object
//            logger.info("resultList     - ${resultList}")
            if (operation == "one" || (!(environment.source instanceof List) && operation == "list")) {
                return resultList.empty ? null : resultList.get(0)
            } else {
                return resultList
            }
        }
        finally {
            if (loggedInAnonymous) ((UserFacadeImpl) ec.getUser()).logoutAnonymousOnly()
        }
        return null
    }

    static abstract class InternalDataFetcher {
        FieldDefinition fieldDef
        ExecutionContextFactory ecf
        Map<String, String> relKeyMap = new HashMap<>(1)

        InternalDataFetcher(FieldDefinition fieldDef, ExecutionContextFactory ecf, Map<String, String> relKeyMap) {
            this.fieldDef = fieldDef
            this.ecf = ecf
            this.relKeyMap.putAll(relKeyMap)
        }

        abstract List<Map<String, Object>> searchFormMap(List<Object> sourceItems, Map<String, Object> inputFieldsMap, DataFetchingEnvironment environment)
        abstract Map<String, Object> searchFormMap(Map sourceItem, Map<String, Object> inputFieldsMap, DataFetchingEnvironment environment)
        abstract Map<String, Object> searchFormMapWithPagination(List<Object> sourceItems, Map<String, Object> inputFieldsMap, DataFetchingEnvironment environment)

//        abstract List<Map<String, Object>> getByPkField(String pkField, List<String> pkValues)
    }

    static class InternalEntityDataFetcher extends InternalDataFetcher {
        String entityName
        boolean useCache = false

        InternalEntityDataFetcher(MNode node, FieldDefinition fieldDef, ExecutionContextFactory ecf, Map<String, String> relKeyMap) {
            super(fieldDef, ecf, relKeyMap)

            entityName = node.attribute("entity-name")

            EntityDefinition ed = ((ExecutionContextFactoryImpl) ecf).entityFacade.getEntityDefinition(entityName)
            if (ed == null) throw new IllegalArgumentException("Entity ${entityName} not found")

            if (node.attribute("cache")) {
                useCache = ("true" == node.attribute("cache")) && !ed.entityInfo.neverCache
            } else {
                useCache = "true" == ed.entityInfo.useCache
            }
        }

        @Override
        List<Map<String, Object>> searchFormMap(List<Object> sourceItems, Map<String, Object> inputFieldsMap, DataFetchingEnvironment environment) {
            ExecutionContext ec = ecf.getExecutionContext()
            EntityFind ef = ec.entity.find(entityName).useCache(useCache).searchFormMap(inputFieldsMap, null, null, null, false)
            if (environment) GraphQLSchemaUtil.addPeriodValidArguments(ec, ef, environment.arguments)

            DataFetcherUtils.patchWithConditions(ef, sourceItems, relKeyMap, ec)

            return ef.list().getValueMapList()
        }

        @Override
        Map<String, Object> searchFormMap(Map sourceItem, Map<String, Object> inputFieldsMap, DataFetchingEnvironment environment) {
            ExecutionContext ec = ecf.getExecutionContext()
            EntityFind ef = ec.entity.find(entityName).useCache(useCache).searchFormMap(inputFieldsMap, null, null, null, false)
            if (environment) GraphQLSchemaUtil.addPeriodValidArguments(ec, ef, environment.arguments)

            DataFetcherUtils.patchFindOneWithConditions(ef, sourceItem, relKeyMap, ec)
            return ef.one()?.getMap()
        }

        @Override
        Map<String, Object> searchFormMapWithPagination(List<Object> sourceItems, Map<String, Object> inputFieldsMap, DataFetchingEnvironment environment) {
            ExecutionContext ec = ecf.getExecutionContext()
            EntityFind ef = ec.entity.find(entityName).searchFormMap(inputFieldsMap, null, null, null, false)
            if (environment) GraphQLSchemaUtil.addPeriodValidArguments(ec, ef, environment.arguments)

            DataFetcherUtils.patchWithConditions(ef, sourceItems, relKeyMap, ec)

            if (!ef.getLimit()) ef.limit(100)
            ef.useCache(useCache)

            Map<String, Object> resultMap = new HashMap<>()

            resultMap.put("pageIndex", ef.getPageIndex())
            resultMap.put("pageSize", ef.getPageSize())
            resultMap.put("count", ef.count())
            resultMap.put("data", ef.list().getValueMapList())
            return resultMap
        }
    }

    static class InternalServiceDataFetcher extends InternalDataFetcher {
        InternalServiceDataFetcher(MNode node, FieldDefinition fieldDef, ExecutionContextFactory ecf, Map<String, String> relKeyMap) {
            super(fieldDef, ecf, relKeyMap)
        }

        @Override
        List<Map<String, Object>> searchFormMap(List<Object> sourceItems, Map<String, Object> inputFieldsMap, DataFetchingEnvironment environment) {
            throw new IllegalArgumentException("The method is not supported yet.")
        }

        @Override
        Map<String, Object> searchFormMap(Map sourceItem, Map<String, Object> inputFieldsMap, DataFetchingEnvironment environment) {
            throw new IllegalArgumentException("The method is not supported yet.")
        }

        @Override
        Map<String, Object> searchFormMapWithPagination(List<Object> sourceItems, Map<String, Object> inputFieldsMap, DataFetchingEnvironment environment) {
            throw new IllegalArgumentException("The method is not supported yet.")
        }
    }
}

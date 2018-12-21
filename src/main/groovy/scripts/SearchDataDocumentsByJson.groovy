import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.index.query.QueryBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.SearchHit
import org.elasticsearch.search.SearchHits
import org.elasticsearch.search.sort.SortOrder
import org.moqui.context.ExecutionContext
import org.moqui.elasticsearch.ElasticSearchUtil
import org.moqui.elasticsearch.EsClient

ExecutionContext ec = context.ec
String indexName = context.indexName
String documentType = context.documentType
String queryJson = context.queryJson
Integer fromOffset = context.fromOffset
Integer sizeLimit = context.sizeLimit
EsClient elasticSearchClient = ec.getTool("ElasticSearch", EsClient.class)

fromOffset = pageIndex * pageSize
sizeLimit = pageSize

documentList = []

// make sure index exists
if (!ElasticSearchUtil.checkIndexExists(indexName, ec)) {
	ec.loggerFacade.warn("Tried to search with indexName ${indexName} that does not exist, returning empty list")
	documentListCount = 0
	documentListPageIndex = pageIndex
	documentListPageSize = pageSize
	documentListPageMaxIndex = 0
	documentListPageRangeLow = 0
	documentListPageRangeHigh = 0
	return
}

QueryBuilder queryBuilder = QueryBuilders.wrapperQuery((String) queryJson)

SearchRequest searchRequest = new SearchRequest(indexName as String)
searchRequest.source().from(fromOffset).size(sizeLimit).fetchSource(true)
	.query(queryBuilder)

if (documentType) searchRequest.types(((String) documentType).split(","))
for (String orderByField in orderByFields) {
	boolean ascending = true
	if (orderByField.charAt(0) == '-') {
		ascending = false
		orderByField = orderByField.substring(1)
	} else if (orderByField.charAt(0) == '+') {
		ascending = true
		orderByField = orderByField.substring(1)
	}
	// ec.logger.warn("========= adding ${orderByField}, ${ascending}")
	searchRequest.source().sort(orderByField, ascending ? SortOrder.ASC : SortOrder.DESC)
}

SearchHits hits = elasticSearchClient.search(searchRequest).getHits()
documentListCount = hits.getTotalHits()
for (SearchHit hit in hits) {
	Map document = hit.getSourceAsMap()
	// As of ES 2.0 _index, _type, _id aren't included in the document
	document._index = hit.getIndex()
	document._type = hit.getType()
	document._id = hit.getId()
	// how to get timestamp? doesn't seem to be in API: document._timestamp = hit.get?
	document._version = hit.getVersion()
	documentList.add(flattenDocument ? flattenNestedMap(document) : document)
}

// calculate the pagination values
documentListPageIndex = pageIndex
documentListPageSize = pageSize
documentListPageMaxIndex = ((BigDecimal) documentListCount - 1).divide(documentListPageSize, 0, BigDecimal.ROUND_DOWN) as int
documentListPageRangeLow = documentListPageIndex * documentListPageSize + 1
documentListPageRangeHigh = (documentListPageIndex * documentListPageSize) + documentListPageSize
if (documentListPageRangeHigh > documentListCount) documentListPageRangeHigh = documentListCount

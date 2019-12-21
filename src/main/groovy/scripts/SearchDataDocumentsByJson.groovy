import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.index.query.QueryBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.sort.SortOrder
import org.moqui.context.ElasticFacade
import org.moqui.context.ExecutionContext

ExecutionContext ec = context.ec
String indexName = context.indexName
String documentType = context.documentType
String queryJson = context.queryJson
Integer fromOffset = context.fromOffset
Integer sizeLimit = context.sizeLimit
ElasticFacade.ElasticClient elasticSearchClient = ec.elastic.default

fromOffset = pageIndex * pageSize
sizeLimit = pageSize

documentList = []

// make sure index exists
if (!elasticSearchClient.indexExists(indexName)) {
	ec.logger.warn("Tried to search with indexName ${indexName} that does not exist, returning empty list")
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
def sourceMap = elasticSearchClient.jsonToObject(searchRequest.source().toString()) as Map

def hits = elasticSearchClient.search(indexName, sourceMap).hits
documentListCount = hits.total?.value

for (hit in hits.hits) {
	Map document = hit._source
	document._index = hit._index
	document._id = hit._id
	document._version = hit._version
	documentList.add(flattenDocument ? flattenNestedMap(document) : document)
}

// calculate the pagination values
documentListPageIndex = pageIndex
documentListPageSize = pageSize
documentListPageMaxIndex = ((BigDecimal) documentListCount - 1).divide(documentListPageSize, 0, BigDecimal.ROUND_DOWN) as int
documentListPageRangeLow = documentListPageIndex * documentListPageSize + 1
documentListPageRangeHigh = (documentListPageIndex * documentListPageSize) + documentListPageSize
if (documentListPageRangeHigh > documentListCount) documentListPageRangeHigh = documentListCount

package org.statemach.db.graphql;

import org.statemach.db.schema.ForeignKey;
import org.statemach.db.schema.PrimaryKey;
import org.statemach.db.schema.Schema;
import org.statemach.db.schema.TableInfo;
import org.statemach.db.sql.DataAccess;
import org.statemach.db.sql.From;
import org.statemach.db.sql.Join;
import org.statemach.db.sql.SQLBuilder;
import org.statemach.db.sql.Select;
import org.statemach.db.sql.View;
import org.statemach.util.Java;
import org.statemach.util.NodeLinkTree;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLType;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;

public class GraphQLQuery {

    static interface Argument {
        static final String FILTER = "filter";
        static final String LIMIT  = "limit";
        static final String ORDER  = "order";
        static final String SKIP   = "skip";
    }

    static final String QUERY_TYPE     = "QueryType";
    static final String ID_COLUMN_NAME = "id";

    static final String CTE_FILTER_NAME = "filter";

    final Schema              schema;
    final DataAccess          dataAccess;
    final SQLBuilder          sqlBuilder;
    final GraphQLQueryExtract extract;
    final GraphQLQueryFilter  filter;
    final GraphQLQueryOrder   order;

    GraphQLQuery(Schema schema,
                 DataAccess dataAccess,
                 SQLBuilder sqlBuilder,
                 GraphQLQueryExtract extract,
                 GraphQLQueryFilter filter,
                 GraphQLQueryOrder order) {
        this.schema = schema;
        this.dataAccess = dataAccess;
        this.sqlBuilder = sqlBuilder;
        this.extract = extract;
        this.filter = filter;
        this.order = order;
    }

    public static GraphQLQuery of(Schema schema, GraphQLNaming naming, DataAccess dataAccess) {
        GraphQLMapping mapping = GraphQLMapping.of(schema.vendor);

        return new GraphQLQuery(schema,
                dataAccess,
                dataAccess.builder(),
                new GraphQLQueryExtract(schema, naming, mapping),
                new GraphQLQueryFilter(schema, dataAccess.builder(), naming, mapping),
                new GraphQLQueryOrder(schema, naming));
    }

    public GraphQLObjectType buildQueryType() {
        return GraphQLObjectType.newObject()
            .name(QUERY_TYPE)
            .fields(schema.tables.keySet()
                .map(t -> extract.buildQueryField(t, t))
                .toJavaList())
            .build();
    }

    public List<GraphQLType> buildAddtionalTypes() {
        return extract.buildAllTypes()
            .appendAll(filter.buildAllTypes())
            .appendAll(order.buildAllTypes());
    }

    public List<Tuple2<FieldCoordinates, DataFetcher<?>>> buildAllFetchers() {
        return buildQueryFetchers();
    }

    List<Tuple2<FieldCoordinates, DataFetcher<?>>> buildQueryFetchers() {
        return schema.tables.values()
            .map(this::buildQueryFetcher)
            .toList();
    }

    Tuple2<FieldCoordinates, DataFetcher<?>> buildQueryFetcher(TableInfo table) {
        return new Tuple2<>(
                FieldCoordinates.coordinates(QUERY_TYPE, table.name),
                e -> fetchQuery(table, e));
    }

    Object fetchQuery(TableInfo table, DataFetchingEnvironment environment) throws Exception {
        return fetchQueryCommon(GraphQLField.of(environment), table, Option.none(), Option.none())
            .toJavaList();
    }

    List<Map<String, Object>> fetchSubQuery(List<Map<String, Object>> result, SubQuery q) {
        Set<List<Object>> ids         = result.map(r -> q.extracts.map(e -> r.get(e.name).get())).toSet();
        List<String>      fromColumns = q.incoming.matchingColumns.map(m -> m.from);
        String            fk          = q.incoming.name;

        List<java.util.Map<String, Object>> subResult = fetchQueryCommon(
                q.field,
                q.table,
                Option.of(fromColumns),
                Option.of(new Tuple2<>(fk, ids)));

        Map<List<Object>, List<java.util.Map<String, Object>>> subResultById = subResult
            .groupBy(r -> fromColumns.map(r::get));

        return result.map(r -> putSubQueryResult(r, q, subResultById));
    }

    List<java.util.Map<String, Object>> fetchQueryCommon(GraphQLField field,
                                                         TableInfo table,
                                                         Option<List<String>> extraColumn,
                                                         Option<Tuple2<String, Set<List<Object>>>> columnNameWithIds) {
        Tuple2<List<Extract>, List<SubQuery>> selects = extract.parse(table, field.getSelectionSet(), extraColumn);
        List<Filter>                          filters = filter.parse(
                table,
                field.getArgument(Argument.FILTER),
                columnNameWithIds);

        List<OrderBy>         orders    = order.parse(table, field.getArgument(Argument.ORDER));
        Integer               skip      = Java.ifNull((Integer) field.getArgument(Argument.SKIP), 0);
        Integer               limit     = Java.ifNull((Integer) field.getArgument(Argument.LIMIT), 10);
        Tuple2<Long, Integer> skipLimit = new Tuple2<>(skip.longValue(), limit);

        Option<String> cteName = Option.none();

        NodeLinkTree<String, TableInfo, ForeignKeyJoin> preparedJoins = filter.buildJoins(table, filters);
        List<View<String>>                              views         = List.empty();
        if (filters.exists(f -> f.plural)) {
            views = views.append(buildFilterView(preparedJoins, filters));
            cteName = Option.of(CTE_FILTER_NAME);
            filters = List.empty();
            preparedJoins = NodeLinkTree.of(table);
        }

        preparedJoins = order.buildJoins(preparedJoins, orders);
        preparedJoins = extract.buildJoins(preparedJoins, selects._1);
        View<Tuple2<String, org.statemach.db.jdbc.Extract<?>>> extractView = buildExtractView(preparedJoins,
                cteName,
                selects._1,
                filters,
                orders,
                skipLimit);

        List<Map<String, Object>> subResult = dataAccess.query(views, extractView);
        subResult = selects._2.foldLeft(subResult, this::fetchSubQuery);

        Map<String, List<String>> paths = selects._1.toMap(e -> new Tuple2<>(e.name, e.path))
            .merge(selects._2.toMap(q -> new Tuple2<>(q.name, q.path)));
        return subResult.map(r -> buildGraphQLResult(r, paths));
    }

    java.util.Map<String, Object> buildGraphQLResult(Map<String, Object> row, Map<String, List<String>> paths) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        row.forEach(t -> putIntoMapTree(result, paths.get(t._1).get(), t._2));
        return result;
    }

    Map<String, Object> putSubQueryResult(Map<String, Object> row,
                                          SubQuery subQuery,
                                          Map<List<Object>, List<java.util.Map<String, Object>>> subResultById) {
        List<Object>                                key  = subQuery.extracts.map(e -> row.get(e.name).get());
        Option<List<java.util.Map<String, Object>>> list = subResultById.get(key);
        return row.put(subQuery.name, list.getOrElse(List.empty()).toJavaList());
    }

    void putIntoMapTree(java.util.Map<String, Object> tree, List<String> path, Object value) {
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> last = path
            .dropRight(1)
            .foldLeft(tree, (t, s) -> (java.util.Map<String, Object>) (t.computeIfAbsent(s, n -> new java.util.HashMap<>())));

        last.put(path.last(), value);
    }

    View<Tuple2<String, org.statemach.db.jdbc.Extract<?>>> buildExtractView(NodeLinkTree<String, TableInfo, ForeignKeyJoin> preparedJoins,
                                                                            Option<String> cteName,
                                                                            List<Extract> extracts,
                                                                            List<Filter> filters,
                                                                            List<OrderBy> orders,
                                                                            Tuple2<Long, Integer> skipLimit) {
        PrimaryKey                       pk    = preparedJoins.node.primary.get();
        NodeLinkTree<String, From, Join> joins = preparedJoins
            .mapNodesWithIndex(1, (t, i) -> new From(t.name, "q" + i))
            .mapLinksWithNodes(t -> buildJoin(t._1, t._2, t._3));

        var select = extract.buildExtracts(joins, extracts);
        var where  = filter.buildWhere(joins, filters);
        var sort   = order.buildOrders(joins, orders);

        if (cteName.isDefined()) {
            joins = NodeLinkTree.<String, From, Join>of(new From(cteName.get(), "f"))
                .put("", buildCteJoin("f", pk.columns.map(c -> new ForeignKey.Match(c, c)), joins.getNode()), joins);
        }

        return new View<Tuple2<String, org.statemach.db.jdbc.Extract<?>>>(
                "",
                joins,
                where,
                sort,
                select,
                false,
                skipLimit._1,
                skipLimit._2);
    }

    View<String> buildFilterView(NodeLinkTree<String, TableInfo, ForeignKeyJoin> preparedJoins, List<Filter> filters) {
        PrimaryKey                       pk    = preparedJoins.node.primary.get();
        NodeLinkTree<String, From, Join> joins = preparedJoins
            .mapNodesWithIndex(1, (t, i) -> new From(t.name, "f" + i))
            .mapLinksWithNodes(t -> buildJoin(t._1, t._2, t._3));

        var select = pk.columns.map(c -> Select.of(joins.getNode().alias, c, c));
        var where  = filter.buildWhere(joins, filters);
        return new View<>(
                CTE_FILTER_NAME,
                joins,
                where,
                List.empty(),
                select,
                true,
                0L,
                Integer.MAX_VALUE);
    }

    NodeLinkTree<String, From, Join> buildJoins(NodeLinkTree<String, TableInfo, ForeignKeyJoin> preparedJoins) {
        return preparedJoins
            .mapNodesWithIndex(1, (t, i) -> new From(t.name, "f" + i))
            .mapLinksWithNodes(t -> buildJoin(t._1, t._2, t._3));
    }

    Join buildJoin(From left, ForeignKeyJoin preparedJoin, From right) {
        return new Join(preparedJoin.joinKind,
                sqlBuilder.and(preparedJoin.foreignKey.matchingColumns
                    .map(m -> sqlBuilder.equal(
                            Select.of(left.alias, preparedJoin.outgoing ? m.from : m.to),
                            Select.of(right.alias, preparedJoin.outgoing ? m.to : m.from)))));
    }

    Join buildCteJoin(String cteAlias, List<ForeignKey.Match> matchingColumns, From right) {
        return new Join(Join.Kind.INNER,
                sqlBuilder.and(matchingColumns
                    .map(m -> sqlBuilder.equal(
                            Select.of(cteAlias, m.from),
                            Select.of(right.alias, m.to)))));
    }
}
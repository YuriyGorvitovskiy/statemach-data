package org.statemach.db.graphql;

import java.util.function.Function;

import org.statemach.db.jdbc.Extract;
import org.statemach.db.jdbc.Inject;
import org.statemach.db.schema.ColumnInfo;
import org.statemach.db.schema.PrimaryKey;
import org.statemach.db.schema.Schema;
import org.statemach.db.schema.TableInfo;
import org.statemach.db.sql.DataAccess;

import graphql.Scalars;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLType;
import graphql.schema.SelectedField;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;

public class GraphQLMutation {

    static interface Argument {
        static final String ID = "ID";
    }

    static final String MUTATION_TYPE = "MutationType";
    static final String ID_COLUMN     = "id";

    final Schema         schema;
    final GraphQLNaming  naming;
    final GraphQLMapping mapping;
    final DataAccess     dataAccess;

    GraphQLMutation(Schema schema, GraphQLNaming naming, GraphQLMapping mapping, DataAccess dataAccess) {
        this.schema = schema;
        this.naming = naming;
        this.mapping = mapping;
        this.dataAccess = dataAccess;
    }

    public static GraphQLMutation of(Schema schema, GraphQLNaming naming, DataAccess dataAccess) {
        return new GraphQLMutation(schema, naming, GraphQLMapping.of(schema.vendor), dataAccess);
    }

    public GraphQLObjectType buildMutationType() {
        return GraphQLObjectType.newObject()
            .name(MUTATION_TYPE)
            .fields(schema.tables.values()
                .filter(t -> t.primary.isDefined())
                .flatMap(this::buildMutationFields)
                .toJavaList())
            .build();
    }

    public List<GraphQLType> buildAddtionalTypes() {
        return schema.tables.values()
            .filter(t -> t.primary.isDefined())
            .flatMap(this::buildTypes)
            .toList();
    }

    public List<Tuple2<FieldCoordinates, DataFetcher<?>>> buildAllFetchers() {
        return schema.tables.values()
            .filter(t -> t.primary.isDefined())
            .flatMap(this::buildFetchers)
            .toList();
    }

    public List<Tuple2<FieldCoordinates, DataFetcher<?>>> buildFetchers(TableInfo table) {
        return List.of(
                new Tuple2<>(FieldCoordinates.coordinates(MUTATION_TYPE, naming.getInsertMutationName(table.name)),
                        (DataFetcher<?>) (e -> fetchInsert(table, e))),
                new Tuple2<>(FieldCoordinates.coordinates(MUTATION_TYPE, naming.getUpsertMutationName(table.name)),
                        (DataFetcher<?>) (e -> fetchUpsert(table, e))),
                new Tuple2<>(FieldCoordinates.coordinates(MUTATION_TYPE, naming.getUpdateMutationName(table.name)),
                        (DataFetcher<?>) (e -> fetchUpsert(table, e))),
                new Tuple2<>(FieldCoordinates.coordinates(MUTATION_TYPE, naming.getDeleteMutationName(table.name)),
                        (DataFetcher<?>) (e -> fetchUpsert(table, e))));
    }

    List<GraphQLFieldDefinition> buildMutationFields(TableInfo table) {
        return List.of(
                buildInsertMutations(table.name),
                buildUpsertMutations(table.name),
                buildUpdateMutations(table.name),
                buildDeleteMutations(table.name));
    }

    GraphQLFieldDefinition buildInsertMutations(String tableName) {
        return GraphQLFieldDefinition.newFieldDefinition()
            .name(naming.getInsertMutationName(tableName))
            .type(naming.getExtractTypeRef(tableName))
            .argument(GraphQLArgument.newArgument()
                .name(tableName)
                .type(naming.getInsertTypeRef(tableName)))
            .build();
    }

    GraphQLFieldDefinition buildUpsertMutations(String tableName) {
        return GraphQLFieldDefinition.newFieldDefinition()
            .name(naming.getUpsertMutationName(tableName))
            .type(naming.getExtractTypeRef(tableName))
            .argument(GraphQLArgument.newArgument()
                .name(Argument.ID)
                .type(Scalars.GraphQLID))
            .argument(GraphQLArgument.newArgument()
                .name(tableName)
                .type(naming.getUpdateTypeRef(tableName)))
            .build();
    }

    GraphQLFieldDefinition buildUpdateMutations(String tableName) {
        return GraphQLFieldDefinition.newFieldDefinition()
            .name(naming.getUpdateMutationName(tableName))
            .type(naming.getExtractTypeRef(tableName))
            .argument(GraphQLArgument.newArgument()
                .name(Argument.ID)
                .type(Scalars.GraphQLID))
            .argument(GraphQLArgument.newArgument()
                .name(tableName)
                .type(naming.getUpdateTypeRef(tableName)))
            .build();
    }

    GraphQLFieldDefinition buildDeleteMutations(String tableName) {
        return GraphQLFieldDefinition.newFieldDefinition()
            .name(naming.getDeleteMutationName(tableName))
            .type(naming.getExtractTypeRef(tableName))
            .argument(GraphQLArgument.newArgument()
                .name(Argument.ID)
                .type(Scalars.GraphQLID))
            .build();
    }

    List<GraphQLType> buildTypes(TableInfo table) {
        return List.of(
                buildInsertType(table),
                buildUpdateType(table));
    }

    GraphQLType buildInsertType(TableInfo table) {
        return GraphQLInputObjectType.newInputObject()
            .name(naming.getInsertTypeName(table.name))
            .fields(table.columns.values()
                .filter(this::isInsertableColumn)
                .map(this::buildMutableField)
                .toJavaList())
            .build();
    }

    GraphQLType buildUpdateType(TableInfo table) {
        return GraphQLInputObjectType.newInputObject()
            .name(naming.getUpdateTypeName(table.name))
            .fields(table.columns.values()
                .filter(c -> isUpdatableColumn(table.primary, c))
                .map(this::buildMutableField)
                .toJavaList())
            .build();
    }

    GraphQLInputObjectField buildMutableField(ColumnInfo column) {
        return GraphQLInputObjectField.newInputObjectField()
            .name(column.name)
            .type(mapping.scalar(column.type))
            .build();
    }

    boolean isInsertableColumn(ColumnInfo column) {
        return mapping.isMutable(column.type);
    }

    boolean isUpdatableColumn(Option<PrimaryKey> pk, ColumnInfo column) {
        return isInsertableColumn(column) && (pk.isEmpty() || !pk.get().columns.contains(column.name));
    }

    java.util.Map<String, Object> fetchInsert(TableInfo table, DataFetchingEnvironment environment) throws Exception {
        Map<String, Inject> entity = getEntity(table, environment);

        return dataAccess.insert(table.name, entity, returnFields(table, environment)).toJavaMap();
    }

    java.util.Map<String, Object> fetchUpsert(TableInfo table, DataFetchingEnvironment environment) throws Exception {
        Map<String, Inject> pk     = primaryKey(table, environment);
        Map<String, Inject> entity = getEntity(table, environment);

        return dataAccess.merge(table.name, pk, entity, returnFields(table, environment)).toJavaMap();
    }

    java.util.Map<String, Object> fetchUpdate(TableInfo table, DataFetchingEnvironment environment) throws Exception {
        Map<String, Inject> pk     = primaryKey(table, environment);
        Map<String, Inject> entity = getEntity(table, environment);

        return dataAccess.update(table.name, pk, entity, returnFields(table, environment)).get().toJavaMap();
    }

    java.util.Map<String, Object> fetchDelete(TableInfo table, DataFetchingEnvironment environment) throws Exception {
        Map<String, Inject> pk = primaryKey(table, environment);
        return dataAccess.delete(table.name, pk, returnFields(table, environment)).get().toJavaMap();
    }

    Map<String, Inject> primaryKey(TableInfo table, DataFetchingEnvironment environment) {
        return table.primary.isEmpty()
                ? HashMap.empty()
                : table.primary.get().columns.map(c -> columnInject(table, c, environment)).toMap(t -> t);
    }

    Tuple2<String, Inject> columnInject(TableInfo table, String columnName, DataFetchingEnvironment environment) {
        return getInject(table, columnName, environment.getArgument(columnName)).get();
    }

    Map<String, Inject> getEntity(TableInfo table, DataFetchingEnvironment environment) {
        java.util.Map<String, Object> entity = environment.getArgument(table.name);
        return HashMap.ofAll(entity)
            .flatMap(t -> getInject(table, t._1, t._2))
            .toLinkedMap(t -> t);
    }

    Option<Tuple2<String, Inject>> getInject(TableInfo table, String columnName, Object value) {
        Option<ColumnInfo> column = table.columns.get(columnName);
        if (column.isEmpty()) {
            return Option.none();
        }

        Option<Function<Object, Inject>> injector = mapping.injectors.get(column.get().type);
        if (injector.isEmpty()) {
            return Option.none();
        }

        return Option.of(new Tuple2<>(columnName, injector.get().apply(value)));
    }

    Map<String, Extract<?>> returnFields(TableInfo table, DataFetchingEnvironment environment) {
        DataFetchingFieldSelectionSet selectionSet = environment.getSelectionSet();
        return List.ofAll(selectionSet.getImmediateFields())
            .filter(f -> table.columns.containsKey(f.getName()))
            .map(SelectedField::getName)
            .append(ID_COLUMN)
            .distinct()
            .map(c -> new Tuple2<>(c, mapping.extract(table.columns.get(c).get().type)))
            .toLinkedMap(t -> t);
    }

}
package com.yg.inventory.data.graphql;

import java.io.IOException;

import org.statemach.util.Json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import com.yg.inventory.data.db.DataAccess;
import com.yg.inventory.data.db.SchemaAccess;
import com.yg.inventory.model.db.Schema;
import com.yg.util.Rest;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLSchema;
import io.vavr.collection.List;
import io.vavr.collection.Map;

public class GraphQLHandler implements HttpHandler {

    static interface Param {
        static final String OPERATION_NAME = "operationName";
        static final String QUERY          = "query";
        static final String VARIABLES      = "variables";
    }

    static class Input {
        public String              query;
        public String              operationName;
        public Map<String, Object> variables;

        public ExecutionInput buildExecutionInput() {
            return ExecutionInput.newExecutionInput(query)
                .operationName(operationName)
                .variables(variables.toJavaMap())
                .build();
        }
    }

    final GraphQL graphQL;

    GraphQLHandler(GraphQL graphQL) {
        this.graphQL = graphQL;
    }

    public static GraphQLHandler build() {
        Schema          schema     = Schema.from(new SchemaAccess());
        GraphQLNaming   naming     = new GraphQLNaming();
        DataAccess      dataAccess = new DataAccess();
        GraphQLQuery    query      = GraphQLQuery.of(schema, naming, dataAccess);
        GraphQLMutation mutation   = GraphQLMutation.of(schema, naming, dataAccess);
        GraphQLSchema   schemaQL   = buildSchema(query, mutation);
        GraphQL         graphQL    = GraphQL.newGraphQL(schemaQL).build();

        return new GraphQLHandler(graphQL);
    }

    static GraphQLSchema buildSchema(GraphQLQuery query, GraphQLMutation mutation) {
        GraphQLCodeRegistry.Builder code = GraphQLCodeRegistry.newCodeRegistry();
        query.buildAllFetchers().forEach(t -> code.dataFetcher(t._1, t._2));
        mutation.buildAllFetchers().forEach(t -> code.dataFetcher(t._1, t._2));

        return GraphQLSchema.newSchema()
            .query(query.buildQueryType())
            .mutation(mutation.buildMutationType())
            .additionalTypes(query.buildAddtionalTypes().toJavaSet())
            .additionalTypes(mutation.buildAddtionalTypes().toJavaSet())
            .codeRegistry(code.build())
            .build();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        switch (exchange.getRequestMethod().toUpperCase()) {
            case "GET":
                get(exchange);
                break;
            case "POST":
                post(exchange);
                break;
            default:
                break;
        }
    }

    @SuppressWarnings("unchecked")
    void get(HttpExchange exchange) {
        Map<String, List<String>> params = Rest.queryParams(exchange);
        Input                     input  = new Input();

        input.query = params.get(Param.QUERY)
            .getOrElseThrow(() -> new Rest.Error(Rest.ErrorCode.BAD_REQUEST,
                    "HTTP GET request should have request parameter '${0}' specified",
                    Param.QUERY))
            .get();

        String variablesJson = params.get(Param.VARIABLES).map(l -> l.get()).getOrNull();
        input.variables = Json.parse(variablesJson, Map.class);
        input.operationName = params.get(Param.OPERATION_NAME).map(l -> l.get()).getOrNull();

        execute(exchange, input);
    }

    void post(HttpExchange exchange) {
        Input input = Rest.extract(exchange, Input.class);

        execute(exchange, input);
    }

    void execute(HttpExchange exchange, Input input) {
        ExecutionResult result = graphQL.execute(input.buildExecutionInput());
        Rest.json(exchange, result.toSpecification());
    }

}

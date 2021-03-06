package org.statemach.db.graphql;

import org.statemach.db.schema.ForeignKey;

import graphql.schema.GraphQLTypeReference;

public class GraphQLNaming {
    static interface Prefix {
        static final String INSERT = "insert_";
        static final String UPSERT = "upsert_";
        static final String UPDATE = "update_";
        static final String DELETE = "delete_";
    }

    static interface Suffix {
        static final String FILTER = "_filter";
        static final String ORDER  = "_order";
        static final String INSERT = "_insert";
        static final String UPSERT = "_upsert";
        static final String UPDATE = "_update";
        static final String DELETE = "_delete";
        static final String MUTATE = "_mutate";

        static final String REVERSE = "_reverse";

        static final String FROM = "_from";
        static final String TO   = "_to";

    }

    public GraphQLTypeReference getExtractTypeRef(String tableName) {
        return GraphQLTypeReference.typeRef(getExtractTypeName(tableName));
    }

    public String getExtractTypeName(String tableName) {
        return tableName;
    }

    public GraphQLTypeReference getMutateTypeRef(String tableName) {
        return GraphQLTypeReference.typeRef(getMutateTypeName(tableName));
    }

    public String getMutateTypeName(String tableName) {
        return tableName + Suffix.MUTATE;
    }

    public GraphQLTypeReference getFilterTypeRef(String tableName) {
        return GraphQLTypeReference.typeRef(getFilterTypeName(tableName));
    }

    public String getFilterTypeName(String tableName) {
        return tableName + Suffix.FILTER;
    }

    public GraphQLTypeReference getOrderTypeRef(String tableName) {
        return GraphQLTypeReference.typeRef(getOrderTypeName(tableName));
    }

    public String getOrderTypeName(String tableName) {
        return tableName + Suffix.ORDER;
    }

    public GraphQLTypeReference getInsertTypeRef(String tableName) {
        return GraphQLTypeReference.typeRef(getInsertTypeName(tableName));
    }

    public String getInsertTypeName(String tableName) {
        return tableName + Suffix.INSERT;
    }

    public GraphQLTypeReference getUpdateTypeRef(String tableName) {
        return GraphQLTypeReference.typeRef(getUpdateTypeName(tableName));
    }

    public String getUpdateTypeName(String tableName) {
        return tableName + Suffix.UPDATE;
    }

    public String getInsertMutationName(String tableName) {
        return Prefix.INSERT + tableName;
    }

    public String getUpsertMutationName(String tableName) {
        return Prefix.UPSERT + tableName;
    }

    public String getUpdateMutationName(String tableName) {
        return Prefix.UPDATE + tableName;
    }

    public String getDeleteMutationName(String tableName) {
        return Prefix.DELETE + tableName;
    }

    public String getReverseName(String foreignKeyName) {
        return foreignKeyName + Suffix.REVERSE;
    }

    public String getForeignKey(String reverseName) {
        return reverseName.substring(0, reverseName.length() - Suffix.REVERSE.length());
    }

    public String getFromType(ForeignKey fk) {
        return fk.name + Suffix.FROM;
    }

    public String getToType(ForeignKey fk) {
        return fk.name + Suffix.TO;
    }
}

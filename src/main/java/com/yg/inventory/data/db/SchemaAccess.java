package com.yg.inventory.data.db;

import com.yg.util.DB;
import com.yg.util.DB.DataType;
import com.yg.util.Java;

import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vavr.collection.List;
import io.vavr.collection.Map;

public class SchemaAccess {

    public static class ForeignKey {
        public final String name;
        public final String fromTable;
        public final String fromColumn;
        public final String toTable;
        public final String toColumn;

        ForeignKey(String name, String fromTable, String fromColumn, String toTable, String toColumn) {
            this.name = name;
            this.fromTable = fromTable;
            this.fromColumn = fromColumn;
            this.toTable = toTable;
            this.toColumn = toColumn;
        }
    }

    public static class PrimaryKey {
        public final String name;
        public final String table;
        public final String column;

        PrimaryKey(String name, String table, String column) {
            this.name = name;
            this.table = table;
            this.column = column;
        }
    }

    static final String QUERY_FOR_ALL_FOREIGN_KEYS   = Java.resource("QueryForAllForeignKeys.sql");
    static final String QUERY_FOR_ALL_PRIMARY_KEYS   = Java.resource("QueryForAllPrimaryKeys.sql");
    static final String QEURY_FOR_COLUMNS            = Java.resource("QueryForColumns.sql");
    static final String QEURY_FOR_TABLE              = Java.resource("QueryForTables.sql");
    static final String QUERY_FOR_TABLE_FOREIGN_KEYS = Java.resource("QueryForTableForeignKeys.sql");
    static final String QUERY_FOR_TABLE_PRIMARY_KEY  = Java.resource("QueryForTablePrimaryKey.sql");

    public Map<String, Map<String, DataType>> getTables() {
        return DB.query(QEURY_FOR_TABLE,
                ps -> {},
                rs -> new Tuple3<>(
                        rs.getString(1), // Table name
                        rs.getString(2), // Column name
                        DataType.DB_NAMES.get(rs.getString(3)).get()))
            .groupBy(t -> t._1)
            .mapValues(tl -> tl
                .groupBy(t -> t._2)
                .mapValues(cl -> cl.get()._3));
    }

    public Map<String, DataType> getTableColumns(String table) {
        return DB.query(QEURY_FOR_COLUMNS,
                ps -> ps.setString(1, table),
                rs -> new Tuple2<>(
                        rs.getString(1),
                        DataType.DB_NAMES.get(rs.getString(2)).get()))
            .toLinkedMap(t -> t);
    }

    public List<ForeignKey> getAllForeignKeys() {
        return DB.query(QUERY_FOR_ALL_FOREIGN_KEYS,
                ps -> {},
                rs -> new ForeignKey(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getString(5)));
    }

    public List<ForeignKey> getTableForeignKeys(String table) {
        return DB.query(QUERY_FOR_TABLE_FOREIGN_KEYS,
                ps -> ps.setString(1, table),
                rs -> new ForeignKey(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getString(5)));
    }

    public List<PrimaryKey> getAllPrimaryKeys() {
        return DB.query(QUERY_FOR_ALL_PRIMARY_KEYS,
                ps -> {},
                rs -> new PrimaryKey(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3)));
    }

    public PrimaryKey getTablePrimaryKey(String table) {
        return DB.query(QUERY_FOR_TABLE_PRIMARY_KEY,
                ps -> ps.setString(1, table),
                rs -> new PrimaryKey(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3)))
            .getOrNull();
    }
}
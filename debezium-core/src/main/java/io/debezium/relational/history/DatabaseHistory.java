/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.relational.history;

import java.util.Map;

import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.Type;
import org.apache.kafka.common.config.ConfigDef.Width;

import io.debezium.config.Configuration;
import io.debezium.config.Field;
import io.debezium.relational.Tables;
import io.debezium.relational.ddl.DdlParser;

/**
 * A history of the database schema described by a {@link Tables}. Changes to the database schema can be
 * {@link #record(Map, Map, String, Tables, String) recorded}, and a {@link Tables database schema} can be
 * {@link #record(Map, Map, String, Tables, String) recovered} to various points in that history.
 *
 * @author Randall Hauch
 */
public interface DatabaseHistory {

    public static final String CONFIGURATION_FIELD_PREFIX_STRING = "database.history.";

    public static final Field NAME = Field.create(CONFIGURATION_FIELD_PREFIX_STRING + "name")
            .withDisplayName("Logical name for the database history")
            .withType(Type.STRING)
            .withWidth(Width.MEDIUM)
            .withImportance(Importance.LOW)
            .withDescription("The name used for the database history, perhaps differently by each implementation.")
            .withValidation(Field::isOptional);

    public static final Field SKIP_UNPARSEABLE_DDL_STATEMENTS = Field.create(CONFIGURATION_FIELD_PREFIX_STRING + "skip.unparseable.ddl")
            .withDisplayName("Skip DDL statements that cannot be parsed")
            .withType(Type.BOOLEAN)
            .withWidth(Width.SHORT)
            .withImportance(Importance.LOW)
            .withDescription("Controls the action Debezium will take when it meets a DDL statement in binlog, that it cannot parse."
                    + "By default the connector will stop operating but by changing the setting it can ignore the statements "
                    + "which it cannot parse. If skipping is enabled then Debezium can miss metadata changes.")
            .withDefault(false);

    public static final Field STORE_ONLY_MONITORED_TABLES_DDL = Field.create(CONFIGURATION_FIELD_PREFIX_STRING + "store.only.monitored.tables.ddl")
            .withDisplayName("Store only DDL that modifies whitelisted/not-blacklisted tables")
            .withType(Type.BOOLEAN)
            .withWidth(Width.SHORT)
            .withImportance(Importance.LOW)
            .withDescription("Controls what DDL will Debezium store in database history."
                    + "By default (false) Debezium will store all incoming DDL statements. If set to true"
                    + "then only DDL that manipulates a monitored table will be stored.")
            .withDefault(false);

    public static final Field DDL_FILTER = Field.createInternal(CONFIGURATION_FIELD_PREFIX_STRING + "ddl.filter")
            .withDisplayName("DDL filter")
            .withType(Type.STRING)
            .withDefault(
                    "DROP TEMPORARY TABLE IF EXISTS .+ /\\* generated by server \\*/," +
                    // Filter out RDS heartbeat statements, see DBZ-469
                            "INSERT INTO mysql.rds_heartbeat2\\(.*\\) values \\(.*\\) ON DUPLICATE KEY UPDATE value = .*," +
                            "DELETE FROM mysql.rds_sysinfo.*," +
                            "INSERT INTO mysql.rds_sysinfo\\(.*\\) values \\(.*\\)," +
                            "DELETE FROM mysql.rds_monitor.*," +
                            "FLUSH RELAY LOGS.*," +
                            "flush relay logs.*," +
                            "SAVEPOINT .*")
            .withWidth(Width.LONG)
            .withImportance(Importance.LOW)
            .withDescription("A regular expression to filter out a subset of incoming DDL statements "
                    + "from processing and storing into schema history evolution.")
            .withValidation(Field::isListOfRegex);

    /**
     * Configure this instance.
     *
     * @param config the configuration for this history store
     * @param comparator the function that should be used to compare history records during
     *            {@link #recover(Map, Map, Tables, DdlParser) recovery}; may be null if the
     *            {@link HistoryRecordComparator#INSTANCE default comparator} is to be used
     * @param listener TODO
     * @param useCatalogBeforeSchema true if the parsed string for a table contains only 2 items and the first should be used as 
                             the catalog and the second as the table name, or false if the first should be used as the schema and the 
                             second as the table name
     */
    void configure(Configuration config, HistoryRecordComparator comparator, DatabaseHistoryListener listener, boolean useCatalogBeforeSchema);

    /**
     * Start the history.
     */
    void start();

    /**
     * Record a change to the schema of the named database, and store it in the schema storage.
     *
     * @param source the information about the source database; may not be null
     * @param position the point in history where these DDL changes were made, which may be used when
     *            {@link #recover(Map, Map, Tables, DdlParser) recovering} the schema to some point in history; may not be
     *            null
     * @param databaseName the name of the database whose schema is being changed; may be null
     * @param ddl the DDL statements that describe the changes to the database schema; may not be null
     * @throws DatabaseHistoryException if the record could not be written
     */
    void record(Map<String, ?> source, Map<String, ?> position, String databaseName, String ddl) throws DatabaseHistoryException;

    void record(Map<String, ?> source, Map<String, ?> position, String databaseName, String schemaName, String ddl, TableChanges changes) throws DatabaseHistoryException;

    /**
     * Recover the {@link Tables database schema} to a known point in its history. Note that it is possible to recover the
     * database schema to a point in history that is earlier than what has been {@link #record(Map, Map, String, Tables, String)
     * recorded}. Likewise, when recovering to a point in history <em>later</em> than what was recorded, the database schema will
     * reflect the latest state known to the history.
     *
     * @param source the information about the source database; may not be null
     * @param position the point in history at which the {@link Tables database schema} should be recovered; may not be null
     * @param schema the table definitions that should be changed to reflect the database schema at the desired point in history;
     *            may not be null
     * @param ddlParser the DDL parser that can be used to apply DDL statements to the given {@code schema}; may not be null
     */
    void recover(Map<String, ?> source, Map<String, ?> position, Tables schema, DdlParser ddlParser);

    /**
     * Stop recording history and release any resources acquired since {@link #configure(Configuration, HistoryRecordComparator, DatabaseHistoryListener)}.
     */
    void stop();

    /**
     * Determines if the DatabaseHistory entity exists
     * @return
     */
    boolean exists();

    /**
     * Determines if the underlying storage exists.
     * Note: storage may exists while history entities not yet written, see {@link #exists()}
     * @return
     */
    boolean storageExists();

    /**
     * Called to initialize permanent storage of the history.
     */
    void initializeStorage();
}

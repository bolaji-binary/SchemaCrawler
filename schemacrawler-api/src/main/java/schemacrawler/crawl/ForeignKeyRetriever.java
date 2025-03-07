/*
========================================================================
SchemaCrawler
http://www.schemacrawler.com
Copyright (c) 2000-2021, Sualeh Fatehi <sualeh@hotmail.com>.
All rights reserved.
------------------------------------------------------------------------

SchemaCrawler is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

SchemaCrawler and the accompanying materials are made available under
the terms of the Eclipse Public License v1.0, GNU General Public License
v3 or GNU Lesser General Public License v3.

You may elect to redistribute this code under any of these licenses.

The Eclipse Public License is available at:
http://www.eclipse.org/legal/epl-v10.html

The GNU General Public License v3 and the GNU Lesser General Public
License v3 are available at:
http://www.gnu.org/licenses/

========================================================================
*/

package schemacrawler.crawl;

import static java.util.Objects.requireNonNull;
import static schemacrawler.schemacrawler.InformationSchemaKey.FOREIGN_KEYS;
import static schemacrawler.schemacrawler.SchemaInfoMetadataRetrievalStrategy.foreignKeysRetrievalStrategy;
import static us.fatehi.utility.Utility.isBlank;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

import schemacrawler.SchemaCrawlerLogger;
import schemacrawler.schema.Column;
import schemacrawler.schema.ForeignKeyDeferrability;
import schemacrawler.schema.ForeignKeyUpdateRule;
import schemacrawler.schema.NamedObjectKey;
import schemacrawler.schema.View;
import schemacrawler.schemacrawler.InformationSchemaViews;
import schemacrawler.schemacrawler.Query;
import schemacrawler.schemacrawler.SchemaCrawlerOptions;
import schemacrawler.schemacrawler.SchemaCrawlerSQLException;
import us.fatehi.utility.string.StringFormat;

/**
 * A retriever uses database metadata to get the details about the database forign keys.
 *
 * @author Sualeh Fatehi
 */
final class ForeignKeyRetriever extends AbstractRetriever {

  private static final SchemaCrawlerLogger LOGGER =
      SchemaCrawlerLogger.getLogger(ForeignKeyRetriever.class.getName());

  ForeignKeyRetriever(
      final RetrieverConnection retrieverConnection,
      final MutableCatalog catalog,
      final SchemaCrawlerOptions options) {
    super(retrieverConnection, catalog, options);
  }

  void retrieveForeignKeys(final NamedObjectList<MutableTable> allTables) throws SQLException {
    requireNonNull(allTables, "No tables provided");

    switch (getRetrieverConnection().get(foreignKeysRetrievalStrategy)) {
      case data_dictionary_all:
        LOGGER.log(Level.INFO, "Retrieving foreign keys, using fast data dictionary retrieval");
        retrieveForeignKeysFromDataDictionary();
        break;

      case metadata:
        LOGGER.log(Level.INFO, "Retrieving foreign keys");
        retrieveForeignKeysFromMetadata(allTables);
        break;

      default:
        LOGGER.log(Level.INFO, "Not retrieving foreign keys");
        break;
    }
  }

  private void createForeignKeys(
      final MetadataResultSet results, final Map<NamedObjectKey, MutableForeignKey> foreignKeys)
      throws SQLException {
    while (results.next()) {
      final String foreignKeyName = results.getString("FK_NAME");
      LOGGER.log(Level.FINE, new StringFormat("Retrieving foreign key: %s", foreignKeyName));

      final String pkTableCatalogName = normalizeCatalogName(results.getString("PKTABLE_CAT"));
      final String pkTableSchemaName = normalizeSchemaName(results.getString("PKTABLE_SCHEM"));
      final String pkTableName = results.getString("PKTABLE_NAME");
      final String pkColumnName = results.getString("PKCOLUMN_NAME");

      final String fkTableCatalogName = normalizeCatalogName(results.getString("FKTABLE_CAT"));
      final String fkTableSchemaName = normalizeSchemaName(results.getString("FKTABLE_SCHEM"));
      final String fkTableName = results.getString("FKTABLE_NAME");
      final String fkColumnName = results.getString("FKCOLUMN_NAME");

      final int keySequence = results.getInt("KEY_SEQ", 0);
      final ForeignKeyUpdateRule updateRule =
          results.getEnumFromId("UPDATE_RULE", ForeignKeyUpdateRule.unknown);
      final ForeignKeyUpdateRule deleteRule =
          results.getEnumFromId("DELETE_RULE", ForeignKeyUpdateRule.unknown);
      final ForeignKeyDeferrability deferrability =
          results.getEnumFromId("DEFERRABILITY", ForeignKeyDeferrability.unknown);

      final Column pkColumn =
          lookupOrCreateColumn(pkTableCatalogName, pkTableSchemaName, pkTableName, pkColumnName);
      final Column fkColumn =
          lookupOrCreateColumn(fkTableCatalogName, fkTableSchemaName, fkTableName, fkColumnName);
      final boolean isPkColumnPartial = pkColumn instanceof ColumnPartial;
      final boolean isFkColumnPartial = fkColumn instanceof ColumnPartial;

      if (pkColumn == null || fkColumn == null || isFkColumnPartial && isPkColumnPartial) {
        continue;
      }

      final String specificName;
      if (isBlank(foreignKeyName)) {
        specificName =
            RetrieverUtility.constructForeignKeyName(pkColumn.getParent(), fkColumn.getParent());
      } else {
        specificName = foreignKeyName;
      }

      final NamedObjectKey fkLookupKey =
          new NamedObjectKey(fkTableCatalogName, fkTableSchemaName, specificName);

      final Optional<MutableForeignKey> foreignKeyOptional =
          Optional.ofNullable(foreignKeys.get(fkLookupKey));
      final MutableForeignKey foreignKey;
      if (foreignKeyOptional.isPresent()) {
        foreignKey = foreignKeyOptional.get();
      } else {
        foreignKey = new MutableForeignKey(foreignKeyName, specificName);
        foreignKeys.put(fkLookupKey, foreignKey);
      }

      foreignKey.addColumnReference(keySequence, pkColumn, fkColumn);
      foreignKey.setUpdateRule(updateRule);
      foreignKey.setDeleteRule(deleteRule);
      foreignKey.setDeferrability(deferrability);
      foreignKey.addAttributes(results.getAttributes());

      if (fkColumn instanceof MutableColumn) {
        ((MutableColumn) fkColumn).setReferencedColumn(pkColumn);
        ((MutableTable) fkColumn.getParent()).addForeignKey(foreignKey);
      } else if (isFkColumnPartial) {
        ((ColumnPartial) fkColumn).setReferencedColumn(pkColumn);
        ((TablePartial) fkColumn.getParent()).addForeignKey(foreignKey);
      }

      if (pkColumn instanceof MutableColumn) {
        ((MutableTable) pkColumn.getParent()).addForeignKey(foreignKey);
      } else if (isPkColumnPartial) {
        ((TablePartial) pkColumn.getParent()).addForeignKey(foreignKey);
      }
    }
  }

  /**
   * Looks up a column in the database. If the column and table are not found, they are created, and
   * added to the schema. This is prevent foreign key relationships from having a null pointer.
   */
  private Column lookupOrCreateColumn(
      final String catalogName,
      final String schemaName,
      final String tableName,
      final String columnName) {
    return RetrieverUtility.lookupOrCreateColumn(
        catalog, catalogName, schemaName, tableName, columnName);
  }

  private void retrieveForeignKeysFromDataDictionary() throws SchemaCrawlerSQLException {
    final InformationSchemaViews informationSchemaViews =
        getRetrieverConnection().getInformationSchemaViews();

    if (!informationSchemaViews.hasQuery(FOREIGN_KEYS)) {
      LOGGER.log(Level.FINE, "Extended foreign keys SQL statement was not provided");
      return;
    }

    final Map<NamedObjectKey, MutableForeignKey> foreignKeys = new HashMap<>();
    final Query fkSql = informationSchemaViews.getQuery(FOREIGN_KEYS);
    final Connection connection = getDatabaseConnection();
    try (final Statement statement = connection.createStatement();
        final MetadataResultSet results =
            new MetadataResultSet(fkSql, statement, getSchemaInclusionRule())) {
      results.setDescription("retrieveForeignKeysUsingSql");
      createForeignKeys(results, foreignKeys);
    } catch (final SQLException e) {
      throw new SchemaCrawlerSQLException("Could not retrieve foreign keys from SQL:\n" + fkSql, e);
    }
  }

  private void retrieveForeignKeysFromMetadata(final NamedObjectList<MutableTable> allTables)
      throws SchemaCrawlerSQLException {
    final Map<NamedObjectKey, MutableForeignKey> foreignKeys = new HashMap<>();
    for (final MutableTable table : allTables) {
      if (table instanceof View) {
        continue;
      }

      final DatabaseMetaData metaData = getMetaData();

      // Get imported foreign keys
      try (final MetadataResultSet results =
          new MetadataResultSet(
              metaData.getImportedKeys(
                  table.getSchema().getCatalogName(),
                  table.getSchema().getName(),
                  table.getName()))) {
        createForeignKeys(results, foreignKeys);
      } catch (final SQLException e) {
        throw new SchemaCrawlerSQLException(
            "Could not retrieve foreign keys for table " + table, e);
      }

      // We need to get exported keys as well, since if only a single
      // table is selected, we have not retrieved it's keys that are
      // imported by other tables.
      try (final MetadataResultSet results =
          new MetadataResultSet(
              metaData.getExportedKeys(
                  table.getSchema().getCatalogName(),
                  table.getSchema().getName(),
                  table.getName()))) {
        createForeignKeys(results, foreignKeys);
      } catch (final SQLException e) {
        // Since not all database drivers may support exported keys, log a warning instead of
        // throwing an error
        LOGGER.log(Level.WARNING, "Could not retrieve exported foreign keys for table " + table, e);
      }
    }
  }
}

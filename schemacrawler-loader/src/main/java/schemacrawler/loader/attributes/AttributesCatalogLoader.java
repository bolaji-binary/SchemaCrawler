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

package schemacrawler.loader.attributes;

import static schemacrawler.loader.attributes.model.CatalogAttributesUtility.readCatalogAttributes;
import static us.fatehi.utility.Utility.isBlank;

import java.util.Map.Entry;
import java.util.Optional;
import java.util.logging.Level;

import schemacrawler.SchemaCrawlerLogger;
import schemacrawler.crawl.AlternateKeyBuilder;
import schemacrawler.crawl.AlternateKeyBuilder.AlternateKeyDefinition;
import schemacrawler.crawl.WeakAssociationBuilder;
import schemacrawler.crawl.WeakAssociationBuilder.WeakAssociationColumn;
import schemacrawler.loader.attributes.model.AlternateKeyAttributes;
import schemacrawler.loader.attributes.model.CatalogAttributes;
import schemacrawler.loader.attributes.model.ColumnAttributes;
import schemacrawler.loader.attributes.model.TableAttributes;
import schemacrawler.loader.attributes.model.WeakAssociationAttributes;
import schemacrawler.schema.Catalog;
import schemacrawler.schema.Column;
import schemacrawler.schema.PrimaryKey;
import schemacrawler.schema.Table;
import schemacrawler.schema.WeakAssociation;
import schemacrawler.schemacrawler.SchemaCrawlerException;
import schemacrawler.tools.catalogloader.BaseCatalogLoader;
import schemacrawler.tools.executable.CommandDescription;
import schemacrawler.tools.executable.commandline.PluginCommand;
import schemacrawler.tools.options.Config;
import us.fatehi.utility.StopWatch;
import us.fatehi.utility.ioresource.InputResource;
import us.fatehi.utility.ioresource.InputResourceUtility;
import us.fatehi.utility.string.StringFormat;

public class AttributesCatalogLoader extends BaseCatalogLoader {

  private static final SchemaCrawlerLogger LOGGER =
      SchemaCrawlerLogger.getLogger(AttributesCatalogLoader.class.getName());

  private static final String OPTION_ATTRIBUTES_FILE = "attributes-file";

  public AttributesCatalogLoader() {
    super(
        new CommandDescription(
            "attributesloader", "Loader for catalog attributes, such as remarks or tags"),
        2);
  }

  @Override
  public PluginCommand getCommandLineCommand() {
    final CommandDescription commandDescription = getCommandDescription();
    final PluginCommand pluginCommand =
        PluginCommand.newCatalogLoaderCommand(
            commandDescription.getName(), commandDescription.getDescription());
    pluginCommand.addOption(
        OPTION_ATTRIBUTES_FILE,
        String.class,
        "Path to a YAML file with table and column attributes to add to the schema");
    return pluginCommand;
  }

  @Override
  public void loadCatalog() throws SchemaCrawlerException {
    if (!isLoaded()) {
      return;
    }

    LOGGER.log(Level.INFO, "Retrieving catalog attributes");
    final StopWatch stopWatch = new StopWatch("loadTableRowCounts");
    try {
      final Catalog catalog = getCatalog();
      final Config config = getAdditionalConfiguration();
      stopWatch.time(
          "retrieveCatalogAttributes",
          () -> {
            final String catalogAttributesFile = config.getObject(OPTION_ATTRIBUTES_FILE, null);
            if (isBlank(catalogAttributesFile)) {
              return null;
            }
            final InputResource inputResource =
                InputResourceUtility.createInputResource(catalogAttributesFile)
                    .orElseThrow(
                        () ->
                            new SchemaCrawlerException(
                                "Cannot locate catalog attributes file, " + catalogAttributesFile));
            final CatalogAttributes catalogAttributes = readCatalogAttributes(inputResource);
            loadRemarks(catalog, catalogAttributes);
            loadAlternateKeys(catalog, catalogAttributes);
            loadWeakAssociations(catalog, catalogAttributes);

            return null;
          });

      LOGGER.log(Level.INFO, stopWatch.stringify());
    } catch (final Exception e) {
      throw new SchemaCrawlerException("Exception loading catalog attributes", e);
    }
  }

  private void loadAlternateKeys(final Catalog catalog, final CatalogAttributes catalogAttributes) {
    final AlternateKeyBuilder alternateKeyBuilder = AlternateKeyBuilder.builder(catalog);
    for (final AlternateKeyAttributes alternateKeyAttributes :
        catalogAttributes.getAlternateKeys()) {

      final AlternateKeyDefinition alternateKeyDefinition =
          new AlternateKeyDefinition(
              alternateKeyAttributes.getSchema(),
              alternateKeyAttributes.getTableName(),
              alternateKeyAttributes.getName(),
              alternateKeyAttributes.getColumns());

      final Optional<PrimaryKey> optionalAlternateKey =
          alternateKeyBuilder.addAlternateKey(alternateKeyDefinition);
      if (!optionalAlternateKey.isPresent()) {
        continue;
      }
      final PrimaryKey alternateKey = optionalAlternateKey.get();

      alternateKey.setRemarks(alternateKeyAttributes.getRemarks());
      for (final Entry<String, String> attribute :
          alternateKeyAttributes.getAttributes().entrySet()) {
        alternateKey.setAttribute(attribute.getKey(), attribute.getValue());
      }
    }
  }

  private void loadRemarks(final Catalog catalog, final CatalogAttributes catalogAttributes) {
    for (final TableAttributes tableAttributes : catalogAttributes.getTables()) {
      final Optional<Table> lookupTable =
          catalog.lookupTable(tableAttributes.getSchema(), tableAttributes.getName());
      final Table table;
      if (lookupTable.isPresent()) {
        table = lookupTable.get();
      } else {
        LOGGER.log(Level.CONFIG, new StringFormat("Table %s not found", tableAttributes));
        continue;
      }

      if (tableAttributes.hasRemarks()) {
        table.setRemarks(tableAttributes.getRemarks());
      }

      for (final ColumnAttributes columnAttributes : tableAttributes) {
        if (columnAttributes.hasRemarks()) {
          final Optional<Column> lookupColumn = table.lookupColumn(columnAttributes.getName());
          if (lookupColumn.isPresent()) {
            final Column column = lookupColumn.get();
            column.setRemarks(columnAttributes.getRemarks());
          } else {
            LOGGER.log(Level.CONFIG, new StringFormat("Column %s not found", columnAttributes));
          }
        }
      }
    }
  }

  private void loadWeakAssociations(
      final Catalog catalog, final CatalogAttributes catalogAttributes) {
    for (final WeakAssociationAttributes weakAssociationAttributes :
        catalogAttributes.getWeakAssociations()) {

      final TableAttributes pkTableAttributes = weakAssociationAttributes.getReferencedTable();
      final TableAttributes fkTableAttributes = weakAssociationAttributes.getReferencingTable();

      final WeakAssociationBuilder weakAssociationBuilder = WeakAssociationBuilder.builder(catalog);

      for (final Entry<String, String> entry :
          weakAssociationAttributes.getColumnReferences().entrySet()) {
        final String fkColumnName = entry.getKey();
        final String pkColumnName = entry.getValue();

        final WeakAssociationColumn fkColumn =
            new WeakAssociationColumn(
                fkTableAttributes.getSchema(), fkTableAttributes.getName(), fkColumnName);
        final WeakAssociationColumn pkColumn =
            new WeakAssociationColumn(
                pkTableAttributes.getSchema(), pkTableAttributes.getName(), pkColumnName);

        weakAssociationBuilder.addColumnReference(fkColumn, pkColumn);
      }

      final WeakAssociation weakAssociation =
          weakAssociationBuilder.build(weakAssociationAttributes.getName());
      if (weakAssociation == null) {
        continue;
      }

      weakAssociation.setRemarks(weakAssociationAttributes.getRemarks());
      for (final Entry<String, String> attribute :
          weakAssociationAttributes.getAttributes().entrySet()) {
        weakAssociation.setAttribute(attribute.getKey(), attribute.getValue());
      }
    }
  }
}

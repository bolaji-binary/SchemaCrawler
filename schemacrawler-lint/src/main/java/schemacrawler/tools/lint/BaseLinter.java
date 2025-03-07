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
package schemacrawler.tools.lint;

import static java.util.Objects.requireNonNull;

import java.io.Serializable;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import schemacrawler.SchemaCrawlerLogger;
import schemacrawler.filter.TableTypesFilter;
import schemacrawler.inclusionrule.IncludeAll;
import schemacrawler.inclusionrule.InclusionRule;
import schemacrawler.schema.Catalog;
import schemacrawler.schema.Column;
import schemacrawler.schema.CrawlInfo;
import schemacrawler.schema.Table;
import schemacrawler.schemacrawler.SchemaCrawlerException;
import schemacrawler.tools.lint.config.LinterConfig;
import us.fatehi.utility.string.StringFormat;

/**
 * Evaluates a catalog and creates lints. This base class has core for visiting a catalog, and
 * creating states.Also contains utility methods for subclasses. Needs to be overridden by custom
 * linters.
 *
 * @author Sualeh Fatehi
 */
public abstract class BaseLinter extends Linter {

  private static final SchemaCrawlerLogger LOGGER =
      SchemaCrawlerLogger.getLogger(BaseLinter.class.getName());

  private Catalog catalog;
  private InclusionRule tableInclusionRule;
  private InclusionRule columnInclusionRule;
  private TableTypesFilter tableTypesFilter;

  protected BaseLinter() {
    setTableTypesFilter(null);
    setTableInclusionRule(null);
    setColumnInclusionRule(null);
  }

  protected final void addCatalogLint(final String message) {
    addLint(LintObjectType.catalog, catalog, message, null);
  }

  protected final <V extends Serializable> void addCatalogLint(
      final String message, final V value) {
    addLint(LintObjectType.catalog, catalog, message, value);
  }

  protected final void addTableLint(final Table table, final String message) {
    addLint(LintObjectType.table, table, message, null);
  }

  protected final <V extends Serializable> void addTableLint(
      final Table table, final String message, final V value) {
    addLint(LintObjectType.table, table, message, value);
  }

  protected void end(final Connection connection) throws SchemaCrawlerException {}

  protected final List<Column> getColumns(final Table table) {
    if (table == null) {
      return Collections.emptyList();
    }

    final List<Column> columns = new ArrayList<>(table.getColumns());
    for (final Iterator<Column> iterator = columns.iterator(); iterator.hasNext(); ) {
      final Column column = iterator.next();
      if (!includeColumn(column)) {
        iterator.remove();
      }
    }
    return columns;
  }

  protected final CrawlInfo getCrawlInfo() {
    return catalog.getCrawlInfo();
  }

  protected final TableTypesFilter getTableTypesFilter() {
    return tableTypesFilter;
  }

  protected final boolean includeColumn(final Column column) {
    return column != null && columnInclusionRule.test(column.getFullName());
  }

  protected final boolean includeTable(final Table table) {
    return table != null && tableInclusionRule.test(table.getFullName());
  }

  protected abstract void lint(Table table, Connection connection) throws SchemaCrawlerException;

  protected final void setTableTypesFilter(final TableTypesFilter tableTypesFilter) {
    if (tableTypesFilter == null) {
      this.tableTypesFilter = new TableTypesFilter();
    } else {
      this.tableTypesFilter = tableTypesFilter;
    }
  }

  protected void start(final Connection connection) throws SchemaCrawlerException {}

  @Override
  final void configure(final LinterConfig linterConfig) {
    super.configure(linterConfig);
    if (linterConfig != null) {
      setTableInclusionRule(linterConfig.getTableInclusionRule());
      setColumnInclusionRule(linterConfig.getColumnInclusionRule());
    }
  }

  @Override
  final void lint(final Catalog catalog, final Connection connection)
      throws SchemaCrawlerException {
    this.catalog = requireNonNull(catalog, "No catalog provided");

    start(connection);
    for (final Table table : catalog.getTables()) {
      if (tableInclusionRule.test(table.getFullName()) && tableTypesFilter.test(table)) {
        lint(table, connection);
      } else {
        LOGGER.log(
            Level.FINE,
            new StringFormat("Excluding table <%s> for lint <%s>", table, getLinterId()));
      }
    }
    end(connection);
    this.catalog = null;
  }

  private final void setColumnInclusionRule(final InclusionRule columnInclusionRule) {
    if (columnInclusionRule == null) {
      this.columnInclusionRule = new IncludeAll();
    } else {
      this.columnInclusionRule = columnInclusionRule;
    }
  }

  private final void setTableInclusionRule(final InclusionRule tableInclusionRule) {
    if (tableInclusionRule == null) {
      this.tableInclusionRule = new IncludeAll();
    } else {
      this.tableInclusionRule = tableInclusionRule;
    }
  }
}

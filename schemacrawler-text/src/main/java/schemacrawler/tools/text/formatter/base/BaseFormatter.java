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
package schemacrawler.tools.text.formatter.base;

import static java.util.Objects.requireNonNull;
import static us.fatehi.utility.Utility.convertForComparison;
import static us.fatehi.utility.Utility.hasNoUpperCase;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Level;

import schemacrawler.SchemaCrawlerLogger;
import schemacrawler.schema.Column;
import schemacrawler.schema.DatabaseObject;
import schemacrawler.schema.IndexColumn;
import schemacrawler.schema.NamedObjectKey;
import schemacrawler.schemacrawler.Identifiers;
import schemacrawler.schemacrawler.SchemaCrawlerException;
import schemacrawler.tools.command.text.schema.options.TextOutputFormat;
import schemacrawler.tools.options.OutputOptions;
import schemacrawler.tools.text.formatter.base.helper.HtmlFormattingHelper;
import schemacrawler.tools.text.formatter.base.helper.PlainTextFormattingHelper;
import schemacrawler.tools.text.formatter.base.helper.TextFormattingHelper;
import schemacrawler.tools.text.options.BaseTextOptions;
import schemacrawler.tools.text.options.DatabaseObjectColorMap;
import schemacrawler.tools.traversal.TraversalHandler;

public abstract class BaseFormatter<O extends BaseTextOptions> implements TraversalHandler {

  private static final SchemaCrawlerLogger LOGGER =
      SchemaCrawlerLogger.getLogger(BaseFormatter.class.getName());

  protected final O options;
  protected final OutputOptions outputOptions;
  protected final TextFormattingHelper formattingHelper;
  protected final DatabaseObjectColorMap colorMap;
  protected final Identifiers identifiers;
  protected final boolean printVerboseDatabaseInfo;
  private final PrintWriter out;

  protected BaseFormatter(
      final O options,
      final boolean printVerboseDatabaseInfo,
      final OutputOptions outputOptions,
      final String identifierQuoteString)
      throws SchemaCrawlerException {
    this.options = requireNonNull(options, "Options not provided");

    this.outputOptions = requireNonNull(outputOptions, "Output options not provided");

    colorMap = options.getColorMap();

    this.printVerboseDatabaseInfo = !options.isNoInfo() && printVerboseDatabaseInfo;

    identifiers =
        Identifiers.identifiers()
            .withIdentifierQuoteString(identifierQuoteString)
            .withIdentifierQuotingStrategy(options.getIdentifierQuotingStrategy())
            .build();

    try {
      out = new PrintWriter(outputOptions.openNewOutputWriter(false), true);
    } catch (final IOException e) {
      throw new SchemaCrawlerException("Cannot open output writer", e);
    }

    final TextOutputFormat outputFormat =
        TextOutputFormat.fromFormat(outputOptions.getOutputFormatValue());
    switch (outputFormat) {
      case html:
        formattingHelper = new HtmlFormattingHelper(out, outputFormat);
        break;
      case text:
      default:
        formattingHelper = new PlainTextFormattingHelper(out, outputFormat);
        break;
    }
  }

  @Override
  public void end() throws SchemaCrawlerException {
    LOGGER.log(Level.INFO, "Closing writer");
    out.flush();
    out.close();
  }

  protected String columnNullable(final String columnTypeName, final boolean isNullable) {
    final String columnNullable;
    if (isNullable) {
      columnNullable = "";
    } else if (hasNoUpperCase(columnTypeName)) {
      columnNullable = " not null";
    } else {
      columnNullable = " NOT NULL";
    }

    return columnNullable;
  }

  protected boolean isColumnSignificant(final Column column) {
    return column != null
        && (column instanceof IndexColumn
            || column.isPartOfPrimaryKey()
            || column.isPartOfForeignKey()
            || column.isPartOfIndex());
  }

  protected String nodeId(final DatabaseObject dbObject) {
    if (dbObject == null) {
      return "";
    } else {
      final NamedObjectKey dbObjectLookupKey = dbObject.key();
      return convertForComparison(dbObject.getName())
          + "_"
          + Integer.toHexString(dbObjectLookupKey.hashCode());
    }
  }
}

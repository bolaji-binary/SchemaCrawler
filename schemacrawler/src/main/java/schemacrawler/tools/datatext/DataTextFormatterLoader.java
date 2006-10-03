/* 
 *
 * SchemaCrawler
 * http://sourceforge.net/projects/schemacrawler
 * Copyright (c) 2000-2006, Sualeh Fatehi.
 *
 * This library is free software; you can redistribute it and/or modify it under the terms
 * of the GNU Lesser General Public License as published by the Free Software Foundation;
 * either version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this
 * library; if not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA 02111-1307, USA.
 *
 */

package schemacrawler.tools.datatext;


import schemacrawler.crawl.SchemaCrawlerException;
import schemacrawler.execute.DataHandler;
import schemacrawler.tools.OutputFormat;
import schemacrawler.tools.OutputOptions;

/**
 * Loads text formatters for handling SQL executions.
 */
public final class DataTextFormatterLoader
{

  private DataTextFormatterLoader()
  {
  }

  /**
   * Checks if the CrawlHandler mnemonic is valid.
   * 
   * @param printFormatterName
   *          Mnemonic name for a CrawlHandler
   * @return True if the mnemonic is known
   */
  private static boolean canLoad(final DataTextFormatOptions options)
  {
    return options.getOutputOptions().getOutputFormat() != null;
  }

  /**
   * Instantiates a text formatter type of DataHandler from the mnemonic string.
   * 
   * @param options
   *          Options
   * @return CrawlHandler instance
   */
  public static DataHandler load(final DataTextFormatOptions options)
    throws SchemaCrawlerException
  {

    if (!canLoad(options))
    {
      return null;
    }

    DataHandler handler = null;
    final OutputOptions outputOptions = options.getOutputOptions();
    final OutputFormat outputFormatType = outputOptions.getOutputFormat();
    if (outputFormatType == OutputFormat.TEXT)
    {
      handler = new DataPlainTextFormatter(options);
    }
    else if (outputFormatType == OutputFormat.HTML)
    {
      handler = new DataHTMLFormatter(options);
    }
    else if (outputFormatType == OutputFormat.CSV)
    {
      handler = new DataCSVFormatter(options);
    }

    return handler;

  }

}

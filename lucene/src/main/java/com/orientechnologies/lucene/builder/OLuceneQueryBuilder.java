/*
 *
 *
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.orientechnologies.lucene.builder;

import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.lucene.analyzer.OLuceneAnalyzerFactory;
import com.orientechnologies.lucene.parser.OLuceneMultiFieldQueryParser;
import com.orientechnologies.core.index.OCompositeKey;
import com.orientechnologies.core.index.OIndexDefinition;
import com.orientechnologies.core.metadata.schema.YTType;
import com.orientechnologies.core.sql.parser.ParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;

/**
 *
 */
public class OLuceneQueryBuilder {

  public static final Map<String, ?> EMPTY_METADATA = Collections.emptyMap();

  private final boolean allowLeadingWildcard;
  // private final boolean                lowercaseExpandedTerms;
  private final boolean splitOnWhitespace;
  private final OLuceneAnalyzerFactory analyzerFactory;

  public OLuceneQueryBuilder(final Map<String, ?> metadata) {
    this(
        Optional.ofNullable((Boolean) metadata.get("allowLeadingWildcard")).orElse(false),
        Optional.ofNullable((Boolean) metadata.get("lowercaseExpandedTerms")).orElse(true),
        Optional.ofNullable((Boolean) metadata.get("splitOnWhitespace")).orElse(true));
  }

  public OLuceneQueryBuilder(
      final boolean allowLeadingWildcard,
      final boolean lowercaseExpandedTerms,
      final boolean splitOnWhitespace) {
    this.allowLeadingWildcard = allowLeadingWildcard;
    // this.lowercaseExpandedTerms = lowercaseExpandedTerms;
    this.splitOnWhitespace = splitOnWhitespace;
    analyzerFactory = new OLuceneAnalyzerFactory();
  }

  public Query query(
      final OIndexDefinition index,
      final Object key,
      final Map<String, ?> metadata,
      final Analyzer analyzer)
      throws ParseException {
    final String query = constructQueryString(key);
    if (query.isEmpty()) {
      return new MatchNoDocsQuery();
    }
    return buildQuery(index, query, metadata, analyzer);
  }

  private static String constructQueryString(final Object key) {
    if (key instanceof OCompositeKey) {
      final Object params = ((OCompositeKey) key).getKeys().get(0);
      return params.toString();
    } else {
      return key.toString();
    }
  }

  protected Query buildQuery(
      final OIndexDefinition index,
      final String query,
      final Map<String, ?> metadata,
      final Analyzer queryAnalyzer)
      throws ParseException {
    String[] fields;
    if (index.isAutomatic()) {
      fields = index.getFields().toArray(new String[index.getFields().size()]);
    } else {
      final int length = index.getTypes().length;
      fields = new String[length];
      for (int i = 0; i < length; i++) {
        fields[i] = "k" + i;
      }
    }
    final Map<String, YTType> types = new HashMap<>();
    for (int i = 0; i < fields.length; i++) {
      final String field = fields[i];
      types.put(field, index.getTypes()[i]);
    }
    return getQuery(index, query, metadata, queryAnalyzer, fields, types);
  }

  private Query getQuery(
      final OIndexDefinition index,
      final String query,
      final Map<String, ?> metadata,
      final Analyzer queryAnalyzer,
      final String[] fields,
      final Map<String, YTType> types)
      throws ParseException {
    @SuppressWarnings("unchecked") final Map<String, Float> boost =
        Optional.ofNullable((Map<String, Number>) metadata.get("boost"))
            .orElse(new HashMap<>())
            .entrySet()
            .stream()
            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().floatValue()));
    final Analyzer analyzer =
        Optional.ofNullable((Boolean) metadata.get("customAnalysis"))
            .filter(b -> b)
            .map(
                b ->
                    analyzerFactory.createAnalyzer(
                        index, OLuceneAnalyzerFactory.AnalyzerKind.QUERY, metadata))
            .orElse(queryAnalyzer);
    final OLuceneMultiFieldQueryParser queryParser =
        new OLuceneMultiFieldQueryParser(types, fields, analyzer, boost);
    queryParser.setAllowLeadingWildcard(
        Optional.ofNullable((Boolean) metadata.get("allowLeadingWildcard"))
            .orElse(allowLeadingWildcard));
    queryParser.setSplitOnWhitespace(
        Optional.ofNullable((Boolean) metadata.get("splitOnWhitespace"))
            .orElse(splitOnWhitespace));
    //  TODO   REMOVED
    //    queryParser.setLowercaseExpandedTerms(
    //        Optional.ofNullable(metadata.<Boolean>getProperty("lowercaseExpandedTerms"))
    //            .orElse(lowercaseExpandedTerms));
    try {
      return queryParser.parse(query);
    } catch (final org.apache.lucene.queryparser.classic.ParseException e) {
      final Throwable cause = prepareParseError(e, metadata);
      OLogManager.instance().error(this, "Exception is suppressed, original exception is ", cause);
      //noinspection ThrowInsideCatchBlockWhichIgnoresCaughtException
      throw new ParseException(cause.getMessage());
    }
  }

  /**
   * Produces a Lucene {@link ParseException} that can be reported in logs.
   *
   * <p>If the metadata contains a `reportQueryAs` parameter, that will be reported as the text of
   * the query that failed to parse.
   *
   * <p>This is generally useful when the contents of a Lucene query contain privileged information
   * (e.g.Personally Identifiable Information in privacy sensitive settings) that should not be
   * persisted in logs.
   */
  private static Throwable prepareParseError(
      org.apache.lucene.queryparser.classic.ParseException e, Map<String, ?> metadata) {
    final Throwable cause;
    final String reportAs = (String) metadata.get("reportQueryAs");
    if (reportAs == null) {
      cause = e;
    } else {
      cause =
          new org.apache.lucene.queryparser.classic.ParseException(
              String.format("Cannot parse '%s'", reportAs));
      cause.initCause(e.getCause());
      cause.setStackTrace(e.getStackTrace());
    }
    return cause;
  }
}

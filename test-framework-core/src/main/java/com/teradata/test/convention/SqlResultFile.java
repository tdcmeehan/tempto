/*
 * Copyright 2013-2015, Teradata, Inc. All rights reserved.
 */

package com.teradata.test.convention;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.teradata.test.assertions.QueryAssert.Row;
import com.teradata.test.internal.convention.HeaderFileParser;
import com.teradata.test.internal.convention.HeaderFileParser.SectionParsingResult;
import com.teradata.test.internal.query.QueryRowMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.sql.JDBCType;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.io.Resources.getResource;
import static com.teradata.test.assertions.QueryAssert.Row.row;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

public class SqlResultFile
{

    private static final String DEFAULT_DELIMITER = "|";

    private static final String DEFAULT_IGNORE_ORDER = "false";
    private static final String DEFAULT_IGNORE_EXCESS = "false";
    private static final String DEFAULT_TRIM_VALUES = "false";
    private static final String JOIN_ALL_VALUES_TO_ONE = "false";
    private static final Splitter TYPES_SPLITTER = Splitter.on('|');

    private final SectionParsingResult sqlFileSectionParsingResult;
    private final Optional<List<JDBCType>> expectedTypes;

    public static SqlResultFile sqlResultFileForResource(String resourceName)
    {
        try {
            return sqlResultFileFor(getResource(resourceName).openStream());
        }
        catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    public static SqlResultFile sqlResultFileFor(Path resultFile)
    {
        return new SqlResultFile(getOnlyElement(new HeaderFileParser().parseFile(resultFile)));
    }

    public static SqlResultFile sqlResultFileFor(InputStream inputStream)
            throws IOException
    {
        return new SqlResultFile(getOnlyElement(new HeaderFileParser().parseFile(inputStream)));
    }

    public SqlResultFile(SectionParsingResult sqlFileSectionParsingResult)
    {
        this.sqlFileSectionParsingResult = sqlFileSectionParsingResult;
        this.expectedTypes = parseExpectedTypes(sqlFileSectionParsingResult);
    }

    private Optional<List<JDBCType>> parseExpectedTypes(SectionParsingResult sqlFileSectionParsingResult)
    {
        Optional<String> typesProperty = sqlFileSectionParsingResult.getProperty("types");

        return typesProperty.map(
                value -> stream(TYPES_SPLITTER.split(value).spliterator(), false)
                        .map(JDBCType::valueOf)
                        .collect(toList()));
    }

    public List<Row> getRows(List<JDBCType> columnTypes)
    {
        List<Row> values = newArrayList();
        String delimiter = getDelimiter();

        QueryRowMapper rowMapper = new QueryRowMapper(columnTypes);
        Splitter valuesSplitter = Splitter.on(delimiter);
        if (isTrimValues()) {
            valuesSplitter = valuesSplitter.trimResults();
        }

        for (String line : sqlFileSectionParsingResult.getContentLines()) {
            List<String> rowValues = parseLine(line, delimiter, valuesSplitter);
            values.add(rowMapper.mapToRow(rowValues));
        }

        if (isJoinAllRowsToOne()) {
            checkState(columnTypes.size() == 1, "Expected single column result when 'joinAllRowsToOne' property is set, columnTypes: %s", columnTypes);
            String joinedRows = values.stream()
                    .map(row -> String.valueOf(row.getValues().get(0)))
                    .collect(joining("\n"));
            return ImmutableList.of(row(joinedRows));
        }

        return values;
    }

    public String getFileContent()
    {
        return sqlFileSectionParsingResult.getOriginalContent();
    }

    public Optional<List<JDBCType>> getExpectedTypes()
    {
        return expectedTypes;
    }

    private List<String> parseLine(String line, String delimiter, Splitter valuesSplitter)
    {
        List<String> rowValues = valuesSplitter.splitToList(line);
        if (line.trim().endsWith(delimiter)) {
            rowValues = rowValues.subList(0, rowValues.size() - 1);
        }
        return rowValues;
    }

    public boolean isIgnoreOrder()
    {
        return Boolean.valueOf(sqlFileSectionParsingResult.getProperty("ignoreOrder").orElse(DEFAULT_IGNORE_ORDER));
    }

    public boolean isIgnoreExcessRows()
    {
        return Boolean.valueOf(sqlFileSectionParsingResult.getProperty("ignoreExcessRows").orElse(DEFAULT_IGNORE_EXCESS));
    }

    public boolean isTrimValues()
    {
        return Boolean.valueOf(sqlFileSectionParsingResult.getProperty("trimValues").orElse(DEFAULT_TRIM_VALUES));
    }

    public boolean isJoinAllRowsToOne()
    {
        return Boolean.valueOf(sqlFileSectionParsingResult.getProperty("joinAllRowsToOne").orElse(JOIN_ALL_VALUES_TO_ONE));
    }

    private String getDelimiter()
    {
        return sqlFileSectionParsingResult.getProperty("delimiter").orElse(DEFAULT_DELIMITER);
    }
}

package org.asamk.signal.manager.storage;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceIdType;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.InvalidObjectException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Utils {

    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

    private Utils() {
    }

    public static ObjectMapper createStorageObjectMapper() {
        final ObjectMapper objectMapper = new ObjectMapper();

        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.PUBLIC_ONLY);
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT); // for pretty print
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);
        objectMapper.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);

        return objectMapper;
    }

    public static JsonNode getNotNullNode(JsonNode parent, String name) throws InvalidObjectException {
        var node = parent.get(name);
        if (node == null || node.isNull()) {
            throw new InvalidObjectException(String.format("Incorrect file format: expected parameter %s not found ",
                    name));
        }

        return node;
    }

    public static RecipientAddress getRecipientAddressFromIdentifier(final String identifier) {
        if (UuidUtil.isUuid(identifier)) {
            return new RecipientAddress(ServiceId.parseOrThrow(identifier));
        } else {
            return new RecipientAddress(Optional.empty(), Optional.of(identifier));
        }
    }

    public static int getAccountIdType(ServiceIdType serviceIdType) {
        return switch (serviceIdType) {
            case ACI -> 0;
            case PNI -> 1;
        };
    }

    public static <T> T executeQuerySingleRow(
            PreparedStatement statement, ResultSetMapper<T> mapper
    ) throws SQLException {
        final var resultSet = statement.executeQuery();
        if (!resultSet.next()) {
            throw new RuntimeException("Expected a row in result set, but none found.");
        }
        return mapper.apply(resultSet);
    }

    public static <T> Optional<T> executeQueryForOptional(
            PreparedStatement statement, ResultSetMapper<T> mapper
    ) throws SQLException {
        final var resultSet = statement.executeQuery();
        if (!resultSet.next()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.apply(resultSet));
    }

    public static <T> Stream<T> executeQueryForStream(
            PreparedStatement statement, ResultSetMapper<T> mapper
    ) throws SQLException {
        final var resultSet = statement.executeQuery();

        return StreamSupport.stream(new Spliterators.AbstractSpliterator<>(Long.MAX_VALUE, Spliterator.ORDERED) {
            @Override
            public boolean tryAdvance(final Consumer<? super T> consumer) {
                try {
                    if (!resultSet.next()) {
                        return false;
                    }
                    consumer.accept(mapper.apply(resultSet));
                    return true;
                } catch (SQLException e) {
                    logger.warn("Failed to read from database result", e);
                    throw new RuntimeException(e);
                }
            }
        }, false);
    }

    public interface ResultSetMapper<T> {

        T apply(ResultSet resultSet) throws SQLException;
    }
}

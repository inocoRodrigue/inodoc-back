package com.esn.inodoc.service;

import com.esn.inodoc.config.DataInitializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class ApplicationBackupService {
    private static final int MAX_BYTES = 256 * 1024 * 1024;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final S3ImageStorage storage;
    private final DataInitializer initializer;

    public ApplicationBackupService(JdbcTemplate jdbc, ObjectMapper mapper, S3ImageStorage storage,
                                    DataInitializer initializer) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.storage = storage;
        this.initializer = initializer;
    }

    public record Column(String name, int type, String typeName) {}
    public record ForeignKey(String column, String parent, String parentColumn) {}
    public record Table(String name, List<Column> columns, List<ForeignKey> keys,
                        List<Map<String, String>> rows) {}
    public record Snapshot(int version, List<Table> tables, List<S3ImageStorage.BackupImage> images) {}

    @Transactional
    public byte[] exportZip() {
        return jdbc.execute((ConnectionCallback<byte[]>) connection -> {
            List<Table> tables = schema(connection);
            lock(connection, tables);
            for (Table table : tables) {
                try (var statement = connection.createStatement();
                     var result = statement.executeQuery("SELECT * FROM " + quote(table.name()))) {
                    while (result.next()) {
                        Map<String, String> row = new LinkedHashMap<>();
                        for (Column column : table.columns()) {
                                String value = column.typeName().equalsIgnoreCase("uuid")
                                    ? result.getString(column.name()) : switch (column.type()) {
                                case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY -> {
                                    byte[] bytes = result.getBytes(column.name());
                                    yield bytes == null ? null : Base64.getEncoder().encodeToString(bytes);
                                }
                                case Types.BOOLEAN, Types.BIT -> {
                                    boolean flag = result.getBoolean(column.name());
                                    yield result.wasNull() ? null : Boolean.toString(flag);
                                }
                                case Types.TIMESTAMP_WITH_TIMEZONE -> {
                                    OffsetDateTime date = result.getObject(column.name(), OffsetDateTime.class);
                                    yield date == null ? null : date.toString();
                                }
                                default -> {
                                    if (column.typeName().equalsIgnoreCase("timestamptz")) {
                                        OffsetDateTime date = result.getObject(column.name(), OffsetDateTime.class);
                                        yield date == null ? null : date.toString();
                                    }
                                    yield result.getString(column.name());
                                }
                            };
                            row.put(column.name(), value);
                        }
                        table.rows().add(row);
                    }
                }
            }
            retainMasters(tables);
            Set<String> urls = new HashSet<>();
            for (Table table : tables) {
                for (Map<String, String> row : table.rows()) {
                    for (String value : row.values()) collectImages(value, urls);
                }
            }
            List<S3ImageStorage.BackupImage> images = urls.stream().sorted().map(storage::backupImage).toList();
            try (var bytes = new ByteArrayOutputStream(); var zip = new ZipOutputStream(bytes)) {
                zip.putNextEntry(new ZipEntry("database.json"));
                byte[] json = mapper.writeValueAsBytes(new Snapshot(1, tables, images));
                if (json.length > MAX_BYTES) throw new IllegalArgumentException("Sauvegarde superieure a 256 Mo");
                zip.write(json);
                zip.closeEntry();
                zip.finish();
                return bytes.toByteArray();
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("La sauvegarde a echoue", exception);
            }
        });
    }

    @Transactional
    public void importZip(byte[] bytes) {
        Snapshot snapshot = read(bytes);
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            List<Table> current = schema(connection);
            validate(snapshot, current);
            lock(connection, current);
            restoreImages(snapshot);
            clear(connection, current);
            Map<String, Table> saved = new LinkedHashMap<>();
            snapshot.tables().forEach(table -> saved.put(table.name(), table));
            for (Table table : current) insert(connection, saved.get(table.name()));
            return null;
        });
    }

    @Transactional
    public byte[] reset(UUID adminId) {
        byte[] zip = exportZip();
        Snapshot snapshot = read(zip);
        Table users = snapshot.tables().stream().filter(table -> table.name().equals("users"))
                .findFirst().orElseThrow(ApplicationBackupService::invalid);
        Map<String, String> admin = users.rows().stream().filter(row -> adminId.toString().equals(row.get("id")))
                .findFirst().map(LinkedHashMap::new).orElseThrow(ApplicationBackupService::invalid);
        admin.put("profile_id", null);
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            clear(connection, schema(connection));
            initializer.initializeAfterReset();
            insert(connection, new Table(users.name(), users.columns(), users.keys(), List.of(admin)));
            try (var statement = connection.prepareStatement("INSERT INTO user_roles(user_id, role_id) "
                    + "SELECT ?, id FROM roles WHERE name = 'ADMIN'")) {
                statement.setObject(1, adminId);
                statement.executeUpdate();
            }
            return null;
        });
        return zip;
    }

    private void collectImages(String value, Set<String> urls) {
        if (value == null) return;
        if (storage.isStoredImage(value)) {
            urls.add(value);
        } else if (value.stripLeading().startsWith("{") || value.stripLeading().startsWith("[")) {
            try {
                collectImages(mapper.readTree(value), urls);
            } catch (java.io.IOException ignored) {
                return;
            }
        }
    }

    private void collectImages(JsonNode node, Set<String> urls) {
        if (node.isTextual()) {
            if (storage.isStoredImage(node.textValue())) urls.add(node.textValue());
        } else if (node.isContainerNode()) {
            node.forEach(child -> collectImages(child, urls));
        }
    }

    private void restoreImages(Snapshot snapshot) {
        List<String> uploaded = new ArrayList<>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    for (String url : uploaded) {
                        try {
                            storage.deleteRestoredImage(url);
                        } catch (RuntimeException exception) {
                            org.slf4j.LoggerFactory.getLogger(ApplicationBackupService.class)
                                    .warn("Unable to clean up restored image after rollback", exception);
                        }
                    }
                }
            }
        });
        for (S3ImageStorage.BackupImage image : snapshot.images()) {
            String restored = storage.restoreImage(image);
            uploaded.add(restored);
            for (Table table : snapshot.tables()) {
                for (Map<String, String> row : table.rows()) {
                    row.replaceAll((column, value) -> replaceImage(value, image.url(), restored));
                }
            }
        }
    }

    private String replaceImage(String value, String previous, String restored) {
        if (value == null) return null;
        if (value.equals(previous)) return restored;
        if (value.stripLeading().startsWith("{") || value.stripLeading().startsWith("[")) {
            try {
                JsonNode json = mapper.readTree(value);
                replaceImage(json, previous, restored);
                return mapper.writeValueAsString(json);
            } catch (java.io.IOException exception) {
                return value;
            }
        }
        return value;
    }

    private void replaceImage(JsonNode node, String previous, String restored) {
        if (node instanceof com.fasterxml.jackson.databind.node.ObjectNode object) {
            List<String> fields = new ArrayList<>();
            object.fieldNames().forEachRemaining(fields::add);
            for (String field : fields) {
                JsonNode child = object.get(field);
                if (child.isTextual() && child.textValue().equals(previous)) object.put(field, restored);
                else replaceImage(child, previous, restored);
            }
        } else if (node instanceof com.fasterxml.jackson.databind.node.ArrayNode array) {
            for (int index = 0; index < array.size(); index++) {
                JsonNode child = array.get(index);
                if (child.isTextual() && child.textValue().equals(previous)) array.set(index,
                        com.fasterxml.jackson.databind.node.TextNode.valueOf(restored));
                else replaceImage(child, previous, restored);
            }
        }
    }

    private Snapshot read(byte[] bytes) {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry = zip.getNextEntry();
            if (entry == null || !"database.json".equals(entry.getName())) throw invalid();
            byte[] json = zip.readNBytes(MAX_BYTES + 1);
            if (json.length > MAX_BYTES || zip.getNextEntry() != null) throw invalid();
            Snapshot snapshot = mapper.readValue(json, Snapshot.class);
            if (snapshot == null || snapshot.version() != 1 || snapshot.tables() == null || snapshot.images() == null) throw invalid();
            return snapshot;
        } catch (java.io.IOException | IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private void validate(Snapshot snapshot, List<Table> current) {
        Set<String> imageUrls = new HashSet<>();
        for (S3ImageStorage.BackupImage image : snapshot.images()) {
            if (image == null || image.url() == null || image.bytes() == null || image.contentType() == null
                    || !imageUrls.add(image.url())) throw invalid();
        }
        Map<String, Table> saved = new LinkedHashMap<>();
        for (Table table : snapshot.tables()) {
            if (table == null || saved.put(table.name(), table) != null) throw invalid();
        }
        if (saved.size() != current.size()) throw invalid();
        for (Table table : current) {
            Table source = saved.get(table.name());
            if (source == null || !table.columns().equals(source.columns()) || source.rows() == null) throw invalid();
            Set<String> columns = new HashSet<>();
            table.columns().forEach(column -> columns.add(column.name()));
            for (Map<String, String> row : source.rows()) {
                if (row == null || !columns.equals(row.keySet())) throw invalid();
            }
        }
        Table users = saved.get("users");
        Table roles = saved.get("roles");
        Table assignments = saved.get("user_roles");
        if (users != null && roles != null && assignments != null) {
            Set<String> admins = new HashSet<>();
            roles.rows().stream().filter(row -> "ADMIN".equals(row.get("name")))
                    .forEach(row -> admins.add(row.get("id")));
            Set<String> enabled = new HashSet<>();
            users.rows().stream().filter(row -> Boolean.parseBoolean(row.get("enabled")))
                    .forEach(row -> enabled.add(row.get("id")));
            if (assignments.rows().stream().noneMatch(row -> admins.contains(row.get("role_id"))
                    && enabled.contains(row.get("user_id")))) throw invalid();
        }
    }

    private void retainMasters(List<Table> tables) {
        Map<String, Set<String>> removed = new LinkedHashMap<>();
        for (Table table : tables) {
            Set<String> ids = new HashSet<>();
            table.rows().removeIf(row -> {
                boolean exclude = table.name().equals("competence_dossiers")
                        && !Boolean.parseBoolean(row.get("master"));
                for (ForeignKey key : table.keys()) {
                    exclude |= removed.getOrDefault(key.parent(), Set.of()).contains(row.get(key.column()));
                }
                if (exclude && row.get("id") != null) ids.add(row.get("id"));
                return exclude;
            });
            removed.put(table.name(), ids);
        }
    }

    private List<Table> schema(Connection connection) throws SQLException {
        List<Table> pending = new ArrayList<>();
        String schema = connection.getSchema();
        try (var result = connection.getMetaData().getTables(null, schema, "%", new String[]{"TABLE"})) {
            while (result.next()) {
                String name = result.getString("TABLE_NAME");
                if (name.equalsIgnoreCase("flyway_schema_history")
                    || name.equalsIgnoreCase("application_reset_challenges")) continue;
                List<Column> columns = new ArrayList<>();
                try (var fields = connection.getMetaData().getColumns(null, schema, name, "%")) {
                    while (fields.next()) columns.add(new Column(fields.getString("COLUMN_NAME"),
                            fields.getInt("DATA_TYPE"), fields.getString("TYPE_NAME")));
                }
                List<ForeignKey> keys = new ArrayList<>();
                try (var references = connection.getMetaData().getImportedKeys(null, schema, name)) {
                    while (references.next()) keys.add(new ForeignKey(references.getString("FKCOLUMN_NAME"),
                            references.getString("PKTABLE_NAME"), references.getString("PKCOLUMN_NAME")));
                }
                pending.add(new Table(name, columns, keys, new ArrayList<>()));
            }
        }
        List<Table> ordered = new ArrayList<>();
        Set<String> done = new HashSet<>();
        while (!pending.isEmpty()) {
            Table next = pending.stream().filter(table -> table.keys().stream()
                    .allMatch(key -> done.contains(key.parent()))).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Dependances de sauvegarde cycliques"));
            ordered.add(next);
            done.add(next.name());
            pending.remove(next);
        }
        return ordered;
    }

    private void lock(Connection connection, List<Table> tables) throws SQLException {
        if (connection.getMetaData().getDatabaseProductName().equals("PostgreSQL")) {
            String names = String.join(",", tables.stream().map(table -> quote(table.name())).sorted().toList());
            try (var statement = connection.createStatement()) {
                statement.execute("LOCK TABLE " + names + " IN SHARE ROW EXCLUSIVE MODE");
            }
        }
    }

    private void clear(Connection connection, List<Table> tables) throws SQLException {
        List<Table> reverse = new ArrayList<>(tables);
        Collections.reverse(reverse);
        try (var statement = connection.createStatement()) {
            for (Table table : reverse) statement.executeUpdate("DELETE FROM " + quote(table.name()));
        }
    }

    private void insert(Connection connection, Table table) throws SQLException {
        String names = String.join(",", table.columns().stream().map(column -> quote(column.name())).toList());
        String slots = String.join(",", Collections.nCopies(table.columns().size(), "?"));
        try (var statement = connection.prepareStatement("INSERT INTO " + quote(table.name())
                + " (" + names + ") VALUES (" + slots + ")")) {
            for (Map<String, String> row : table.rows()) {
                int index = 0;
                for (Column column : table.columns()) {
                    String value = row.get(column.name());
                        Object typed = value == null ? null : column.typeName().equalsIgnoreCase("uuid")
                            ? UUID.fromString(value) : switch (column.type()) {
                        case Types.BOOLEAN, Types.BIT -> Boolean.valueOf(value);
                        case Types.INTEGER, Types.SMALLINT, Types.TINYINT -> Integer.valueOf(value);
                        case Types.BIGINT -> Long.valueOf(value);
                        case Types.NUMERIC, Types.DECIMAL, Types.FLOAT, Types.REAL, Types.DOUBLE -> new BigDecimal(value);
                        case Types.DATE -> java.sql.Date.valueOf(value);
                        case Types.TIMESTAMP -> column.typeName().equalsIgnoreCase("timestamptz")
                            ? OffsetDateTime.parse(value) : java.sql.Timestamp.valueOf(value);
                        case Types.TIMESTAMP_WITH_TIMEZONE -> OffsetDateTime.parse(value);
                        case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY -> Base64.getDecoder().decode(value);
                        default -> column.typeName().equalsIgnoreCase("uuid") ? UUID.fromString(value) : value;
                    };
                        if (column.typeName().equalsIgnoreCase("timestamptz")
                            || column.typeName().equalsIgnoreCase("uuid")) statement.setObject(++index, typed);
                    else statement.setObject(++index, typed, column.type());
                }
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Sauvegarde invalide ou incompatible avec cette version de l'application");
    }
}
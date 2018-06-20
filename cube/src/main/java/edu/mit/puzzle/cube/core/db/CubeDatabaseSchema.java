package edu.mit.puzzle.cube.core.db;

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.io.Resources;

import edu.mit.puzzle.cube.core.model.VisibilityStatusSet;
import edu.mit.puzzle.cube.core.permissions.CubePermission;
import edu.mit.puzzle.cube.core.permissions.CubeRole;

import org.apache.commons.lang3.text.StrSubstitutor;

import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class CubeDatabaseSchema {
    private static final String VAR_AUTO_INCREMENT_TYPE = "auto_increment_type";
    private static final String VAR_DEFAULT_VISIBILITY_STATUS = "default_visibility_status";
    private static final String VAR_TIMESTAMP_DIFF_TO_NUMBER_PREFIX = "timestamp_diff_to_number_prefix";
    private static final String VAR_TIMESTAMP_DIFF_TO_NUMBER_SUFFIX = "timestamp_diff_to_number_suffix";
    private static final String VAR_CURRENT_TIMESTAMP = "current_timestamp";

    private final String schema;

    public CubeDatabaseSchema(String jdbcDriverClassName, VisibilityStatusSet visibilityStatusSet) {
        Map<String, String> schemaTemplateMap = new HashMap<>();
        schemaTemplateMap.put(
                VAR_DEFAULT_VISIBILITY_STATUS,
                visibilityStatusSet.getDefaultVisibilityStatus()
        );
        switch (jdbcDriverClassName) {
        case "org.sqlite.JDBC":
            schemaTemplateMap.put(VAR_AUTO_INCREMENT_TYPE, "INTEGER");
            schemaTemplateMap.put(VAR_TIMESTAMP_DIFF_TO_NUMBER_PREFIX, "CAST(");
            schemaTemplateMap.put(VAR_TIMESTAMP_DIFF_TO_NUMBER_SUFFIX, " AS REAL)");
            schemaTemplateMap.put(VAR_CURRENT_TIMESTAMP, "DATETIME('now')");
            break;
        case "org.postgresql.Driver":
            schemaTemplateMap.put(VAR_AUTO_INCREMENT_TYPE, "SERIAL");
            schemaTemplateMap.put(VAR_TIMESTAMP_DIFF_TO_NUMBER_PREFIX, "EXTRACT(EPOCH FROM ");
            schemaTemplateMap.put(VAR_TIMESTAMP_DIFF_TO_NUMBER_SUFFIX, ")");
            schemaTemplateMap.put(VAR_CURRENT_TIMESTAMP, "CURRENT_TIMESTAMP");
            break;
        case "com.mysql.jdbc.Driver":
            schemaTemplateMap.put(VAR_AUTO_INCREMENT_TYPE, "INT NOT NULL AUTO_INCREMENT");
            schemaTemplateMap.put(VAR_TIMESTAMP_DIFF_TO_NUMBER_PREFIX, "(");
            schemaTemplateMap.put(VAR_TIMESTAMP_DIFF_TO_NUMBER_SUFFIX, ")");
            schemaTemplateMap.put(VAR_CURRENT_TIMESTAMP, "NOW()");
            break;
        default:
            throw new RuntimeException(
                    "Unsupported database driver: " + jdbcDriverClassName);
        }

        URL schemaUrl = Resources.getResource("cube.sql");
        String schemaTemplate;
        try {
            schemaTemplate = Resources.toString(schemaUrl, Charsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        schema = new StrSubstitutor(schemaTemplateMap).replace(schemaTemplate);
    }

    public void execute(Connection connection) throws SQLException {
        Splitter schemaSplitter = Splitter.on(";").omitEmptyStrings().trimResults();
        for (String schemaStatement : schemaSplitter.split(schema)) {
            try (PreparedStatement statement = connection.prepareStatement(schemaStatement)) {
                statement.execute();
            }
        }

        try (
                PreparedStatement insertRole = connection.prepareStatement(
                        "INSERT INTO roles (role_name) VALUES (?)"
                );
                PreparedStatement insertPermission = connection.prepareStatement(
                        "INSERT INTO roles_permissions (role_name, permission) VALUES (?,?)"
                )
        ) {
            for (CubeRole role : CubeRole.ALL_ROLES) {
                insertRole.setString(1, role.getName());
                insertRole.executeUpdate();
                insertPermission.setString(1, role.getName());
                for (CubePermission permission : role.getPermissions()) {
                    insertPermission.setString(2, permission.getWildcardString());
                    insertPermission.executeUpdate();
                }
            }
        }
    }
}

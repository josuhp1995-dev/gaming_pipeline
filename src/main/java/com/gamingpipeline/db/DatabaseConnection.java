package com.gamingpipeline.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class DatabaseConnection {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5432;

    private DatabaseConnection() {
    }

    public static Connection open() throws SQLException {
        String host = environmentOrDefault("PGHOST", DEFAULT_HOST);
        int port = readPort(environmentOrDefault("PGPORT", Integer.toString(DEFAULT_PORT)));
        String database = requiredEnvironment("PGDATABASE");
        String username = requiredEnvironment("PGUSER");
        String password = requiredEnvironment("PGPASSWORD");

        String url = "jdbc:postgresql://" + host + ":" + port + "/" + database;
        Properties properties = new Properties();
        properties.setProperty("user", username);
        properties.setProperty("password", password);

        return DriverManager.getConnection(url, properties);
    }

    private static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Falta la variable de entorno obligatoria " + name + "."
            );
        }
        return value;
    }

    private static int readPort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("PGPORT debe estar entre 1 y 65535.");
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("PGPORT debe ser un número válido.", e);
        }
    }
}

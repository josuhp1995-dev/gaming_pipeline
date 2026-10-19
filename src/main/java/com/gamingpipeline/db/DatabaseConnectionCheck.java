package com.gamingpipeline.db;

import java.sql.Connection;
import java.sql.SQLException;

public final class DatabaseConnectionCheck {
    private DatabaseConnectionCheck() {
    }

    public static void main(String[] args) throws SQLException {
        try (Connection connection = DatabaseConnection.open()) {
            System.out.printf(
                    "Conexión correcta a PostgreSQL: %s (base de datos: %s)%n",
                    connection.getMetaData().getDatabaseProductVersion(),
                    connection.getCatalog()
            );
        }
    }
}

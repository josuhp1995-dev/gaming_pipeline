package com.gamingpipeline.etl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamingpipeline.db.DatabaseConnection;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;

public final class CheapSharkIngestion {
    private static final String API_BASE_URL = "https://www.cheapshark.com/api/1.0";
    private static final int PAGE_SIZE = 60;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private CheapSharkIngestion() {
    }

    public static void main(String[] args) throws IOException, InterruptedException, SQLException {
        CheapSharkIngestion ingestion = new CheapSharkIngestion();
        JsonNode stores = ingestion.fetchJson("/stores");
        JsonNode deals = ingestion.fetchJson(
                "/deals?pageSize=" + PAGE_SIZE + "&pageNumber=0");

        try (Connection connection = DatabaseConnection.open()) {
            ingestion.saveDataToDatabase(connection, stores, deals);
        }

        System.out.printf("Ingestadas %d tiendas y %d ofertas.%n", stores.size(), deals.size());
    }

    private JsonNode fetchJson(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("User-Agent", "GamingPipeline/1.0 (Java CheapShark ETL)")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(
                    "CheapShark devolvió HTTP " + response.statusCode()
                            + " al solicitar " + path + ": " + response.body());
        }

        JsonNode result = objectMapper.readTree(response.body());
        if (result == null || !result.isArray()) {
            throw new IOException("CheapShark devolvió una respuesta JSON inesperada para " + path);
        }
        return result;
    }

    private void saveDataToDatabase(
            Connection connection,
            JsonNode stores,
            JsonNode deals) throws SQLException {
        String insertStoreSql = "INSERT INTO dim_tienda (id_tienda, nombre_tienda) VALUES (?, ?) " +
                "ON CONFLICT (id_tienda) DO UPDATE " +
                "SET nombre_tienda = EXCLUDED.nombre_tienda";
        String insertGameSql = "INSERT INTO dim_juego (id_juego, titulo, precio_normal) VALUES (?, ?, ?) " +
                "ON CONFLICT (id_juego) DO NOTHING";
        String insertDealSql = "INSERT INTO fact_oferta " +
                "(id_juego, id_tienda, precio_rebajado, descuento_pct) " +
                "SELECT ?, ?, ?, ? " +
                "WHERE NOT EXISTS (" +
                "SELECT 1 FROM fact_oferta " +
                "WHERE id_juego = ? AND id_tienda = ? " +
                "AND precio_rebajado = ? AND descuento_pct = ?" +
                ")";

        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (PreparedStatement storeStatement = connection.prepareStatement(insertStoreSql);
                PreparedStatement gameStatement = connection.prepareStatement(insertGameSql);
                PreparedStatement dealStatement = connection.prepareStatement(insertDealSql)) {

            for (JsonNode store : stores) {
                storeStatement.setInt(1, requiredInteger(store, "storeID"));
                storeStatement.setString(2, requiredText(store, "storeName"));
                storeStatement.addBatch();
            }
            storeStatement.executeBatch();

            for (JsonNode deal : deals) {
                String gameId = requiredText(deal, "gameID");
                int storeId = requiredInteger(deal, "storeID");
                BigDecimal normalPrice = requiredDecimal(deal, "normalPrice");
                BigDecimal salePrice = requiredDecimal(deal, "salePrice");
                BigDecimal savings = requiredDecimal(deal, "savings");

                gameStatement.setString(1, gameId);
                gameStatement.setString(2, requiredText(deal, "title"));
                gameStatement.setBigDecimal(3, normalPrice);
                gameStatement.addBatch();

                dealStatement.setString(1, gameId);
                dealStatement.setInt(2, storeId);
                dealStatement.setBigDecimal(3, salePrice);
                dealStatement.setBigDecimal(4, savings);
                dealStatement.setString(5, gameId);
                dealStatement.setInt(6, storeId);
                dealStatement.setBigDecimal(7, salePrice);
                dealStatement.setBigDecimal(8, savings);
                dealStatement.addBatch();
            }
            gameStatement.executeBatch();
            dealStatement.executeBatch();
            connection.commit();
        } catch (SQLException | RuntimeException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackError) {
                e.addSuppressed(rollbackError);
            }
            throw e;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    private static String requiredText(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException(
                    "Falta el campo obligatorio '" + field + "' en la respuesta de CheapShark.");
        }
        return value.asText();
    }

    private static BigDecimal requiredDecimal(JsonNode object, String field) {
        String value = requiredText(object, field);
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "El campo '" + field + "' no contiene un número válido: " + value,
                    e);
        }
    }

    private static int requiredInteger(JsonNode object, String field) {
        String value = requiredText(object, field);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "El campo '" + field + "' no contiene un entero válido: " + value,
                    e);
        }
    }
}

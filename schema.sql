-- Esquema estrella simplificado para análisis de ofertas de videojuegos.
-- Las dimensiones descriptivas se separan de los hechos de oferta para
-- facilitar consultas analíticas y cargas ETL desde la API de CheapShark.

DROP TABLE IF EXISTS fact_oferta CASCADE;
DROP TABLE IF EXISTS dim_juego CASCADE;
DROP TABLE IF EXISTS dim_tienda CASCADE;

CREATE TABLE dim_tienda (
    id_tienda INT PRIMARY KEY,
    nombre_tienda VARCHAR NOT NULL
);

CREATE TABLE dim_juego (
    id_juego VARCHAR PRIMARY KEY,
    titulo VARCHAR NOT NULL,
    precio_normal NUMERIC(10, 2) NOT NULL
);

CREATE TABLE fact_oferta (
    id_oferta SERIAL PRIMARY KEY,
    id_juego VARCHAR REFERENCES dim_juego (id_juego),
    id_tienda INT REFERENCES dim_tienda (id_tienda),
    precio_rebajado NUMERIC(10, 2) NOT NULL,
    descuento_pct NUMERIC(5, 2) NOT NULL,
    fecha_registro TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- El índice B-Tree compuesto acelera búsquedas y agregaciones por juego y tienda.
CREATE INDEX idx_fact_oferta_juego_tienda
    ON fact_oferta USING BTREE (id_juego, id_tienda);

COMMENT ON TABLE dim_tienda IS
    'Dimensión de tiendas para segmentar y analizar ofertas por comercio.';
COMMENT ON TABLE dim_juego IS
    'Dimensión de juegos con atributos descriptivos y precio normal para análisis.';
COMMENT ON TABLE fact_oferta IS
    'Hechos de ofertas registrados para cargas ETL y análisis históricos de precios y descuentos.';

-- Почетна шема. Flyway ја извршува автоматски при старт на апликацијата.

CREATE EXTENSION IF NOT EXISTS vector;

-- Град: координати на центарот и граници (bounding box) од Nominatim
CREATE TABLE city (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(200)     NOT NULL,
    country           VARCHAR(100),
    lat               DOUBLE PRECISION NOT NULL,
    lon               DOUBLE PRECISION NOT NULL,
    south             DOUBLE PRECISION NOT NULL,
    west              DOUBLE PRECISION NOT NULL,
    north             DOUBLE PRECISION NOT NULL,
    east              DOUBLE PRECISION NOT NULL,
    places_fetched_at TIMESTAMP        -- кога последен пат се преземени местата (кеш)
);

-- Место од OpenStreetMap: атракција, ресторан или кафуле
CREATE TABLE place (
    id             BIGSERIAL PRIMARY KEY,
    city_id        BIGINT           NOT NULL REFERENCES city (id) ON DELETE CASCADE,
    osm_id         BIGINT           NOT NULL,
    name           VARCHAR(300)     NOT NULL,
    lat            DOUBLE PRECISION NOT NULL,
    lon            DOUBLE PRECISION NOT NULL,
    category       VARCHAR(50)      NOT NULL, -- museum, park, viewpoint ...
    kind           VARCHAR(20)      NOT NULL, -- ATTRACTION | RESTAURANT | CAFE
    indoor         BOOLEAN          NOT NULL DEFAULT FALSE,
    has_wikipedia  BOOLEAN          NOT NULL DEFAULT FALSE,
    opening_hours  VARCHAR(300),
    visit_minutes  INT              NOT NULL DEFAULT 45,
    UNIQUE (city_id, osm_id)
);

CREATE INDEX idx_place_city_kind ON place (city_id, kind);

-- Генериран план. Самиот план (денови, места, време) се чува како JSON.
CREATE TABLE trip (
    id         UUID PRIMARY KEY,
    city_id    BIGINT       NOT NULL REFERENCES city (id),
    start_date DATE         NOT NULL,
    end_date   DATE         NOT NULL,
    pace       VARCHAR(20)  NOT NULL,
    interests  TEXT[]       NOT NULL,
    plan       JSONB        NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);

-- Секој објект во OpenStreetMap се идентификува со тип (node, way, relation) и број.
-- Бројот сам не е уникатен: node 123 и way 123 се различни објекти.
-- Затоа го чуваме и типот, за да знаеме дали град/место веќе постои во базата.

ALTER TABLE city ADD COLUMN osm_type VARCHAR(10) NOT NULL;
ALTER TABLE city ADD COLUMN osm_id   BIGINT      NOT NULL;
ALTER TABLE city ADD CONSTRAINT uq_city_osm UNIQUE (osm_type, osm_id);

ALTER TABLE place ADD COLUMN osm_type VARCHAR(10) NOT NULL;
ALTER TABLE place DROP CONSTRAINT place_city_id_osm_id_key;
ALTER TABLE place ADD CONSTRAINT uq_place_osm UNIQUE (city_id, osm_type, osm_id);

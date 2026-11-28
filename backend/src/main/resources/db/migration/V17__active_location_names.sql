ALTER TABLE locations DROP CONSTRAINT locations_name_key;
CREATE UNIQUE INDEX locations_active_name_key ON locations (btrim(name)) WHERE status = 'ACTIVE';

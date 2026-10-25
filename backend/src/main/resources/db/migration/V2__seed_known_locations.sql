-- Temporary display names for the three known offices and the materials room.
-- Change these through a later migration or management UI before a real rollout.
INSERT INTO locations (name, type) VALUES
    ('办公室 A', 'OFFICE'),
    ('办公室 B', 'OFFICE'),
    ('办公室 C', 'OFFICE'),
    ('物资室', 'STORAGE');

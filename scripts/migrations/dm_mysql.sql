-- Run once against MySQL after taking a database backup and stopping Spring.
ALTER TABLE `groups` ADD COLUMN dm_key VARCHAR(255) NULL;
CREATE UNIQUE INDEX ux_groups_dm_key ON `groups` (dm_key);
CREATE TABLE IF NOT EXISTS dm_read_receipt (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    read_at VARCHAR(255)
);

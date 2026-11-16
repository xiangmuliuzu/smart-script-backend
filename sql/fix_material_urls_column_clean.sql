USE script_platform_dev;

ALTER TABLE sys_copyright_seal 
MODIFY COLUMN material_urls TEXT COMMENT 'Material URLs (comma-separated)';

SHOW CREATE TABLE sys_copyright_seal;

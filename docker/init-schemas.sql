-- StorePoint v2: Schema-per-Service Isolation on Single Self-Hosted PostgreSQL Instance
-- Ensures strict service boundaries without forcing small stores to run 7 database containers.

CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS catalog;
CREATE SCHEMA IF NOT EXISTS inventory;
CREATE SCHEMA IF NOT EXISTS pos;
CREATE SCHEMA IF NOT EXISTS purchasing;
CREATE SCHEMA IF NOT EXISTS finance_crm;
CREATE SCHEMA IF NOT EXISTS extensions;

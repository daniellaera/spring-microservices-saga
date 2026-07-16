-- Create testuser
DO $$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'testuser') THEN
            CREATE USER testuser WITH PASSWORD 'testpass';
        END IF;
    END
$$;

-- Create all databases
CREATE DATABASE authdb;
CREATE DATABASE orderdb;
CREATE DATABASE inventorydb;
CREATE DATABASE paymentdb;
CREATE DATABASE auditdb;

-- Grant privileges on databases
GRANT ALL PRIVILEGES ON DATABASE authdb TO testuser;
GRANT ALL PRIVILEGES ON DATABASE orderdb TO testuser;
GRANT ALL PRIVILEGES ON DATABASE inventorydb TO testuser;
GRANT ALL PRIVILEGES ON DATABASE paymentdb TO testuser;
GRANT ALL PRIVILEGES ON DATABASE auditdb TO testuser;

-- Grant schema privileges (required for PostgreSQL 15+)
\connect authdb
GRANT ALL ON SCHEMA public TO testuser;

\connect orderdb
GRANT ALL ON SCHEMA public TO testuser;

\connect inventorydb
GRANT ALL ON SCHEMA public TO testuser;

\connect paymentdb
GRANT ALL ON SCHEMA public TO testuser;

\connect auditdb
GRANT ALL ON SCHEMA public TO testuser;
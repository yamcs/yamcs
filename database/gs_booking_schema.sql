-- Simplified Ground Station Booking System Database Schema
-- PostgreSQL Database Setup for MCC Ground Station Booking

-- Connect to mcc database
\c mcc;

-- Ground Station Provider Types
CREATE TYPE provider_type AS ENUM ('leafspace', 'dhruva', 'isro');

-- Booking Frequency/Rule Types
CREATE TYPE booking_rule_type AS ENUM ('daily', 'weekly', 'monthly', 'one_time');

-- Booking Status
CREATE TYPE booking_status AS ENUM ('pending', 'approved', 'rejected', 'cancelled', 'completed');

-- Ground Station Providers Table
CREATE TABLE gs_providers (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    type provider_type NOT NULL,
    contact_email VARCHAR(100),
    contact_phone VARCHAR(20),
    api_endpoint VARCHAR(200),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Ground Station Bookings Table (references existing YAMCS ground stations)
CREATE TABLE gs_bookings (
    id SERIAL PRIMARY KEY,
    provider_id INTEGER NOT NULL REFERENCES gs_providers(id),
    yamcs_gs_name VARCHAR(100) NOT NULL, -- Reference to existing YAMCS ground station name

    -- Booking Details
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    purpose TEXT NOT NULL,
    mission_name VARCHAR(100),
    satellite_name VARCHAR(100),

    -- Rule-based booking
    rule_type booking_rule_type NOT NULL DEFAULT 'one_time',
    frequency_days INTEGER, -- for daily/weekly patterns

    -- Status and Approval
    status booking_status DEFAULT 'pending',
    requested_by VARCHAR(50) NOT NULL, -- YAMCS username
    approved_by VARCHAR(50),
    approved_at TIMESTAMP,
    rejection_reason TEXT,

    -- Metadata
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    CONSTRAINT valid_time_range CHECK (end_time > start_time)
);

-- Booking Approval Log
CREATE TABLE booking_approvals (
    id SERIAL PRIMARY KEY,
    booking_id INTEGER NOT NULL REFERENCES gs_bookings(id),
    approver VARCHAR(50) NOT NULL,
    action VARCHAR(20) NOT NULL, -- 'approved', 'rejected'
    comments TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_gs_bookings_provider ON gs_bookings(provider_id);
CREATE INDEX idx_gs_bookings_yamcs_gs ON gs_bookings(yamcs_gs_name);
CREATE INDEX idx_gs_bookings_start_time ON gs_bookings(start_time);
CREATE INDEX idx_gs_bookings_status ON gs_bookings(status);

-- Function for automatic timestamp updates
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers
CREATE TRIGGER update_gs_providers_updated_at BEFORE UPDATE ON gs_providers FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_gs_bookings_updated_at BEFORE UPDATE ON gs_bookings FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Sample data
INSERT INTO gs_providers (name, type, contact_email, api_endpoint) VALUES
('Leafspace Network', 'leafspace', 'booking@leafspace.in', 'https://api.leafspace.in/booking'),
('Dhruva Space', 'dhruva', 'ops@dhruvaspace.com', 'https://api.dhruvaspace.com/gs'),
('ISRO Ground Network', 'isro', 'gn@isro.gov.in', null);

-- Views
CREATE VIEW booking_dashboard AS
SELECT
    b.id,
    b.start_time,
    b.end_time,
    b.yamcs_gs_name,
    b.purpose,
    b.mission_name,
    b.rule_type,
    b.status,
    p.name as provider_name,
    p.type as provider_type,
    b.requested_by,
    EXTRACT(EPOCH FROM (b.end_time - b.start_time))/60 as duration_minutes
FROM gs_bookings b
JOIN gs_providers p ON b.provider_id = p.id
ORDER BY b.start_time DESC;

CREATE VIEW pending_approvals AS
SELECT * FROM booking_dashboard
WHERE status = 'pending'
ORDER BY start_time ASC;

-- Grant permissions
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO mcc_dbadmin;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO mcc_dbadmin;
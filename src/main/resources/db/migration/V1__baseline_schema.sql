CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS businesses (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(120) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS appointment_slots (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id),
    staff_id UUID NOT NULL REFERENCES users(id),
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT uk_slot_business_staff_start UNIQUE (business_id, staff_id, start_time)
);

CREATE TABLE IF NOT EXISTS appointments (
    id UUID PRIMARY KEY,
    slot_id UUID NOT NULL UNIQUE REFERENCES appointment_slots(id),
    customer_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS waitlist_entries (
    id UUID PRIMARY KEY,
    slot_id UUID NOT NULL REFERENCES appointment_slots(id),
    customer_id UUID NOT NULL REFERENCES users(id),
    priority INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_waitlist_slot_customer UNIQUE (slot_id, customer_id)
);

CREATE TABLE IF NOT EXISTS slot_blocks (
    id UUID PRIMARY KEY,
    slot_id UUID NOT NULL REFERENCES appointment_slots(id),
    customer_id UUID NOT NULL REFERENCES users(id),
    reason VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_slot_block_customer UNIQUE (slot_id, customer_id)
);

#!/bin/bash

# Ground Station Booking System Setup Script
# This script sets up the database and initializes the booking system

set -e

echo "🚀 Setting up YAMCS Ground Station Booking System..."

# Check if PostgreSQL is running
if ! pg_isready -h localhost -p 5432 > /dev/null 2>&1; then
    echo "❌ PostgreSQL is not running on localhost:5432"
    echo "Please start PostgreSQL and try again."
    exit 1
fi

echo "✅ PostgreSQL is running"

# Load environment variables
if [ -f .env ]; then
    echo "📄 Loading environment variables from .env"
    source .env
else
    echo "⚠️  .env file not found, using default values"
    export POSTGRES_USER=${POSTGRES_USER:-mcc_dbadmin}
    export POSTGRES_PASSWORD=${POSTGRES_PASSWORD:-mcc_dbadmin}
    export POSTGRES_DB=${POSTGRES_DB:-mcc}
    export POSTGRES_HOST=${POSTGRES_HOST:-localhost}
    export POSTGRES_PORT=${POSTGRES_PORT:-5432}
fi

echo "🔧 Database configuration:"
echo "  Host: $POSTGRES_HOST:$POSTGRES_PORT"
echo "  Database: $POSTGRES_DB"
echo "  User: $POSTGRES_USER"

# Test database connection
echo "🔍 Testing database connection..."
if ! PGPASSWORD=$POSTGRES_PASSWORD psql -h $POSTGRES_HOST -p $POSTGRES_PORT -U $POSTGRES_USER -d postgres -c "SELECT 1;" > /dev/null 2>&1; then
    echo "❌ Failed to connect to PostgreSQL"
    echo "Please check your database configuration and credentials."
    exit 1
fi

echo "✅ Database connection successful"

# Create database if it doesn't exist
echo "🗄️  Creating database '$POSTGRES_DB' if it doesn't exist..."
PGPASSWORD=$POSTGRES_PASSWORD psql -h $POSTGRES_HOST -p $POSTGRES_PORT -U $POSTGRES_USER -d postgres -c "CREATE DATABASE $POSTGRES_DB;" 2>/dev/null || echo "Database '$POSTGRES_DB' already exists"

# Run the schema setup
echo "📊 Setting up database schema..."
if [ -f "database/gs_booking_schema.sql" ]; then
    PGPASSWORD=$POSTGRES_PASSWORD psql -h $POSTGRES_HOST -p $POSTGRES_PORT -U $POSTGRES_USER -d $POSTGRES_DB -f database/gs_booking_schema.sql
    echo "✅ Database schema created successfully"
else
    echo "❌ Schema file 'database/gs_booking_schema.sql' not found"
    exit 1
fi

# Build the project
echo "🔨 Building YAMCS with booking module..."
if command -v mvn &> /dev/null; then
    mvn clean compile -q
    echo "✅ Build completed successfully"
else
    echo "⚠️  Maven not found, skipping build step"
    echo "Please run 'mvn clean install' manually to build the project"
fi

echo ""
echo "🎉 YAMCS Ground Station Booking System setup completed!"
echo ""
echo "📋 Next steps:"
echo "1. Start YAMCS server: 'mvn exec:java -Dexec.mainClass=org.yamcs.YamcsServer'"
echo "2. Open YAMCS web interface: http://localhost:8090"
echo "3. Navigate to 'Booking' section to start using the system"
echo ""
echo "📖 For more information, see yamcs-booking/README.md"
echo ""
echo "🔗 Booking API endpoints:"
echo "  - GET  /api/booking/providers"
echo "  - GET  /api/booking/bookings"
echo "  - POST /api/booking/bookings"
echo "  - GET  /api/booking/bookings/pending"
echo "  - POST /api/booking/bookings/{id}/approve"
echo "  - POST /api/booking/bookings/{id}/reject"
echo ""
echo "Happy booking! 🛰️"
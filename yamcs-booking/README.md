# YAMCS Ground Station Booking System

A comprehensive ground station booking system for YAMCS with rule-based scheduling, approval workflow, and support for multiple ground station providers (Leafspace, Dhruva, ISRO).

## Features

- **Multiple Provider Support**: Leafspace, Dhruva Space, and ISRO ground station networks
- **Rule-based Booking**: Support for one-time, daily, weekly, and monthly recurring bookings
- **Approval Workflow**: Booking requests require approval before confirmation
- **Dashboard**: Real-time overview of bookings, approvals, and provider status
- **REST API**: Complete REST API for integration with external systems
- **Web Interface**: Modern Angular-based web interface integrated with YAMCS

## Architecture

### Backend (Java)
- **yamcs-booking**: REST API module built on YAMCS framework
- **PostgreSQL**: Database for storing bookings, providers, and approval workflow
- **HikariCP**: Connection pooling for optimal database performance

### Frontend (Angular)
- **Booking Dashboard**: Overview of system status and recent activity
- **Booking Management**: Create, view, and manage bookings
- **Approval Workflow**: Interface for approving/rejecting pending bookings
- **Provider Management**: View available ground station providers

## Setup Instructions

### 1. Database Setup

```bash
# Create PostgreSQL database
createdb mcc

# Run the schema script
psql -d mcc -f database/gs_booking_schema.sql
```

### 2. Environment Configuration

Update `.env` file with PostgreSQL connection details:

```env
POSTGRES_USER=mcc_dbadmin
POSTGRES_PASSWORD=mcc_dbadmin
POSTGRES_DB=mcc
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
```

### 3. Build and Deploy

```bash
# Build the complete project including booking module
mvn clean install

# The booking plugin will be automatically included in the YAMCS build
```

### 4. YAMCS Configuration

The booking plugin is automatically loaded when YAMCS starts. The plugin configuration is in:
`yamcs-booking/src/main/resources/org/yamcs/booking/BookingPlugin.yaml`

## API Endpoints

### Providers
- `GET /api/booking/providers` - Get all ground station providers

### Bookings
- `GET /api/booking/bookings` - Get all bookings
- `POST /api/booking/bookings` - Create new booking
- `GET /api/booking/bookings/pending` - Get pending approval bookings

### Approvals
- `POST /api/booking/bookings/{id}/approve` - Approve booking
- `POST /api/booking/bookings/{id}/reject` - Reject booking

## Usage

### Creating a Booking

1. Navigate to **Booking** in the YAMCS web interface
2. Click **New Booking**
3. Fill in the booking details:
   - Select ground station provider
   - Enter YAMCS ground station name
   - Set start and end times
   - Choose booking rule (one-time or recurring)
   - Add mission details and purpose
4. Submit for approval

### Managing Approvals

1. Navigate to **Booking > Pending Approvals**
2. Review pending booking requests
3. Click **Approve** or **Reject** with optional comments
4. Bookings are automatically updated with approval status

### Dashboard Overview

The main booking dashboard provides:
- Count of pending approvals
- Active bookings status
- Available ground station providers
- Recent booking activity

## Database Schema

### Core Tables

- **gs_providers**: Ground station provider information
- **gs_bookings**: Booking requests and their status
- **booking_approvals**: Approval workflow log

### Key Features

- **Conflict Prevention**: Database constraints prevent overlapping bookings
- **Audit Trail**: Complete audit trail of all booking actions
- **Rule-based Scheduling**: Support for recurring booking patterns

## Ground Station Providers

### Supported Providers

1. **Leafspace Network** (`leafspace`)
   - API integration ready
   - Contact: booking@leafspace.in

2. **Dhruva Space** (`dhruva`)
   - API integration ready
   - Contact: ops@dhruvaspace.com

3. **ISRO Ground Network** (`isro`)
   - Government network integration
   - Contact: gn@isro.gov.in

## Development

### Adding New Providers

1. Update the `provider_type` enum in the database schema
2. Add the new provider to the sample data
3. Update the booking service to handle provider-specific logic

### Extending the API

1. Add new endpoints in `BookingRestApi.java`
2. Update the Angular service in `booking.service.ts`
3. Create corresponding UI components

### Custom Booking Rules

1. Extend the `BookingRuleType` enum
2. Update the database schema
3. Implement rule logic in the booking service

## Security

- All API endpoints require authentication
- Role-based access control for approvals
- Input validation and SQL injection prevention
- Audit logging for compliance

## Monitoring

- Database connection pool metrics
- Booking request volume tracking
- Approval workflow performance
- Provider availability monitoring

## Troubleshooting

### Common Issues

1. **Database Connection**: Check PostgreSQL service and credentials
2. **Plugin Loading**: Verify plugin configuration in YAMCS logs
3. **API Errors**: Check YAMCS HTTP server configuration
4. **Frontend Issues**: Ensure Angular build includes booking module

### Logs

Check YAMCS logs for booking-related messages:
```
grep "booking" /path/to/yamcs/logs/yamcs.log
```

## License

This module is part of the YAMCS project and follows the same AGPL-3.0 license terms.
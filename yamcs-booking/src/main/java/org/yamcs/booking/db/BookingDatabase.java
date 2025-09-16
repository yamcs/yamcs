package org.yamcs.booking.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YConfiguration;
import org.yamcs.booking.model.*;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Database access layer for Ground Station Booking System
 */
public class BookingDatabase {

    private static final Logger log = LoggerFactory.getLogger(BookingDatabase.class);

    private final HikariDataSource dataSource;

    public BookingDatabase(YConfiguration config) {
        log.info("Initializing Booking Database");

        // Get database configuration from environment or config
        String host = System.getenv("POSTGRES_HOST");
        String port = System.getenv("POSTGRES_PORT");
        String database = System.getenv("POSTGRES_DB");
        String username = System.getenv("POSTGRES_USER");
        String password = System.getenv("POSTGRES_PASSWORD");

        if (host == null) host = "localhost";
        if (port == null) port = "5432";
        if (database == null) database = "mcc";
        if (username == null) username = "mcc_dbadmin";
        if (password == null) password = "mcc_dbadmin";

        String jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s", host, port, database);

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setIdleTimeout(600000);
        hikariConfig.setMaxLifetime(1800000);

        this.dataSource = new HikariDataSource(hikariConfig);

        log.info("Database connection pool initialized for {}", jdbcUrl);
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("Database connection pool closed");
        }
    }


    // Provider methods
    public List<GSProvider> getAllProviders() throws SQLException {
        List<GSProvider> providers = new ArrayList<>();
        String sql = "SELECT * FROM gs_providers WHERE is_active = true ORDER BY name";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                providers.add(mapProviderFromResultSet(rs));
            }
        }

        return providers;
    }

    public GSProvider getProviderById(int id) throws SQLException {
        String sql = "SELECT * FROM gs_providers WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapProviderFromResultSet(rs);
                }
            }
        }

        return null;
    }

    // Booking methods
    public List<GSBooking> getAllBookings() throws SQLException {
        List<GSBooking> bookings = new ArrayList<>();
        String sql = """
            SELECT b.*, p.name as provider_name, p.type as provider_type
            FROM gs_bookings b
            JOIN gs_providers p ON b.provider_id = p.id
            ORDER BY b.start_time DESC
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                bookings.add(mapBookingFromResultSet(rs));
            }
        }

        return bookings;
    }

    public List<GSBooking> getPendingBookings() throws SQLException {
        List<GSBooking> bookings = new ArrayList<>();
        String sql = """
            SELECT b.*, p.name as provider_name, p.type as provider_type
            FROM gs_bookings b
            JOIN gs_providers p ON b.provider_id = p.id
            WHERE b.status = 'pending'
            ORDER BY b.start_time ASC
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                bookings.add(mapBookingFromResultSet(rs));
            }
        }

        return bookings;
    }

    public GSBooking createBooking(GSBooking booking) throws SQLException {
        String sql = """
            INSERT INTO gs_bookings (provider_id, yamcs_gs_name, start_time, end_time,
                                   purpose, mission_name, satellite_name, rule_type,
                                   frequency_days, requested_by, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::booking_rule_type, ?, ?, ?)
            RETURNING id
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, booking.getProviderId());
            stmt.setString(2, booking.getYamcsGsName());
            stmt.setTimestamp(3, Timestamp.valueOf(booking.getStartTime()));
            stmt.setTimestamp(4, Timestamp.valueOf(booking.getEndTime()));
            stmt.setString(5, booking.getPurpose());
            stmt.setString(6, booking.getMissionName());
            stmt.setString(7, booking.getSatelliteName());
            stmt.setString(8, booking.getRuleType().toString());
            stmt.setObject(9, booking.getFrequencyDays());
            stmt.setString(10, booking.getRequestedBy());
            stmt.setString(11, booking.getNotes());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    booking.setId(rs.getInt(1));
                }
            }
        }

        return booking;
    }

    public boolean approveBooking(int bookingId, String approver, String comments) throws SQLException {
        String updateSql = """
            UPDATE gs_bookings
            SET status = 'approved', approved_by = ?, approved_at = CURRENT_TIMESTAMP
            WHERE id = ? AND status = 'pending'
            """;

        String logSql = """
            INSERT INTO booking_approvals (booking_id, approver, action, comments)
            VALUES (?, ?, 'approved', ?)
            """;

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql);
                 PreparedStatement logStmt = conn.prepareStatement(logSql)) {

                // Update booking status
                updateStmt.setString(1, approver);
                updateStmt.setInt(2, bookingId);
                int updated = updateStmt.executeUpdate();

                if (updated > 0) {
                    // Log approval
                    logStmt.setInt(1, bookingId);
                    logStmt.setString(2, approver);
                    logStmt.setString(3, comments);
                    logStmt.executeUpdate();

                    conn.commit();
                    return true;
                } else {
                    conn.rollback();
                    return false;
                }
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public boolean rejectBooking(int bookingId, String approver, String reason) throws SQLException {
        String updateSql = """
            UPDATE gs_bookings
            SET status = 'rejected', rejection_reason = ?
            WHERE id = ? AND status = 'pending'
            """;

        String logSql = """
            INSERT INTO booking_approvals (booking_id, approver, action, comments)
            VALUES (?, ?, 'rejected', ?)
            """;

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql);
                 PreparedStatement logStmt = conn.prepareStatement(logSql)) {

                // Update booking status
                updateStmt.setString(1, reason);
                updateStmt.setInt(2, bookingId);
                int updated = updateStmt.executeUpdate();

                if (updated > 0) {
                    // Log rejection
                    logStmt.setInt(1, bookingId);
                    logStmt.setString(2, approver);
                    logStmt.setString(3, reason);
                    logStmt.executeUpdate();

                    conn.commit();
                    return true;
                } else {
                    conn.rollback();
                    return false;
                }
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private GSProvider mapProviderFromResultSet(ResultSet rs) throws SQLException {
        GSProvider provider = new GSProvider();
        provider.setId(rs.getInt("id"));
        provider.setName(rs.getString("name"));
        provider.setType(ProviderType.valueOf(rs.getString("type")));
        provider.setContactEmail(rs.getString("contact_email"));
        provider.setContactPhone(rs.getString("contact_phone"));
        provider.setApiEndpoint(rs.getString("api_endpoint"));
        provider.setActive(rs.getBoolean("is_active"));
        provider.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        provider.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return provider;
    }

    private GSBooking mapBookingFromResultSet(ResultSet rs) throws SQLException {
        GSBooking booking = new GSBooking();
        booking.setId(rs.getInt("id"));
        booking.setProviderId(rs.getInt("provider_id"));
        booking.setYamcsGsName(rs.getString("yamcs_gs_name"));
        booking.setStartTime(rs.getTimestamp("start_time").toLocalDateTime());
        booking.setEndTime(rs.getTimestamp("end_time").toLocalDateTime());
        booking.setPurpose(rs.getString("purpose"));
        booking.setMissionName(rs.getString("mission_name"));
        booking.setSatelliteName(rs.getString("satellite_name"));
        booking.setRuleType(BookingRuleType.valueOf(rs.getString("rule_type")));
        booking.setFrequencyDays(rs.getObject("frequency_days", Integer.class));
        booking.setStatus(BookingStatus.valueOf(rs.getString("status")));
        booking.setRequestedBy(rs.getString("requested_by"));
        booking.setApprovedBy(rs.getString("approved_by"));
        if (rs.getTimestamp("approved_at") != null) {
            booking.setApprovedAt(rs.getTimestamp("approved_at").toLocalDateTime());
        }
        booking.setRejectionReason(rs.getString("rejection_reason"));
        booking.setNotes(rs.getString("notes"));
        booking.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        booking.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());

        // Additional fields from JOIN
        try {
            booking.setProviderName(rs.getString("provider_name"));
            booking.setProviderType(rs.getString("provider_type"));
        } catch (SQLException e) {
            // These fields might not be present in all queries
        }

        return booking;
    }
}
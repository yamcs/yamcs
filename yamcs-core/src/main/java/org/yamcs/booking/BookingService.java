package org.yamcs.booking;

import org.yamcs.AbstractYamcsService;
import org.yamcs.InitException;
import org.yamcs.YConfiguration;
import org.yamcs.booking.db.BookingDatabase;

/**
 * Ground Station Booking Service for YAMCS
 * Provides database access for managing ground station bookings with approval workflow
 */
public class BookingService extends AbstractYamcsService {

    private BookingDatabase database;

    @Override
    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        super.init(yamcsInstance, serviceName, config);
        log.info("Initializing Ground Station Booking Service for instance: {}", yamcsInstance);

        try {
            // Initialize database connection
            database = new BookingDatabase(config);
            log.info("Ground Station Booking Service initialized successfully");

        } catch (Exception e) {
            log.error("Failed to initialize Booking Service", e);
            throw new InitException("Failed to initialize Booking Service: " + e.getMessage(), e);
        }
    }

    @Override
    protected void doStart() {
        log.info("Starting Ground Station Booking Service");
        // API is automatically registered by HttpServer
        notifyStarted();
        log.info("Ground Station Booking Service started successfully");
    }

    @Override
    protected void doStop() {
        log.info("Stopping Ground Station Booking Service");

        try {
            if (database != null) {
                database.close();
            }
        } catch (Exception e) {
            log.error("Error during service shutdown", e);
        }

        notifyStopped();
        log.info("Ground Station Booking Service stopped");
    }

    public BookingDatabase getDatabase() {
        return database;
    }

    // Static method to get the service instance
    public static BookingService getInstance() {
        return org.yamcs.YamcsServer.getServer().getGlobalService(BookingService.class);
    }
}
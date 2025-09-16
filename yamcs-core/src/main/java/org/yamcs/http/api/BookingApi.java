package org.yamcs.http.api;

import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import org.yamcs.api.Observer;
import org.yamcs.booking.BookingService;
import org.yamcs.booking.db.BookingDatabase;
import org.yamcs.booking.model.*;
import org.yamcs.http.Context;
import org.yamcs.protobuf.*;
import org.yamcs.security.User;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

public class BookingApi extends AbstractBookingApi<Context> {

    @Override
    public void getProviders(Context ctx, Empty request, Observer<GetProvidersResponse> observer) {
        try {
            BookingDatabase database = getBookingDatabase();
            List<GSProvider> providers = database.getAllProviders();

            GetProvidersResponse.Builder responseBuilder = GetProvidersResponse.newBuilder();
            for (GSProvider provider : providers) {
                responseBuilder.addProviders(toProviderInfo(provider));
            }

            observer.complete(responseBuilder.build());
        } catch (Exception e) {
            observer.completeExceptionally(e);
        }
    }

    @Override
    public void getBookings(Context ctx, Empty request, Observer<GetBookingsResponse> observer) {
        try {
            BookingDatabase database = getBookingDatabase();
            List<GSBooking> bookings = database.getAllBookings();

            GetBookingsResponse.Builder responseBuilder = GetBookingsResponse.newBuilder();
            for (GSBooking booking : bookings) {
                responseBuilder.addBookings(toBookingInfo(booking));
            }

            observer.complete(responseBuilder.build());
        } catch (Exception e) {
            observer.completeExceptionally(e);
        }
    }

    @Override
    public void createBooking(Context ctx, CreateBookingRequest request, Observer<BookingInfo> observer) {
        try {
            User user = ctx.user;
            if (user == null) {
                observer.completeExceptionally(new RuntimeException("Authentication required"));
                return;
            }

            BookingDatabase database = getBookingDatabase();

            GSBooking booking = new GSBooking();
            booking.setProviderId(request.getProviderId());
            booking.setYamcsGsName(request.getYamcsGsName());
            booking.setStartTime(toLocalDateTime(request.getStartTime()));
            booking.setEndTime(toLocalDateTime(request.getEndTime()));
            booking.setPurpose(request.getPurpose());

            // Set optional fields only if they exist
            if (request.hasMissionName() && !request.getMissionName().isEmpty()) {
                booking.setMissionName(request.getMissionName());
            }
            if (request.hasSatelliteName() && !request.getSatelliteName().isEmpty()) {
                booking.setSatelliteName(request.getSatelliteName());
            }
            if (request.hasNotes() && !request.getNotes().isEmpty()) {
                booking.setNotes(request.getNotes());
            }

            booking.setRuleType(BookingRuleType.valueOf(request.getRuleType()));
            if (request.hasFrequencyDays()) {
                booking.setFrequencyDays(request.getFrequencyDays());
            }
            booking.setRequestedBy(user.getName());

            GSBooking createdBooking = database.createBooking(booking);
            observer.complete(toBookingInfo(createdBooking));

        } catch (Exception e) {
            observer.completeExceptionally(e);
        }
    }

    @Override
    public void getPendingBookings(Context ctx, Empty request, Observer<GetBookingsResponse> observer) {
        try {
            BookingDatabase database = getBookingDatabase();
            List<GSBooking> bookings = database.getPendingBookings();

            GetBookingsResponse.Builder responseBuilder = GetBookingsResponse.newBuilder();
            for (GSBooking booking : bookings) {
                responseBuilder.addBookings(toBookingInfo(booking));
            }

            observer.complete(responseBuilder.build());
        } catch (Exception e) {
            observer.completeExceptionally(e);
        }
    }

    @Override
    public void approveBooking(Context ctx, ApprovalRequest request, Observer<BookingInfo> observer) {
        try {
            User user = ctx.user;
            if (user == null) {
                observer.completeExceptionally(new RuntimeException("Authentication required"));
                return;
            }

            BookingDatabase database = getBookingDatabase();
            boolean success = database.approveBooking(
                request.getId(),
                user.getName(),
                request.getComments()
            );

            if (success) {
                BookingInfo.Builder builder = BookingInfo.newBuilder()
                    .setId(request.getId())
                    .setStatus("approved");
                observer.complete(builder.build());
            } else {
                observer.completeExceptionally(new RuntimeException("Booking not found or already processed"));
            }

        } catch (Exception e) {
            observer.completeExceptionally(e);
        }
    }

    @Override
    public void rejectBooking(Context ctx, ApprovalRequest request, Observer<BookingInfo> observer) {
        try {
            User user = ctx.user;
            if (user == null) {
                observer.completeExceptionally(new RuntimeException("Authentication required"));
                return;
            }

            if (request.getComments() == null || request.getComments().trim().isEmpty()) {
                observer.completeExceptionally(new RuntimeException("Rejection reason is required"));
                return;
            }

            BookingDatabase database = getBookingDatabase();
            boolean success = database.rejectBooking(
                request.getId(),
                user.getName(),
                request.getComments()
            );

            if (success) {
                BookingInfo.Builder builder = BookingInfo.newBuilder()
                    .setId(request.getId())
                    .setStatus("rejected")
                    .setRejectionReason(request.getComments());
                observer.complete(builder.build());
            } else {
                observer.completeExceptionally(new RuntimeException("Booking not found or already processed"));
            }

        } catch (Exception e) {
            observer.completeExceptionally(e);
        }
    }

    private BookingDatabase getBookingDatabase() throws SQLException {
        BookingService service = BookingService.getInstance();
        if (service == null) {
            throw new RuntimeException("BookingService not available");
        }
        return service.getDatabase();
    }

    private ProviderInfo toProviderInfo(GSProvider provider) {
        ProviderInfo.Builder builder = ProviderInfo.newBuilder()
            .setId(provider.getId())
            .setName(provider.getName())
            .setType(provider.getType().toString())
            .setIsActive(provider.isActive());

        if (provider.getContactEmail() != null) {
            builder.setContactEmail(provider.getContactEmail());
        }
        if (provider.getContactPhone() != null) {
            builder.setContactPhone(provider.getContactPhone());
        }
        if (provider.getApiEndpoint() != null) {
            builder.setApiEndpoint(provider.getApiEndpoint());
        }
        if (provider.getCreatedAt() != null) {
            builder.setCreatedAt(toTimestamp(provider.getCreatedAt()));
        }
        if (provider.getUpdatedAt() != null) {
            builder.setUpdatedAt(toTimestamp(provider.getUpdatedAt()));
        }

        return builder.build();
    }

    private BookingInfo toBookingInfo(GSBooking booking) {
        BookingInfo.Builder builder = BookingInfo.newBuilder()
            .setId(booking.getId())
            .setProviderId(booking.getProviderId())
            .setYamcsGsName(booking.getYamcsGsName())
            .setStartTime(toTimestamp(booking.getStartTime()))
            .setEndTime(toTimestamp(booking.getEndTime()))
            .setPurpose(booking.getPurpose())
            .setRuleType(booking.getRuleType().toString())
            .setStatus(booking.getStatus().toString())
            .setRequestedBy(booking.getRequestedBy())
            .setCreatedAt(toTimestamp(booking.getCreatedAt()))
            .setUpdatedAt(toTimestamp(booking.getUpdatedAt()));

        if (booking.getMissionName() != null) {
            builder.setMissionName(booking.getMissionName());
        }
        if (booking.getSatelliteName() != null) {
            builder.setSatelliteName(booking.getSatelliteName());
        }
        if (booking.getFrequencyDays() != null) {
            builder.setFrequencyDays(booking.getFrequencyDays());
        }
        if (booking.getApprovedBy() != null) {
            builder.setApprovedBy(booking.getApprovedBy());
        }
        if (booking.getApprovedAt() != null) {
            builder.setApprovedAt(toTimestamp(booking.getApprovedAt()));
        }
        if (booking.getRejectionReason() != null) {
            builder.setRejectionReason(booking.getRejectionReason());
        }
        if (booking.getNotes() != null) {
            builder.setNotes(booking.getNotes());
        }
        if (booking.getProviderName() != null) {
            builder.setProviderName(booking.getProviderName());
        }
        if (booking.getProviderType() != null) {
            builder.setProviderType(booking.getProviderType());
        }

        return builder.build();
    }

    private Timestamp toTimestamp(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        Instant instant = dateTime.toInstant(ZoneOffset.UTC);
        return Timestamp.newBuilder()
            .setSeconds(instant.getEpochSecond())
            .setNanos(instant.getNano())
            .build();
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        if (timestamp == null) return null;
        Instant instant = Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
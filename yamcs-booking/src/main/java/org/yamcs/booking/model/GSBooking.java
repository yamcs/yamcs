package org.yamcs.booking.model;

import java.time.LocalDateTime;

/**
 * Ground Station Booking model
 */
public class GSBooking {
    private int id;
    private int providerId;
    private String yamcsGsName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String purpose;
    private String missionName;
    private String satelliteName;
    private BookingRuleType ruleType;
    private Integer frequencyDays;
    private BookingStatus status;
    private String requestedBy;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private String rejectionReason;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Additional fields for display (from JOINs)
    private String providerName;
    private String providerType;

    public GSBooking() {
        this.status = BookingStatus.pending;
        this.ruleType = BookingRuleType.one_time;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getProviderId() { return providerId; }
    public void setProviderId(int providerId) { this.providerId = providerId; }

    public String getYamcsGsName() { return yamcsGsName; }
    public void setYamcsGsName(String yamcsGsName) { this.yamcsGsName = yamcsGsName; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public String getMissionName() { return missionName; }
    public void setMissionName(String missionName) { this.missionName = missionName; }

    public String getSatelliteName() { return satelliteName; }
    public void setSatelliteName(String satelliteName) { this.satelliteName = satelliteName; }

    public BookingRuleType getRuleType() { return ruleType; }
    public void setRuleType(BookingRuleType ruleType) { this.ruleType = ruleType; }

    public Integer getFrequencyDays() { return frequencyDays; }
    public void setFrequencyDays(Integer frequencyDays) { this.frequencyDays = frequencyDays; }

    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }

    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }

    public String getProviderType() { return providerType; }
    public void setProviderType(String providerType) { this.providerType = providerType; }

    public long getDurationMinutes() {
        if (startTime != null && endTime != null) {
            return java.time.Duration.between(startTime, endTime).toMinutes();
        }
        return 0;
    }

    @Override
    public String toString() {
        return "GSBooking{" +
                "id=" + id +
                ", providerId=" + providerId +
                ", yamcsGsName='" + yamcsGsName + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", purpose='" + purpose + '\'' +
                ", status=" + status +
                ", requestedBy='" + requestedBy + '\'' +
                '}';
    }
}
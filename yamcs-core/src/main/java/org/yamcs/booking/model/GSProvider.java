package org.yamcs.booking.model;

import java.time.LocalDateTime;

/**
 * Ground Station Provider model
 */
public class GSProvider {
    private int id;
    private String name;
    private ProviderType type;
    private String contactEmail;
    private String contactPhone;
    private String apiEndpoint;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public GSProvider() {}

    public GSProvider(String name, ProviderType type) {
        this.name = name;
        this.type = type;
        this.active = true;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ProviderType getType() { return type; }
    public void setType(ProviderType type) { this.type = type; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getApiEndpoint() { return apiEndpoint; }
    public void setApiEndpoint(String apiEndpoint) { this.apiEndpoint = apiEndpoint; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "GSProvider{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", type=" + type +
                ", contactEmail='" + contactEmail + '\'' +
                ", active=" + active +
                '}';
    }
}
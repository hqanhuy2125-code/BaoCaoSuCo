package com.example.baocaosuco.data.incident;

/** Candidate CON-01 input: caller must not supply UID, status, timestamps or assignee. */
public final class NewIncidentInput {
    private final String title;
    private final String description;
    private final String location;
    private final String priority;

    public NewIncidentInput(String title, String description, String location, String priority) {
        this.title = required(title, "title");
        this.description = required(description, "description");
        this.location = required(location, "location");
        if (!"URGENT".equals(priority) && !"NORMAL".equals(priority) && !"LOW".equals(priority)) {
            throw new IllegalArgumentException("Unsupported canonical priority");
        }
        this.priority = priority;
    }

    static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing " + field);
        }
        return value.trim();
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getLocation() { return location; }
    public String getPriority() { return priority; }
}

package com.example.baocaosuco.data.incident;

/** Immutable CON-01 v1 input. Caller supplies no UID, role, status or timestamps. */
public final class NewIncidentInput {
    private final String title;
    private final String description;
    private final String location;
    private final String priority;

    public NewIncidentInput(String title, String description, String location, String priority) {
        this.title = boundedRequired(title, "title", 120);
        this.description = boundedRequired(description, "description", 2000);
        this.location = boundedRequired(location, "location", 160);
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

    static String boundedRequired(String value, String field, int maxCharacters) {
        String trimmed = required(value, field);
        if (trimmed.codePointCount(0, trimmed.length()) > maxCharacters) {
            throw new IllegalArgumentException("Too long " + field);
        }
        return trimmed;
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getLocation() { return location; }
    public String getPriority() { return priority; }
}

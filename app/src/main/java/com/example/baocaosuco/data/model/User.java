package com.example.baocaosuco.data.model;

/**
 * POJO dung chung cho toan bo app.
 * Shared model - chinh sua can phe duyet tu truong nhom.
 * Ghi chu: truong createdAt va uid se them lai khi noi Firebase (QD-02).
 */
public class User {

    public static final String ROLE_RESIDENT = "RESIDENT";
    public static final String ROLE_MANAGER  = "MANAGER";

    private String displayName;
    private String email;
    private String role;

    /** Constructor rong bat buoc cho deserialize (Firebase sau nay). */
    public User() {}

    public User(String displayName, String email, String role) {
        this.displayName = displayName;
        this.email       = email;
        this.role        = role;
    }

    public String getDisplayName()         { return displayName; }
    public void   setDisplayName(String v) { displayName = v; }

    public String getEmail()         { return email; }
    public void   setEmail(String v) { email = v; }

    public String getRole()         { return role; }
    public void   setRole(String v) { role = v; }
}
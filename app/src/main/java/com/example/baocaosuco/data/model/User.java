package com.example.baocaosuco.data.model;

import com.google.firebase.Timestamp;

/**
 * POJO dung chung cho toan bo app.
 * Shared model - ho tro ca MockAuth va Firebase Auth + Firestore.
 */
public class User {

    public static final String ROLE_RESIDENT = "RESIDENT";
    public static final String ROLE_MANAGER  = "MANAGER";

    private String uid;
    private String displayName;
    private String email;
    private String role;
    private Timestamp createdAt;

    /** Constructor rong bat buoc cho deserialize tu Firestore. */
    public User() {}

    /** Constructor day du thong tin Firestore. */
    public User(String uid, String displayName, String email, String role, Timestamp createdAt) {
        this.uid         = uid;
        this.displayName = displayName;
        this.email       = email;
        this.role        = role;
        this.createdAt   = createdAt;
    }

    /** Constructor tuong thich cho MockAuth va logic truoc do. */
    public User(String displayName, String email, String role) {
        this.displayName = displayName;
        this.email       = email;
        this.role        = role;
    }

    public String getUid()         { return uid; }
    public void   setUid(String v) { uid = v; }

    public String getDisplayName()         { return displayName; }
    public void   setDisplayName(String v) { displayName = v; }

    public String getEmail()         { return email; }
    public void   setEmail(String v) { email = v; }

    public String getRole()         { return role; }
    public void   setRole(String v) { role = v; }

    public Timestamp getCreatedAt()            { return createdAt; }
    public void      setCreatedAt(Timestamp v) { createdAt = v; }
}
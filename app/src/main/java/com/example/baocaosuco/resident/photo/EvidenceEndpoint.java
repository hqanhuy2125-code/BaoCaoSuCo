package com.example.baocaosuco.resident.photo;

import java.net.URI;
import java.util.regex.Pattern;

/** Build-time configuration. Device inputs and remote responses never choose a host. */
final class EvidenceEndpoint {
    private static final Pattern PROJECT_REF = Pattern.compile("[a-z]{20}");
    private static final Pattern FIREBASE_ID = Pattern.compile("[a-z][a-z0-9-]{4,28}[a-z0-9]");
    private static final String PRODUCTION_REF = "xnmlnqaetcaawvsbmhjy";
    private static final String PRODUCTION_FIREBASE = "baocaosuco-c4ccc";

    final URI gateway;
    final String firebaseProject;

    private EvidenceEndpoint(String ref, String firebaseProject) {
        this.gateway = URI.create("https://" + ref + ".supabase.co/functions/v1/evidence-gateway");
        this.firebaseProject = firebaseProject;
    }

    static EvidenceEndpoint resolve(String environment, String stagingRef, String stagingFirebase, boolean debug) {
        if ("production".equals(environment)) return new EvidenceEndpoint(PRODUCTION_REF, PRODUCTION_FIREBASE);
        if (!"staging".equals(environment) || !debug
                || stagingRef == null || !PROJECT_REF.matcher(stagingRef).matches()
                || stagingFirebase == null || !FIREBASE_ID.matcher(stagingFirebase).matches()
                || PRODUCTION_REF.equals(stagingRef) || PRODUCTION_FIREBASE.equals(stagingFirebase)) return null;
        return new EvidenceEndpoint(stagingRef, stagingFirebase);
    }
}

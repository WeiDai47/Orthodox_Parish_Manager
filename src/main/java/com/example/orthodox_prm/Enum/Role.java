package com.example.orthodox_prm.Enum;

/**
 * Role-based access control roles for the Orthodox Parish Manager.
 *
 * PRIEST: Full access + user management (whitelist emails, assign roles)
 * SECRETARY: Edit access to parishioners, send emails, manage events, export data
 * VIEWER: Read-only access to parishioner list and details
 */
public enum Role {
    PRIEST,     // Full access + user management
    SECRETARY,  // Edit access, no user management
    VIEWER      // Read-only
}

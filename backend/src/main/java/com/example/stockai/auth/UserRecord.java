package com.example.stockai.auth;

import java.time.Instant;

record UserRecord(
    String email,
    String passwordSalt,
    String passwordHash,
    Instant createdAt,
    String authProvider
) {}

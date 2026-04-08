package com.jhun.backend.dto.reservation;

public record ReservationCreateSeedAccountResponse(
        String userId,
        String username,
        String email,
        String loginAccount,
        String password,
        String role) {
}

package com.jhun.backend.dto.reservation;

import java.util.List;

public record ReservationCreateSeedResponse(
        String scenario,
        ReservationCreateSeedAccountResponse userAccount,
        ReservationCreateSeedAccountResponse deviceAdminAccount,
        ReservationCreateSeedAccountResponse systemAdminAccount,
        String categoryId,
        String categoryName,
        List<ReservationCreateSeedDeviceResponse> devices,
        CreateMultiReservationRequest reservationRequest,
        List<BlockingDeviceResponse> blockingDevices,
        String conflictReservationId) {
}

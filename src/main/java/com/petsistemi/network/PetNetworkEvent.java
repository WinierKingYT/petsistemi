package com.petsistemi.network;

import java.util.UUID;

public record PetNetworkEvent(long eventId, String serverId, PetNetworkEventType type,
                              UUID ownerId, UUID petId, String payload, long createdAt) {}

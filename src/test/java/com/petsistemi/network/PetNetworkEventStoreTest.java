package com.petsistemi.network;

import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.persistence.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class PetNetworkEventStoreTest {
    private Connection connection;
    private JdbcPetNetworkEventStore store;
    private ConnectionProvider provider;

    @BeforeEach void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        SchemaMigrator.migrate(connection);
        provider = new ConnectionProvider() {
            @Override public Connection getConnection() { return connection; }
            @Override public void close() {}
        };
        DatabaseThreadGuard.setGuardEnabled(false);
        store = new JdbcPetNetworkEventStore(provider);
    }

    @AfterEach void tearDown() throws Exception {
        DatabaseThreadGuard.setGuardEnabled(true);
        connection.close();
    }

    @Test void cursorPollingIsOrderedAndExclusive() {
        UUID owner = UUID.randomUUID();
        store.publish("a", PetNetworkEventType.PET_CREATED, owner, UUID.randomUUID(), "wolf");
        long first = store.latestId();
        store.publish("b", PetNetworkEventType.SELECTION_CHANGED, owner, UUID.randomUUID(), null);

        var events = store.pollAfter(first, 100);

        assertEquals(1, events.size());
        assertEquals("b", events.get(0).serverId());
        assertTrue(events.get(0).eventId() > first);
    }

    @Test void repositoryDecoratorPublishesMutationAfterSuccessfulWrite() {
        PetRepository delegate = new SqlitePetRepository(provider, Logger.getAnonymousLogger());
        PetRepository repository = new NetworkAwarePetRepository(delegate, store, "server-a");
        PetInstance pet = new PetInstance(UUID.randomUUID(), UUID.randomUUID(), "wolf", null, 1, 0,
                PetAvailabilityState.AVAILABLE, 1, 1);

        repository.insert(pet);

        var events = store.pollAfter(0, 10);
        assertEquals(1, events.size());
        assertEquals(PetNetworkEventType.PET_CREATED, events.get(0).type());
        assertEquals(pet.ownerId(), events.get(0).ownerId());
    }
}

package com.petsistemi.persistence;

import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.network.JdbcPetNetworkEventStore;
import com.petsistemi.network.MysqlNetworkLockManager;
import com.petsistemi.network.NetworkAwarePetRepository;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class MysqlPersistenceIntegrationTest {
    @Test void connectorSchemaCrudAndUpsertWorkAgainstRealMysqlWhenConfigured() throws Exception {
        String url = System.getenv("PETSISTEMI_TEST_MYSQL_URL");
        if (url == null || url.isBlank()) return; // CI supplies it; local unit runs stay self-contained.
        String user = System.getenv().getOrDefault("PETSISTEMI_TEST_MYSQL_USER", "root");
        String password = System.getenv().getOrDefault("PETSISTEMI_TEST_MYSQL_PASSWORD", "root");
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS=0");
                statement.execute("DROP TABLE IF EXISTS player_selected_pets, pet_network_events, pet_pack_installations, pet_audit_log, pets, schema_migrations");
                statement.execute("SET FOREIGN_KEY_CHECKS=1");
            }
            MysqlSchemaMigrator.migrate(connection);
            ConnectionProvider provider = new ConnectionProvider() {
                @Override public Connection getConnection() { return connection; }
                @Override public void close() {}
            };
            DatabaseThreadGuard.setGuardEnabled(false);
            try {
                PetRepository pets = new MysqlPetRepository(provider, Logger.getAnonymousLogger());
                PetSelectionRepository selections = new MysqlPetSelectionRepository(provider, Logger.getAnonymousLogger());
                UUID owner = UUID.randomUUID();
                PetInstance pet = new PetInstance(UUID.randomUUID(), owner, "wolf", "Bobi", 3, 20,
                        PetAvailabilityState.AVAILABLE, 1, 1);
                pets.insert(pet);
                selections.select(owner, pet.petId());
                assertEquals(pet.petId(), selections.findByOwner(owner).orElseThrow().petId());
                assertEquals("wolf", pets.findById(pet.petId()).orElseThrow().definitionId());
                pets.restoreActivePet(owner, pet.petId(), null);
                assertEquals(pet.petId(), pets.findActiveByOwner(owner).orElseThrow().petId());
                JdbcPetNetworkEventStore events = new JdbcPetNetworkEventStore(provider);
                PetRepository networkPets = new NetworkAwarePetRepository(pets, events, "ci-server",
                        new MysqlNetworkLockManager(provider));
                networkPets.update(pet.withDefinitionId("wolf"));
                assertEquals("ci-server", events.pollAfter(0, 10).get(0).serverId());
            } finally {
                DatabaseThreadGuard.setGuardEnabled(true);
            }
        }
    }
}

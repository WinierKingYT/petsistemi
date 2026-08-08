package com.petsistemi.definition.editor;

import be.seeseemelk.mockbukkit.MockBukkit;
import com.petsistemi.definition.AtomicPetDefinitionRegistry;
import com.petsistemi.domain.PetDefinition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

class PetDefinitionEditorServiceTest {

    private static final String VALID = """
            schema-version: 1
            display-name: Test Pet
            gui-material: BONE
            entity-type: WOLF
            custom-extension:
              preserved: true
            """;

    @TempDir
    Path folder;

    @BeforeAll
    static void startBukkit() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopBukkit() {
        MockBukkit.unmock();
    }

    @Test
    void validDraftPublishesAndPreservesUnknownSections() throws Exception {
        Path file = definitionFile();
        AtomicPetDefinitionRegistry registry = mock(AtomicPetDefinitionRegistry.class);
        PetDefinitionEditorService service = new PetDefinitionEditorService(folder, registry);
        PetDefinitionEditorService.Draft draft = service.open("test_pet");
        draft.set(PetEditorField.DISPLAY_NAME, "Edited Pet");
        PetDefinition parsed = service.validate(draft).definition();
        when(registry.loadCandidateSnapshot()).thenReturn(Map.of("test_pet", parsed));

        PetDefinitionEditorService.SaveResult result = service.save(draft);

        assertTrue(result.success());
        String saved = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(saved.contains("display-name: Edited Pet"));
        assertTrue(saved.contains("custom-extension:"));
        assertTrue(saved.contains("preserved: true"));
        verify(registry).publishSnapshot(anyMap());
    }

    @Test
    void invalidDraftNeverTouchesFile() throws Exception {
        Path file = definitionFile();
        AtomicPetDefinitionRegistry registry = mock(AtomicPetDefinitionRegistry.class);
        PetDefinitionEditorService service = new PetDefinitionEditorService(folder, registry);
        PetDefinitionEditorService.Draft draft = service.open("test_pet");
        draft.set(PetEditorField.GUI_MATERIAL, "NOT_A_MATERIAL");

        PetDefinitionEditorService.SaveResult result = service.save(draft);

        assertFalse(result.success());
        assertEquals(VALID, Files.readString(file, StandardCharsets.UTF_8));
        verifyNoInteractions(registry);
    }

    @Test
    void concurrentExternalChangeRejectsSave() throws Exception {
        Path file = definitionFile();
        AtomicPetDefinitionRegistry registry = mock(AtomicPetDefinitionRegistry.class);
        PetDefinitionEditorService service = new PetDefinitionEditorService(folder, registry);
        PetDefinitionEditorService.Draft draft = service.open("test_pet");
        draft.set(PetEditorField.DISPLAY_NAME, "Editor Value");
        Files.writeString(file, VALID.replace("Test Pet", "External Value"), StandardCharsets.UTF_8);

        PetDefinitionEditorService.SaveResult result = service.save(draft);

        assertFalse(result.success());
        assertTrue(result.message().contains("çakışmayı"));
        assertTrue(Files.readString(file).contains("External Value"));
        verifyNoInteractions(registry);
    }

    @Test
    void failedSnapshotPublicationRollsFileBack() throws Exception {
        Path file = definitionFile();
        AtomicPetDefinitionRegistry registry = mock(AtomicPetDefinitionRegistry.class);
        when(registry.loadCandidateSnapshot()).thenThrow(new IllegalStateException("another.yml invalid"));
        PetDefinitionEditorService service = new PetDefinitionEditorService(folder, registry);
        PetDefinitionEditorService.Draft draft = service.open("test_pet");
        draft.set(PetEditorField.DISPLAY_NAME, "Should Roll Back");

        PetDefinitionEditorService.SaveResult result = service.save(draft);

        assertFalse(result.success());
        assertEquals(VALID, Files.readString(file, StandardCharsets.UTF_8));
        verify(registry, never()).publishSnapshot(anyMap());
    }

    private Path definitionFile() throws Exception {
        Files.createDirectories(folder);
        Path file = folder.resolve("test_pet.yml");
        Files.writeString(file, VALID, StandardCharsets.UTF_8);
        return file;
    }
}

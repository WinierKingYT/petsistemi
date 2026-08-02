package com.petsistemi.definition;

import com.petsistemi.domain.PetDefinition;

import java.util.List;
import java.util.Map;

public record DefinitionLoadReport(
        int totalFiles,
        List<PetDefinition> validDefinitions,
        Map<String, List<String>> errorsPerFile,
        boolean success
) {}

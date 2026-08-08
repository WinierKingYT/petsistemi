package com.petsistemi.definition.editor;

public enum PetEditorField {
    DISPLAY_NAME("Görünen ad", "display-name", false),
    GUI_MATERIAL("GUI materyali", "gui-material", false),
    REPRESENTATION_TYPE("Görünüm türü", "representation.type", true),
    MODEL_ID("Model kimliği", "representation.model-id", true),
    ENTITY_TYPE("Entity türü", "entity-type", true),
    MOVEMENT_TYPE("Hareket türü", "movement.type", true);

    private final String label;
    private final String path;
    private final boolean removable;

    PetEditorField(String label, String path, boolean removable) {
        this.label = label;
        this.path = path;
        this.removable = removable;
    }

    public String label() { return label; }
    public String path() { return path; }
    public boolean removable() { return removable; }
}

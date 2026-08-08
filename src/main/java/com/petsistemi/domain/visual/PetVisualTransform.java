package com.petsistemi.domain.visual;

import com.petsistemi.domain.PetVector3;

/** Local transform relative to a visual node's parent. Rotation is expressed in degrees. */
public record PetVisualTransform(PetVector3 translation, PetVector3 rotation, PetVector3 scale) {
    public static final PetVisualTransform IDENTITY =
            new PetVisualTransform(PetVector3.ZERO, PetVector3.ZERO, PetVector3.ONE);

    public PetVisualTransform {
        translation = translation != null ? translation : PetVector3.ZERO;
        rotation = rotation != null ? rotation : PetVector3.ZERO;
        scale = scale != null ? scale : PetVector3.ONE;
        if (!scale.isValidScale()) throw new IllegalArgumentException("Visual node scale değerleri 0'dan büyük olmalıdır.");
    }
}

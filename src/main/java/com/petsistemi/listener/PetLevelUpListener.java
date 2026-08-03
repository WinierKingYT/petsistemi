package com.petsistemi.listener;

import com.petsistemi.api.PetSnapshot;
import com.petsistemi.api.event.PetLevelUpEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.time.Duration;

public class PetLevelUpListener implements Listener {

    @EventHandler
    public void onPetLevelUp(PetLevelUpEvent event) {
        PetSnapshot pet = event.getPetSnapshot();
        Player owner = Bukkit.getPlayer(pet.ownerId());
        if (owner == null || !owner.isOnline()) {
            return;
        }

        String petName = pet.customName() != null ? pet.customName() : pet.definitionId();

        // 1. Play Sound
        owner.playSound(owner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

        // 2. Display Title & Subtitle
        Component titleText = Component.text("★ SEVİYE ATLADI ★", NamedTextColor.GOLD, TextDecoration.BOLD);
        Component subtitleText = Component.text(petName + " Seviye ", NamedTextColor.YELLOW)
                .append(Component.text(event.getNewLevel(), NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text(" Oldu!", NamedTextColor.YELLOW));

        Title title = Title.title(
                titleText,
                subtitleText,
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2500), Duration.ofMillis(500))
        );
        owner.showTitle(title);

        // 3. Send Chat Message
        owner.sendMessage(Component.text("-----------------------------------------", NamedTextColor.DARK_GRAY));
        owner.sendMessage(Component.text(" TEBRİKLER! ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text("Peti '", NamedTextColor.YELLOW))
                .append(Component.text(petName, NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text("' Seviye ", NamedTextColor.YELLOW))
                .append(Component.text(event.getNewLevel(), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" seviyesine ulaştı!", NamedTextColor.YELLOW)));
        owner.sendMessage(Component.text("-----------------------------------------", NamedTextColor.DARK_GRAY));

        // 4. Particle Effect at player location
        World world = owner.getWorld();
        try {
            Particle particle = Particle.valueOf("TOTEM_OF_UNDYING");
            world.spawnParticle(particle, owner.getLocation().add(0, 1, 0), 45, 0.5, 0.8, 0.5, 0.15);
        } catch (Throwable ignored) {
            try {
                Particle particle = Particle.valueOf("TOTEM");
                world.spawnParticle(particle, owner.getLocation().add(0, 1, 0), 45, 0.5, 0.8, 0.5, 0.15);
            } catch (Throwable ignored2) {}
        }
    }
}

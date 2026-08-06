package com.petsistemi.listener;

import com.petsistemi.api.PetSnapshot;
import com.petsistemi.api.event.PetLevelUpEvent;
import com.petsistemi.message.MessageService;
import com.petsistemi.message.PlaceholderMap;
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

    private final MessageService messageService;

    public PetLevelUpListener() {
        this(null);
    }

    public PetLevelUpListener(MessageService messageService) {
        this.messageService = messageService;
    }

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
        Component titleText;
        Component subtitleText;
        if (messageService != null) {
            titleText = messageService.getComponent("levelup.title", "<gold><b>★ SEVİYE ATLADI ★</b></gold>", null);
            subtitleText = messageService.getComponent("levelup.subtitle",
                    "<yellow><name> Seviye </yellow><green><b><level></b></green><yellow> Oldu!</yellow>",
                    PlaceholderMap.of("name", petName).add("level", String.valueOf(event.getNewLevel())));
        } else {
            titleText = Component.text("★ SEVİYE ATLADI ★", NamedTextColor.GOLD, TextDecoration.BOLD);
            subtitleText = Component.text(petName + " Seviye ", NamedTextColor.YELLOW)
                    .append(Component.text(event.getNewLevel(), NamedTextColor.GREEN, TextDecoration.BOLD))
                    .append(Component.text(" Oldu!", NamedTextColor.YELLOW));
        }

        Title title = Title.title(
                titleText,
                subtitleText,
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2500), Duration.ofMillis(500))
        );
        owner.showTitle(title);

        // 3. Send Chat Message (localized)
        if (messageService != null) {
            messageService.send(owner, "command.level-up",
                    "<dark_gray>-----------------------------------------</dark_gray>" +
                    "<newline><gold><b> TEBRİKLER! </b></gold><yellow>Peti '</yellow><green><b>" + petName + "</b></green>" +
                    "<yellow>' Seviye </yellow><gold><b>" + event.getNewLevel() + "</b></gold><yellow> seviyesine ulaştı!</yellow>" +
                    "<newline><dark_gray>-----------------------------------------</dark_gray>",
                    PlaceholderMap.of("name", petName).add("level", String.valueOf(event.getNewLevel())));
        } else {
            owner.sendMessage(Component.text("-----------------------------------------", NamedTextColor.DARK_GRAY));
            owner.sendMessage(Component.text(" TEBRİKLER! ", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .append(Component.text("Peti '", NamedTextColor.YELLOW))
                    .append(Component.text(petName, NamedTextColor.GREEN, TextDecoration.BOLD))
                    .append(Component.text("' Seviye ", NamedTextColor.YELLOW))
                    .append(Component.text(event.getNewLevel(), NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(" seviyesine ulaştı!", NamedTextColor.YELLOW)));
            owner.sendMessage(Component.text("-----------------------------------------", NamedTextColor.DARK_GRAY));
        }

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

package com.petsistemi.command;

import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PetAdminCommandPermissionTest {

    private TestSender noPermSender;
    private TestSender listOnlySender;
    private TestSender addxpOnlySender;
    private TestSender adminSender;
    private PetAdminCommand adminCommand;

    @BeforeEach
    void setUp() {
        noPermSender = new TestSender();
        
        listOnlySender = new TestSender();
        listOnlySender.permissions.put("companionpets.admin.list", true);

        addxpOnlySender = new TestSender();
        addxpOnlySender.permissions.put("companionpets.admin.addxp", true);

        adminSender = new TestSender();
        adminSender.permissions.put("companionpets.admin", true);

        adminCommand = new PetAdminCommand(null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void testTabCompletionWithoutPermissionReturnsEmptyList() {
        List<String> results = adminCommand.onTabComplete(noPermSender, null, "petadmin", new String[]{""});
        assertTrue(results.isEmpty(), "Sender without permissions should receive empty tab completion list.");
    }

    @Test
    void testTabCompletionWithSubPermissionReturnsOnlyPermittedSubcommands() {
        List<String> results = adminCommand.onTabComplete(listOnlySender, null, "petadmin", new String[]{""});
        assertEquals(1, results.size());
        assertEquals("list", results.get(0));
    }

    @Test
    void testTabCompletionWithFullAdminReturnsAllSubcommands() {
        List<String> results = adminCommand.onTabComplete(adminSender, null, "petadmin", new String[]{""});
        assertTrue(results.contains("give"));
        assertTrue(results.contains("list"));
        assertTrue(results.contains("addxp"));
        assertTrue(results.contains("summon"));
    }

    private static class TestSender implements CommandSender {
        final Map<String, Boolean> permissions = new HashMap<>();
        String lastMessage;

        @Override
        public boolean hasPermission(String name) {
            return permissions.getOrDefault(name, false);
        }

        @Override
        public boolean hasPermission(Permission perm) {
            return hasPermission(perm.getName());
        }

        @Override
        public boolean isPermissionSet(String name) {
            return permissions.containsKey(name);
        }

        @Override
        public boolean isPermissionSet(Permission perm) {
            return isPermissionSet(perm.getName());
        }

        @Override public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) { return null; }
        @Override public PermissionAttachment addAttachment(Plugin plugin) { return null; }
        @Override public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) { return null; }
        @Override public PermissionAttachment addAttachment(Plugin plugin, int ticks) { return null; }
        @Override public void removeAttachment(PermissionAttachment attachment) {}
        @Override public void recalculatePermissions() {}
        @Override public Set<PermissionAttachmentInfo> getEffectivePermissions() { return Collections.emptySet(); }
        @Override public boolean isOp() { return false; }
        @Override public void setOp(boolean value) {}

        @Override
        public void sendMessage(String message) {
            this.lastMessage = message;
        }

        @Override public void sendMessage(String[] messages) {}
        @Override public void sendMessage(UUID sender, String message) {}
        @Override public void sendMessage(UUID sender, String[] messages) {}
        @Override public Server getServer() { return null; }
        @Override public String getName() { return "TestSender"; }
        @Override public Spigot spigot() { return null; }
        @Override public net.kyori.adventure.text.Component name() { return net.kyori.adventure.text.Component.text("TestSender"); }
    }
}

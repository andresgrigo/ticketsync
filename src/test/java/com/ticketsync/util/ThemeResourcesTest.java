package com.ticketsync.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeResourcesTest {

    private static final Path RESOURCES_ROOT = Path.of("src", "main", "resources", "com", "ticketsync");

    @Test
    void themedViews_doNotRetainInlineStyleAttributes() throws IOException {
        List<String> themedViews = List.of(
                "LoginView.fxml",
                "AdminDashboardView.fxml",
                "PosView.fxml",
                "SelectionPanelView.fxml",
                "LayoutViewTab.fxml",
                "EventFormView.fxml",
                "UserFormView.fxml",
                "ZoneFormView.fxml",
                "EventsTab.fxml",
                "UsersTab.fxml",
                "SeatingTab.fxml",
                "AuditLogTab.fxml"
        );

        for (String themedView : themedViews) {
            String content = Files.readString(RESOURCES_ROOT.resolve(themedView));
            assertFalse(content.contains("style=\""), themedView + " should use shared CSS classes instead of inline styles");
        }
    }

    @Test
    void applicationStylesheet_definesSharedThemeHooks() throws IOException {
        String stylesheet = Files.readString(RESOURCES_ROOT.resolve("application.css"));

        assertTrue(stylesheet.contains(".app-login-title"));
        assertTrue(stylesheet.contains(".admin-header-bar"));
        assertTrue(stylesheet.contains(".selection-panel"));
        assertTrue(stylesheet.contains(".system-health-badge"));
        assertTrue(stylesheet.contains(".status-table-cell"));
        assertTrue(stylesheet.contains(".canvas-hover-label"));
    }
}

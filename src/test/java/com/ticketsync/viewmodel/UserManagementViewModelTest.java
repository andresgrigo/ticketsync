package com.ticketsync.viewmodel;

import com.ticketsync.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link UserManagementViewModel}.
 *
 * <p>{@code FXCollections.observableArrayList()} and the Simple*Property
 * classes work without initialising the JavaFX toolkit, so no
 * {@code Platform.startup()} guard is needed here.
 */
class UserManagementViewModelTest {

    private UserManagementViewModel viewModel;

    @BeforeEach
    void setUp() {
        viewModel = new UserManagementViewModel();
    }

    @Test
    void initialState_usersListIsEmpty() {
        assertTrue(viewModel.usersProperty().isEmpty(), "users list should start empty");
    }

    @Test
    void initialState_loadingIsFalse() {
        assertFalse(viewModel.loadingProperty().get(), "loading should start false");
    }

    @Test
    void initialState_statusMessageIsEmpty() {
        assertEquals("", viewModel.statusMessageProperty().get(), "statusMessage should start empty");
    }

    @Test
    void initialState_selectedUserIsNull() {
        assertNull(viewModel.selectedUserProperty().get(), "selectedUser should start null");
    }

    @Test
    void setUsers_populatesList() {
        User u1 = user(1, "alice", "ADMIN");
        User u2 = user(2, "bob", "VENDOR");

        viewModel.setUsers(List.of(u1, u2));

        assertEquals(2, viewModel.usersProperty().size());
    }

    @Test
    void setUsers_replacesExistingList() {
        viewModel.setUsers(List.of(user(1, "alice", "ADMIN")));
        viewModel.setUsers(List.of(user(2, "bob", "VENDOR"), user(3, "carol", "VENDOR")));

        assertEquals(2, viewModel.usersProperty().size());
        assertEquals("bob", viewModel.usersProperty().get(0).getUsername());
    }

    @Test
    void addUser_appendsToList() {
        viewModel.setUsers(List.of(user(1, "alice", "ADMIN")));
        viewModel.addUser(user(2, "bob", "VENDOR"));

        assertEquals(2, viewModel.usersProperty().size());
        assertEquals("bob", viewModel.usersProperty().get(1).getUsername());
    }

    @Test
    void removeUser_removesMatchingUserId() {
        User alice = user(1, "alice", "ADMIN");
        User bob = user(2, "bob", "VENDOR");
        viewModel.setUsers(List.of(alice, bob));

        viewModel.removeUser(alice);

        assertEquals(1, viewModel.usersProperty().size());
        assertEquals("bob", viewModel.usersProperty().get(0).getUsername());
    }

    @Test
    void updateUser_replacesMatchingEntry() {
        User alice = user(1, "alice", "VENDOR");
        viewModel.setUsers(List.of(alice));

        User alicePromoted = user(1, "alice", "ADMIN");
        viewModel.updateUser(alicePromoted);

        assertEquals("ADMIN", viewModel.usersProperty().get(0).getRole());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static User user(int id, String username, String role) {
        return new User(id, username, "hash", role, LocalDateTime.of(2026, 1, 1, 0, 0));
    }
}

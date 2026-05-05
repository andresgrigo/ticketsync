package com.ticketsync.viewmodel;

import com.ticketsync.model.User;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;

/**
 * Presentation-layer state for the user management table within the Admin Dashboard.
 *
 * <p>Holds an {@link ObservableList} of {@link User} objects that the
 * {@code AdminDashboardController} binds to its {@code TableView}. Changes
 * to the list are automatically reflected in the UI through JavaFX property
 * bindings.
 *
 * <p>This class has no reference to JavaFX UI controls and can therefore be
 * tested without initialising the JavaFX toolkit.
 */
public class UserManagementViewModel {

    /** Creates a new UserManagementViewModel with an empty user list. */
    public UserManagementViewModel() { }

    private final ObservableList<User> users = FXCollections.observableArrayList();
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final StringProperty statusMessage = new SimpleStringProperty("");
    private final ObjectProperty<User> selectedUser = new SimpleObjectProperty<>(null);

    /**
     * Returns the observable list of users displayed in the table.
     *
     * @return the observable users list; never {@code null}
     */
    public ObservableList<User> usersProperty() {
        return users;
    }

    /**
     * Returns the loading flag property.
     *
     * <p>When {@code true}, a background data-loading operation is in progress.
     *
     * @return the loading property
     */
    public BooleanProperty loadingProperty() {
        return loading;
    }

    /**
     * Returns the status message property used to display loading or error text.
     *
     * @return the statusMessage property
     */
    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    /**
     * Returns the currently selected user property.
     *
     * <p>Bound to the {@code TableView} selection model so that controller
     * action handlers can retrieve the selected row without querying the
     * table directly.
     *
     * @return the selectedUser property
     */
    public ObjectProperty<User> selectedUserProperty() {
        return selectedUser;
    }

    /**
     * Replaces the contents of the users list with the supplied list.
     *
     * <p>The existing list is cleared first so that any previously loaded
     * data is discarded before repopulating.
     *
     * @param list the new list of users to display; must not be {@code null}
     */
    public void setUsers(List<User> list) {
        users.clear();
        users.addAll(list);
    }

    /**
     * Appends a single user to the end of the observable list.
     *
     * @param user the user to add; must not be {@code null}
     */
    public void addUser(User user) {
        users.add(user);
    }

    /**
     * Removes the user whose {@code userId} matches the supplied user.
     *
     * @param user the user to remove; matched by {@code userId}
     */
    public void removeUser(User user) {
        users.removeIf(u -> u.getUserId() == user.getUserId());
    }

    /**
     * Replaces the list entry whose {@code userId} matches that of the
     * supplied user with the new user object.
     *
     * @param updated the updated user; matched and replaced by {@code userId}
     */
    public void updateUser(User updated) {
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getUserId() == updated.getUserId()) {
                users.set(i, updated);
                return;
            }
        }
    }
}

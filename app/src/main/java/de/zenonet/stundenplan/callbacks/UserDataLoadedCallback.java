package de.zenonet.stundenplan.callbacks;

import de.zenonet.stundenplan.models.User;

public interface UserDataLoadedCallback {
    void userDataFetched(User user);
}

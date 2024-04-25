package de.zenonet.stundenplan.common.callbacks;

import de.zenonet.stundenplan.common.models.User;

public interface UserDataLoadedCallback {
    void userDataFetched(User user);
}

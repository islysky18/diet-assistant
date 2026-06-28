package com.chaoting.dietassistant.profile;

import java.util.Optional;

public interface CurrentProfileProvider {

    Optional<ProfileResponse> getProfile();
}

package com.chaoting.dietassistant.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface ProfileRepository extends JpaRepository<Profile, Long> {

    Optional<Profile> findFirstByOrderByIdAsc();
}

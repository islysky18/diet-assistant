CREATE TABLE profiles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    singleton_key INT NOT NULL DEFAULT 1,
    birth_year INT NULL,
    height_cm DECIMAL(5, 2) NULL,
    weight_kg DECIMAL(5, 2) NULL,
    primary_goal VARCHAR(50) NULL,
    meals_per_day INT NULL,
    weekday_dinner_time TIME NULL,
    notes VARCHAR(2000) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_profiles_singleton_key UNIQUE (singleton_key),
    CONSTRAINT ck_profiles_singleton_key CHECK (singleton_key = 1)
);

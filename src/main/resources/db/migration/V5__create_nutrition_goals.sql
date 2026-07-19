CREATE TABLE nutrition_goals (
    id BIGINT NOT NULL AUTO_INCREMENT,
    profile_id BIGINT NOT NULL,
    daily_calories DECIMAL(10, 2) NOT NULL,
    daily_protein_grams DECIMAL(10, 2) NOT NULL,
    daily_carbohydrate_grams DECIMAL(10, 2) NOT NULL,
    daily_fat_grams DECIMAL(10, 2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_nutrition_goals_profile UNIQUE (profile_id),
    CONSTRAINT fk_nutrition_goals_profile FOREIGN KEY (profile_id) REFERENCES profiles (id) ON DELETE CASCADE,
    CONSTRAINT ck_nutrition_goals_calories_non_negative CHECK (daily_calories >= 0),
    CONSTRAINT ck_nutrition_goals_protein_non_negative CHECK (daily_protein_grams >= 0),
    CONSTRAINT ck_nutrition_goals_carbohydrate_non_negative CHECK (daily_carbohydrate_grams >= 0),
    CONSTRAINT ck_nutrition_goals_fat_non_negative CHECK (daily_fat_grams >= 0)
);

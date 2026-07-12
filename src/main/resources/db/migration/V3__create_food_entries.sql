CREATE TABLE food_entries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    profile_id BIGINT NOT NULL,
    food_name VARCHAR(255) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    unit VARCHAR(50) NOT NULL,
    calories DECIMAL(10, 2) NOT NULL,
    protein_grams DECIMAL(10, 2) NOT NULL,
    carbohydrate_grams DECIMAL(10, 2) NOT NULL,
    fat_grams DECIMAL(10, 2) NOT NULL,
    fiber_grams DECIMAL(10, 2) NOT NULL,
    meal_type VARCHAR(50) NOT NULL,
    eaten_at DATETIME(6) NOT NULL,
    notes VARCHAR(2000) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_food_entries_profile FOREIGN KEY (profile_id) REFERENCES profiles (id) ON DELETE CASCADE,
    CONSTRAINT ck_food_entries_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_food_entries_calories_non_negative CHECK (calories >= 0),
    CONSTRAINT ck_food_entries_protein_non_negative CHECK (protein_grams >= 0),
    CONSTRAINT ck_food_entries_carbohydrate_non_negative CHECK (carbohydrate_grams >= 0),
    CONSTRAINT ck_food_entries_fat_non_negative CHECK (fat_grams >= 0),
    CONSTRAINT ck_food_entries_fiber_non_negative CHECK (fiber_grams >= 0)
);

CREATE INDEX ix_food_entries_profile_eaten_at ON food_entries (profile_id, eaten_at DESC, id DESC);

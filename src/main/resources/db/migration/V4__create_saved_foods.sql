CREATE TABLE saved_foods (
    id BIGINT NOT NULL AUTO_INCREMENT,
    profile_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    brand VARCHAR(255) NULL,
    reference_amount DECIMAL(10, 2) NOT NULL,
    reference_unit VARCHAR(50) NOT NULL,
    reference_weight_grams DECIMAL(10, 2) NULL,
    calories DECIMAL(10, 2) NOT NULL,
    protein_grams DECIMAL(10, 2) NOT NULL,
    carbohydrate_grams DECIMAL(10, 2) NOT NULL,
    fat_grams DECIMAL(10, 2) NOT NULL,
    fiber_grams DECIMAL(10, 2) NOT NULL,
    notes VARCHAR(2000) NULL,
    active BIT(1) NOT NULL DEFAULT b'1',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_saved_foods_profile FOREIGN KEY (profile_id) REFERENCES profiles (id) ON DELETE CASCADE,
    CONSTRAINT ck_saved_foods_reference_amount_positive CHECK (reference_amount > 0),
    CONSTRAINT ck_saved_foods_reference_weight_positive CHECK (reference_weight_grams IS NULL OR reference_weight_grams > 0),
    CONSTRAINT ck_saved_foods_calories_non_negative CHECK (calories >= 0),
    CONSTRAINT ck_saved_foods_protein_non_negative CHECK (protein_grams >= 0),
    CONSTRAINT ck_saved_foods_carbohydrate_non_negative CHECK (carbohydrate_grams >= 0),
    CONSTRAINT ck_saved_foods_fat_non_negative CHECK (fat_grams >= 0),
    CONSTRAINT ck_saved_foods_fiber_non_negative CHECK (fiber_grams >= 0)
);

CREATE INDEX ix_saved_foods_profile_active_name ON saved_foods (profile_id, active, name);

ALTER TABLE food_entries
    ADD COLUMN saved_food_id BIGINT NULL,
    ADD COLUMN saved_food_name VARCHAR(255) NULL,
    ADD COLUMN saved_food_brand VARCHAR(255) NULL,
    ADD COLUMN saved_food_reference_amount DECIMAL(10, 2) NULL,
    ADD COLUMN saved_food_reference_unit VARCHAR(50) NULL,
    ADD COLUMN saved_food_reference_weight_grams DECIMAL(10, 2) NULL,
    ADD COLUMN calculation_multiplier DECIMAL(18, 8) NULL,
    ADD CONSTRAINT fk_food_entries_saved_food FOREIGN KEY (saved_food_id) REFERENCES saved_foods (id) ON DELETE SET NULL,
    ADD CONSTRAINT ck_food_entries_calculation_multiplier_non_negative CHECK (calculation_multiplier IS NULL OR calculation_multiplier >= 0);

CREATE INDEX ix_food_entries_saved_food ON food_entries (saved_food_id);

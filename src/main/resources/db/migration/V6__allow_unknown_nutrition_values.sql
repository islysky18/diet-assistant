ALTER TABLE saved_foods
    MODIFY COLUMN calories DECIMAL(10, 2) NULL,
    MODIFY COLUMN protein_grams DECIMAL(10, 2) NULL,
    MODIFY COLUMN carbohydrate_grams DECIMAL(10, 2) NULL,
    MODIFY COLUMN fat_grams DECIMAL(10, 2) NULL,
    MODIFY COLUMN fiber_grams DECIMAL(10, 2) NULL;

ALTER TABLE food_entries
    MODIFY COLUMN calories DECIMAL(10, 2) NULL,
    MODIFY COLUMN protein_grams DECIMAL(10, 2) NULL,
    MODIFY COLUMN carbohydrate_grams DECIMAL(10, 2) NULL,
    MODIFY COLUMN fat_grams DECIMAL(10, 2) NULL,
    MODIFY COLUMN fiber_grams DECIMAL(10, 2) NULL;

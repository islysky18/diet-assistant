CREATE TABLE health_metrics (
    id BIGINT NOT NULL AUTO_INCREMENT,
    profile_id BIGINT NOT NULL,
    metric_type VARCHAR(50) NOT NULL,
    value DECIMAL(10, 2) NOT NULL,
    unit VARCHAR(50) NOT NULL,
    measured_date DATE NOT NULL,
    notes VARCHAR(2000) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_health_metrics_profile FOREIGN KEY (profile_id) REFERENCES profiles (id) ON DELETE CASCADE,
    CONSTRAINT ck_health_metrics_value_positive CHECK (value > 0)
);

CREATE INDEX ix_health_metrics_profile_measured_date ON health_metrics (profile_id, measured_date DESC, id DESC);

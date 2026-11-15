-- =====================================================================
-- V3: seed admin account (demo / development)
--   email:    admin@trading.local
--   password: Admin12345
-- Hash generated once with Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().
-- Change the password (or remove this user) in any non-local environment.
-- =====================================================================

INSERT INTO users (email, password_hash, role)
VALUES ('admin@trading.local',
        '$argon2id$v=19$m=16384,t=2,p=1$+QNfyLDCvaE/KoOBCkHC3g$jFD+YlLL5WwVdAuce7IcFg83+5Qb1xyMD8ffAVG34so',
        'ADMIN');

-- every user has exactly one cash account (BR09)
INSERT INTO accounts (user_id)
SELECT id FROM users WHERE email = 'admin@trading.local';

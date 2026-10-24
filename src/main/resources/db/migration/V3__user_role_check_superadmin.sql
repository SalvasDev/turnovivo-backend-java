-- Normaliza la restricción CHECK del rol de usuario para incluir SUPER_ADMIN.
--
-- Contexto: en bases creadas originalmente por Hibernate (ddl-auto) antes de
-- introducir el rol SUPER_ADMIN, quedó una restricción "users_role_check" que
-- solo admitía ADMIN/STAFF/CUSTOMER. Esto rompía la siembra del SUPER_ADMIN.
--
-- Esta migración es idempotente y segura tanto en bases existentes como nuevas:
-- elimina la restricción previa (si existe) y crea una que contempla los 4 roles.

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;

ALTER TABLE users
    ADD CONSTRAINT users_role_check
    CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'STAFF', 'CUSTOMER'));

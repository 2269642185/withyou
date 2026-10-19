BEGIN;
CREATE TABLE IF NOT EXISTS wy_private.registration_slots (
 code_hash text PRIMARY KEY CHECK(length(code_hash)=64),
 username text UNIQUE, state text NOT NULL DEFAULT 'available' CHECK(state IN('available','reserved','created')),
 expires_at timestamptz NOT NULL DEFAULT now()+interval '7 days', created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS wy_private.registration_limits (
 key text PRIMARY KEY, window_start timestamptz NOT NULL, attempts integer NOT NULL
);
REVOKE ALL ON wy_private.registration_slots,wy_private.registration_limits FROM PUBLIC,anon,authenticated;
CREATE OR REPLACE FUNCTION wy_private.reserve_registration(hash text, requested_name text, client_hash text) RETURNS boolean LANGUAGE plpgsql SET search_path=pg_catalog,wy_private AS $$
DECLARE slot wy_private.registration_slots; hits integer;
BEGIN
 INSERT INTO wy_private.registration_limits(key,window_start,attempts) VALUES(client_hash,date_trunc('minute',now()),1)
 ON CONFLICT(key) DO UPDATE SET window_start=date_trunc('minute',now()),attempts=CASE WHEN registration_limits.window_start=date_trunc('minute',now()) THEN registration_limits.attempts+1 ELSE 1 END RETURNING attempts INTO hits;
 IF hits>8 THEN RETURN false; END IF;
 SELECT * INTO slot FROM wy_private.registration_slots WHERE code_hash=hash FOR UPDATE;
 IF NOT FOUND OR slot.expires_at<now() OR slot.state='created' OR (slot.username IS NOT NULL AND slot.username<>requested_name) THEN RETURN false; END IF;
 UPDATE wy_private.registration_slots SET username=requested_name,state='reserved' WHERE code_hash=hash;
 RETURN true;
END $$;
REVOKE ALL ON FUNCTION wy_private.reserve_registration(text,text,text) FROM PUBLIC,anon,authenticated;
COMMIT;

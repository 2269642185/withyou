-- Native JWT access tokens remain valid until expiry after revoke-all. Enforce
-- a per-member cutoff for this app's SQL/RLS business data as well.
BEGIN;
ALTER TABLE wy_private.members ADD COLUMN IF NOT EXISTS auth_iat_min bigint NOT NULL DEFAULT 0;
CREATE OR REPLACE FUNCTION wy_private.auth_uid() RETURNS text LANGUAGE plpgsql STABLE
SET search_path=pg_catalog,public AS $$
DECLARE claims jsonb; u text; cutoff bigint; issued bigint;
BEGIN
 claims:=nullif(current_setting('request.jwt.claims',true),'')::jsonb;u:=claims->>'sub';
 IF u IS NULL OR u='' OR u IN ('anon','service_account') THEN RAISE EXCEPTION 'AUTH_REQUIRED' USING ERRCODE='42501'; END IF;
 SELECT auth_iat_min INTO cutoff FROM wy_private.members WHERE user_id=u;
 IF coalesce(cutoff,0)>0 THEN
  issued:=coalesce(nullif(claims->>'iat','')::bigint,0);
  IF issued<cutoff THEN RAISE EXCEPTION 'SESSION_REVOKED' USING ERRCODE='42501'; END IF;
 END IF;
 RETURN u;
END $$;
REVOKE ALL ON FUNCTION wy_private.auth_uid() FROM PUBLIC,anon,authenticated;
CREATE OR REPLACE FUNCTION public.wy_revoke_old_sessions() RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER
SET search_path=pg_catalog,public AS $$
DECLARE u text:=wy_private.auth_uid(); issued bigint; cutoff bigint;
BEGIN
 PERFORM pg_advisory_xact_lock(81420401);
 issued:=nullif(nullif(current_setting('request.jwt.claims',true),'')::jsonb->>'iat','')::bigint;
 IF issued IS NULL OR issued<1 OR issued>extract(epoch FROM now())::bigint+120 THEN RAISE EXCEPTION 'AUTH_TIME_UNAVAILABLE'; END IF;
 IF NOT EXISTS(SELECT 1 FROM wy_private.members WHERE user_id=u) THEN
  RETURN jsonb_build_object('revoked',true,'wait_seconds',0);
 END IF;
 -- Include this short-lived recovery session, preventing equal-second tokens
 -- from surviving. The client waits briefly, then requires a fresh login.
 UPDATE wy_private.members SET auth_iat_min=greatest(auth_iat_min,issued+1) WHERE user_id=u RETURNING auth_iat_min INTO cutoff;
 RETURN jsonb_build_object('revoked',true,'wait_seconds',greatest(0,cutoff-floor(extract(epoch FROM now()))::bigint));
END $$;
REVOKE ALL ON FUNCTION public.wy_revoke_old_sessions() FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.wy_revoke_old_sessions() TO authenticated;
COMMIT;

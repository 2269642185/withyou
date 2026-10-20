-- Apply with 001..005 already deployed. Backfill all pre-existing Auth UIDs
-- into members before enabling the new client; see email/README.md.
BEGIN;
CREATE TABLE IF NOT EXISTS wy_private.members (
 user_id text PRIMARY KEY, admitted_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO wy_private.members(user_id) SELECT user_id FROM public.wy_profiles ON CONFLICT DO NOTHING;
CREATE TABLE IF NOT EXISTS wy_private.email_registration (
 singleton boolean PRIMARY KEY DEFAULT true CHECK(singleton),
 registration_open boolean NOT NULL DEFAULT false,
 member_limit integer NOT NULL DEFAULT 200 CHECK(member_limit BETWEEN 2 AND 1000)
);
INSERT INTO wy_private.email_registration(singleton) VALUES(true) ON CONFLICT DO NOTHING;
CREATE TABLE IF NOT EXISTS wy_private.friend_invites (
 code_hash text PRIMARY KEY, owner_id text NOT NULL REFERENCES wy_private.members(user_id),
 created_at timestamptz NOT NULL DEFAULT now(), expires_at timestamptz NOT NULL,
 used_by text UNIQUE, used_at timestamptz
);
REVOKE ALL ON wy_private.members,wy_private.email_registration,wy_private.friend_invites FROM PUBLIC,anon,authenticated;

DO $install$ BEGIN
IF to_regprocedure('wy_private.auth_uid()') IS NULL THEN
EXECUTE $create$CREATE FUNCTION wy_private.auth_uid() RETURNS text LANGUAGE plpgsql STABLE
SET search_path=pg_catalog,public AS $body$
DECLARE u text; BEGIN
 u:=nullif(current_setting('request.jwt.claims',true),'')::jsonb->>'sub';
 IF u IS NULL OR u='' OR u IN ('anon','service_account') THEN RAISE EXCEPTION 'AUTH_REQUIRED' USING ERRCODE='42501'; END IF;
 RETURN u;
END $body$$create$;
END IF;
END $install$;
REVOKE ALL ON FUNCTION wy_private.auth_uid() FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION public.wy_uid() RETURNS text LANGUAGE plpgsql STABLE SECURITY DEFINER
SET search_path=pg_catalog,public AS $$
DECLARE u text:=wy_private.auth_uid(); BEGIN
 IF NOT EXISTS(SELECT 1 FROM wy_private.members WHERE user_id=u) THEN RAISE EXCEPTION 'FRIEND_INVITE_REQUIRED' USING ERRCODE='42501'; END IF;
 RETURN u;
END $$;
REVOKE ALL ON FUNCTION public.wy_uid() FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.wy_uid() TO authenticated;

CREATE OR REPLACE FUNCTION public.wy_account_status() RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER
SET search_path=pg_catalog,public AS $$
DECLARE u text:=wy_private.auth_uid(); BEGIN
 RETURN jsonb_build_object('admitted',EXISTS(SELECT 1 FROM wy_private.members WHERE user_id=u),
  'nickname',coalesce((SELECT nickname FROM public.wy_profiles WHERE user_id=u),''),
  'registration_open',coalesce((SELECT registration_open FROM wy_private.email_registration WHERE singleton),false));
END $$;

CREATE OR REPLACE FUNCTION public.wy_friend_invite(action text, code text DEFAULT '', nickname text DEFAULT '') RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE u text:=wy_private.auth_uid(); raw text; h text; inv wy_private.friend_invites; cfg wy_private.email_registration;
BEGIN
 PERFORM pg_advisory_xact_lock(81420401);
 SELECT * INTO cfg FROM wy_private.email_registration WHERE singleton;
 IF action='join' AND EXISTS(SELECT 1 FROM wy_private.members WHERE user_id=u) THEN RETURN public.wy_account_status(); END IF;
 IF NOT cfg.registration_open THEN RAISE EXCEPTION 'REGISTRATION_CLOSED'; END IF;
 IF action='create' THEN
  PERFORM public.wy_uid();
  IF (SELECT count(*) FROM wy_private.friend_invites WHERE owner_id=u AND created_at>now()-interval '24 hours')>=5 THEN RAISE EXCEPTION 'TOO_FAST'; END IF;
  raw:=replace(gen_random_uuid()::text,'-','');
  h:=encode(sha256(convert_to(raw,'UTF8')),'hex');
  INSERT INTO wy_private.friend_invites(code_hash,owner_id,expires_at) VALUES(h,u,now()+interval '72 hours');
  RETURN jsonb_build_object('code',raw,'expires_in',259200);
 ELSIF action='join' THEN
  IF length(code)<>32 OR code!~'^[a-f0-9]{32}$' OR length(btrim(nickname)) NOT BETWEEN 2 AND 32 THEN RAISE EXCEPTION 'INVALID_FRIEND_INVITE'; END IF;
  h:=encode(sha256(convert_to(code,'UTF8')),'hex');
  SELECT * INTO inv FROM wy_private.friend_invites WHERE code_hash=h FOR UPDATE;
  IF inv.code_hash IS NULL OR inv.used_by IS NOT NULL OR inv.expires_at<=now() THEN RAISE EXCEPTION 'INVALID_FRIEND_INVITE'; END IF;
  IF (SELECT count(*) FROM wy_private.members)>=cfg.member_limit THEN RAISE EXCEPTION 'REGISTRATION_CLOSED'; END IF;
  INSERT INTO wy_private.members(user_id) VALUES(u);
  INSERT INTO public.wy_profiles(user_id,nickname) VALUES(u,btrim(nickname));
  UPDATE wy_private.friend_invites SET used_by=u,used_at=now() WHERE code_hash=h;
  RETURN public.wy_account_status();
 ELSE RAISE EXCEPTION 'INVALID_ACTION'; END IF;
END $$;
REVOKE ALL ON FUNCTION public.wy_account_status(),public.wy_friend_invite(text,text,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.wy_account_status(),public.wy_friend_invite(text,text,text) TO authenticated;
COMMIT;

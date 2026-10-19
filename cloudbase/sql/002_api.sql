BEGIN;
CREATE SCHEMA IF NOT EXISTS wy_private;
REVOKE ALL ON SCHEMA wy_private FROM PUBLIC,anon;
GRANT USAGE ON SCHEMA wy_private TO authenticated;
-- SECURITY DEFINER entrypoints infer identity only from the verified gateway JWT.
CREATE OR REPLACE FUNCTION public.wy_uid() RETURNS text LANGUAGE plpgsql STABLE SET search_path=pg_catalog,public AS $$
DECLARE u text; BEGIN
 u:=nullif(current_setting('request.jwt.claims',true),'')::jsonb->>'sub';
 IF u IS NULL OR u='' OR u IN ('anon','service_account') THEN RAISE EXCEPTION 'AUTH_REQUIRED' USING ERRCODE='42501'; END IF;
 RETURN u;
END $$;
CREATE OR REPLACE FUNCTION wy_private.wy_partner(uid text) RETURNS text LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog,public AS $$
 SELECT CASE WHEN a=uid THEN b ELSE a END FROM public.wy_pairs WHERE active AND uid IN (a,b) LIMIT 1
$$;
CREATE OR REPLACE FUNCTION wy_private.wy_visible(t public.wy_tasks, uid text) RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog,public AS $$
 SELECT t.owner_id=uid OR (t.visibility='partner' AND EXISTS(SELECT 1 FROM public.wy_pairs p WHERE p.id=t.pair_id AND p.active AND uid IN(p.a,p.b)))
$$;
CREATE OR REPLACE FUNCTION public.wy_pair_action(action text, value text DEFAULT '') RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE u text:=public.wy_uid(); inv public.wy_invites; code text; pid uuid;
BEGIN
 PERFORM pg_advisory_xact_lock(81420401);
 IF action='create' THEN
  IF wy_private.wy_partner(u) IS NOT NULL THEN RAISE EXCEPTION 'ALREADY_PAIRED'; END IF;
  IF EXISTS(SELECT 1 FROM public.wy_invites WHERE owner_id=u AND created_at>now()-interval '30 seconds') THEN RAISE EXCEPTION 'TOO_FAST'; END IF;
  UPDATE public.wy_invites SET closed=true WHERE owner_id=u AND NOT closed;
  code:=replace(gen_random_uuid()::text,'-','');
  INSERT INTO public.wy_invites(owner_id,code_hash,expires_at) VALUES(u,encode(sha256(convert_to(code,'UTF8')),'hex'),now()+interval '10 minutes');
  RETURN jsonb_build_object('code',code,'expires_seconds',600);
 ELSIF action='join' THEN
  IF wy_private.wy_partner(u) IS NOT NULL THEN RAISE EXCEPTION 'ALREADY_PAIRED'; END IF;
  SELECT * INTO inv FROM public.wy_invites WHERE code_hash=encode(sha256(convert_to(lower(value),'UTF8')),'hex') AND NOT closed AND expires_at>now() FOR UPDATE;
  IF NOT FOUND OR inv.owner_id=u OR inv.candidate_id IS NOT NULL OR wy_private.wy_partner(inv.owner_id) IS NOT NULL THEN RAISE EXCEPTION 'INVALID_INVITE'; END IF;
  IF EXISTS(SELECT 1 FROM public.wy_invites WHERE candidate_id=u AND NOT closed AND expires_at>now()) THEN RAISE EXCEPTION 'INVITE_PENDING'; END IF;
  UPDATE public.wy_invites SET candidate_id=u WHERE id=inv.id;
  RETURN jsonb_build_object('pending',true);
 ELSIF action='confirm' THEN
  SELECT * INTO inv FROM public.wy_invites WHERE id=value::uuid AND owner_id=u AND NOT closed AND candidate_id IS NOT NULL AND expires_at>now() FOR UPDATE;
  IF NOT FOUND OR wy_private.wy_partner(u) IS NOT NULL OR wy_private.wy_partner(inv.candidate_id) IS NOT NULL THEN RAISE EXCEPTION 'INVALID_INVITE'; END IF;
  INSERT INTO public.wy_pairs(a,b) VALUES(u,inv.candidate_id) RETURNING id INTO pid;
  UPDATE public.wy_invites SET closed=true WHERE owner_id IN(u,inv.candidate_id) OR candidate_id IN(u,inv.candidate_id);
  RETURN jsonb_build_object('pair_id',pid);
 ELSIF action='cancel' THEN
  UPDATE public.wy_invites SET closed=true WHERE id=value::uuid AND u IN(owner_id,candidate_id);
  RETURN '{}'::jsonb;
 ELSIF action='unlink' THEN
  UPDATE public.wy_pairs SET active=false WHERE id=value::uuid AND active AND u IN(a,b);
  IF NOT FOUND THEN RAISE EXCEPTION 'PAIR_CHANGED'; END IF;
  UPDATE public.wy_invites SET closed=true WHERE owner_id=u OR candidate_id=u;
  RETURN '{}'::jsonb;
 END IF;
 RAISE EXCEPTION 'INVALID_ACTION';
END $$;
CREATE OR REPLACE FUNCTION public.wy_snapshot(task_cursor bigint DEFAULT 0, check_cursor bigint DEFAULT 0, known_pair text DEFAULT '', nickname text DEFAULT '') RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE u text:=public.wy_uid(); partner text; p public.wy_pairs; reset boolean; result jsonb;
BEGIN
 -- Serializes membership changes with snapshots and mutation authorization.
 PERFORM pg_advisory_xact_lock(81420401);
 IF length(trim(nickname)) BETWEEN 1 AND 32 THEN
  INSERT INTO public.wy_profiles(user_id,nickname) VALUES(u,trim(nickname)) ON CONFLICT(user_id) DO UPDATE SET nickname=excluded.nickname;
 END IF;
 SELECT * INTO p FROM public.wy_pairs WHERE active AND u IN(a,b) LIMIT 1;
 partner:=CASE WHEN p.a=u THEN p.b ELSE p.a END;
 reset:=coalesce(p.id::text,'')<>known_pair;
 -- Return only current partner profiles and current pending invitations, never all users.
 result:=jsonb_build_object('pair_id',coalesce(p.id::text,''),'partner_id',coalesce(partner,''),
  'partner_name',coalesce((SELECT pr.nickname FROM public.wy_profiles pr WHERE pr.user_id=partner),'伙伴'),
  'reset',reset,'server_day',(now() AT TIME ZONE 'Asia/Shanghai')::date,
  'task_cursor',coalesce((SELECT max(seq) FROM public.wy_tasks),0),
  'check_cursor',coalesce((SELECT max(seq) FROM public.wy_checks),0),
  'invites',coalesce((SELECT jsonb_agg(jsonb_build_object('id',i.id,'owner_id',i.owner_id,'candidate_id',coalesce(i.candidate_id,''),'name',coalesce(pr.nickname,'伙伴'),'expires_at',i.expires_at)) FROM public.wy_invites i LEFT JOIN public.wy_profiles pr ON pr.user_id=CASE WHEN i.owner_id=u THEN i.candidate_id ELSE i.owner_id END WHERE NOT i.closed AND i.expires_at>now() AND (i.owner_id=u OR i.candidate_id=u)),'[]'::jsonb),
  'visible_ids',coalesce((SELECT jsonb_agg(t.id) FROM public.wy_tasks t WHERE wy_private.wy_visible(t,u)),'[]'::jsonb),
  'tasks',coalesce((SELECT jsonb_agg(to_jsonb(t)) FROM public.wy_tasks t WHERE wy_private.wy_visible(t,u) AND (reset OR t.seq>task_cursor)),'[]'::jsonb),
  'checks',coalesce((SELECT jsonb_agg(to_jsonb(c)) FROM public.wy_checks c JOIN public.wy_tasks t ON t.id=c.task_id WHERE wy_private.wy_visible(t,u) AND (c.user_id=u OR (t.visibility='partner' AND c.user_id=partner AND t.pair_id=p.id)) AND (reset OR c.seq>check_cursor)),'[]'::jsonb));
 RETURN result;
END $$;
CREATE OR REPLACE FUNCTION public.wy_apply(op_id uuid, payload jsonb) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE u text:=public.wy_uid(); result jsonb; t public.wy_tasks; c public.wy_checks; wanted uuid; expected bigint; pid uuid; d date; v text; k text; arr integer[];
BEGIN
 PERFORM pg_advisory_xact_lock(81420401);
 SELECT r.result INTO result FROM public.wy_receipts r WHERE r.user_id=u AND r.op_id=wy_apply.op_id;
 IF FOUND THEN RETURN result; END IF;
 wanted:=(payload->>'id')::uuid; expected:=(payload->>'expected')::bigint;
 SELECT * INTO t FROM public.wy_tasks WHERE id=wanted FOR UPDATE;
 IF payload->>'type'='task' THEN
  IF t.id IS NOT NULL AND t.owner_id<>u THEN RAISE EXCEPTION 'FORBIDDEN' USING ERRCODE='42501'; END IF;
  IF (t.id IS NULL AND expected<>0) OR (t.id IS NOT NULL AND (t.revision<>expected OR t.deleted)) THEN
   result:=jsonb_build_object('conflict',true,'message','事项已在其他设备修改或删除，请查看最新内容');
  ELSE
   IF t.id IS NULL AND (SELECT count(*) FROM public.wy_tasks WHERE owner_id=u AND NOT deleted)>=500 THEN RAISE EXCEPTION 'TASK_LIMIT'; END IF;
   k:=payload->>'kind'; v:=payload->>'visibility'; pid:=nullif(payload->>'pair_id','')::uuid;
   IF v='partner' OR k='shared' THEN
    IF NOT EXISTS(SELECT 1 FROM public.wy_pairs WHERE id=pid AND active AND u IN(a,b)) AND NOT
      (t.id IS NOT NULL AND t.pair_id=pid AND (coalesce((payload->>'archived')::boolean,false) OR coalesce((payload->>'deleted')::boolean,false))
       AND k=t.kind AND v=t.visibility AND payload->>'title'=t.title AND coalesce(payload->>'notes','')=t.notes)
    THEN RAISE EXCEPTION 'PAIR_CHANGED'; END IF;
   ELSE pid:=NULL; END IF;
   IF t.id IS NOT NULL AND k<>t.kind THEN RAISE EXCEPTION 'KIND_IMMUTABLE'; END IF;
   SELECT array_agg(value::integer) INTO arr FROM jsonb_array_elements_text(payload->'weekdays');
   INSERT INTO public.wy_tasks(id,owner_id,pair_id,kind,visibility,title,notes,due,weekdays,archived,deleted)
   VALUES(wanted,u,pid,k,v,trim(payload->>'title'),coalesce(payload->>'notes',''),nullif(payload->>'due','')::date,arr,coalesce((payload->>'archived')::boolean,false),coalesce((payload->>'deleted')::boolean,false))
   ON CONFLICT(id) DO UPDATE SET pair_id=excluded.pair_id,visibility=excluded.visibility,title=excluded.title,notes=excluded.notes,due=excluded.due,weekdays=excluded.weekdays,archived=excluded.archived,deleted=excluded.deleted,revision=public.wy_tasks.revision+1,seq=nextval('public.wy_tasks_seq_seq'),updated_at=clock_timestamp();
   result:='{"ok":true}'::jsonb;
  END IF;
 ELSIF payload->>'type'='check' THEN
  IF t.id IS NULL OR NOT wy_private.wy_visible(t,u) OR (t.owner_id<>u AND t.kind<>'shared') OR t.deleted OR t.archived THEN RAISE EXCEPTION 'FORBIDDEN' USING ERRCODE='42501'; END IF;
  IF coalesce(payload->>'pair_id','')<>coalesce(t.pair_id::text,'') THEN RAISE EXCEPTION 'PAIR_CHANGED'; END IF;
  IF t.visibility='partner' AND NOT EXISTS(SELECT 1 FROM public.wy_pairs WHERE id=t.pair_id AND active AND u IN(a,b)) THEN RAISE EXCEPTION 'PAIR_CHANGED'; END IF;
  d:=CASE WHEN t.kind='todo' THEN date '1970-01-01' ELSE (payload->>'day')::date END;
  IF t.kind<>'todo' AND (d<>(now() AT TIME ZONE 'Asia/Shanghai')::date OR NOT extract(isodow FROM d)::integer=ANY(t.weekdays)) THEN RAISE EXCEPTION 'DAY_CHANGED'; END IF;
  SELECT * INTO c FROM public.wy_checks WHERE task_id=wanted AND user_id=u AND day=d FOR UPDATE;
  IF coalesce(c.revision,0)<>expected THEN result:='{"conflict":true,"message":"打卡已在其他设备修改，请刷新后重试"}'::jsonb;
  ELSE
   INSERT INTO public.wy_checks(task_id,user_id,day,done) VALUES(wanted,u,d,(payload->>'done')::boolean)
   ON CONFLICT(task_id,user_id,day) DO UPDATE SET done=excluded.done,revision=public.wy_checks.revision+1,seq=nextval('public.wy_checks_seq_seq'),updated_at=clock_timestamp();
   result:='{"ok":true}'::jsonb;
  END IF;
 ELSE RAISE EXCEPTION 'INVALID_ACTION'; END IF;
 INSERT INTO public.wy_receipts(user_id,op_id,result) VALUES(u,wy_apply.op_id,result);
 RETURN result;
END $$;
-- Defense in depth for read access: no client receives direct mutation grants.
GRANT SELECT ON public.wy_tasks,public.wy_checks TO authenticated;
DROP POLICY IF EXISTS wy_task_read ON public.wy_tasks;
CREATE POLICY wy_task_read ON public.wy_tasks FOR SELECT TO authenticated USING(wy_private.wy_visible(wy_tasks,public.wy_uid()));
DROP POLICY IF EXISTS wy_check_read ON public.wy_checks;
CREATE POLICY wy_check_read ON public.wy_checks FOR SELECT TO authenticated USING(EXISTS(SELECT 1 FROM public.wy_tasks t WHERE t.id=task_id AND wy_private.wy_visible(t,public.wy_uid())) AND (user_id=public.wy_uid() OR user_id=wy_private.wy_partner(public.wy_uid())));
REVOKE ALL ON FUNCTION public.wy_uid(),wy_private.wy_partner(text),wy_private.wy_visible(public.wy_tasks,text),public.wy_pair_action(text,text),public.wy_snapshot(bigint,bigint,text,text),public.wy_apply(uuid,jsonb) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.wy_uid(),wy_private.wy_partner(text),wy_private.wy_visible(public.wy_tasks,text),public.wy_pair_action(text,text),public.wy_snapshot(bigint,bigint,text,text),public.wy_apply(uuid,jsonb) TO authenticated;
COMMIT;

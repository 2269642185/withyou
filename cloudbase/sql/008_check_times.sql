BEGIN;
ALTER TABLE public.wy_checks ADD COLUMN IF NOT EXISTS completed_at timestamptz;
-- No timestamp backfill: updated_at is synchronization time, not completion time.
CREATE OR REPLACE FUNCTION public.wy_apply(op_id uuid, payload jsonb) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE u text:=public.wy_uid(); result jsonb; t public.wy_tasks; c public.wy_checks; wanted uuid; expected bigint; pid uuid; d date; completed date; stamp timestamptz; v text; k text; arr integer[];
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
  stamp:=coalesce(nullif(payload->>'recorded_at',''),nullif(payload->>'completed_at',''))::timestamptz;
  IF stamp>clock_timestamp()+interval '5 minutes' THEN RAISE EXCEPTION 'TIME_INVALID'; END IF;
  IF t.kind<>'todo' AND (d>(now() AT TIME ZONE 'Asia/Shanghai')::date OR NOT extract(isodow FROM d)::integer=ANY(t.weekdays)
    OR (stamp IS NULL AND d<>(now() AT TIME ZONE 'Asia/Shanghai')::date)
    OR (stamp IS NOT NULL AND (stamp AT TIME ZONE 'Asia/Shanghai')::date<>d)) THEN RAISE EXCEPTION 'DAY_CHANGED'; END IF;
  completed:=CASE WHEN t.kind='todo' THEN coalesce(nullif(payload->>'completed_on','')::date,(now() AT TIME ZONE 'Asia/Shanghai')::date) ELSE d END;
  IF stamp IS NOT NULL AND (stamp AT TIME ZONE 'Asia/Shanghai')::date<>completed THEN RAISE EXCEPTION 'TIME_INVALID'; END IF;
  IF completed>(now() AT TIME ZONE 'Asia/Shanghai')::date THEN RAISE EXCEPTION 'DAY_CHANGED'; END IF;
  SELECT * INTO c FROM public.wy_checks WHERE task_id=wanted AND user_id=u AND day=d FOR UPDATE;
  IF coalesce(c.revision,0)<>expected THEN result:='{"conflict":true,"message":"打卡已在其他设备修改，请刷新后重试"}'::jsonb;
  ELSE
   INSERT INTO public.wy_checks(task_id,user_id,day,done,completed_on,completed_at) VALUES(wanted,u,d,(payload->>'done')::boolean,CASE WHEN (payload->>'done')::boolean THEN completed ELSE NULL END,CASE WHEN (payload->>'done')::boolean THEN stamp ELSE NULL END)
   ON CONFLICT(task_id,user_id,day) DO UPDATE SET done=excluded.done,completed_on=excluded.completed_on,completed_at=excluded.completed_at,revision=public.wy_checks.revision+1,seq=nextval('public.wy_checks_seq_seq'),updated_at=clock_timestamp();
   result:='{"ok":true}'::jsonb;
  END IF;
 ELSE RAISE EXCEPTION 'INVALID_ACTION'; END IF;
 INSERT INTO public.wy_receipts(user_id,op_id,result) VALUES(u,wy_apply.op_id,result);
 RETURN result;
END $$;

REVOKE ALL ON FUNCTION public.wy_apply(uuid,jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.wy_apply(uuid,jsonb) TO authenticated;
COMMIT;

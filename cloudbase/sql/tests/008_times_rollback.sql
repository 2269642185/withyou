-- Administrative SQL integration test, not native HTTP/Auth verification.
-- Every synthetic member/profile/task/check/receipt rolls back in the inner block.
-- Sequence gaps may remain, as PostgreSQL sequences are nontransactional.
DO $checks$
DECLARE ua text:='v182-qa-'||gen_random_uuid(); ub text:='v182-qa-'||gen_random_uuid();
 pid uuid:=gen_random_uuid(); tid uuid:=gen_random_uuid(); hid uuid:=gen_random_uuid();
 op uuid:=gen_random_uuid(); d date:=(now() AT TIME ZONE 'Asia/Shanghai')::date-1;
 stamp timestamptz; payload jsonb; snapshot jsonb; row jsonb; checked boolean:=false;
BEGIN
 stamp:=(d+time '10:19:32') AT TIME ZONE 'Asia/Shanghai';
 BEGIN
  INSERT INTO wy_private.members(user_id) VALUES(ua),(ub);
  INSERT INTO public.wy_profiles(user_id,nickname) VALUES(ua,'时间验证 A'),(ub,'时间验证 B');
  INSERT INTO public.wy_pairs(id,a,b) VALUES(pid,ua,ub);
  PERFORM set_config('request.jwt.claims',jsonb_build_object('sub',ua)::text,true);
  PERFORM public.wy_apply(gen_random_uuid(),jsonb_build_object('type','task','id',tid,'expected',0,'title','时间验证','kind','todo','visibility','partner','pair_id',pid,'weekdays',jsonb_build_array(1,2,3,4,5,6,7)));
  payload:=jsonb_build_object('type','check','id',tid,'expected',0,'pair_id',pid,'day','1970-01-01','done',true,'completed_on',d,'recorded_at',stamp,'completed_at',stamp);
  PERFORM public.wy_apply(op,payload);PERFORM public.wy_apply(op,payload);
  PERFORM set_config('request.jwt.claims',jsonb_build_object('sub',ub)::text,true);
  snapshot:=public.wy_snapshot();
  SELECT value INTO row FROM jsonb_array_elements(snapshot->'checks') WHERE value->>'task_id'=tid::text;
  IF row IS NULL OR (row->>'completed_at')::timestamptz<>stamp OR (row->>'completed_on')::date<>d OR (row->>'revision')::bigint<>1 THEN RAISE EXCEPTION 'TIME_TEST_PARTNER_OR_DEDUP'; END IF;
  PERFORM set_config('request.jwt.claims',jsonb_build_object('sub',ua)::text,true);
  PERFORM public.wy_apply(gen_random_uuid(),payload||jsonb_build_object('expected',1,'done',false));
  SELECT to_jsonb(c) INTO row FROM public.wy_checks c WHERE c.task_id=tid;
  IF (row->>'done')::boolean OR row->>'completed_at' IS NOT NULL OR row->>'completed_on' IS NOT NULL THEN RAISE EXCEPTION 'TIME_TEST_UNDO'; END IF;
  PERFORM public.wy_apply(gen_random_uuid(),jsonb_build_object('type','task','id',hid,'expected',0,'title','离线习惯验证','kind','habit','visibility','private','pair_id','','weekdays',jsonb_build_array(1,2,3,4,5,6,7)));
  PERFORM public.wy_apply(gen_random_uuid(),payload||jsonb_build_object('id',hid,'expected',0,'pair_id','','day',d));
  SELECT to_jsonb(c) INTO row FROM public.wy_checks c WHERE c.task_id=hid;
  IF (row->>'completed_at')::timestamptz<>stamp OR (row->>'day')::date<>d THEN RAISE EXCEPTION 'TIME_TEST_OFFLINE_HABIT'; END IF;
  checked:=true;
  RAISE EXCEPTION USING ERRCODE='P1820',MESSAGE='ROLLBACK_CONTROLLED_TIME_FIXTURES';
 EXCEPTION WHEN SQLSTATE 'P1820' THEN NULL;
 END;
 IF NOT checked THEN RAISE EXCEPTION 'TIME_TEST_NOT_COMPLETED'; END IF;
 IF EXISTS(SELECT 1 FROM wy_private.members WHERE user_id IN(ua,ub)) OR EXISTS(SELECT 1 FROM public.wy_tasks WHERE id IN(tid,hid)) OR EXISTS(SELECT 1 FROM public.wy_pairs WHERE id=pid) OR EXISTS(SELECT 1 FROM public.wy_receipts WHERE user_id IN(ua,ub)) THEN RAISE EXCEPTION 'TIME_TEST_ROLLBACK_FAILED'; END IF;
END $checks$;

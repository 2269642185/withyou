BEGIN;
CREATE TABLE IF NOT EXISTS public.wy_memories (
 id uuid NOT NULL, owner_id text NOT NULL, pair_id uuid NOT NULL REFERENCES public.wy_pairs(id),
 payload jsonb NOT NULL, photo_path text NOT NULL DEFAULT '', revision bigint NOT NULL DEFAULT 1,
 active boolean NOT NULL DEFAULT true, updated_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(owner_id,id)
);
ALTER TABLE public.wy_memories ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.wy_memories FROM PUBLIC,anon,authenticated;
CREATE OR REPLACE FUNCTION wy_private.memory_visible(owner_key text, pair_key uuid, is_active boolean) RETURNS boolean
 LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog,public AS $$
 SELECT owner_key=public.wy_uid() OR (is_active AND EXISTS(SELECT 1 FROM public.wy_pairs WHERE id=pair_key AND active AND public.wy_uid() IN(a,b)))
$$;
REVOKE ALL ON FUNCTION wy_private.memory_visible(text,uuid,boolean) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION wy_private.memory_visible(text,uuid,boolean) TO authenticated;
DROP POLICY IF EXISTS wy_memories_read ON public.wy_memories;
CREATE POLICY wy_memories_read ON public.wy_memories FOR SELECT TO authenticated USING(wy_private.memory_visible(owner_id,pair_id,active));
GRANT SELECT ON public.wy_memories TO authenticated;
CREATE OR REPLACE FUNCTION public.wy_memory_snapshot() RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE u text:=public.wy_uid(); BEGIN
 PERFORM pg_advisory_xact_lock(81420401);
 RETURN jsonb_build_object('memories',coalesce((SELECT jsonb_agg(to_jsonb(m)) FROM public.wy_memories m WHERE wy_private.memory_visible(m.owner_id,m.pair_id,m.active)),'[]'::jsonb));
END $$;
CREATE OR REPLACE FUNCTION public.wy_memory_apply(op_id uuid, item jsonb) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE u text:=public.wy_uid(); old public.wy_memories; pid uuid; wanted uuid:=(item->>'id')::uuid; p jsonb:=item->'payload'; path text:=coalesce(item->>'photo_path',''); result jsonb; appearance_json jsonb; k text; going boolean:=coalesce((item->>'active')::boolean,true);
BEGIN
 PERFORM pg_advisory_xact_lock(81420401);
 SELECT r.result INTO result FROM public.wy_receipts r WHERE r.user_id=u AND r.op_id=wy_memory_apply.op_id;
 IF FOUND THEN RETURN result; END IF;
 SELECT * INTO old FROM public.wy_memories WHERE owner_id=u AND id=wanted FOR UPDATE;
 IF coalesce(old.revision,0)<>coalesce((item->>'expected')::bigint,0) THEN RETURN jsonb_build_object('conflict',true); END IF;
 IF going THEN
  SELECT id INTO pid FROM public.wy_pairs WHERE active AND u IN(a,b) AND id::text=item->>'pair_id';
  IF pid IS NULL THEN RAISE EXCEPTION 'PAIR_CHANGED' USING ERRCODE='42501'; END IF;
  IF (SELECT count(*) FROM public.wy_memories WHERE owner_id=u AND active)>=100 AND (old.id IS NULL OR NOT old.active) THEN RAISE EXCEPTION 'MEMORY_LIMIT'; END IF;
  FOREACH k IN ARRAY ARRAY['id','title','date','mode','template'] LOOP
   IF jsonb_typeof(p->k) IS DISTINCT FROM 'string' THEN RAISE EXCEPTION 'INVALID_MEMORY'; END IF;
  END LOOP;
  p:=p || jsonb_build_object('nickname',coalesce(p->>'nickname',''),'pinned',false);
  IF p IS NULL OR jsonb_typeof(p)<>'object' OR NOT (p ?& ARRAY['id','title','date','mode','color','template','appearance']) OR octet_length(p::text)>12000 OR length(p->>'title') NOT BETWEEN 1 AND 60 OR length(coalesce(p->>'nickname',''))>40 OR p->>'id'<>wanted::text OR p ? 'photo' OR p ? 'categoryId' OR (p->>'date')::date NOT BETWEEN date '0001-01-01' AND date '9998-12-31' OR p->>'mode' NOT IN('INCLUSIVE','ELAPSED') OR (p->>'color')::int NOT BETWEEN 0 AND 29 OR p->>'template' NOT IN('classic','polaroid','photo','minimal','ticket','note') THEN RAISE EXCEPTION 'INVALID_MEMORY'; END IF;
  appearance_json:=p->'appearance';
  IF appearance_json IS NULL OR jsonb_typeof(appearance_json)<>'object' OR NOT (appearance_json ?& ARRAY['tone','overlay']) OR appearance_json->>'tone' NOT IN('AUTO','DARK','LIGHT','CUSTOM') OR (appearance_json->>'overlay')::numeric NOT BETWEEN 0 AND 1 THEN RAISE EXCEPTION 'INVALID_APPEARANCE'; END IF;
  FOREACH k IN ARRAY ARRAY['background','text','title','days','auxiliary'] LOOP
   IF appearance_json ? k AND (appearance_json->>k IS NULL OR appearance_json->>k !~ '^#[A-Fa-f0-9]{6}$') THEN RAISE EXCEPTION 'INVALID_COLOR'; END IF;
  END LOOP;
  IF path<>'' AND (path !~ ('^'||u||'/'||wanted::text||'/[a-f0-9]{64}\.jpg$') OR NOT EXISTS(SELECT 1 FROM storage.objects WHERE bucket_id='withyou-memories' AND name=path)) THEN RAISE EXCEPTION 'INVALID_PHOTO'; END IF;
 ELSE
  IF old.id IS NULL THEN RETURN '{}'::jsonb; END IF;
  pid:=old.pair_id;p:=old.payload;path:='';
 END IF;
 INSERT INTO public.wy_memories(id,owner_id,pair_id,payload,photo_path,active,revision) VALUES(wanted,u,pid,p,path,going,coalesce(old.revision,0)+1)
 ON CONFLICT(owner_id,id) DO UPDATE SET pair_id=excluded.pair_id,payload=excluded.payload,photo_path=excluded.photo_path,active=excluded.active,revision=excluded.revision,updated_at=now();
 result:=jsonb_build_object('revision',coalesce(old.revision,0)+1);
 INSERT INTO public.wy_receipts(user_id,op_id,result) VALUES(u,op_id,result);
 RETURN result;
END $$;
REVOKE ALL ON FUNCTION public.wy_memory_snapshot(),public.wy_memory_apply(uuid,jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.wy_memory_snapshot(),public.wy_memory_apply(uuid,jsonb) TO authenticated;
-- PostgreSQL CloudBase Storage. Private bucket, raw authenticated downloads only.
INSERT INTO storage.buckets(id,name,public,file_size_limit,allowed_mime_types) VALUES('withyou-memories','withyou-memories',false,786432,ARRAY['image/jpeg']) ON CONFLICT(id) DO NOTHING;
CREATE OR REPLACE FUNCTION wy_private.photo_read(path text) RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog,public AS $$
 SELECT split_part(path,'/',1)=public.wy_uid() OR EXISTS(SELECT 1 FROM public.wy_memories m JOIN public.wy_pairs p ON p.id=m.pair_id WHERE m.photo_path=path AND m.active AND p.active AND public.wy_uid() IN(p.a,p.b))
$$;
CREATE OR REPLACE FUNCTION wy_private.photo_write(path text) RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog,public AS $$
 SELECT path ~ ('^'||public.wy_uid()||'/[a-f0-9-]{36}/[a-f0-9]{64}\.jpg$') AND EXISTS(SELECT 1 FROM public.wy_pairs WHERE active AND public.wy_uid() IN(a,b)) AND (SELECT count(*) FROM storage.objects WHERE bucket_id='withyou-memories' AND split_part(name,'/',1)=public.wy_uid())<150
$$;
REVOKE ALL ON FUNCTION wy_private.photo_read(text),wy_private.photo_write(text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION wy_private.photo_read(text),wy_private.photo_write(text) TO authenticated;
DROP POLICY IF EXISTS wy_photo_select ON storage.objects;
CREATE POLICY wy_photo_select ON storage.objects FOR SELECT TO authenticated USING(bucket_id='withyou-memories' AND wy_private.photo_read(name));
DROP POLICY IF EXISTS wy_photo_insert ON storage.objects;
CREATE POLICY wy_photo_insert ON storage.objects FOR INSERT TO authenticated WITH CHECK(bucket_id='withyou-memories' AND wy_private.photo_write(name));
DROP POLICY IF EXISTS wy_photo_delete ON storage.objects;
CREATE POLICY wy_photo_delete ON storage.objects FOR DELETE TO authenticated USING(bucket_id='withyou-memories' AND split_part(name,'/',1)=public.wy_uid());
-- No UPDATE / overwrite policy: an appearance version always references immutable photo bytes.
COMMIT;

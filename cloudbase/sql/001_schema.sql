-- Run as CloudBase administrator in the SQL editor. No changes to auth schema.
BEGIN;
CREATE TABLE IF NOT EXISTS public.wy_profiles (
 user_id text PRIMARY KEY, nickname text NOT NULL CHECK(length(nickname) BETWEEN 1 AND 32)
);
CREATE TABLE IF NOT EXISTS public.wy_pairs (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), a text NOT NULL, b text NOT NULL,
 active boolean NOT NULL DEFAULT true, created_at timestamptz NOT NULL DEFAULT now(), CHECK(a<>b)
);
CREATE INDEX IF NOT EXISTS wy_pairs_active ON public.wy_pairs(a,b) WHERE active;
CREATE TABLE IF NOT EXISTS public.wy_invites (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), owner_id text NOT NULL, candidate_id text,
 code_hash text NOT NULL UNIQUE, expires_at timestamptz NOT NULL, closed boolean NOT NULL DEFAULT false,
 created_at timestamptz NOT NULL DEFAULT now(), CHECK(owner_id IS DISTINCT FROM candidate_id)
);
CREATE TABLE IF NOT EXISTS public.wy_tasks (
 id uuid PRIMARY KEY, owner_id text NOT NULL, pair_id uuid REFERENCES public.wy_pairs(id),
 kind text NOT NULL CHECK(kind IN ('todo','habit','shared')),
 visibility text NOT NULL CHECK(visibility IN ('private','partner')),
 title text NOT NULL CHECK(length(title) BETWEEN 1 AND 80), notes text NOT NULL DEFAULT '' CHECK(length(notes)<=1000),
 due date, weekdays integer[] NOT NULL DEFAULT ARRAY[1,2,3,4,5,6,7],
 archived boolean NOT NULL DEFAULT false, deleted boolean NOT NULL DEFAULT false,
 revision bigint NOT NULL DEFAULT 1, seq bigserial NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(),
 CHECK(cardinality(weekdays) BETWEEN 1 AND 7 AND weekdays <@ ARRAY[1,2,3,4,5,6,7]),
 CHECK(kind<>'shared' OR (visibility='partner' AND pair_id IS NOT NULL)),
 CHECK(visibility<>'partner' OR pair_id IS NOT NULL)
);
CREATE TABLE IF NOT EXISTS public.wy_checks (
 task_id uuid NOT NULL REFERENCES public.wy_tasks(id), user_id text NOT NULL, day date NOT NULL,
 done boolean NOT NULL, revision bigint NOT NULL DEFAULT 1, seq bigserial NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(task_id,user_id,day)
);
CREATE TABLE IF NOT EXISTS public.wy_receipts (
 user_id text NOT NULL, op_id uuid NOT NULL, result jsonb NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(user_id,op_id)
);
CREATE INDEX IF NOT EXISTS wy_tasks_owner_seq ON public.wy_tasks(owner_id,seq);
CREATE INDEX IF NOT EXISTS wy_tasks_pair_seq ON public.wy_tasks(pair_id,seq);
CREATE INDEX IF NOT EXISTS wy_checks_seq ON public.wy_checks(seq);
ALTER TABLE public.wy_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.wy_pairs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.wy_invites ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.wy_tasks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.wy_checks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.wy_receipts ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.wy_profiles,public.wy_pairs,public.wy_invites,public.wy_tasks,public.wy_checks,public.wy_receipts FROM PUBLIC,anon,authenticated;
REVOKE ALL ON SEQUENCE public.wy_tasks_seq_seq,public.wy_checks_seq_seq FROM PUBLIC,anon,authenticated;
COMMIT;

-- BOW BOW — Supabase schema (Phase 4 draft, to apply in Supabase SQL editor)
-- Project name: bow-bow-assistant
-- Enable RLS on every table. Service-role key stays server-side only.

create table if not exists users (
  id uuid primary key default gen_random_uuid(),
  email text unique,
  preferences jsonb default '{}'::jsonb,
  preferred_language text default 'tanglish',
  created_at timestamptz default now()
);

create table if not exists tasks (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references users(id),
  title text not null,
  description text,
  status text not null default 'pending'
    check (status in ('pending','running','completed','failed','awaiting_approval')),
  priority text default 'normal'
    check (priority in ('low','normal','high')),
  created_at timestamptz default now(),
  completed_at timestamptz
);

create table if not exists conversations (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references users(id),
  message text not null,
  response text,
  language text,
  timestamp timestamptz default now()
);

create table if not exists reminders (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references users(id),
  title text not null,
  scheduled_at timestamptz not null,
  status text not null default 'pending'
    check (status in ('pending','sent','done','cancelled')),
  created_at timestamptz default now()
);

create table if not exists task_history (
  id uuid primary key default gen_random_uuid(),
  task_id uuid references tasks(id),
  action text not null,
  execution_result jsonb,
  timestamp timestamptz default now()
);

alter table users enable row level security;
alter table tasks enable row level security;
alter table conversations enable row level security;
alter table reminders enable row level security;
alter table task_history enable row level security;

-- Single-user v1: authenticated users see only their own rows.
-- (Policies to be tightened with auth.uid() once Supabase Auth is wired.)
create policy "own rows" on tasks
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own rows" on conversations
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own rows" on reminders
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

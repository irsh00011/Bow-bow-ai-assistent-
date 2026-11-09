-- BOW BOW — migration 001: let the anon role write what the Activepieces flows need.
-- Run in the Supabase SQL editor (project bow-bow-assistant).
--
-- Why: Flows B and C call Supabase with ONLY the anon key (the service-role
-- key must never go into a flow — user rule). The base schema's "own rows"
-- policies use auth.uid(), which anon never has, so anon INSERTs into
-- reminders/task_history failed with 401 "new row violates row-level
-- security policy", and anon could not SELECT/UPDATE tasks either
-- (Flow E could never see awaiting_approval tasks).
--
-- Tradeoff, stated plainly: anyone holding the anon key can now read and
-- write rows in tasks, reminders and task_history. The anon key is NOT in
-- git or the app — it lives only in backend/.env and the Activepieces
-- Supabase connection. This is acceptable for single-user v1; tighten it
-- later with Supabase Auth (authenticated role + auth.uid() policies)
-- once user sign-in exists. The users and conversations tables are
-- untouched by this migration.

create policy "anon flow select" on tasks
  for select to anon using (true);
create policy "anon flow insert" on tasks
  for insert to anon with check (true);
create policy "anon flow update" on tasks
  for update to anon using (true) with check (true);

create policy "anon flow select" on reminders
  for select to anon using (true);
create policy "anon flow insert" on reminders
  for insert to anon with check (true);
create policy "anon flow update" on reminders
  for update to anon using (true) with check (true);

create policy "anon flow select" on task_history
  for select to anon using (true);
create policy "anon flow insert" on task_history
  for insert to anon with check (true);

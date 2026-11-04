"""Build a transactional, single-use SQL import from reviewed candidates."""
import collections,datetime as dt,hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];out=ROOT/'docs/import-review';x=json.loads((out/'candidates.json').read_text())
sha=x['sha256'];tag='outdoor-xlsx:'+sha

def q(s):
 if s is None:return 'NULL'
 if isinstance(s,bool):return 'TRUE' if s else 'FALSE'
 if isinstance(s,int):return str(s)
 return "'"+str(s).replace("'","''")+"'"
def js(o):return q(json.dumps(o,ensure_ascii=False))+'::jsonb'
def sqlrow(values):return '('+','.join(q(v) for v in values)+')'
sql=["\\set ON_ERROR_STOP on","BEGIN;","SET LOCAL standard_conforming_strings=on;","SET LOCAL lock_timeout='10s';","SELECT pg_advisory_xact_lock(74626831);",f"DO $$ BEGIN IF EXISTS (SELECT 1 FROM import_batches WHERE details_json::jsonb->>'import_key'={q(tag)}) THEN RAISE EXCEPTION 'This workbook has already been imported'; END IF; IF NOT EXISTS(SELECT 1 FROM app_users u JOIN user_roles r ON r.user_id=u.id WHERE u.id=1 AND u.email='egorchuiko7@gmail.com' AND r.role='SUPER_ADMIN') THEN RAISE EXCEPTION 'Import owner mismatch'; END IF; END $$;"]
# Keep the source identity explicitly supplied by the user.
sql += ["INSERT INTO audit_logs(entity_type,entity_id,field_name,old_value,new_value,author_id,created_at,updated_at) SELECT 'USER',id,'fullName',full_name,'Чуйко Егор',1,now(),now() FROM app_users WHERE id=1 AND full_name<>'Чуйко Егор';","UPDATE app_users SET full_name='Чуйко Егор',updated_at=now() WHERE id=1 AND full_name<>'Чуйко Егор';"]

def stage(name,cols,rows):
 sql.append(f'CREATE TEMP TABLE {name} ({cols}) ON COMMIT DROP;')
 if rows:
  for start in range(0,len(rows),300):sql.append(f'INSERT INTO {name} VALUES '+','.join(sqlrow(r) for r in rows[start:start+300])+';')
stage('imp_city','name text PRIMARY KEY,category text,needs_repair boolean',[(c['name'],c['category'],c['needs_repair']) for c in x['cities']])
sql.append("INSERT INTO cities(name,category,needs_repair,repair_status,repair_plan,created_at,updated_at) SELECT name,category,needs_repair,'NONE',CASE WHEN needs_repair THEN 'В исходном Excel город находится в разделе «Нужен ремонт».' END,now(),now() FROM imp_city s WHERE NOT EXISTS(SELECT 1 FROM cities c WHERE lower(c.name)=lower(s.name));")
stage('imp_company','key text PRIMARY KEY,legal_form text,short_name text,canonical_name text,status text',[(c['key'],c['legal_form'],c['short_name'],c['canonical_name'],c['status']) for c in x['companies']])
sql.append("INSERT INTO companies(legal_form,short_name,canonical_name,status,created_by_id,created_at,updated_at) SELECT legal_form,short_name,canonical_name,status,1,now(),now() FROM imp_company s WHERE NOT EXISTS(SELECT 1 FROM companies c WHERE lower(c.canonical_name)=s.key);")
# Preassign IDs in temporary maps, allowing all relations to be checked before commit.
sql.append("CREATE TEMP TABLE imp_company_map AS SELECT s.key,c.id FROM imp_company s JOIN companies c ON lower(c.canonical_name)=s.key;")
stage('imp_contact','company_key text,fio text,phone text,email text',[(c['company_key'],c['fio'],c['phone'],c['email']) for c in x['contacts']])
sql.append("INSERT INTO contacts(company_id,fio,phone,email,created_at,updated_at) SELECT m.id,s.fio,s.phone,s.email,now(),now() FROM imp_contact s JOIN imp_company_map m ON m.key=s.company_key WHERE NOT EXISTS(SELECT 1 FROM contacts c WHERE c.company_id=m.id AND c.phone IS NOT DISTINCT FROM s.phone AND c.email IS NOT DISTINCT FROM s.email);")
goals=sorted(set(d.get('goal','').strip() for d in x['deals'])-{''})
assert all(len(g)<=255 for g in goals)
stage('imp_goal','name text PRIMARY KEY',[(g,) for g in goals])
sql.append("INSERT INTO goal_options(name,active,created_at,updated_at) SELECT s.name,true,now(),now() FROM imp_goal s WHERE NOT EXISTS(SELECT 1 FROM goal_options g WHERE lower(g.name)=lower(s.name));")
dates=sorted(set(d['date'] for d in x['deals'])|set(s['date'] for s in x['shifts']))
weeks={}
for date in dates:
 d=dt.date.fromisoformat(date);y,w,_=d.isocalendar();start=d-dt.timedelta(days=d.weekday());end=start+dt.timedelta(days=6)
 weeks[(y,w)]=(y,w,str(start),str(end),f'{w}-я неделя: {start:%d.%m}–{end:%d.%m.%Y}')
stage('imp_week','iso_year int,iso_week_number int,start_date date,end_date date,label text',list(weeks.values()))
sql.append("INSERT INTO weeks(iso_year,iso_week_number,start_date,end_date,label,created_at,updated_at) SELECT iso_year,iso_week_number,start_date,end_date,label,now(),now() FROM imp_week ON CONFLICT(iso_year,iso_week_number) DO NOTHING;")
rows=[];targets=[];source_map=[]
for i,d in enumerate(x['deals'],1):
 src=d['source'];source_id=f"{tag}:{src['sheet_index']}:{src['row']}"
 note=f"Источник: {x['source_file']} / {src['sheet']} / строка {src['row']}."
 if d.get('manager'):note+=' Менеджер в Excel: '+d['manager']+'.'
 if d['date_basis']=='sheet_start':note+=' Дата взята из начала недели в названии листа (план на неделю).'
 if d.get('section'):note+=' Раздел: '+d['section']+'.'
 note+='\n'+d.get('comment','')
 # Preserve every original column including contacts, links, unknown funnel values and missing fields.
 note+='\n\nИсходные данные строки:\n'+'\n'.join(k+': '+v for k,v in src['cells'].items())
 if src['links']:note+='\nСсылки:\n'+'\n'.join(l.get('target') or l.get('location','') for l in src['links'])
 if d['duplicate_sources']:note+='\nТакже встречается: '+', '.join(s['sheet']+'!'+str(s['row']) for s in d['duplicate_sources'])
 action=d.get('action','')
 rows.append((i,d['company_key'],d['date'],d['service_type'],d.get('goal','').strip() or None,d['goal_achieved'],d['called_today'],d.get('next_call_date'),note,d['kanban_stage'],action[:500] or None,source_id))
 targets.extend((i,c) for c in d['target_cities'])
stage('imp_deal','source_no int PRIMARY KEY,company_key text,activity_date date,service_type text,goal text,goal_achieved boolean,called_today text,next_call_date date,comment text,kanban_stage text,next_action text,source_key text',rows)
sql += ["ALTER TABLE imp_deal ADD COLUMN id bigint;","UPDATE imp_deal SET id=nextval(pg_get_serial_sequence('deals','id'));", "INSERT INTO deals(id,company_id,service_type,goal_option_id,goal_achieved,called_today,next_call_date,comment,prospective,kanban_stage,activity_date,daily_sequence,week_id,created_by_id,document_intent,next_action,created_at,updated_at) SELECT s.id,m.id,s.service_type,g.id,s.goal_achieved,s.called_today,s.next_call_date,s.comment,s.kanban_stage='PROSPECTIVE',s.kanban_stage,s.activity_date,COALESCE((SELECT max(d.daily_sequence) FROM deals d WHERE d.created_by_id=1 AND d.activity_date=s.activity_date),0)+row_number() OVER(PARTITION BY s.activity_date ORDER BY s.source_no),w.id,1,'NONE',s.next_action,now(),now() FROM imp_deal s JOIN imp_company_map m ON m.key=s.company_key LEFT JOIN goal_options g ON lower(g.name)=lower(s.goal) JOIN weeks w ON w.iso_year=extract(isoyear from s.activity_date) AND w.iso_week_number=extract(week from s.activity_date);", "INSERT INTO audit_logs(entity_type,entity_id,field_name,new_value,author_id,created_at,updated_at) SELECT 'DEAL',id,'EXCEL_IMPORT',source_key,1,now(),now() FROM imp_deal;"]
stage('imp_target','source_no int,city text',targets)
sql.append("INSERT INTO deal_target_cities(deal_id,city_id) SELECT DISTINCT d.id,c.id FROM imp_target t JOIN imp_deal d ON d.source_no=t.source_no JOIN cities c ON lower(c.name)=lower(t.city);")
stage('imp_shift','city text,date date,status text,source text,raw text,color text,a4_sold boolean',[(s['city'],s['date'],s['status'],s['source'],s['raw'],s['color'],s['a4_sold']) for s in x['shifts']])
sql.append("INSERT INTO shifts(city_id,week_id,status,a4_sold,failure_reason,created_at,updated_at) SELECT c.id,w.id,s.status,s.a4_sold,'Excel: '||s.source||'; исходное значение: '||s.raw||'; цвет #'||s.color,now(),now() FROM imp_shift s JOIN cities c ON lower(c.name)=lower(s.city) JOIN weeks w ON w.iso_year=extract(isoyear from s.date) AND w.iso_week_number=extract(week from s.date) WHERE NOT EXISTS(SELECT 1 FROM shifts t WHERE t.city_id=c.id AND t.week_id=w.id);")
# Timelines preserve incomplete source rows without generating fake deals.
by_company=collections.defaultdict(list)
for c in x['company_sources']:by_company[c['key']].append(c['source'])
stage('imp_company_history','key text,note text',[(k,json.dumps({'import_key':tag,'sources':v},ensure_ascii=False)) for k,v in by_company.items()])
sql.append("INSERT INTO audit_logs(entity_type,entity_id,field_name,new_value,author_id,created_at,updated_at) SELECT 'COMPANY',m.id,'EXCEL_IMPORT',s.note,1,now(),now() FROM imp_company_history s JOIN imp_company_map m ON m.key=s.key;")
by_city=collections.defaultdict(list)
for c in x['city_notes']:by_city[c['city']].append(c)
stage('imp_city_history','name text,note text',[(k,json.dumps({'import_key':tag,'notes':v},ensure_ascii=False)) for k,v in by_city.items()])
sql.append("INSERT INTO audit_logs(entity_type,entity_id,field_name,new_value,author_id,created_at,updated_at) SELECT 'CITY',c.id,'EXCEL_IMPORT',s.note,1,now(),now() FROM imp_city_history s JOIN cities c ON lower(c.name)=lower(s.name);")
summary={k:len(x[k]) for k in ['cities','shifts','companies','deals','contacts','issues']};summary.update({'import_key':tag,'sha256':sha,'sheets':len(json.loads((out/'workbook.json').read_text())),'exact_duplicate_activities':x['exact_duplicate_activities'],'limitations':'Неполные строки сохранены в истории компаний и исходной книге; неизвестные города не создавались без категории; документы по закрытым ссылкам не скачивались.'})
sql.append(f"INSERT INTO stored_documents(document_type,title,original_file_name,stored_path,mime_type,notes,created_at,updated_at) VALUES ('OTHER','Управление наружкой — исходная книга импорта',{q(x['source_file'])},{q('/app/data/documents/outdoor-'+sha[:16]+'.xlsx')},'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',{q(json.dumps(summary,ensure_ascii=False))},now(),now());")
sql.append(f"INSERT INTO import_batches(source_type,source_name,created_by_id,imported_rows,skipped_rows,error_rows,details_json,created_at,updated_at) VALUES ('OUTDOOR_WORKBOOK',{q(x['source_file'])},1,{len(x['deals'])},{x['exact_duplicate_activities']},{len(x['issues'])},{q(json.dumps(summary,ensure_ascii=False))},now(),now());")
sql.append(f"DO $$ BEGIN IF (SELECT count(*) FROM imp_deal s JOIN deals d ON d.id=s.id)<>{len(x['deals'])} THEN RAISE EXCEPTION 'Deal count mismatch'; END IF; IF (SELECT count(*) FROM imp_company_map)<>{len(x['companies'])} THEN RAISE EXCEPTION 'Company map mismatch'; END IF; IF EXISTS (SELECT 1 FROM imp_deal s JOIN deals d ON d.id=s.id JOIN weeks w ON w.id=d.week_id WHERE d.activity_date NOT BETWEEN w.start_date AND w.end_date) THEN RAISE EXCEPTION 'Week date mismatch'; END IF; END $$;")
sql += ["COMMIT;","SELECT 'companies' entity,count(*) FROM companies UNION ALL SELECT 'contacts',count(*) FROM contacts UNION ALL SELECT 'deals',count(*) FROM deals UNION ALL SELECT 'cities',count(*) FROM cities UNION ALL SELECT 'shifts',count(*) FROM shifts;"]
(out/'import.sql').write_text('\n'.join(sql)+'\n')
(out/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2))
print(json.dumps(summary,ensure_ascii=False,indent=2))

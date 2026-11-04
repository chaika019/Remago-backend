"""Prepare a reviewable, source-linked import of the supplied workbook; no database writes."""
import collections, datetime as dt, hashlib, json, re, zipfile, xml.etree.ElementTree as E
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/import-review'
book=json.loads((OUT/'workbook.json').read_text())
styles=json.loads((OUT/'styles.json').read_text())
BASE=dt.date(1899,12,30)
def clean(v):return re.sub(r'\s+',' ',v or '').strip()
def col(k):return re.sub(r'\d','',k)
def values(row):return {col(k):v['value'].strip() for k,v in row['cells'].items()}
def fill(style):
 s=styles.get(str(style),{}).get('fill','');m=re.search(r'fgColor rgb="([A-F0-9]+)"',s)
 return m.group(1)[-6:] if m else 'FFFFFF'
def date(v,year=None):
 v=clean(v)
 try:
  n=float(v)
  if 43000<=n<=48000:return BASE+dt.timedelta(days=int(n))
 except ValueError:pass
 m=re.search(r'(?<!\d)(\d{1,2})[./](\d{1,2})(?:[./](20\d{2}))?',v)
 if m:
  try:return dt.date(int(m[3] or year),int(m[2]),int(m[1]))
  except (ValueError,TypeError):pass
 return None
def source(si,row):return {'sheet':book[si]['name'],'sheet_index':si,'row':row['row'],'cells':{k:v['value'] for k,v in row['cells'].items()},'links':[l for l in book[si]['links'] if re.search(r'\d+',l['ref']).group()==str(row['row'])]}
issues=[];cities=[];shifts=[];city_notes=[];company_rows=[];deals=[];contacts=[]
# The current network list supplies explicit categories and the repair section.
for row in book[0]['rows']:
 v=values(row);r=row['row']
 if v.get('C') in ['А','Б','В'] and 16<=r<=48:
  cities.append({'name':v['B'],'category':{'А':'A','Б':'B','В':'V'}[v['C']], 'needs_repair':r>=37,'source':source(0,row)})
def key(v):return re.sub(r'[^\w]','',v.lower().replace('ё','е'))
city_alias={key(c['name']):c['name'] for c in cities}
for alias,name in {'Боровляны':'Боровляны, Лесной','Лесной':'Боровляны, Лесной','Боровляны + Лесной':'Боровляны, Лесной','Марьина Горка':'М.Горка, Дружный, Свислочь','М Горка':'М.Горка, Дружный, Свислочь','Дружный':'М.Горка, Дружный, Свислочь','Свислочь':'М.Горка, Дружный, Свислочь','Марьина Горка + Дружный + Свислочь':'М.Горка, Дружный, Свислочь','Любань + Уречье':'Любань','Уречье':'Любань','Колодищи + Городище + Юхновка +Сухорукие':'Колодищи','Колодищи, Городище, Юхновка, Сухорукие':'Колодищи'}.items():city_alias[key(alias)]=name
unmatched=collections.Counter()
def target_cities(raw):
 if not raw:return []
 if key(raw) in city_alias:return [city_alias[key(raw)]]
 found=[]
 for part in re.split(r'[,;+\n/]',raw):
  k=key(part)
  if not k:continue
  if k in city_alias:found.append(city_alias[k])
  else:unmatched[clean(part)]+=1
 return sorted(set(found))
# Schedule: parse all cells, including blank cells whose fill indicates a status.
ns={'m':'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
with zipfile.ZipFile(ROOT/'docs/Управление наружкой.xlsx') as z:
 wb=E.fromstring(z.read('xl/workbook.xml'));rel={r.attrib['Id']:r.attrib['Target'] for r in E.fromstring(z.read('xl/_rels/workbook.xml.rels'))}
 for si in [0,1]:
  sh=list(wb.find('m:sheets',ns))[si];path=rel[sh.attrib['{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id']];path=path.lstrip('/') if path.startswith('/') else 'xl/'+path
  xml=E.fromstring(z.read(path));stored={r['row']:values(r) for r in book[si]['rows']}
  dates={};yr=2026 if si==0 else 2025;lastmonth=0
  for c,v in stored[13 if si==0 else 12].items():
   if c in ['A','B'] or (si==0 and c=='C'):continue
   d=date(v,yr)
   if d:
    if si==1 and d.month<lastmonth and not re.fullmatch(r'\d+\.0',v):yr+=1;d=date(v,yr)
    dates[c]=d;lastmonth=d.month
  for xr in xml.findall('m:sheetData/m:row',ns):
   rn=int(xr.attrib['r']);v=stored.get(rn,{})
   if (si==0 and not 16<=rn<=48) or (si==1 and not 13<=rn<=44):continue
   name=city_alias.get(key(v.get('B','')))
   if not name:continue
   for cell in xr:
    ref=cell.attrib['r'];c=col(ref);raw=v.get(c,'');color=fill(cell.attrib.get('s'));status=None
    if c not in dates:
     if c not in ['A','B','C'] and raw:issues.append({'kind':'schedule_missing_date','sheet':book[si]['name'],'cell':ref,'value':raw})
     continue
    if si==0:
     t=clean(raw).lower()
     status={'смена и продажа':'SHIFT_AND_SALE','не состоялась':'NOT_OCCURRED','-':'MINUS','смена и уведомление':'SHIFT_AND_NOTIFICATION','плюс/минус':'PLUS_MINUS','автосмена':'AUTO_SHIFT','авто и смена':'AUTO_AND_SHIFT','???':'UNDEFINED_9','активно продавать':'ACTIVE_SELL','продавать':'ACTIVE_SELL'}.get(t)
     if t=='смена':status={'548DD4':'SHIFT_AND_SALE','92D050':'SHIFT_AND_NOTIFICATION','FFFFFF':'WHITE_SHIFT'}.get(color)
     if raw and not status:city_notes.append({'city':name,'note':f"{book[si]['name']}!{ref}, {dates[c]}: {raw}",'kind':'schedule_task'})
    else:
     # Interpret only exact legend colors, preserving ambiguous cells as notes.
     if raw=='+':status={'FFFFFF':'WHITE_SHIFT','92D050':'SHIFT_AND_NOTIFICATION','FFFF00':'PLUS_MINUS'}.get(color)
     elif raw=='-':status='MINUS'
     elif raw=='?' and color=='FFFF00':status='AUTO_SHIFT'
     elif color=='FF0000':status='NOT_OCCURRED'
     elif color=='548DD4':status='SHIFT_AND_SALE'
     elif color=='92D050':status='SHIFT_AND_NOTIFICATION'
     if raw:city_notes.append({'city':name,'note':f"{book[si]['name']}!{ref}, {dates[c]}, цвет #{color}: {raw}",'kind':'schedule_source'})
    if status:shifts.append({'city':name,'date':str(dates[c]),'status':status,'source':f"{book[si]['name']}!{ref}",'raw':raw,'color':color,'a4_sold':status=='WHITE_SHIFT'})
# Company normalization: no fuzzy merges or invented legal forms.
forms={'ООО':'OOO','ЗАО':'ZAO','ОАО':'OAO','ЧУП':'CHUP','УП':'UP','ИП':'IP'}
def company(raw):
 if clean(raw).lower().startswith(('список ваших компаний','1-я группа городов','2-я группа городов','города на ','наименование ')):return None
 if re.search(r'связаться с|надо перезвонить|нужно позвонить',raw,re.I):return None
 # Contact-rich cells are reviewed instead of inventing a legal company name.
 if len(clean(raw))>100 and re.search(r'\d{7,}',raw):return None
 if '@' in raw or re.search(r'(?:\+?375|80)[\s(]*\d{2}[\s)]*\d{3}',raw):return None
 name=clean(raw).replace('«',' ').replace('»',' ').replace('"',' ');name=clean(name)
 m=re.match(r'^(ООО|ЗАО|ОАО|ЧУП|УП|ИП)\b\s*(.*)$',name,re.I)
 suffix=re.match(r'^(.*?)\s+(ООО|ЗАО|ОАО|ЧУП|УП|ИП)$',name,re.I)
 if m:form=m[1].upper();short=clean(m[2])
 elif suffix:form=suffix[2].upper();short=clean(suffix[1])
 else:form='Организация';short=name
 if not short or len(short)>255:return None
 canonical=(form+' '+short) if form=='ИП' else f'{form} «{short}»'
 return {'key':canonical.lower(),'legal_form':forms.get(form,'OTHER'),'short_name':short,'canonical_name':canonical,'raw':raw}
def header_map(row):
 result={}
 for c,h in values(row).items():
  h=clean(h).lower()
  field=None
  if 'наименование заказчика' in h:field='company'
  elif 'целевые города' in h:field='cities'
  elif h=='клиент':field='client_status'
  elif 'рекламируемые услуги' in h:field='service'
  elif 'достигнута' in h:field='achieved'
  elif h.startswith('цель'):field='goal'
  elif 'дозвонил' in h:field='called'
  elif h.startswith('дата перезвона'):field='next_call'
  elif 'комментарий' in h:field='comment'
  elif 'лпр' in h:field='contact'
  elif h.startswith('что сделать'):field='action'
  if field:result[field]=c
 return result
layout18={'company':'F','cities':'J','client_status':'K','service':'L','goal':'M','achieved':'N','called':'O','next_call':'P','comment':'R','contact':'I','action':'H'}
weekly_stats=[]
for si,s in enumerate(book):
 if si<3 or si in [6,7,67,73,74]:continue
 hm=header_map(s['rows'][0]);hm.setdefault('company','B');hm.setdefault('comment','J')
 year=2025 if si<=22 or si>=69 else 2026
 fallback=date(s['name'],year);cur=None;manager='Роман' if 'Роман' in s['name'] else None;section=None;count=0
 for row in s['rows']:
  rn=row['row'];v=values(row)
  if rn<=3:continue
  h=hm
  # Some pasted blocks use the newer 18-column layout beneath an older header.
  if hm['company']!='F' and v.get('L') in ['Вакансии','Продвижение'] and v.get('F') and any(c in v for c in ['R','P','O']):h=layout18
  raw=v.get(h['company'],'')
  marker_cols={'A',h['company']}
  if h['company']=='F':marker_cols.add('G')
  marker=None
  for c in marker_cols:
   text=v.get(c,'');d=date(text,year)
   if d and (re.fullmatch(r'\d{5}(?:\.0)?',text) or (not raw and len(text)<100)):
    marker=d;section=text;break
  if marker:
   if fallback and marker.year!=fallback.year:
    adjusted=marker.replace(year=fallback.year)
    if 0<=(adjusted-fallback).days<=6:
     issues.append({'kind':'corrected_section_year','sheet':s['name'],'row':rn,'original':str(marker),'used':str(adjusted)})
     marker=adjusted
   cur=marker
   if not raw or raw==v.get('A') or date(raw,year):continue
  if rn<=4:continue
  if not raw:
   text=v.get('G','')
   if text:
    section=text
    for n in ['Светлана','Дмитрий','Станислав','Иван','Роман']:
     if n.lower() in text.lower():manager=n;break
   continue
  if date(raw,year) and re.fullmatch(r'\d{5}(?:\.0)?',raw):continue
  if raw.lower().startswith(('цветовые','города на','наименование заказчика','список на','план на','постоянные')):continue
  if re.fullmatch(r'[\d.\s]+',raw):continue
  co=company(raw)
  if not co:issues.append({'kind':'invalid_company','source':source(si,row)});continue
  src=source(si,row);at=cur or fallback
  data={field:v.get(c,'') for field,c in h.items()}
  data.update({'company_key':co['key'],'source':src,'manager':manager,'section':section,'date':str(at) if at else None,'date_basis':'section' if cur else 'sheet_start'})
  company_rows.append(dict(co,status={'новый':'NEW','возврат':'RETURN','продление':'RENEWAL'}.get(data.get('client_status','').lower(),'NEW'),date=str(at) if at else '',source=src,contact=data.get('contact','')))
  service={'вакансии':'VACANCIES','продвижение':'PROMOTION'}.get(clean(data.get('service','')).lower())
  if not service or at is None:
   issues.append({'kind':'company_only_missing_service_or_date','company':raw,'source':src});continue
  data['service_type']=service
  data['target_cities']=target_cities(data.get('cities',''))
  data['next_call_date']=str(date(data.get('next_call',''),at.year)) if date(data.get('next_call',''),at.year) else None
  status_color=fill(row['cells'].get(h['company']+str(rn),{}).get('style'))
  data['kanban_stage']={'00FFFF':'PROSPECTIVE','00FF00':'GOING_TO_DEAL'}.get(status_color,'IN_WORK')
  data['called_today']='YES' if clean(data.get('called','')).lower().startswith('да') else 'NO' if clean(data.get('called','')).lower()=='нет' else 'NOT_CALLED'
  data['goal_achieved']=clean(data.get('achieved','')).lower()=='да'
  deals.append(data);count+=1
 weekly_stats.append({'sheet':s['name'],'deals':count})
# Lead/contact lists have no reliable activity dates; retain source in company timeline.
for si in [6,7,67]:
 for row in book[si]['rows'][1:]:
  v=values(row);raw=v.get('E' if si==67 else 'D','');co=company(raw)
  if not raw or not co:continue
  company_rows.append(dict(co,status='NEW',date='',source=source(si,row),contact='\n'.join(v.get(c,'') for c in (['F','G','J'] if si==67 else ['E'])),manager='Роман' if si==67 else v.get('F')))
# Resolve unqualified names only if exactly one explicitly qualified form exists.
qualified=collections.defaultdict(set)
for c in company_rows:
 if c['legal_form']!='OTHER':qualified[key(c['short_name'])].add(c['key'])
remap={c['key']:next(iter(qualified[key(c['short_name'])])) for c in company_rows if c['legal_form']=='OTHER' and len(qualified[key(c['short_name'])])==1}
companies={}
for c in sorted(company_rows,key=lambda x:x['date']):
 old=c['key'];c['key']=remap.get(old,old)
 if c['key'] not in companies or old==c['key']:
  prev=companies.get(c['key'],{});companies[c['key']]={k:c[k] for k in ['key','legal_form','short_name','canonical_name','status']}
  if prev.get('status') and not c['date']:companies[c['key']]['status']=prev['status']
for d in deals:d['company_key']=remap.get(d['company_key'],d['company_key'])
# Only exact duplicate activity content on the same date is collapsed; retain all sources.
unique={}
for d in deals:
 sig=json.dumps({k:d[k] for k in ['company_key','date','service_type','cities','goal','achieved','called','next_call','comment','contact','action'] if k in d},ensure_ascii=False,sort_keys=True)
 if sig in unique:unique[sig]['duplicate_sources'].append(d['source'])
 else:d['duplicate_sources']=[];unique[sig]=d
dedup=len(deals)-len(unique);deals=list(unique.values())
# Source contact strings are never discarded; extract phone/email as separate unnamed contacts.
for c in company_rows:
 raw=c.get('contact','');src=c['source'];raw=raw.strip()
 if re.fullmatch(r'\d\.\d+E\+?\d+',raw,re.I):raw=str(int(float(raw)))
 phones=set()
 for match in re.finditer(r'(?<!\d)(?:\+?375|80)[\s(-]*\d{2}[\s)-]*\d{3}[\s-]*\d{2}[\s-]*\d{2}(?!\d)',raw):
  num=re.sub(r'\D','',match.group());num='375'+num[2:] if num.startswith('80') else num
  phones.add('+'+num)
 emails=set(re.findall(r'[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}',raw))
 for phone in sorted(phones):contacts.append({'company_key':c['key'],'fio':'Контакт из Excel (телефон)','phone':phone,'email':None})
 for email in sorted(emails):contacts.append({'company_key':c['key'],'fio':'Контакт из Excel (email)','phone':None,'email':email.lower()})
contacts=list({(c['company_key'],c['phone'],c['email']):c for c in contacts}.values())
# Preserve worker directory and daily operations without inventing user accounts or shift states.
for si in [2,73]:
 for row in book[si]['rows']:
  if row['row']<(5 if si==2 else 3):continue
  v=values(row);names=target_cities(v.get('B',''))
  for name in names:city_notes.append({'city':name,'kind':'daily_operations' if si==2 else 'shift_workers','note':json.dumps(source(si,row),ensure_ascii=False)})
result={'source_file':'Управление наружкой.xlsx','sha256':hashlib.sha256((ROOT/'docs/Управление наружкой.xlsx').read_bytes()).hexdigest(),'owner_id':1,'owner_email':'egorchuiko7@gmail.com','cities':cities,'shifts':shifts,'companies':list(companies.values()),'company_sources':company_rows,'deals':deals,'contacts':contacts,'city_notes':city_notes,'issues':issues,'unmatched_cities':dict(unmatched.most_common()),'sheet_summary':weekly_stats,'exact_duplicate_activities':dedup}
(OUT/'candidates.json').write_text(json.dumps(result,ensure_ascii=False,indent=2))
print(json.dumps({k:len(result[k]) for k in ['cities','shifts','companies','company_sources','deals','contacts','city_notes','issues']},ensure_ascii=False))
print('exact duplicate activities:',dedup)
print('issues:',collections.Counter(i['kind'] for i in issues))
print('unmatched city labels:',unmatched.most_common(15))
print('date range:',min(d['date'] for d in deals),max(d['date'] for d in deals))

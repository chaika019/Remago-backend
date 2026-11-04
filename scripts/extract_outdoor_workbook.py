import zipfile,xml.etree.ElementTree as E,json,posixpath
from pathlib import Path
ns={'m':'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
with zipfile.ZipFile('docs/Управление наружкой.xlsx') as z:
 strings=[''.join(x.itertext()) for x in E.fromstring(z.read('xl/sharedStrings.xml'))]
 rel={r.attrib['Id']:r.attrib['Target'] for r in E.fromstring(z.read('xl/_rels/workbook.xml.rels'))}
 out=[]
 for s in E.fromstring(z.read('xl/workbook.xml')).find('m:sheets',ns):
  target=rel[s.attrib['{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id']];path=target.lstrip('/') if target.startswith('/') else 'xl/'+target
  root=E.fromstring(z.read(path));rows=[]
  for row in root.findall('m:sheetData/m:row',ns):
   cells={}
   for c in row:
    v=c.find('m:v',ns);val=v.text if v is not None else ''
    if c.attrib.get('t')=='s' and val:val=strings[int(val)]
    if c.attrib.get('t')=='inlineStr':val=''.join(c.find('m:is',ns).itertext())
    if val and val.strip():cells[c.attrib['r']]={'value':val,'style':c.attrib.get('s'),'type':c.attrib.get('t'),'formula':c.findtext('m:f',default=None,namespaces=ns)}
   if cells: rows.append({'row':int(row.attrib['r']),'cells':cells})
  links=[]
  rp=posixpath.dirname(path)+'/_rels/'+posixpath.basename(path)+'.rels'
  lr={r.attrib['Id']:r.attrib['Target'] for r in E.fromstring(z.read(rp))} if rp in z.namelist() else {}
  for h in root.findall('m:hyperlinks/m:hyperlink',ns):links.append(dict(h.attrib,target=lr.get(h.attrib.get('{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id'))))
  out.append({'name':s.attrib['name'],'rows':rows,'links':links,'merges':[m.attrib['ref'] for m in root.findall('m:mergeCells/m:mergeCell',ns)]})
 Path('docs/import-review/workbook.json').write_text(json.dumps(out,ensure_ascii=False,indent=2))
 for i,s in enumerate(out):print(i,s['name'], 'nonempty rows',len(s['rows']),'links',len(s['links']))

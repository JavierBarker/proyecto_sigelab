"""Configura los tres componentes sin publicar credenciales; ejecutar una sola vez."""
from pathlib import Path
import base64
import getpass
import json
import secrets

ROOT = Path(__file__).resolve().parents[1]

def ask(label, default):
    return input(f'{label} [{default}]: ').strip() or default

def property_value(value):
    return value.replace('\\', '\\\\').replace('\n', '\\n').replace('\r', '\\r').replace(' ', '\\ ')

def main():
    files = [ROOT/'config/application.properties', ROOT/'facial-service/.env', ROOT/'.env']
    if any(p.exists() for p in files):
        raise SystemExit('Ya hay configuración local. Edítela manualmente; no se reemplazan claves ni datos existentes.')
    print('SIGELAB: configuración inicial. Docker incluido usa 14330; SQL Server nativo usa su puerto TCP real.')
    host = ask('Host de SQL Server', 'localhost')
    port = ask('Puerto TCP', '14330')
    if not port.isdigit() or not 1 <= int(port) <= 65535:
        raise SystemExit('Puerto inválido.')
    name = ask('Base de datos (los scripts crean SIGELAB)', 'SIGELAB')
    if name != 'SIGELAB':
        raise SystemExit('Esta entrega usa SIGELAB. Adapte primero los scripts si requiere otro nombre.')
    user = ask('Usuario SQL Server', 'sa')
    password = getpass.getpass('Contraseña SQL Server (no se mostrará): ')
    if len(password) < 12 or any(c in password for c in '\r\n'):
        raise SystemExit('Use una contraseña de al menos 12 caracteres, sin saltos de línea.')
    token = secrets.token_urlsafe(48)
    key = base64.b64encode(secrets.token_bytes(32)).decode('ascii')
    java_values = {'db.host':host,'db.port':port,'db.name':name,'db.user':user,'db.password':password,
                   'db.encrypt':'true','db.trustServerCertificate':'true','facial.url':'http://127.0.0.1:8765','facial.token':token}
    files[0].parent.mkdir(parents=True, exist_ok=True)
    files[0].write_text(''.join(k+'='+property_value(v)+'\n' for k,v in java_values.items()),encoding='utf-8')
    template=(ROOT/'facial-service/.env.example').read_text(encoding='utf-8')
    values={'DB_HOST':host,'DB_PORT':port,'DB_NAME':name,'DB_USER':user,'DB_PASSWORD':password,
            'FACIAL_API_TOKEN':token,'FACIAL_ENCRYPTION_KEY':key}
    lines=[]
    for line in template.splitlines():
        keyname=line.partition('=')[0]
        lines.append(keyname+'='+json.dumps(values[keyname],ensure_ascii=False) if keyname in values else line)
    files[1].write_text('\n'.join(lines)+'\n',encoding='utf-8')
    compose_password=password.replace("'", "\\'")
    files[2].write_text("MSSQL_SA_PASSWORD='"+compose_password+"'\n",encoding='utf-8')
    print('Configuración creada. Las claves no se muestran. No suba los archivos .env ni application.properties a Git.')
    print('Docker crea el usuario sa: si eligió otro usuario, créelo antes en su SQL Server existente.')

if __name__=='__main__':
    main()

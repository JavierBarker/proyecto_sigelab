# Facial Service

Servicio local FastAPI de autenticación facial para SIGELAB. La cámara se abre en el equipo local, Java se comunica por HTTP en `127.0.0.1` y SQL Server almacena únicamente el embedding cifrado y la auditoría.

La guía general de instalación por sistema operativo está en el [README raíz](../README.md). Este documento se concentra en el componente biométrico.

## Arquitectura interna

```text
scripts / wrappers
        │
        ▼
FastAPI → FaceService → FacialRepository → SQL Server
             │                  ├── PerfilFacial
             │                  └── AuditoriaFacial
             ├── FaceEngine → InsightFace / ArcFace → ONNX Runtime
             ├── CameraManager → OpenCV → webcam local
             └── LivenessSession / Quality checks
```

El modelo predeterminado es `buffalo_l`. InsightFace descarga el modelo durante la primera carga si no existe en su caché de usuario y lo reutiliza en las siguientes ejecuciones. El modelo no se guarda en este repositorio.

## Versiones y dependencias

Python soportado oficialmente: 3.11 y 3.12. No se consideran soportados Python 3.13 ni 3.14. `requirements.txt` mantiene una fuente común para Windows, macOS Intel, macOS arm64 y Linux x86_64, con un marker para el wheel específico de ONNX Runtime en macOS arm64.

La instalación usa `onnxruntime` CPU, no `onnxruntime-gpu`, por lo que no requiere GPU. `ONNX_PROVIDERS=auto` selecciona providers disponibles en runtime y añade `CPUExecutionProvider` como fallback. La disponibilidad de wheels nativas para Apple Silicon debe comprobarse en el equipo con `python -m scripts.doctor`.

Dependencias relevantes:

- `insightface==0.7.3` y ArcFace.
- `onnxruntime==1.20.1` CPU en Windows y Linux; macOS Intel y arm64 usan `onnxruntime==1.19.2` porque es la versión fijada con wheel universal2.
- `opencv-python==4.10.0.84` para webcam y preview.
- `numpy==1.26.4`, `pyodbc==5.2.0`, FastAPI, Pydantic, `cryptography` y `python-dotenv`.

## Variables de entorno

En una instalación manual, copie la plantilla y complete los valores locales:

```bash
cp .env.example .env
```

```powershell
Copy-Item .env.example .env
```

En macOS Apple Silicon, `./setup-facial-macos.sh` crea o conserva este archivo
automáticamente a partir de la configuración Java y del SQL Server local ya
existente; también genera una clave si aún no existe. La contraseña y la clave
no se muestran en consola. En Windows/Linux, complete `.env` manualmente.

Variables de SQL Server:

```text
DB_HOST=127.0.0.1
DB_PORT=1433
DB_NAME=SIGELAB
DB_USER=sa
DB_PASSWORD=<secreto-local>
DB_DRIVER=ODBC Driver 18 for SQL Server
DB_ENCRYPT=yes
DB_TRUST_SERVER_CERTIFICATE=yes
```

Variables del servicio y cámara:

```text
FACIAL_HOST=127.0.0.1
FACIAL_PORT=8765
CAMERA_INDEX=
CAMERA_NAME_HINT=
CAMERA_WIDTH=1280
CAMERA_HEIGHT=720
SHOW_PREVIEW=true
CAMERA_GUIDE=true
FACIAL_MODE=completo
ONNX_PROVIDERS=auto
```

`CAMERA_GUIDE=true` muestra la cámara como una app de celular: fondo blanco, el rostro dentro de un círculo con un anillo turquesa que avanza con las muestras (naranja cuando hay que corregir algo, rojo si falla) y abajo una sola instrucción, por ejemplo "Permanezca quieto, con su rostro en el centro del círculo". Con `false` vuelve la vista clásica con el panel de texto.

`FACIAL_MODE=completo` (por defecto) registra el rostro de frente, de perfil izquierdo y de perfil derecho, y pide girar la cabeza como prueba de vida. `FACIAL_MODE=simple` registra solo de frente (10 muestras) y no pide giros; sirve cuando la cámara de la laptop no logra completar los perfiles.

Variables del modelo y calibración:

```text
FACE_MODEL=buffalo_l
FACE_MATCH_THRESHOLD=0.55
FACE_MIN_SIZE=120
BLUR_THRESHOLD=40
BRIGHTNESS_MIN=30
BRIGHTNESS_MAX=220
MAX_YAW=28
MAX_PITCH=25
ENROLL_SAMPLE_COUNT=20
VERIFY_SAMPLE_COUNT=7
ENROLL_TIMEOUT_SECONDS=60
VERIFY_TIMEOUT_SECONDS=20
LIVENESS_TIMEOUT_SECONDS=15
SAMPLE_INTERVAL_SECONDS=0.15
```

`FACE_MATCH_THRESHOLD=0.55` es un valor inicial. Calibre con varias verificaciones genuinas y pruebas con otra persona en la iluminación y webcam reales; no es un umbral universal.

### FACIAL_ENCRYPTION_KEY

Genere una clave sin modificar archivos:

```bash
python -m scripts.generate_key
```

Pegue la salida base64 en `.env`. **La clave debe persistir:** si cambia después del enrollment, los embeddings existentes no podrán descifrarse. No cree una clave nueva automáticamente en cada inicio y nunca publique `.env`.

## API

- `GET /health` — estado `ok/degraded`, modelo, `modelLoaded`, base, plataforma y arquitectura. No expone secretos, embeddings ni rutas.
- `GET /face/status/{userId}` — estado del perfil sin devolver el embedding.
- `POST /face/enroll` — body `{ "userId": 1, "replace": false }`.
- `POST /face/verify` — body `{ "userId": 1 }`.
- `DELETE /face/profile/{userId}` — body `{ "adminUserId": 1 }`, requiere ADMIN activo.

## Enrollment y verification

Enrollment y verification exigen exactamente un rostro, detección confiable, tamaño mínimo, iluminación y nitidez aceptables, pose dentro de límites y prueba activa de liveness. Enrollment usa 20 muestras por defecto y variedad de posiciones; verification usa 7. Los frames no se persisten.

El liveness exige primero una pose neutral y después un desafío aleatorio de giro o parpadeo cuando el modelo ofrece landmarks suficientes. Es una mitigación académica y no un sistema anti-spoofing comercial.

```bash
python -m scripts.find_user admin
python -m scripts.interactive_face enroll --username admin
python -m scripts.interactive_face verify --username admin
python -m scripts.enroll_user --user-id 1 --replace
```

El comando por username resuelve el ID en SQL Server. Los wrappers aceptan un
username o un ID: `./enroll-user.sh admin`, `./enroll-user.sh 1` y
`./enroll-user.sh admin --replace`, `.\enroll-user.ps1 1`; ninguno inicia enrollment automáticamente sin la API
arriba y la cámara autorizada.

## Migración

```bash
python -m scripts.migrate
```

La ruta se obtiene desde `Path(__file__).resolve()`, no desde una ruta fija. La migración crea de forma idempotente `PerfilFacial`, `AuditoriaFacial` e índice, y no borra información. El esquema principal de la aplicación debe existir antes porque las tablas faciales referencian `Usuario`.

## Cámara cross-platform

`CAMERA_INDEX` es configurable. El servicio prueba el backend recomendado y luego `cv2.VideoCapture(index)`:

- Windows: DirectShow, Media Foundation y fallback.
- macOS: AVFoundation y fallback.
- Linux: V4L2 y fallback.

En macOS configure `System Settings → Privacy & Security → Camera` para Terminal, iTerm, IntelliJ o Python según el proceso que abra la cámara. En Linux, revise `/dev/video0` y el grupo `video`. `CAMERA_INDEX` explícito tiene prioridad; si queda vacío, se prefieren nombres integrados y `CAMERA_NAME_HINT` puede orientar la selección. El diagnóstico abre y libera la webcam; no toma enrollment.

Las sesiones interactivas usan `scripts.interactive_face` en un proceso hijo cuyo
`MainThread` controla OpenCV HighGUI. La API invoca ese mismo runner mediante
`subprocess`, pasa solamente modo, usuario y `replace`, y recibe un único objeto
JSON por stdout; logs y warnings van por stderr. `PREVIEW_UNAVAILABLE`,
`CAMERA_FRAME_UNAVAILABLE`, `TIMEOUT_FRONT`, `TIMEOUT_LEFT` y `TIMEOUT_RIGHT`
terminan la sesión sin guardar un perfil parcial.

## Scripts

```text
setup-facial.ps1       Windows: venv, pip y doctor
setup-facial-macos.sh  macOS: toolchain arm64, venv, modelo, migración y doctor
setup-facial-linux.sh  Linux: venv, pip y doctor
start-facial.ps1       Windows PowerShell
start-facial.bat       Windows CMD
start-facial.sh        macOS/Linux
enroll-user.ps1        Enrollment Windows
enroll-user.sh         Enrollment macOS/Linux
interactive_face      Runner interactivo con preview, overlay y liveness
preview_test           Prueba aislada de HighGUI y webcam
list_cameras           Enumeración y selección recomendada de cámaras
demo-check.ps1/.sh     Doctor + health, sin enrollment
```

Los wrappers localizan su propia carpeta y usan `.venv` directamente. Si los permisos ejecutables no se conservan al clonar:

```bash
chmod +x *.sh
```

## Doctor

```bash
python -m scripts.doctor
```

Es principalmente read-only. Revisa Python 3.11/3.12, virtualenv, imports, OpenCV GUI, InsightFace, ONNX providers, ODBC Driver 18, SQL Server, base, tablas, modelo en caché, cámara, frames, cámara seleccionada, `imshow`, clave y puerto 8765. No imprime contraseñas ni la clave, no registra rostros, no elimina perfiles y no altera usuarios. El modelo ausente se muestra como `WAIT` en una ejecución normal; el setup de macOS usa `--prepare-model` para descargarlo y validarlo antes de terminar.

Para comprobar una API ya iniciada:

```bash
python -m scripts.health_check
./demo-check.sh
```

## Seguridad, privacidad y limitaciones

No se guardan JPG, PNG ni BMP. Los frames solo viven en memoria. El embedding normalizado se cifra con AES-GCM y se almacena en `VARBINARY(MAX)`; `.env`, passwords, claves y modelos quedan fuera del repositorio. La auditoría no contiene imágenes ni embeddings completos.

La implementación usa webcam RGB. No equivale a Apple Face ID: no tiene TrueDepth, IR, mapeo 3D, Secure Enclave ni anti-spoofing comercial. Es un demo académico moderno de integración biométrica.

## Troubleshooting

- **API inaccesible:** ejecute el wrapper de start, confirme `.env`, `FACIAL_HOST=127.0.0.1` y `FACIAL_PORT=8765`.
- **Webcam no disponible:** permisos de macOS, dispositivo `/dev/video0`, `CAMERA_INDEX` y aplicaciones que puedan estar usando la cámara.
- **ODBC no encontrado:** instale Microsoft ODBC Driver 18 y `unixODBC` según el SO; confirme `pyodbc.drivers()`.
- **SQL Server no disponible:** valide contenedor/servicio, puerto, base y variables `DB_*`.
- **Modelo no descarga:** revise conectividad, espacio y permisos de la caché de InsightFace.
- **Import error:** use Python 3.11/3.12 y reinstale requirements en un `.venv` limpio.
- **Build de InsightFace:** `insightface==0.7.3` se publica como source distribution. En macOS instale Command Line Tools (`xcode-select --install`); en Windows/Linux instale las herramientas de compilación C/C++ requeridas por su Python si pip no puede construir el paquete.
- **Provider ONNX:** mantenga `ONNX_PROVIDERS=auto` y compruebe `onnxruntime.get_available_providers()`.
- **Perfil no registrado:** use `find_user` y luego `enroll_user` con la API arriba.
- **Embedding ilegible:** restaure la misma `FACIAL_ENCRYPTION_KEY`; no genere otra en cada inicio.
- **Puerto ocupado:** use doctor, detenga el proceso responsable o cambie el puerto junto con la URL de Java.

## Tests

```bash
pytest
```

Las pruebas actuales cubren cifrado, similitud, liveness, schemas y comportamiento de servicio sin webcam física. Enrollment, verification, permisos de cámara y SQL Server requieren validación del entorno real.

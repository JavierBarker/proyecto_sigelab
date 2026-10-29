class FacialServiceError(Exception):
    def __init__(self, message: str, status_code: int = 400, code: str = "FACIAL_ERROR"):
        super().__init__(message)
        self.message = message
        self.status_code = status_code
        self.code = code


class CameraUnavailableError(FacialServiceError):
    def __init__(self, message: str = "No se pudo acceder a la cámara."):
        super().__init__(message, 503, "CAMARA_NO_DISPONIBLE")


class CameraFrameUnavailableError(FacialServiceError):
    def __init__(self, message: str = "La cámara dejó de entregar imágenes."):
        super().__init__(message, 503, "CAMERA_FRAME_UNAVAILABLE")


class CameraBusyError(FacialServiceError):
    def __init__(self):
        super().__init__("Camera is currently in use.", 409, "CAMARA_EN_USO")


class CaptureCancelledError(FacialServiceError):
    def __init__(self):
        super().__init__("Captura cancelada por el usuario.", 400, "CAPTURA_CANCELADA")


class CaptureTimeoutError(FacialServiceError):
    def __init__(self, message: str, code: str = "CAPTURA_TIMEOUT"):
        super().__init__(message, 408, code)


class PreviewUnavailableError(FacialServiceError):
    def __init__(self, message: str = "No fue posible mostrar la ventana de cámara."):
        super().__init__(message, 503, "PREVIEW_UNAVAILABLE")


class ModelUnavailableError(FacialServiceError):
    def __init__(self, message: str = "El modelo facial no está disponible."):
        super().__init__(message, 503, "MODELO_NO_DISPONIBLE")

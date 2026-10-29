package proyecto_sigelab.integration;

import com.google.zxing.*;
import com.google.zxing.client.j2se.*;
import com.google.zxing.common.HybridBinarizer;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import proyecto_sigelab.exception.BusinessException;

public final class QrService {
  public void generate(String code, Path destination) {
    try {
      var matrix = new MultiFormatWriter().encode(code, BarcodeFormat.QR_CODE, 320, 320);
      MatrixToImageWriter.writeToPath(matrix, "PNG", destination);
    } catch (Exception e) {
      throw new BusinessException("No se pudo guardar el QR.");
    }
  }

  public String read(Path source) {
    try {
      var image = ImageIO.read(source.toFile());
      if (image == null) throw new IllegalArgumentException();
      return new MultiFormatReader()
          .decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image))))
          .getText();
    } catch (Exception e) {
      throw new BusinessException("No se encontró un QR válido en la imagen.");
    }
  }
}

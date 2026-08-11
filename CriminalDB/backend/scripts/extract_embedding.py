import sys
import os
import urllib.request
import cv2
import numpy as np
import onnxruntime as ort

# ---------------------------------------------------------------------------
# Models
#   - Detection : YuNet (face_detection_yunet_2023mar.onnx) - fast CNN face
#     detector bundled with OpenCV's model zoo. Gives a bounding box plus the
#     5 facial landmarks (eyes, nose tip, mouth corners) needed for alignment.
#   - Recognition : ArcFace (w600k_mbf.onnx from InsightFace's buffalo_sc
#     pack) - a MobileFaceNet backbone trained with the ArcFace
#     (additive angular margin) loss on WebFace600K. Produces a 512-d
#     embedding; cosine similarity between two embeddings measures how
#     likely two photos are of the same person.
# ---------------------------------------------------------------------------

YUNET_URL = "https://github.com/opencv/opencv_zoo/raw/main/models/face_detection_yunet/face_detection_yunet_2023mar.onnx"
ARCFACE_URL = "https://github.com/deepinsight/insightface/releases/download/v0.7/buffalo_sc.zip"

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
MODELS_DIR = os.path.join(SCRIPT_DIR, "models")
YUNET_PATH = os.path.join(MODELS_DIR, "face_detection_yunet_2023mar.onnx")
ARCFACE_PATH = os.path.join(MODELS_DIR, "w600k_mbf.onnx")

# Standard ArcFace 5-point reference template for a 112x112 aligned crop
# (left eye, right eye, nose tip, left mouth corner, right mouth corner -
# "left"/"right" as seen in the image, i.e. not the subject's anatomical
# left/right). This is the same template InsightFace's own alignment code
# uses, and it lines up directly with the landmark order YuNet returns.
ARCFACE_DST = np.array([
    [38.2946, 51.6963],
    [73.5318, 51.5014],
    [56.0252, 71.7366],
    [41.5493, 92.3655],
    [70.7299, 92.2041],
], dtype=np.float32)


def download_file(url, dest_path):
    print(f"Downloading {url} to {dest_path}...", file=sys.stderr)
    os.makedirs(os.path.dirname(dest_path), exist_ok=True)
    tmp_path = dest_path + ".part"
    urllib.request.urlretrieve(url, tmp_path)
    os.replace(tmp_path, dest_path)
    print("Download complete.", file=sys.stderr)


def ensure_models():
    if not os.path.exists(YUNET_PATH):
        download_file(YUNET_URL, YUNET_PATH)

    if not os.path.exists(ARCFACE_PATH):
        # The recognition model ships inside InsightFace's small "buffalo_sc"
        # zip alongside a detector we don't need, so fetch the zip to a temp
        # file and extract just the recognition weights.
        import zipfile
        import tempfile

        os.makedirs(MODELS_DIR, exist_ok=True)
        with tempfile.NamedTemporaryFile(suffix=".zip", delete=False) as tmp:
            tmp_zip_path = tmp.name
        try:
            download_file(ARCFACE_URL, tmp_zip_path)
            with zipfile.ZipFile(tmp_zip_path) as zf:
                with zf.open("w600k_mbf.onnx") as src, open(ARCFACE_PATH + ".part", "wb") as dst:
                    dst.write(src.read())
            os.replace(ARCFACE_PATH + ".part", ARCFACE_PATH)
        finally:
            if os.path.exists(tmp_zip_path):
                os.remove(tmp_zip_path)


def align_face(img, landmarks_5, image_size=112):
    """Warp the detected face to a canonical 112x112 crop using a
    similarity transform fitted between the detected landmarks and the
    ArcFace reference template."""
    src = np.array(landmarks_5, dtype=np.float32)
    matrix, _ = cv2.estimateAffinePartial2D(src, ARCFACE_DST, method=cv2.LMEDS)
    if matrix is None:
        raise RuntimeError("Could not align detected face")
    return cv2.warpAffine(img, matrix, (image_size, image_size), borderValue=0.0)


def get_embedding(session, aligned_bgr):
    """Run the ArcFace recognizer on a 112x112 aligned BGR crop and return
    an L2-normalized 512-d embedding."""
    img = cv2.cvtColor(aligned_bgr, cv2.COLOR_BGR2RGB).astype(np.float32)
    img = (img - 127.5) / 127.5
    blob = np.transpose(img, (2, 0, 1))[np.newaxis, ...]
    input_name = session.get_inputs()[0].name
    output = session.run(None, {input_name: blob})[0][0]
    norm = np.linalg.norm(output)
    if norm > 0:
        output = output / norm
    return output


def main():
    if len(sys.argv) < 2:
        print("Error: Missing image path", file=sys.stderr)
        sys.exit(1)

    image_path = sys.argv[1]
    if not os.path.exists(image_path):
        print(f"Error: File not found at {image_path}", file=sys.stderr)
        sys.exit(1)

    ensure_models()

    # 1. Read image
    img = cv2.imread(image_path)
    if img is None:
        print("Error: Could not decode image", file=sys.stderr)
        sys.exit(1)

    h, w, _ = img.shape
    if w < 100 or h < 100:
        print("Error: Image resolution is too low. Minimum dimension required is 100x100 pixels.", file=sys.stderr)
        sys.exit(1)

    # 2. Image quality checks
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    mean_brightness = np.mean(gray)
    if mean_brightness < 25:
        print("Error: Image is too dark. Please upload a well-lit photograph.", file=sys.stderr)
        sys.exit(1)

    laplacian_var = cv2.Laplacian(gray, cv2.CV_64F).var()
    if laplacian_var < 10.0:
        print("Error: Image is too blurry. Please upload a clear, sharp photograph.", file=sys.stderr)
        sys.exit(1)

    # 3. Detect face + 5-point landmarks
    detector = cv2.FaceDetectorYN.create(YUNET_PATH, "", (w, h), score_threshold=0.5)
    detector.setInputSize((w, h))
    _, faces = detector.detect(img)

    if faces is None or len(faces) == 0:
        print("Error: No face detected in the image.", file=sys.stderr)
        sys.exit(1)

    if len(faces) > 1:
        print("Error: Multiple faces detected. Please upload an image containing exactly one face.", file=sys.stderr)
        sys.exit(1)

    face = faces[0]
    # YuNet output layout: [x, y, w, h, then 5 (x, y) landmark pairs, score]
    landmarks = face[4:14].reshape(5, 2)

    # 4. Align crop and extract the ArcFace embedding
    try:
        aligned = align_face(img, landmarks)
        session = ort.InferenceSession(ARCFACE_PATH, providers=["CPUExecutionProvider"])
        embedding = get_embedding(session, aligned)
    except Exception as e:
        print(f"Error: Face alignment/embedding failed ({e})", file=sys.stderr)
        sys.exit(1)

    # Output embedding as a comma-separated string on stdout
    print(",".join(map(str, embedding.tolist())))


if __name__ == "__main__":
    main()

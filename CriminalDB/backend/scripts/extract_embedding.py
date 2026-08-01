import sys
import os
import urllib.request
import cv2
import numpy as np

# URLs for models
YUNET_URL = "https://github.com/opencv/opencv_zoo/raw/main/models/face_detection_yunet/face_detection_yunet_2023mar.onnx"
SFACE_URL = "https://github.com/opencv/opencv_zoo/raw/main/models/face_recognition_sface/face_recognition_sface_2021dec.onnx"

# Local model paths
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
MODELS_DIR = os.path.join(SCRIPT_DIR, "models")
YUNET_PATH = os.path.join(MODELS_DIR, "face_detection_yunet_2023mar.onnx")
SFACE_PATH = os.path.join(MODELS_DIR, "face_recognition_sface_2021dec.onnx")

def download_file(url, dest_path):
    print(f"Downloading {url} to {dest_path}...", file=sys.stderr)
    os.makedirs(os.path.dirname(dest_path), exist_ok=True)
    urllib.request.urlretrieve(url, dest_path)
    print("Download complete.", file=sys.stderr)

def main():
    if len(sys.argv) < 2:
        print("Error: Missing image path", file=sys.stderr)
        sys.exit(1)
        
    image_path = sys.argv[1]
    if not os.path.exists(image_path):
        print(f"Error: File not found at {image_path}", file=sys.stderr)
        sys.exit(1)

    # 1. Download models if they don't exist
    if not os.path.exists(YUNET_PATH):
        download_file(YUNET_URL, YUNET_PATH)
    if not os.path.exists(SFACE_PATH):
        download_file(SFACE_URL, SFACE_PATH)

    # 2. Read image
    img = cv2.imread(image_path)
    if img is None:
        print("Error: Could not decode image", file=sys.stderr)
        sys.exit(1)

    h, w, _ = img.shape
    if w < 100 or h < 100:
        print("Error: Image resolution is too low. Minimum dimension required is 100x100 pixels.", file=sys.stderr)
        sys.exit(1)

    # 2.5 Image Quality Check
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    mean_brightness = np.mean(gray)
    if mean_brightness < 25:
        print("Error: Image is too dark. Please upload a well-lit photograph.", file=sys.stderr)
        sys.exit(1)

    laplacian_var = cv2.Laplacian(gray, cv2.CV_64F).var()
    if laplacian_var < 10.0:
        print("Error: Image is too blurry. Please upload a clear, sharp photograph.", file=sys.stderr)
        sys.exit(1)

    # 3. Detect Face
    detector = cv2.FaceDetectorYN.create(YUNET_PATH, "", (w, h), score_threshold=0.5)
    detector.setInputSize((w, h))
    _, faces = detector.detect(img)

    if faces is None or len(faces) == 0:
        print("Error: No face detected in the image.", file=sys.stderr)
        sys.exit(1)

    if len(faces) > 1:
        print("Error: Multiple faces detected. Please upload an image containing exactly one face.", file=sys.stderr)
        sys.exit(1)

    # Use the first detected face
    face = faces[0]

    # 4. Extract Embedding using SFace
    recognizer = cv2.FaceRecognizerSF.create(SFACE_PATH, "")
    aligned_face = recognizer.alignCrop(img, face)
    embedding = recognizer.feature(aligned_face)

    # Output embedding as comma-separated string to stdout
    emb_list = embedding[0].tolist()
    print(",".join(map(str, emb_list)))

if __name__ == "__main__":
    main()

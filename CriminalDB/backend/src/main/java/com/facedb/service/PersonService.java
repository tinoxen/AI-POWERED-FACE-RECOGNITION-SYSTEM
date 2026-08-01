package com.facedb.service;

import com.facedb.dto.PersonResponse;
import com.facedb.model.Person;
import com.facedb.repository.PersonRepository;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class PersonService {

    private final PersonRepository personRepository;

    public PersonService(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    public List<Person> findAll() {
        return personRepository.findAll();
    }

    public List<Person> search(String query) {
        if (query == null || query.isBlank()) return findAll();
        // Numeric query -> treat as an ID lookup as well as a name search.
        if (query.matches("\\d+")) {
            return personRepository.findById(Long.parseLong(query))
                    .map(List::of)
                    .orElseGet(() -> personRepository.findByFullNameContainingIgnoreCase(query));
        }
        return personRepository.findByFullNameContainingIgnoreCase(query);
    }

    public Person findById(Long id) {
        return personRepository.findById(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("Person not found: " + id));
    }

    public Person save(Person person) {
        return personRepository.save(person);
    }

    public void delete(Long id) {
        personRepository.deleteById(id);
    }

    public int convertExistingPhotosToWebP() {
        List<Person> people = personRepository.findAll();
        Path uploadsDir = Paths.get("uploads");
        int convertedCount = 0;

        for (Person person : people) {
            String photoPath = person.getPhotoPath();
            if (photoPath == null || photoPath.toLowerCase().endsWith(".webp")) {
                continue;
            }

            Path sourcePath = Paths.get(photoPath);
            if (!Files.exists(sourcePath)) {
                continue;
            }

            try {
                BufferedImage image = ImageIO.read(sourcePath.toFile());
                if (image == null) {
                    continue;
                }

                Files.createDirectories(uploadsDir);
                Path targetPath = uploadsDir.resolve(UUID.randomUUID() + ".webp").normalize();
                if (!targetPath.startsWith(uploadsDir)) {
                    throw new IllegalArgumentException("Invalid target upload path");
                }

                if (!ImageIO.write(image, "webp", targetPath.toFile())) {
                    throw new RuntimeException("Failed to write WebP file for " + sourcePath);
                }

                Files.deleteIfExists(sourcePath);
                person.setPhotoPath(targetPath.toString());
                personRepository.save(person);
                convertedCount++;
            } catch (Exception ignored) {
                // Skip files we cannot convert and continue with the rest.
            }
        }

        return convertedCount;
    }

    public String extractFaceEmbedding(String photoPath) {
        if (photoPath == null) return null;
        try {
            String absolutePath = new java.io.File(photoPath).getAbsolutePath();
            String scriptPath = "scripts/extract_embedding.py";
            
            ProcessBuilder pb = new ProcessBuilder("python", scriptPath, absolutePath);
            pb.environment().put("OPENCV_LOG_LEVEL", "ERROR");
            pb.redirectErrorStream(false);
            Process process = pb.start();
            
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream())
            );
            
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            int exitCode = process.waitFor();
            String result = output.toString().trim();
            if (exitCode != 0) {
                java.io.BufferedReader errReader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getErrorStream())
                );
                StringBuilder errOutput = new StringBuilder();
                while ((line = errReader.readLine()) != null) {
                    errOutput.append(line).append("\n");
                }
                String errResult = errOutput.toString().trim();
                
                if (errResult.contains("Error:")) {
                    int errIdx = errResult.indexOf("Error:");
                    String errLine = errResult.substring(errIdx).split("\n")[0];
                    throw new IllegalArgumentException(errLine.replace("Error:", "").trim());
                }
                throw new RuntimeException("Python face embedding extraction failed: " + errResult);
            }
            return result;
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new RuntimeException("Failed to run face recognition service", e);
        }
    }

    public double calculateCosineSimilarity(String emb1, String emb2) {
        if (emb1 == null || emb2 == null) return 0.0;
        try {
            String[] tokens1 = emb1.split(",");
            String[] tokens2 = emb2.split(",");
            if (tokens1.length != tokens2.length || tokens1.length == 0) return 0.0;
            
            double dotProduct = 0.0;
            double normA = 0.0;
            double normB = 0.0;
            
            for (int i = 0; i < tokens1.length; i++) {
                double valA = Double.parseDouble(tokens1[i]);
                double valB = Double.parseDouble(tokens2[i]);
                dotProduct += valA * valB;
                normA += valA * valA;
                normB += valB * valB;
            }
            
            if (normA == 0.0 || normB == 0.0) return 0.0;
            return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
        } catch (Exception e) {
            return 0.0;
        }
    }

    public double mapSimilarityToScore(double sim) {
        if (sim <= 0.0) return 0.0;
        double score;
        if (sim < 0.20) {
            score = sim * 250.0;
        } else if (sim < 0.30) {
            score = 50.0 + (sim - 0.20) * 250.0;
        } else if (sim < 0.40) {
            score = 75.0 + (sim - 0.30) * 150.0;
        } else if (sim < 0.50) {
            score = 90.0 + (sim - 0.40) * 50.0;
        } else {
            score = 95.0 + (sim - 0.50) * 10.0;
            if (score > 100.0) score = 100.0;
        }
        return Math.round(score * 100.0) / 100.0;
    }

    public List<PersonResponse> findTopMatches(String queryEmbedding, int limit) {
        return personRepository.findAll().stream()
                .filter(p -> p.getFaceEmbedding() != null)
                .map(p -> {
                    PersonResponse resp = PersonResponse.from(p);
                    double similarity = calculateCosineSimilarity(queryEmbedding, p.getFaceEmbedding());
                    resp.matchScore = mapSimilarityToScore(similarity);
                    return resp;
                })
                .filter(resp -> resp.matchScore >= 75.0)
                .sorted((a, b) -> Double.compare(b.matchScore, a.matchScore))
                .limit(limit)
                .toList();
    }
}
